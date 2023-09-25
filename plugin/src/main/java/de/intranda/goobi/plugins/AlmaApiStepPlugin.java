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
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class AlmaApiStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_alma_api";
    @Getter
    private Step step;
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

        // read parameters from correct block in configuration file
        SubnodeConfiguration config = ConfigPlugins.getProjectAndStepConfig(title, step);
        value = config.getString("value", "default value");
        allowTaskFinishButtons = config.getBoolean("allowTaskFinishButtons", false);

        url = config.getString("url", "");
        apiKey = config.getString("api-key", "");
        List<HierarchicalConfiguration> commandConfigs = config.configurationsAt("command");
        for (HierarchicalConfiguration commandConfig : commandConfigs) {
            commandList.add(new AlmaApiCommand(commandConfig));
        }

        log.info("AlmaApi step plugin initialized");
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
            // get method
            String method = command.getMethod();

            // get the full request url
            String endpoint = command.getEndpoint();
            Map<String, String> parameters = command.getParametersMap();
            String requestUrl = createRequestUrl(endpoint, parameters);
            log.debug("requestUrl = " + requestUrl);
            
            // run the command
            runCommand(method, requestUrl);

        }

        log.info("AlmaApi step plugin executed");
        return successful ? PluginReturnValue.FINISH : PluginReturnValue.ERROR;
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

    private void runCommand(String method, String url) {
        runCommand(method, url, "");
    }

    private void runCommand(String method, String url, String json) {
        if (method.toLowerCase().equals("get")) {
            runCommandGet(url);
        } else {
            runCommandNonGet(method, url, json);
        }
    }

    private void runCommandGet(String url) {
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Content-type", "application/json");

            log.info("Executing request " + httpGet.getRequestLine());

            String responseBody = client.execute(httpGet, RESPONSE_HANDLER);
            log.debug(responseBody);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void runCommandNonGet(String method, String url, String json) {
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
            default: // unknown
                return;
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpBase.setHeader("Accept", "application/json");
            httpBase.setHeader("Content-type", "application/json");
            httpBase.setEntity(new StringEntity(json));

            log.info("Executing request " + httpBase.getRequestLine());

            String responseBody = client.execute(httpBase, RESPONSE_HANDLER);
            log.debug(responseBody);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
