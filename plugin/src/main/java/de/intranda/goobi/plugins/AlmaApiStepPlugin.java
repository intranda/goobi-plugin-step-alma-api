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
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
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
    @Getter
    private String value;
    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    private String url;
    private String apiKey;
    private List<AlmaApiCommand> commandList = new ArrayList<>();

    // create a custom response handler
    private static final ResponseHandler<String> RESPONSE_HANDLER = response -> {
        HttpEntity entity = response.getEntity();
        return entity != null ? EntityUtils.toString(entity) : null;

        //        int status = response.getStatusLine().getStatusCode();
        //        
        //        if (status >= 200 && status < 300) {
        //            HttpEntity entity = response.getEntity();
        //            return entity != null ? EntityUtils.toString(entity) : null;
        //        } else {
        //            HttpEntity entity = response.getEntity();
        //            log.debug("error entity = " + EntityUtils.toString(entity));
        //            throw new ClientProtocolException("Unexpected response status: " + status);
        //        }
    };

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.process = step.getProzess();
        this.processId = process.getId();

        // read parameters from correct block in configuration file
        SubnodeConfiguration config = ConfigPlugins.getProjectAndStepConfig(title, step);
        value = config.getString("value", "default value");
        allowTaskFinishButtons = config.getBoolean("allowTaskFinishButtons", false);
        boolean testRun = config.getBoolean("test", false);
        if (testRun) {
            prepareStaticVariablesMapForTest();
        }

        url = config.getString("url", "");
        apiKey = config.getString("api-key", "");

        // initialize the static variable map in AlmaApiCommand, which will be needed to create new AlmaApiCommand instances
        List<HierarchicalConfiguration> variableConfigs = config.configurationsAt("variable");
        initializeVariablesMap(variableConfigs);

        List<HierarchicalConfiguration> commandConfigs = config.configurationsAt("command");
        for (HierarchicalConfiguration commandConfig : commandConfigs) {
            commandList.add(new AlmaApiCommand(commandConfig));
        }

        log.info("AlmaApi step plugin initialized");
    }

    /**
     * prepare sample variables to test
     */
    private void prepareStaticVariablesMapForTest() {
        String var1 = "{$hello}";
        List<String> var1Values = new ArrayList<>();
        var1Values.add("HALLO");
        var1Values.add("WELT");

        String var2 = "what";
        List<String> var2Values = new ArrayList<>();
        var2Values.add("WAS");
        var2Values.add("WHAT");

        String var3 = "$anything";
        List<String> var3Values = new ArrayList<>();
        var3Values.add("AHA");
        var3Values.add("BOBO");

        AlmaApiCommand.updateStaticVariablesMap(var1, var1Values);
        AlmaApiCommand.updateStaticVariablesMap(var2, var2Values);
        AlmaApiCommand.updateStaticVariablesMap(var3, var3Values);
    }

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

    private String getVariableValue(HierarchicalConfiguration variableConfig) {
        // use @value if it is configured
        if (variableConfig.containsKey("@value")) {
            return variableConfig.getString("@value");
        }

        // otherwise, get value from metadata
        if (variableConfig.containsKey("@metadata")) {
            String mdType = variableConfig.getString("@metadata");
            return getVariableValueFromMetadata(mdType);
        }

        // otherwise, report error
        String message = "To define a <variable> tag, one has to specify one of its two attributes: either @value or @metadata.";
        logBoth(processId, LogType.WARN, message);
        return "";
    }

    private String getVariableValueFromMetadata(String mdType) {
        log.debug("Getting variable value from metadata of type: " + mdType);

        try {
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument dd = fileformat.getDigitalDocument();
            DocStruct logical = dd.getLogicalDocStruct();

            return findExistingMetadata(logical, mdType);

        } catch (ReadException | IOException | SwapException e) {
            String message = "Failed to read the metadata file.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();

        } catch (PreferencesException e) {
            String message = "PreferencesException caught while trying to get the digital document.";
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
        }

        return "";
    }

    /**
     * get the value of an existing Metadata
     * 
     * @param ds DocStruct whose Metadata should be searched
     * @param elementType name of MetadataType
     * @return value of the Metadata if successfully found, null otherwise
     */
    private String findExistingMetadata(DocStruct ds, String elementType) {
        if (ds.getAllMetadata() != null) {
            for (Metadata md : ds.getAllMetadata()) {
                if (md.getType().getName().equals(elementType)) {
                    return md.getValue();
                }
            }
        }
        return null;
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

        log.info("AlmaApi step plugin executed");
        return successful ? PluginReturnValue.FINISH : PluginReturnValue.ERROR;
    }

    private boolean prepareAndRunCommand(AlmaApiCommand command) {
        try {
            // update endpoints
            command.updateAllEndpoints();
            // get method
            String method = command.getMethod();

            Map<String, String> parameters = command.getParametersMap();
            List<String> endpoints = command.getEndpoints();

            String targetPath = command.getTargetPath();
            String targetVariable = command.getTargetVariable();

            String filterKey = command.getFilterKey();
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

                    List<Object> filteredValues =
                            JSONUtils.getFilteredValuesFromSource(targetPath, filterKey, filterValue, filterAlternativeOption, jsonObject);
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
            return true;

        } catch (Exception e) {
            // any kind of exception should stop further steps, e.g. NullPointerException
            String message = "Exception caught while running commands: " + e.toString();
            logBoth(processId, LogType.ERROR, message);
            e.printStackTrace();
            return false;
        }

    }

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

    private JSONObject runCommand(String method, String url) {
        return runCommand(method, url, "");
    }

    private JSONObject runCommand(String method, String url, String json) {
        if (method.toLowerCase().equals("get")) {
            return runCommandGet(url);
        } else {
            return runCommandNonGet(method, url, json);
        }
    }

    private JSONObject runCommandGet(String url) {
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Content-type", "application/json");

            log.info("Executing request " + httpGet.getRequestLine());

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

            log.info("Executing request " + httpBase.getRequestLine());

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
        String logMessage = "FetchImagesFromMetadata Step Plugin: " + message;
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
