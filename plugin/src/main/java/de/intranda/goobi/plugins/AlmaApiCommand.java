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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class AlmaApiCommand {
    // pattern that matches every block enclosed by a pair of {} // NOSONAR
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(\\{[^\\{\\}]*\\})");
    // pattern that matches every variable block in the format of {$___} // NOSONAR
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\{\\$[^\\{\\}]*\\})");
    // pattern that matches the xml header <?xml ... ?>
    private static final String XML_HEADER_PATTERN = "<\\?.*\\?>";
    // pattern that matches the xml comments <!-- ... -->
    private static final String XML_COMMENT_PATTERN = "<!--[\\s\\S]*?-->";
    // static variables created before creations of all commands or created by previous commands, shared by all commands
    private static final Map<String, List<Object>> STATIC_VARIABLES_MAP = new HashMap<>();
    @Getter
    private List<String> endpoints;
    @Getter
    private String method;
    @Getter
    private Map<String, String> parametersMap = new HashMap<>();
    @Getter
    private Map<String, String> headerParameters = new HashMap<>();

    @Getter
    private String filterKey;
    @Getter
    private String filterFallbackKey;
    @Getter
    private String filterValue;
    @Getter
    private String filterAlternativeOption;
    @Getter
    private List<Target> targets;
    @Getter
    private String updateVariableName;
    @Getter
    private Map<String, String> updateVariablePathValueMap;
    @Getter
    private String headerAccept;
    @Getter
    private String headerContentType;
    private String bodyValue;

    public AlmaApiCommand(HierarchicalConfiguration config) {
        String rawEndpoint = config.getString("@endpoint");
        initializeEndpoints(rawEndpoint, config);
        method = config.getString("@method");
        headerAccept = wrapHeader(config.getString("@accept", "json"));
        headerContentType = wrapHeader(config.getString("@content-type", "json"));

        List<HierarchicalConfiguration> parameterConfigs = config.configurationsAt("parameter");
        for (HierarchicalConfiguration parameterConfig : parameterConfigs) {
            String parameterName = parameterConfig.getString("@name");
            String parameterValue = parameterConfig.getString("@value");
            parametersMap.put(parameterName, parameterValue);
        }

        List<HierarchicalConfiguration> headerConfigs = config.configurationsAt("header");
        for (HierarchicalConfiguration parameterConfig : headerConfigs) {
            String parameterName = parameterConfig.getString("@name");
            String parameterValue = parameterConfig.getString("@value");
            headerParameters.put(parameterName, parameterValue);
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

        try {
            HierarchicalConfiguration updateConfig = config.configurationAt("update");
            initializeUpdateFields(updateConfig);

        } catch (IllegalArgumentException e) {
            // merely used to make <update> optional, nothing special needs to be done here
        }

        // initialize body settings for current command if it is configured
        try {
            HierarchicalConfiguration bodyConfig = config.configurationAt("body");
            bodyValue = getBodyValue(bodyConfig);

        } catch (IllegalArgumentException e) {
            headerContentType = "application/json";
            bodyValue = "";
        }

    }

    /**
     * update all endpoints via replacing all static variables in them
     */
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
                return null; // NOSONAR as error code
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
        List<String> possibleValues = getVariableValues(staticVariable);

        for (String value : possibleValues) {
            String endpoint = rawEndpoint.replace(staticVariable, value);
            results.add(endpoint);
        }

        return results;
    }

    /**
     * wrap header with the prefix application/
     * 
     * @param str header setting
     * @return header setting wrapped properly
     */
    private String wrapHeader(String str) {
        final String headerPrefix = "application/";

        String[] parts = str.split("/");
        String setting = parts.length > 0 ? parts[parts.length - 1].toLowerCase() : "json";
        if ("xml".equals(setting) || "json".equals(setting)) {
            return headerPrefix + setting;
        } else {
            log.debug("Unknown setting: " + setting + ". Using json instead.");
            return headerPrefix + "json";
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
            filterValue = getVariableValues(wrappedKey).get(0);
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
        targets = new ArrayList<>();
        for (HierarchicalConfiguration config : configs) {
            String variable = config.getString("@var");
            String path = config.getString("@path");
            String type = config.getString("@type", "string");
            Target t = new Target(variable, path, type);
            targets.add(t);
        }
    }

    /**
     * initialize all fields needed for saving updated response JSONObjects
     * 
     * @param config HierarchicalConfiguration
     */
    private void initializeUpdateFields(HierarchicalConfiguration config) {
        updateVariablePathValueMap = new HashMap<>();
        updateVariableName = config.getString("@var");
        List<HierarchicalConfiguration> entryConfigs = config.configurationsAt("entry");
        for (HierarchicalConfiguration entryConfig : entryConfigs) {
            String path = entryConfig.getString("@path");
            String value = entryConfig.getString("@value");
            updateVariablePathValueMap.put(path, value);
        }
    }

    /**
     * used to initialize the value of request body
     * 
     * @param config HierarchicalConfiguration
     * @return content of a file if @src is configured, otherwise just the configured @value
     */
    private String getBodyValue(HierarchicalConfiguration config) {
        // bodyValue can be content of a file if @src is configured, OR variable OR plain text value
        // @src can be a variable or a plain string
        String filePath = getMaybeVariableValue(config.getString("@src", ""));
        String wrapper = config.getString("@wrapper", "");
        // check if it should be content of a file
        if (StringUtils.isNotBlank(filePath)) {
            String fileContent = readFileContent(filePath);
            // remove all comments in the XML file, since otherwise it will regarded as NOT well-formed
            String fileContentWithoutComments = fileContent.replaceAll(XML_COMMENT_PATTERN, "");
            return wrapBodyValue(fileContentWithoutComments, wrapper, headerContentType);
        }

        // otherwise just return the configured value, since variables will be replaced later when @Getter is called
        return config.getString("@value", "");
    }

    /**
     * used to get the value of the request body
     * 
     * @return request body value with all of its variables being replaced properly
     */
    public String getBodyValue() {
        return getMaybeVariableValue(bodyValue);
    }

    /**
     * get value represented by the input string s, which MAYBE a variable
     * 
     * @param s String that may be plain text or variable
     * @return s itself if s is not a variable, otherwise the value of this variable
     */
    private String getMaybeVariableValue(String s) {
        String key = s.startsWith("{$") || s.startsWith("$") ? wrapKey(s) : s;
        return getVariableValues(key).get(0);
    }

    /**
     * read contents from a file
     * 
     * @param path absolute path as String of the file
     * @return contents of the file as String
     */
    private String readFileContent(String path) {
        log.debug("Reading file content from: " + path);
        StringBuilder contentBuilder = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(getContentFilePath(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line + System.lineSeparator());
            }

            return contentBuilder.toString();

        } catch (IOException e) {
            log.debug("Failed to read contents from " + path);
            e.printStackTrace();
            return "";
        }

    }

    /**
     * get the path of the content file that shall be read
     * 
     * @param path path string of a file or a directory containing the file
     * @return Path of the content file that shall be read, which will be its first file if the input path is a directory
     * @throws IOException
     */
    private Path getContentFilePath(String path) throws IOException {
        Path filePath = Path.of(path);
        // if filePath is a directory, return the path of its first file
        if (Files.isDirectory(filePath)) {
            try (Stream<Path> stream = Files.list(filePath)) {
                return stream.filter(file -> !Files.isDirectory(file))
                        .collect(Collectors.toList())
                        .get(0);
            }
        }

        return filePath;
    }

    /**
     * wrap the request body with the input wrapper
     * 
     * @param content string value that is to be wrapped
     * @param wrapper tokens that shall be wrapped around content
     * @param type type of the content, options are xml | json, any other input will return the content itself
     * @return content wrapped by wrapper
     */
    private String wrapBodyValue(String content, String wrapper, String type) {
        if (StringUtils.isBlank(wrapper)) {
            return content;
        }

        String[] wrappers = wrapper.split(" ");

        if (type.endsWith("json")) {
            return wrapBodyValueJSON(content, wrappers);
        }

        if (type.endsWith("xml")) {
            return wrapBodyValueXML(content, wrappers);
        }

        // unknown type
        return content;
    }

    /**
     * wrap the request body of type JSON with the input wrappers
     * 
     * @param content string value that is to be wrapped
     * @param wrappers an array of wrappers that shall be wrapped around content
     * @return content wrapped by wrappers
     */
    private String wrapBodyValueJSON(String content, String[] wrappers) {
        String result = content;
        for (int i = wrappers.length - 1; i >= 0; --i) {
            result = wrapBodyValueJSON(result, wrappers[i]);
        }

        return result;
    }

    /**
     * wrap the request body of type JSON with the input wrappers
     * 
     * @param content string value that is to be wrapped
     * @param wrapper the wrapper that shall be applied
     * @return content wrapped by wrapper
     */
    private String wrapBodyValueJSON(String content, String wrapper) {
        StringBuilder sb = new StringBuilder("{\"");
        sb.append(wrapper)
                .append("\": ")
                .append(content)
                .append("}");

        return sb.toString();
    }

    /**
     * wrap the request body of type XML with the input wrappers
     * 
     * @param content string value that is to be wrapped
     * @param wrappers an array of wrappers that shall be wrapped around content
     * @return content wrapped by wrappers
     */
    private String wrapBodyValueXML(String content, String[] wrappers) {
        // remove the header line <?xml ... ?>
        String result = content.replaceAll(XML_HEADER_PATTERN, "");
        for (int i = wrappers.length - 1; i >= 0; --i) {
            result = wrapBodyValueXML(result, wrappers[i]);
        }

        return result;
    }

    /**
     * wrap the request body of type XML with the input wrappers
     * 
     * @param content string value that is to be wrapped
     * @param wrapper the wrapper that shall be applied
     * @return content wrapped by wrapper
     */
    private String wrapBodyValueXML(String content, String wrapper) {
        StringBuilder sb = new StringBuilder("<");
        sb.append(wrapper)
                .append(">")
                .append(content)
                .append("</")
                .append(wrapper)
                .append(">");

        return sb.toString();
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
        // get a list of all placeholders enclosed in {} // NOSONAR
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(line);
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
    public static boolean updateStaticVariablesMap(String variable, Object value) {
        if (value == null) {
            log.debug("The variable's value should not be blank.");
            return false;
        }
        List<Object> data = new ArrayList<>();
        data.add(value);
        return updateStaticVariablesMap(variable, data);
    }

    /**
     * update the static variables map
     * 
     * @param variable variable name
     * @param values a list of possible variable values
     * @return true if the static variables map is successfully updated, false otherwise
     */
    public static boolean updateStaticVariablesMap(String variable, List<Object> values) {
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
        log.debug("Updating variable: " + wrappedKey);

        if (STATIC_VARIABLES_MAP.containsKey(wrappedKey)) {
            log.debug("The variable '" + variable + "' already exists. Updating...");
        }

        STATIC_VARIABLES_MAP.put(wrappedKey, values);
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
        if (!STATIC_VARIABLES_MAP.containsKey(key)) {
            return Arrays.asList(key);
        }
        List<Object> data = STATIC_VARIABLES_MAP.get(key);
        List<String> results = new ArrayList<>();
        for (Object obj : data) {
            if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (Object object : array.toArray()) {
                    results.add((String) object);
                }
            } else if (obj instanceof JSONObject) {
                JSONObject json = (JSONObject) obj;
                System.out.println(json.toString());
            } else {
                results.add(String.valueOf(obj));
            }
        }
        return results;
    }

}
