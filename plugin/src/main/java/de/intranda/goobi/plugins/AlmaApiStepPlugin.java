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
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

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
    private transient List<EntryToSaveTemplate> entriesToSaveList = new ArrayList<>();

    // create a custom response handler
    private static final ResponseHandler<String> RESPONSE_HANDLER = response -> {
        log.debug("------- STATUS --- LINE -------");
        log.debug(response.getStatusLine());
        log.debug("------- STATUS --- LINE -------");
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

        // initialize the list of all entries that will be saved after running all commands
        List<HierarchicalConfiguration> saveConfigs = config.configurationsAt("save");
        for (HierarchicalConfiguration saveConfig : saveConfigs) {
            String saveType = saveConfig.getString("@type");
            String saveName = saveConfig.getString("@name");
            String saveValue = saveConfig.getString("@value");
            String saveChoice = saveConfig.getString("@choice", "all");
            boolean overwrite = saveConfig.getBoolean("@overwrite", false);
            entriesToSaveList.add(new EntryToSaveTemplate(saveType, saveName, saveValue, saveChoice, overwrite));
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

        for (EntryToSaveTemplate entry : entriesToSaveList) {
            successful = successful && saveEntry(entry);
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
            String headerAccept = command.getHeaderAccept(); // default application/json, unless configured
            String headerContentType = command.getHeaderContentType(); // default application/json, unless in <body> configured
            String bodyValue = command.getBodyValue();
            log.debug("bodyValue = " + bodyValue);

            Map<String, String> parameters = command.getParametersMap();
            List<String> endpoints = command.getEndpoints();
            Map<String, String> targetVariablePathMap = command.getTargetVariablePathMap();

            String filterKey = command.getFilterKey();
            String filterFallbackKey = command.getFilterFallbackKey();
            String filterValue = command.getFilterValue();
            String filterAlternativeOption = command.getFilterAlternativeOption();

            String updateVariableName = command.getUpdateVariableName();
            Map<String, String> updateVariablePathValueMap = command.getUpdateVariablePathValueMap();

            for (String endpoint : endpoints) {
                String requestUrl = createRequestUrl(endpoint, parameters);
                log.debug("requestUrl = " + requestUrl);

                JSONObject jsonObject = runCommand(method, headerAccept, headerContentType, requestUrl, bodyValue);

                if (jsonObject != null) {
                    log.debug("------- jsonObject -------");
                    log.debug(jsonObject.toString());
                    log.debug("------- jsonObject -------");
                    
                    // <filter> and <target>
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

                    // <update>
                    JSONUtils.updateJSONObject(updateVariablePathValueMap, jsonObject);
                    boolean staticVariablesUpdated = AlmaApiCommand.updateStaticVariablesMap(updateVariableName, String.valueOf(jsonObject));
                    if (!staticVariablesUpdated) {
                        log.debug("static variables map was not successfully updated");
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
     * save the entry value as process property or metadata
     * 
     * @param entry EntryToSaveTemplate
     * @return true if the entry is successfully saved or its type is unknown, false if any errors should occur
     */
    private boolean saveEntry(EntryToSaveTemplate entry) {
        String type = entry.getType();
        switch (type.toLowerCase()) {
            case "property":
                return saveProperty(entry);
            case "metadata":
                return saveMetadata(entry);
            default:
                String message = "Ignoring unknown entry type: " + type + ".";
                logBoth(processId, LogType.WARN, message);
                return true;
        }

    }

    /**
     * save process properties
     * 
     * @param propertyTemplate EntryToSaveTemplate of type "property"
     * @return true if successful, false if any exception occurred
     */
    private boolean saveProperty(EntryToSaveTemplate propertyTemplate) {
        try {
            String propertyName = propertyTemplate.getName();
            String wrappedKey = AlmaApiCommand.wrapKey(propertyTemplate.getValue());
            List<String> propertyValues = AlmaApiCommand.getVariableValues(wrappedKey);

            // determine property value according to the configured choice
            String propertyValue = getEntryValue(propertyValues, propertyTemplate.getChoice());

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
     * save metadata
     * 
     * @param metadataTemplate EntryToSaveTemplate of type "metadata"
     * @return true if successful, false if any exception occurred
     */
    private boolean saveMetadata(EntryToSaveTemplate metadataTemplate) {
        String mdTypeName = metadataTemplate.getName();
        List<String> metadataValues = AlmaApiCommand.getVariableValues(metadataTemplate.getValue());

        // determine metadata value according to the configured choice
        String mdValue = getEntryValue(metadataValues, metadataTemplate.getChoice());

        try {
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument digital = fileformat.getDigitalDocument();
            DocStruct logical = digital.getLogicalDocStruct();
            
            Metadata oldMd = metadataTemplate.isOverwrite() ? findExistingMetadata(logical, mdTypeName) : null;
            Metadata newMd = createNewMetadata(mdTypeName, mdValue);
            if (oldMd != null) {
                logical.changeMetadata(oldMd, newMd);
            } else {
                logical.addMetadata(newMd);
            }

            process.writeMetadataFile(fileformat);

            return true;
            
        } catch (ReadException | IOException | SwapException e) {
            String message = "Failed to read the metadata file.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;

        } catch (PreferencesException e) {
            String message = "PreferencesException caught while trying to get the digital document.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;

        } catch (MetadataTypeNotAllowedException e) {
            String message = "Metadata type '" + mdTypeName + "' is not allowed";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;

        } catch (WriteException e) {
            String message = "Failed to save the metadata file.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;

        } catch (Exception e) {
            // any kind of exception should stop further steps, e.g. NullPointerException
            String message = "Unusual exception caught while saving metadata: " + e.toString();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;
        }

    }

    /**
     * try to retrieve an existing metadata object
     * 
     * @param ds DocStruct
     * @param mdTypeName name of the MetadataType
     * @return the metadata object if found, null otherwise
     */
    private Metadata findExistingMetadata(DocStruct ds, String mdTypeName) {
        if (ds.getAllMetadata() != null) {
            for (Metadata md : ds.getAllMetadata()) {
                if (md.getType().getName().equals(mdTypeName)) {
                    return md;
                }
            }
        }
        return null;
    }

    /**
     * prepare a new Metadata object based on the input type and value
     * 
     * @param mdTypeName type of the new Metadata object
     * @param value value of the new Metadata object
     * @return a Metadata object of the given type and initialized with the given value
     * @throws MetadataTypeNotAllowedException
     */
    private Metadata createNewMetadata(String mdTypeName, String value) throws MetadataTypeNotAllowedException {
        log.debug("creating new Metadata of type: " + mdTypeName);
        Prefs prefs = process.getRegelsatz().getPreferences();
        Metadata md = new Metadata(prefs.getMetadataTypeByName(mdTypeName));
        md.setValue(value);
        return md;
    }

    /**
     * get the entry value that should be saved
     * 
     * @param entryValues the list of entry values to choose from
     * @param choice options are all | first | last | random, DEFAULT all
     * @return property value that is to be saved
     */
    private String getEntryValue(List<String> entryValues, String choice) {
        switch (choice.toLowerCase()) {
            case "first":
                return entryValues.get(0);
            case "last":
                return entryValues.get(entryValues.size() - 1);
            case "random":
                return entryValues.get(new Random().nextInt(entryValues.size()));
            default:
                // combine all values
        }

        StringBuilder sb = new StringBuilder();
        for (String entryValue : entryValues) {
            sb.append(entryValue).append(", ");
        }

        String value = sb.toString();

        return value.substring(0, value.lastIndexOf(", "));
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
     * @param headerAccept value for the header parameter Accept
     * @param headerContentType value for the header parameter Content-type
     * @param url request url
     * @param body JSON or XML body that is to be sent by request,
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommand(String method, String headerAccept, String headerContentType, String url, String body) {
        return "get".equalsIgnoreCase(method) ? runCommandGet(headerAccept, headerContentType, url)
                : runCommandNonGet(method, headerAccept, headerContentType, url, body);
    }

    /**
     * run the command via GET method
     * 
     * @param headerAccept value for the header parameter Accept
     * @param headerContentType value for the header parameter Content-type
     * @param url request url
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommandGet(String headerAccept, String headerContentType, String url) {
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpGet.setHeader("Accept", headerAccept);
            httpGet.setHeader("Content-type", headerContentType);

            String message = "Executing request " + httpGet.getRequestLine();
            logBoth(processId, LogType.INFO, message);

            String responseBody = client.execute(httpGet, RESPONSE_HANDLER);
            log.debug("------- response body -------");
            log.debug(responseBody);
            log.debug("------- response body -------");

            return headerAccept.endsWith("json") ? JSONUtils.getJSONObjectFromString(responseBody) : null;

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
     * @param headerAccept value for the header parameter Accept
     * @param headerContentType value for the header parameter Content-type
     * @param url request url
     * @param body JSON or XML body that is to be sent by request
     * @return response as JSONObject, or null if any error occurred
     */
    private JSONObject runCommandNonGet(String method, String headerAccept, String headerContentType, String url, String body) {
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
            httpBase.setHeader("Accept", headerAccept);
            httpBase.setHeader("Content-type", headerContentType);
            httpBase.setEntity(new StringEntity(body));

            String message = "Executing request " + httpBase.getRequestLine();
            logBoth(processId, LogType.INFO, message);

            String responseBody = client.execute(httpBase, RESPONSE_HANDLER);
            log.debug("------- response body -------");
            log.debug(responseBody);
            log.debug("------- response body -------");

            return headerAccept.endsWith("json") ? JSONUtils.getJSONObjectFromString(responseBody) : null;

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
