/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang3.StringUtils;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class AlmaApiCommand {
    // pattern that matches every block enclosed by a pair of {}
    private static final Pattern PATTERN = Pattern.compile("(\\{[^\\{\\}]*\\})");
    // pattern that matches every variable block in the format of {$___}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\{\\$[^\\{\\}]*\\})");
    // static variables created before creations of all commands or created by previous commands, shared by all commands
    private static final Map<String, List<String>> STATIC_VARIABLES_MAP = new HashMap<>();

    private static final StorageProviderInterface storageProvider = StorageProvider.getInstance();
    @Getter
    private List<String> endpoints;
    @Getter
    private String method;
    @Getter
    private Map<String, String> parametersMap = new HashMap<>();
    @Getter
    private String filterKey;
    @Getter
    private String filterFallbackKey;
    @Getter
    private String filterValue;
    @Getter
    private String filterAlternativeOption;
    @Getter
    private Map<String, String> targetVariablePathMap;
    @Getter
    private String headerAccept;
    @Getter
    private String headerContentType;
    @Getter
    private String bodyValue;

    public AlmaApiCommand(HierarchicalConfiguration config) {
        String rawEndpoint = config.getString("@endpoint");
        initializeEndpoints(rawEndpoint, config);
        method = config.getString("@method");
        headerAccept = wrapHeaderAccept(config.getString("@accept", "json"));
        log.debug("headerAccept = " + headerAccept);

        List<HierarchicalConfiguration> parameterConfigs = config.configurationsAt("parameter");
        for (HierarchicalConfiguration parameterConfig : parameterConfigs) {
            String parameterName = parameterConfig.getString("@name");
            String parameterValue = parameterConfig.getString("@value");
            parametersMap.put(parameterName, parameterValue);
        }

        try {
            HierarchicalConfiguration filterConfig = config.configurationAt("filter");
            initializeFilterFields(filterConfig);

        } catch (IllegalArgumentException e) {
            // merely used to make <filter> optional, nothing special needs to be done here
        }

        try {
            List<HierarchicalConfiguration> targetConfigs = config.configurationsAt("target");
            initializeTargetFields(targetConfigs);

        } catch (IllegalArgumentException e) {
            // merely used to make <target> optional, nothing special needs to be done here
        }

        // initialize body settings for current command if it is configured
        try {
            HierarchicalConfiguration bodyConfig = config.configurationAt("body");
            initializeBodyFields(bodyConfig);
        } catch (IllegalArgumentException e) {
            headerContentType = "application/json";
            bodyValue = "";
        }

    }

    public void updateAllEndpoints() {
        // we only have to replace static variables here one by one
        for (String endpoint : endpoints) {
            replaceAllStaticVariablesInEndpoints(endpoint);
        }
    }

    /**
     * initialize all endpoints via replacing all variables with proper values
     * 
     * @param rawEndpoint raw endpoint string which may contain placeholders enclosed by {} and variables in the format of {$VARIABLE}
     * @param config HierarchicalConfiguration
     */
    private void initializeEndpoints(String rawEndpoint, HierarchicalConfiguration config) {
        // static variables may be multiple, but configurable variables will appear only once
        String rawEndpointReplaced = replacePlaceholdersInEndpoint(rawEndpoint, config);

        endpoints = new ArrayList<>();
        endpoints.add(rawEndpointReplaced);

        boolean staticVariablesReplaced = replaceAllStaticVariablesInEndpoints(rawEndpointReplaced);
        if (!staticVariablesReplaced) {
            log.debug("static variables were not successfully replaced");
        }
    }

    /**
     * replace all static variables in endpoints
     * 
     * @param rawEndpoint the raw entpoint string without replacing any variables
     * @return true if all static variables in endpoints are properly replaced, false otherwise
     */
    private boolean replaceAllStaticVariablesInEndpoints(String rawEndpoint) {
        // get the list of all static variables that will be used
        Set<String> staticVariablesNeeded = getStaticVariablesNeeded(rawEndpoint);
        if (staticVariablesNeeded == null) {
            return false;
        }

        for (String staticVariable : staticVariablesNeeded) {
            // staticVariable is key in the STATIC_VARIABLES_MAP, and it also appears as context in the endpoint
            // hence just replace them with values from the STATIC_VARIABLES_MAP
            endpoints = replaceStaticVariableInEndpoints(endpoints, staticVariable);
        }

        return true;
    }

    /**
     * get the set of all static variables needed to replace in the input line
     * 
     * @param line string
     * @return the set of all static variables needed for replacement
     */
    private Set<String> getStaticVariablesNeeded(String line) {
        Set<String> variables = new HashSet<>();

        Matcher matcher = VARIABLE_PATTERN.matcher(line);
        while (matcher.find()) {
            String variableName = matcher.group();
            log.debug("static variable detected: " + variableName);
            if (STATIC_VARIABLES_MAP.containsKey(variableName)) {
                variables.add(variableName);
            } else {
                // unknown static variable
                log.debug("unknown static variable detected: " + variableName);
                return null; // as error code
            }
        }

        return variables;
    }

    /**
     * replace the input static variable in a list of endpoints
     * 
     * @param rawEndpoints a list of endpoints
     * @param staticVariable static variable that is to be replaced
     * @return a list of endpoints with their specified static variable being replaced
     */
    private List<String> replaceStaticVariableInEndpoints(List<String> rawEndpoints, String staticVariable) {
        List<String> results = new ArrayList<>();
        for (String rawEndpoint : rawEndpoints) {
            List<String> replacedEndpoints = replaceStaticVariableInEndpoint(rawEndpoint, staticVariable);
            results.addAll(replacedEndpoints);
        }

        return results;
    }

    /**
     * replace the input static variable in the endpoint
     * 
     * @param rawEndpoint endpoint string
     * @param staticVariable static variable that is to be replaced
     * @return a list of endpoints with their specified static variable being replaced
     */
    private List<String> replaceStaticVariableInEndpoint(String rawEndpoint, String staticVariable) {
        List<String> results = new ArrayList<>();
        List<String> possibleValues = STATIC_VARIABLES_MAP.get(staticVariable);
        for (String value : possibleValues) {
            String endpoint = rawEndpoint.replace(staticVariable, value);
            results.add(endpoint);
        }

        return results;
    }

    private String wrapHeaderAccept(String str) {
        final String headerAcceptHead = "application/";
        //        if (StringUtils.isBlank(str)) {
        //            // use default setting application/json
        //            return headerAcceptHead + "json";
        //        }

        String[] parts = str.split("/");
        String acceptType = parts[parts.length - 1].toLowerCase();
        if ("xml".equals(acceptType) || "json".equals(acceptType)) {
            return headerAcceptHead + acceptType;
        } else {
            log.debug("Unknown accept type: " + acceptType + ". Using JSON instead.");
            return headerAcceptHead + "json";
        }
    }

    /**
     * initialize all fields needed for filtering
     * 
     * @param config HierarchicalConfiguration
     */
    private void initializeFilterFields(HierarchicalConfiguration config) {
        filterKey = config.getString("@key", "");
        filterFallbackKey = config.getString("@fallback", filterKey);
        filterValue = config.getString("@value", "");
        if (filterValue.contains("$")) {
            // configured filterValue is a variable
            String wrappedKey = wrapKey(filterValue);
            if (!STATIC_VARIABLES_MAP.containsKey(wrappedKey)) {
                // variable not found, report error
                log.debug("unknown variable: " + filterValue);
                return;
            }

            // retrieve value from the variables map
            filterValue = STATIC_VARIABLES_MAP.get(wrappedKey).get(0);
            log.debug("filterValue after replacing static variable = " + filterValue);
        }

        filterAlternativeOption = parseFilterAlternativeOption(config.getString("@alt", "")); // all | none | first | last | random
    }

    /**
     * parse the configured filterAlternativeOption to avoid misuse
     * 
     * @param option configured option
     * @return all | first | last | random | none
     */
    private String parseFilterAlternativeOption(String option) {
        String result = "none";
        switch (option.toLowerCase()) {
            case "all":
            case "first":
            case "last":
            case "random":
                result = option.toLowerCase();
                break;
            default:
                // nothing special
        }

        return result;
    }

    /**
     * initialize all fields needed for saving variables
     * 
     * @param configs a list of HierarchicalConfiguration objects
     */
    private void initializeTargetFields(List<HierarchicalConfiguration> configs) {
        targetVariablePathMap = new HashMap<>();
        for (HierarchicalConfiguration config : configs) {
            String variable = config.getString("@var");
            String path = config.getString("@path");
            targetVariablePathMap.put(variable, path);
        }
    }

    private void initializeBodyFields(HierarchicalConfiguration config) {
        String type = config.getString("@type").toLowerCase();
        if ("xml".equals(type) || "json".equals(type)) {
            headerContentType = "application/" + type;
        } else {
            log.debug("Unknown body type: '" + type + "'. Using JSON instead.");
            headerContentType = "application/json";
        }

        String filePath = config.getString("@src");
        String fileContent = readFileContent(filePath);
        log.debug("------- FILE CONTENT -------");
        log.debug(fileContent);
        bodyValue = StringUtils.isBlank(fileContent) ? "" : fileContent;
    }

    private String readFileContent(String path) {
        log.debug("reading file content from: " + path);

        StringBuilder contentBuilder = new StringBuilder();
        Path filePath = Path.of(path);
        try {
            BufferedReader reader = Files.newBufferedReader(filePath);
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line + System.lineSeparator());
            }

            return contentBuilder.toString();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        //        try {
        //            FileInputStream inputStream = storageProvider.newInputStream(Path.of(path));
        //
        //        } catch (IOException e) {
        //            // TODO Auto-generated catch block
        //            e.printStackTrace();
        //        }

        return "";
    }

    /**
     * replace all place holders enclosed by {} by their configured values which may also be static variables created by previous commands
     * 
     * @param rawEndpoint endpoint string
     * @param config HierarchicalConfiguration
     * @return endpoint string whose place holders enclosed by {} are all replaced
     */
    private String replacePlaceholdersInEndpoint(String rawEndpoint, HierarchicalConfiguration config) {
        // replace all configured variables to complete the endpoint
        Map<String, String> placeholdersMap = getPlaceholdersMap(rawEndpoint); // {mms_id} -> mms_id
        String result = rawEndpoint;
        for (Map.Entry<String, String> variable : placeholdersMap.entrySet()) {
            String variableContext = variable.getKey();
            String variableName = variable.getValue();
            String variableValue = config.getString(variableName);
            // TODO: report error when there is no such variable configured
            result = result.replace(variableContext, variableValue);
        }

        return result;
    }

    /**
     * get a map of placeholders found in the line
     * 
     * @param line
     * @return a map whose keys are placeholders found in the line enclosed by {}, while its values are keys with {} removed
     */
    private Map<String, String> getPlaceholdersMap(String line) {
        Map<String, String> placeholdersMap = new HashMap<>();
        // get a list of all placeholders enclosed in {}
        Matcher matcher = PATTERN.matcher(line);
        while (matcher.find()) {
            String matchedText = matcher.group();
            String placeholderName = matchedText.replaceAll("[\\{\\}]", "");
            placeholdersMap.put(matchedText, placeholderName);
        }

        return placeholdersMap;
    }

    /**
     * update the static variables map
     * 
     * @param variable variable name
     * @param value variable value
     * @return true if the static variables map is successfully updated, false otherwise
     */
    public static boolean updateStaticVariablesMap(String variable, String value) {
        if (StringUtils.isBlank(value)) {
            log.debug("The variable's value should not be blank.");
            return false;
        }

        List<String> values = Arrays.asList(value);
        return updateStaticVariablesMap(variable, values);
    }

    /**
     * update the static variables map
     * 
     * @param variable variable name
     * @param values a list of possible variable values
     * @return true if the static variables map is successfully updated, false otherwise
     */
    public static boolean updateStaticVariablesMap(String variable, List<String> values) {
        if (StringUtils.isBlank(variable)) {
            // no variable defined, hence no need to update
            return true;
        }

        if (values == null || values.isEmpty()) {
            // report error
            log.debug("The value of the new variable '" + variable + "' should not be empty or null.");
            return false;
        }

        String wrappedKey = wrapKey(variable);
        log.debug("updating variable: " + wrappedKey);

        if (STATIC_VARIABLES_MAP.containsKey(wrappedKey)) {
            log.debug("The variable '" + variable + "' already exists. Updating...");
        }

        STATIC_VARIABLES_MAP.put(wrappedKey, values);
        log.debug("Static variables map updated: " + wrappedKey + " -> " + values.toString());
        return true;
    }

    /**
     * wrap the input variable name in the following way to formulate a proper map key: key -> {$key}
     * 
     * @param key variable name
     * @return {$key}
     */
    public static String wrapKey(String key) {
        if (key.startsWith("{$")) {
            return key + (key.endsWith("}") ? "" : "}");
        }

        if (key.startsWith("$")) {
            return "{" + key + (key.endsWith("}") ? "" : "}");
        }

        if (key.startsWith("{")) {
            return "{$" + key.substring(1) + (key.endsWith("}") ? "" : "}");
        }

        return "{$" + key + (key.endsWith("}") ? "" : "}");
    }

    /**
     * get values of the static variable
     * 
     * @param key name of the static variable whose value is to be retrieved
     * @return all possible values of this static variable
     */
    public static List<String> getVariableValues(String key) {
        return STATIC_VARIABLES_MAP.containsKey(key) ? STATIC_VARIABLES_MAP.get(key) : Arrays.asList(key);
    }

}
