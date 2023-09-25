package de.intranda.goobi.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.HierarchicalConfiguration;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class AlmaApiCommand {
    // pattern that matches every block enclosed by a pair of {}
    private static final Pattern pattern = Pattern.compile("(\\{[^\\{\\}]*\\})");
    @Getter
    private String endpoint;
    @Getter
    private String method;
    @Getter
    private Map<String, String> parametersMap = new HashMap<>();

    public AlmaApiCommand(HierarchicalConfiguration config) {
        String rawEndpoint = config.getString("@endpoint");
        endpoint = completeEndpoint(rawEndpoint, config);
        method = config.getString("@method");

        List<HierarchicalConfiguration> parameterConfigs = config.configurationsAt("parameter");
        for (HierarchicalConfiguration parameterConfig : parameterConfigs) {
            String parameterName = parameterConfig.getString("@name");
            String parameterValue = parameterConfig.getString("@value");
            parametersMap.put(parameterName, parameterValue);
        }
        
        log.debug("endpoint = " + endpoint);
        log.debug("method = " + method);
    }

    private String completeEndpoint(String rawEndpoint, HierarchicalConfiguration config) {
        Map<String, String> variablesMap = getVariablesMap(rawEndpoint);
        String result = rawEndpoint;
        for (Map.Entry<String, String> variable : variablesMap.entrySet()) {
            String variableContext = variable.getKey();
            String variableName = variable.getValue();
            String variableValue = config.getString(variableName);
            // TODO: report error when there is no such variable configured
            result = result.replace(variableContext, variableValue);
        }

        return result;
    }

    private Map<String, String> getVariablesMap(String line) {
        Map<String, String> variablesMap = new HashMap<>();
        // get a list of all variables enclosed in {}
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String matchedText = matcher.group();
            log.debug("matchedText = " + matchedText);
            String variableName = matchedText.replaceAll("[\\{\\}]", "");
            log.debug("variableName = " + variableName);
            variablesMap.put(matchedText, variableName);
        }

        return variablesMap;
    }

}
