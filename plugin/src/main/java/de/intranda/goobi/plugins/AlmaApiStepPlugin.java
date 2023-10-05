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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

@PluginImplementation
@Log4j2
public class AlmaApiStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_alma_api";
    @Getter
    private Step step;
    private Process process;
    private int processId;

    private String returnPath;

    private String url;
    private String apiKey;
    private transient List<AlmaApiCommand> commandList = new ArrayList<>();
    private transient List<ProcessPropertyTemplate> propertyList = new ArrayList<>();

    // create a custom response handler
    private static final ResponseHandler<String> RESPONSE_HANDLER = response -> {
        HttpEntity entity = response.getEntity();
        return entity != null ? EntityUtils.toString(entity) : null;
    };

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.process = step.getProzess();
        this.processId = process.getId();

        // read parameters from correct block in configuration file
        SubnodeConfiguration config = ConfigPlugins.getProjectAndStepConfig(title, step);

        url = config.getString("url", "");
        apiKey = config.getString("api-key", "");

        // initialize the static variable map in AlmaApiCommand, which will be needed to create new AlmaApiCommand instances
        List<HierarchicalConfiguration> variableConfigs = config.configurationsAt("variable");
        initializeVariablesMap(variableConfigs);

        // initialize the list of all commands that will be run
        List<HierarchicalConfiguration> commandConfigs = config.configurationsAt("command");
        for (HierarchicalConfiguration commandConfig : commandConfigs) {
            commandList.add(new AlmaApiCommand(commandConfig));
        }

        // initialize the list of all process properties that will be saved after running all commands
        List<HierarchicalConfiguration> propertyConfigs = config.configurationsAt("property");
        for (HierarchicalConfiguration propertyConfig : propertyConfigs) {
            String propertyName = propertyConfig.getString("@name");
            String propertyValue = propertyConfig.getString("@value");
            String propertyIndex = propertyConfig.getString("@index", "all");
            boolean overwrite = propertyConfig.getBoolean("@overwrite", false);
            propertyList.add(new ProcessPropertyTemplate(propertyName, propertyValue, propertyIndex, overwrite));
        }

        String message = "AlmaApi step plugin initialized.";
        logBoth(processId, LogType.INFO, message);
    }

    /**
     * initialize the static variables map in AlmaApiCommand, which will be used during creations of AlmaApiCommand objects
     * 
     * @param variableConfigs a list of HierarchicalConfiguration objects
     */
    private void initializeVariablesMap(List<HierarchicalConfiguration> variableConfigs) {
        for (HierarchicalConfiguration variableConfig : variableConfigs) {
            String variableName = variableConfig.getString("@name");
            String variableValue = getVariableValue(variableConfig);

            boolean staticVariablesUpdated = AlmaApiCommand.updateStaticVariablesMap(variableName, variableValue);
            if (staticVariablesUpdated) {
                log.info("Static variable added: " + variableName + " -> " + variableValue);
            } else {
                log.error("Failed to add variable: " + variableName);
            }

        }
    }

    /**
     * get the configured variable value, which may be Goobi variables
     * 
     * @param variableConfig HierarchicalConfiguration
     * @return value of the variable after replacing Goobi variables
     */
    private String getVariableValue(HierarchicalConfiguration variableConfig) {
        if (!variableConfig.containsKey("@value")) {
            String message = "To define a <variable> tag, one has to specify its @value attribute. Usage of Goobi variables is also allowed.";
            logBoth(processId, LogType.WARN, message);
            return "";
        }

        String value = variableConfig.getString("@value");

        try {
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument dd = fileformat.getDigitalDocument();
            Prefs prefs = process.getRegelsatz().getPreferences();
            VariableReplacer replacer = new VariableReplacer(dd, prefs, process, step);

            return replacer.replace(value);

        } catch (ReadException | IOException | SwapException e) {
            String message = "Failed to read the metadata file.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return "";

        } catch (PreferencesException e) {
            String message = "PreferencesException caught while trying to get the digital document.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_alma_api.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
        // your logic goes here
        for (AlmaApiCommand command : commandList) {
            successful = successful && prepareAndRunCommand(command);
        }

        for (ProcessPropertyTemplate property : propertyList) {
            successful = successful && saveProperty(property);
        }

        String message = "AlmaApi step plugin executed.";
        logBoth(processId, LogType.INFO, message);
        return successful ? PluginReturnValue.FINISH : PluginReturnValue.ERROR;
    }

    /**
     * prepare a command and run it
     * 
     * @param command AlmaApiCommand
     * @return true if the command is successfully run, false if any exception occurred
     */
    private boolean prepareAndRunCommand(AlmaApiCommand command) {
        try {
            // update endpoints
            command.updateAllEndpoints();
            // get method
            String method = command.getMethod();

            Map<String, String> parameters = command.getParametersMap();
            List<String> endpoints = command.getEndpoints();
            Map<String, String> targetVariablePathMap = command.getTargetVariablePathMap();

            String filterKey = command.getFilterKey();
            String filterFallbackKey = command.getFilterFallbackKey();
            String filterValue = command.getFilterValue();
            String filterAlternativeOption = command.getFilterAlternativeOption();

            for (String endpoint : endpoints) {
                String requestUrl = createRequestUrl(endpoint, parameters);
                log.debug("requestUrl = " + requestUrl);

                // run the command
                JSONObject jsonObject = runCommand(method, requestUrl);

                if (jsonObject != null) {
                    log.debug("------- jsonObject -------");
                    log.debug(jsonObject.toString());
                    log.debug("------- jsonObject -------");
                    
                    Map<String, List<Object>> filteredTargetsMap = JSONUtils.getFilteredValuesFromSource(targetVariablePathMap, filterKey,
                            filterFallbackKey, filterValue, filterAlternativeOption, jsonObject);

                    for (Map.Entry<String, List<Object>> filteredTargets : filteredTargetsMap.entrySet()) {
                        String targetVariable = filteredTargets.getKey();
                        List<Object> filteredValues = filteredTargets.getValue();
                        if (filteredValues.isEmpty()) {
                            log.debug("no match found");
                        }

                        // save the filteredValues
                        List<String> targetValues = filteredValues.stream()
                                .map(String::valueOf)
                                .collect(Collectors.toList());

                        boolean staticVariablesUpdated = AlmaApiCommand.updateStaticVariablesMap(targetVariable, targetValues);
                        if (!staticVariablesUpdated) {
                            log.debug("static variables map was not successfully updated");
                        }

                    }
                }
            }
            return true;

        } catch (Exception e) {
            // any kind of exception should stop further steps, e.g. NullPointerException
            String message = "Exception caught while running commands: " + e.toString();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;
        }

    }

    /**
     * save process properties
     * 
     * @param propertyTemplate ProcessPropertyTemplate
     * @return true if successful, false if any exception occurred
     */
    private boolean saveProperty(ProcessPropertyTemplate propertyTemplate) {
        try {
            String propertyName = propertyTemplate.getName();
            String wrappedKey = AlmaApiCommand.wrapKey(propertyTemplate.getValue());
            List<String> propertyValues = AlmaApiCommand.getVariableValues(wrappedKey);

            // determine property value according to the configured choice
            String propertyValue = getPropertyValue(propertyValues, propertyTemplate.getChoice());

            // get the Processproperty object
            Processproperty propertyObject = getProcesspropertyObject(propertyName, propertyTemplate.isOverwrite());
            propertyObject.setWert(propertyValue);
            PropertyManager.saveProcessProperty(propertyObject);

            return true;

        } catch (Exception e) {
            // any kind of exception should stop further steps, e.g. NullPointerException
            String message = "Exception caught while saving process properties: " + e.toString();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;
        }

    }

    /**
     * get the property value that should be saved
     * 
     * @param propertyValues the list of property values to choose from
     * @param choice options are all | first | last | random, DEFAULT all
     * @return property value that is to be saved
     */
    private String getPropertyValue(List<String> propertyValues, String choice) {
        switch (choice.toLowerCase()) {
            case "first":
                return propertyValues.get(0);
            case "last":
                return propertyValues.get(propertyValues.size() - 1);
            case "random":
                return propertyValues.get(new Random().nextInt(propertyValues.size()));
            default:
                // combine all values
        }

        StringBuilder sb = new StringBuilder();
        for (String propertyValue : propertyValues) {
            sb.append(propertyValue).append(", ");
        }

        String propertyValue = sb.toString();

        return propertyValue.substring(0, propertyValue.lastIndexOf(", "));
    }

    /**
     * get the Processproperty object
     * 
     * @param title title of the Processproperty object
     * @param overwrite true if an existing old Processproperty object should be used, false if a new one should be created no matter what
     * @return the Processproperty object that is to be saved
     */
    private Processproperty getProcesspropertyObject(String title, boolean overwrite) {
        if (overwrite) {
            // try to retrieve the old object first
            List<Processproperty> props = PropertyManager.getProcessPropertiesForProcess(processId);
            for (Processproperty p : props) {
                if (title.equals(p.getTitel())) {
                    return p;
                }
            }
        }
        // otherwise, create a new one
        Processproperty property = new Processproperty();
        property.setTitel(title);
        property.setProcessId(processId);

        return property;
    }

    /**
     * create the full request url
     * 
     * @param endpoint endpoint string whose space holders as well as variables are already properly replaced
     * @param parameters a map of parameters
     * @return the full request url
     */
    private String createRequestUrl(String endpoint, Map<String, String> parameters) {
        // combine url and endpoint to form the base
        StringBuilder urlBuilder = new StringBuilder(url);
        if (!url.endsWith("/") && !endpoint.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(endpoint);
        urlBuilder.append("?");

        // append all parameters
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            String parameterName = parameter.getKey();
            String parameterValue = parameter.getValue();
            urlBuilder.append(parameterName)
                    .append("=")
                    .append(parameterValue)
                    .append("&");
        }

        // append the api key
        urlBuilder.append("apikey=")
                .append(apiKey);

        return urlBuilder.toString();
    }

    /**
     * run the command
     * 
     * @param method REST method
     * @param url request url
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommand(String method, String url) {
        return runCommand(method, url, "");
    }

    /**
     * run the command
     * 
     * @param method REST method
     * @param url request url
     * @param json JSON body that is to be sent by request, NOT IN USE YET
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommand(String method, String url, String json) {
        return "get".equalsIgnoreCase(method) ? runCommandGet(url) : runCommandNonGet(method, url, json);
    }

    /**
     * run the command via GET method
     * 
     * @param url request url
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommandGet(String url) {
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Content-type", "application/json");

            String message = "Executing request " + httpGet.getRequestLine();
            logBoth(processId, LogType.INFO, message);

            String responseBody = client.execute(httpGet, RESPONSE_HANDLER);

            return JSONUtils.getJSONObjectFromString(responseBody);

        } catch (IOException e) {
            String message = "IOException caught while executing request: " + httpGet.getRequestLine();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return null;

        } catch (ParseException e) {
            String message = "ParseException caught while executing request: " + httpGet.getRequestLine();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * run the command via a method other than GET
     * 
     * @param method REST method
     * @param url request url
     * @param json JSON body that is to be sent by request
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommandNonGet(String method, String url, String json) {
        HttpEntityEnclosingRequestBase httpBase;
        switch (method.toLowerCase()) {
            case "put":
                httpBase = new HttpPut(url);
                break;
            case "post":
                httpBase = new HttpPost(url);
                break;
            case "patch":
                httpBase = new HttpPatch(url);
                break;
            default: // unknown
                return null;
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpBase.setHeader("Accept", "application/json");
            httpBase.setHeader("Content-type", "application/json");
            httpBase.setEntity(new StringEntity(json));

            String message = "Executing request " + httpBase.getRequestLine();
            logBoth(processId, LogType.INFO, message);

            String responseBody = client.execute(httpBase, RESPONSE_HANDLER);
            return JSONUtils.getJSONObjectFromString(responseBody);

        } catch (IOException e) {
            String message = "IOException caught while executing request: " + httpBase.getRequestLine();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return null;

        } catch (ParseException e) {
            String message = "ParseException caught while executing request: " + httpBase.getRequestLine();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * print logs to terminal and journal
     * 
     * @param processId id of the Goobi process
     * @param logType type of the log
     * @param message message to be shown to both terminal and journal
     */
    private void logBoth(int processId, LogType logType, String message) {
        String logMessage = "AlmaApi Step Plugin: " + message;
        switch (logType) {
            case ERROR:
                log.error(logMessage);
                break;
            case DEBUG:
                log.debug(logMessage);
                break;
            case WARN:
                log.warn(logMessage);
                break;
            default: // INFO
                log.info(logMessage);
                break;
        }
        if (processId > 0) {
            Helper.addMessageToProcessJournal(processId, logType, logMessage);
        }
    }

}
