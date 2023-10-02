package de.intranda.goobi.plugins;

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

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class AlmaApiCommand {
    // pattern that matches every block enclosed by a pair of {}
    private static final Pattern PATTERN = Pattern.compile("(\\{[^\\{\\}]*\\})");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\{\\$[^\\{\\}]*\\})");
    //    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\$[^\\{\\}]*)");
    private static final Map<String, List<String>> STATIC_VARIABLES_MAP = new HashMap<>();
    //    @Getter
    //    private String endpoint;
    @Getter
    private List<String> endpoints;
    @Getter
    private String method;
    @Getter
    private Map<String, String> parametersMap = new HashMap<>();

    @Getter
    private String filterKey;
    @Getter
    private String filterValue;
    @Getter
    private String filterAlternativeOption;
    @Getter
    private String targetVariable;
    @Getter
    private String targetPath;

    //    private List<String> staticVariablesNeeded = new ArrayList<>(); // variables that shall be used to complete the endpoint, formulated as {$VARIALBE_NAME}

    public AlmaApiCommand(HierarchicalConfiguration config) {
        String rawEndpoint = config.getString("@endpoint");
        //        endpoint = completeEndpoint(rawEndpoint, config);
        initializeEndpoints(rawEndpoint, config);
        method = config.getString("@method");

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
            HierarchicalConfiguration targetConfig = config.configurationAt("target");
            initializeTargetFields(targetConfig);

        } catch (IllegalArgumentException e) {
            // merely used to make <target> optional, nothing special needs to be done here
        }

        //        log.debug("endpoint = " + endpoint);
        log.debug("method = " + method);
    }

    public void updateAllEndpoints() {
        // we only have to replace static variables here one by one
        for (String endpoint : endpoints) {
            replaceAllStaticVariablesInEndpoints(endpoint);
        }
    }

    private void initializeEndpoints(String rawEndpoint, HierarchicalConfiguration config) {
        String rawEndpointReplaced = completeEndpoint(rawEndpoint, config);

        endpoints = new ArrayList<>();
        endpoints.add(rawEndpointReplaced);

        boolean staticVariablesReplaced = replaceAllStaticVariablesInEndpoints(rawEndpointReplaced);
        if (!staticVariablesReplaced) {
            // TODO: report error
        }

        // static variables may be multiple, but configurable variables will appear only once
        // get the list of all static variables that will be used
        //        Set<String> staticVariablesNeeded = getStaticVariablesNeeded(rawEndpointReplaced);
        //        if (staticVariablesNeeded == null) {
        //            // TODO: report error
        //            return;
        //        }
        //
        //        for (String staticVariable : staticVariablesNeeded) {
        //            // staticVariable is key in the STATIC_VARIABLES_MAP, and it also appears as context in the endpoint
        //            // hence just replace them with values from the STATIC_VARIABLES_MAP
        //            endpoints = replaceStaticVariableInEndpoints(endpoints, staticVariable);
        //        }
        
        // TODO: check existence of other variables, and if so, report error

        // replace all configurable variables in all endpoints
        //        endpoints = replaceAllCustomVariablesInEndpoints(endpoints, config);
    }

    private boolean replaceAllStaticVariablesInEndpoints(String rawEndpoint) {
        // static variables may be multiple, but configurable variables will appear only once
        // get the list of all static variables that will be used
        Set<String> staticVariablesNeeded = getStaticVariablesNeeded(rawEndpoint);
        if (staticVariablesNeeded == null) {
            // TODO: report error
            return false;
        }

        for (String staticVariable : staticVariablesNeeded) {
            // staticVariable is key in the STATIC_VARIABLES_MAP, and it also appears as context in the endpoint
            // hence just replace them with values from the STATIC_VARIABLES_MAP
            endpoints = replaceStaticVariableInEndpoints(endpoints, staticVariable);
        }

        return true;
    }

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

    private List<String> replaceStaticVariableInEndpoints(List<String> rawEndpoints, String staticVariable) {
        List<String> results = new ArrayList<>();
        for (String rawEndpoint : rawEndpoints) {
            List<String> endpoints = replaceStaticVariableInEndpoint(rawEndpoint, staticVariable);
            results.addAll(endpoints);
        }

        return results;
    }

    private List<String> replaceStaticVariableInEndpoint(String rawEndpoint, String staticVariable) {
        List<String> results = new ArrayList<>();
        List<String> possibleValues = STATIC_VARIABLES_MAP.get(staticVariable);
        for (String value : possibleValues) {
            String endpoint = rawEndpoint.replace(staticVariable, value);
            results.add(endpoint);
        }

        return results;
    }

    private void initializeFilterFields(HierarchicalConfiguration config) {
        filterKey = config.getString("@key", "");
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

        filterAlternativeOption = config.getString("@alt", ""); // all | none | first | last | random
        // TODO: check option
    }

    private void initializeTargetFields(HierarchicalConfiguration config) {
        targetVariable = config.getString("@var");
        targetPath = config.getString("@path");
    }

    private List<String> replaceAllCustomVariablesInEndpoints(List<String> rawEndpoints, HierarchicalConfiguration config) {
        List<String> results = new ArrayList<>();

        for (String rawEndpoint : rawEndpoints) {
            String endpoint = completeEndpoint(rawEndpoint, config);
            results.add(endpoint);
        }

        return results;
    }

    private String completeEndpoint(String rawEndpoint, HierarchicalConfiguration config) {
        // replace all configured variables to complete the endpoint
        Map<String, String> variablesMap = getCustomVariablesMap(rawEndpoint); // {mms_id} -> mms_id
        String result = rawEndpoint;
        for (Map.Entry<String, String> variable : variablesMap.entrySet()) {
            String variableContext = variable.getKey();
            String variableName = variable.getValue();
            String variableValue = config.getString(variableName);
            // TODO: report error when there is no such variable configured
            result = result.replace(variableContext, variableValue);
        }

        // report error if there are still {} left
        //        if (result.contains("{") || result.contains("}")) {
        //            // TODO: report error
        //        }

        return result;
    }

    private Map<String, String> getCustomVariablesMap(String line) {
        Map<String, String> variablesMap = new HashMap<>();
        // get a list of all variables enclosed in {}
        Matcher matcher = PATTERN.matcher(line);
        while (matcher.find()) {
            String matchedText = matcher.group();
            log.debug("matchedText = " + matchedText);
            String variableName = matchedText.replaceAll("[\\{\\}]", "");
            log.debug("variableName = " + variableName);
            variablesMap.put(matchedText, variableName);
        }

        return variablesMap;
    }

    public static boolean updateStaticVariablesMap(String variable, String value) {
        if (StringUtils.isBlank(value)) {
            log.debug("The variable's value should not be blank.");
            return false;
        }

        List<String> values = Arrays.asList(new String[] { value });
        return updateStaticVariablesMap(variable, values);
    }

    public static boolean updateStaticVariablesMap(String variable, List<String> values) {
        if (StringUtils.isBlank(variable)) {
            // no variable defined, hence no need to update
            return true;
        }

        String wrappedKey = wrapKey(variable);
        log.debug("updating variable: " + wrappedKey);

        if (STATIC_VARIABLES_MAP.containsKey(wrappedKey)) {
            // report error
            log.debug("The variable '" + variable + "' already exists. Aborting...");
            return false;
        }

        if (values == null || values.isEmpty()) {
            // report error
            log.debug("The value of the new variable '" + variable + "' should not be empty or null.");
            return false;
        }

        STATIC_VARIABLES_MAP.put(wrappedKey, values);
        log.debug("Static variables map updated: " + wrappedKey + " -> " + values.toString());
        return true;
    }

    private static String wrapKey(String key) {
        //        Consumer<String> getEnding = (s) -> {return s.endsWith("}") ? "" : "}";};

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

}
