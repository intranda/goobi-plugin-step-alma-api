---
title: ALMA API Plugin
identifier: intranda_step_alma_api
published: true
description: This plugin was originally implemented to communicate with the ALMA system and process returned responses. However, thanks to its general design, it can also be used to connect to other systems via REST.
---
## Introduction
This plugin is used to send requests to REST APIs, e.g. ALMA, and process the returned responses. Multiple commands can be configured to compile a complicated task. The plugin executes these commands one after the other in a defined order.


## Installation
To use the plugin, the file `plugin_intranda_step_alma_api-base.jar` must be saved in the following location:

```bash
/opt/digiverso/goobi/plugins/step/plugin_intranda_step_alma_api-base.jar
```

The configuration file `plugin_intranda_step_alma_api.xml` is expected under the following path:

```bash
/opt/digiverso/goobi/config/plugin_intranda_step_alma_api.xml
```


## Overview and functionality
This plugin is integrated into the workflow in such a way that it is executed automatically. Manual interaction with the plugin is not necessary. For use within a workflow step, it should be configured as shown in the screenshot below.

![Integration of the plugin into the workflow](screen1_en.png)


## Configuration
The configuration of the plugin is structured as follows as an example:

```xml
<config_plugin>
    <!--
        order of configuration is:
          1.) project name and step name matches
          2.) step name matches and project is *
          3.) project name matches and step name is *
          4.) project name and step name are *
	-->
    <config>
        <!-- which projects to use for (can be more then one, otherwise use *) -->
        <project>*</project>
        <step>*</step>

        <!-- Base URL -->
        <url>https://api-eu.hosted.exlibrisgroup.com</url>

        <!-- API key -->
        <api-key>CHANGE_ME</api-key>

        <!-- Variables that can be used for following commands.
              @name: name of the variable, e.g. VARIABLE. To use this variable's value, one can simply use {$VARIABLE}.
              @value: value to initialize this variable. It can be plain string value, or a Goobi variable, e.g. {meta.NAME} for a Metadata named NAME.
         -->
        <variable name="MMS_ID" value="{meta.CatalogIDDigital}" />
        <!-- There can be multiple variables defined before all commands. -->
        <variable name="SIGNATURE" value="{meta.shelfmarksource}" />

        <!-- A command is a step to perform. There can be several commands configured, and if so, they will be run one by one in the same order as they are defined.
              @method: get | put | post | patch
              @accept: json | xml. Used to set up the header parameter 'Accept'. OPTIONAL. DEFAULT json.
              @content-type: json | xml. Used to set up the header parameter 'Content-type'. OPTIONAL. DEFAULT json.
              @endpoint: raw endpoint string without replacing the placeholders enclosed by {}. For every placeholder say {PLACEHOLDER}, one has to configure it in a
                                sub-tag, and in our example it would be <PLACEHOLDER>. Options for values of these sub-tags are:
                                - plain text value
                                - any variable defined by a <variable> tag before all <command> blocks
                                - any variable defined by a <target> sub-tag of any previous <command> block
        -->      
        <command method="get" accept="json" content-type="json" endpoint="/almaws/v1/bibs/{mms_id}/holdings/ALL/items">
        	<!-- define the value of the placeholder {mms_id} using the variable named MMS_ID -->
        	<mms_id>{$MMS_ID}</mms_id>

        	<!-- Filter condition that is to be applied on the REST call response. OPTIONAL.
        	      @key: JSON path from which the values are to be fetched for the filtering process
        	      @fallback: JSON path that shall be used when the JSON path configured by @key contains no value. OPTIONAL.
        	      @value: value to be compared with, which can be a plain text value, or a variable defined previously in the format {$VARIABLE}
        	      @alt: alternative option if there is no match found. Options are: all | first | last | random | none. OPTIONAL.
                      - all: filter nothing out, search the whole JSONObject for the following targets
                      - first: get the first JSONObject related to the common heading path shared by filter and targets, and search for targets within it
                      - last: get the last JSONObject related to the common heading path shared by filter and targets, and search for targets within it
                      - random: get a random JSONObject related to the common heading path shared by filter and targets, and search for targets within it
                      - none: filter everything out and stop searching. DEFAULT.
        	 -->
        	<filter key="item.item_data.alternative_call_number" fallback="item.holding_data.permanent_call_number" value="{$SIGNATURE}" alt="all" />

        	<!-- Target values that is to be retrieved from the REST call response and saved as a variable. OPTIONAL.
        	      @var: name of the variable that is to be used to save the target values retrieved.
                      If the variable was already defined before, then its value will be updated. Otherwise a new variable under this name will be created.
        	      @path: JSON path from where values are to be retrieved.
        	 -->
        	<target var="ITEM_PID" path="item.item_data.pid" />

        	<!-- There can be multiple targets configured. -->
        	<!-- In this example, the new variable will be named HOLDING_ID and it can be reused in the following steps using {$HOLDING_ID}. -->
        	<target var="HOLDING_ID" path="item.holding_data.holding_id" />
        </command>

        <command method ="post" endpoint="/almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}">
        	<!-- define the value of the placeholder {mms_id} using the variable named MMS_ID -->
        	<mms_id>{$MMS_ID}</mms_id>
        	<!-- define the value of the placeholder {holding_id} using the variable named HOLDING_ID -->
        	<holding_id>{$HOLDING_ID}</holding_id>
        	<!-- define the value of the placeholder {item_pid} using the variable named ITEM_PID -->
        	<item_pid>{$ITEM_PID}</item_pid>

          <!-- A parameter tag can be used to define parameters that shall be sent by the REST request. There can be multiple parameters configured. OPTIONAL.
                  @name: parameter name
                  @value: parameter value, which can only be plain text values here
            -->
        	<parameter name="op" value="scan" />
        	<parameter name="library" value="" />
        	<parameter name="department" value="InDigiZ_Dep" />
        	<parameter name="work_order_type" value="InDigiZ" />
        	<parameter name="done" value="false" />
        </command>

        <!-- A save tag is used to define an entry that is to be saved after running all previous commands. OPTIONAL.
              @type: type of the entry, OPTIONS are property | metadata
                         - property: save the entry as a process property
                         - metadata: save the entry as a metadata
              @name: name of the entry, which means
                           - property name if @type is "property"
                           - metadata type name if @type is "metadata"
              @value: value of the new process property, possible values are
                           - a plain text value
                           - a variable defined before all <command> blocks via a <variable> tag
                           - a variable defined within some <command> block via a <target> tag
              @choice: indicates how many items should be saved into this new property, OPTIONS are first | last | random, or any non-blank strings following a colon.
                           - first: save only the first one among all retrieved values
                           - last: save only the last one among all retrieved values
                           - random: save a random one from all retrieved values
                           - For any non-blank strings following a colon: the substring following this colon will be used as a whole delimiter to combine all results.
                           - For all other cases, including the case where there is only one single colon configured: all results will be combined using commas.
              @overwrite: true if the old property named so should be reused, false if a new property should be created, DEFAULT false.
        -->     
        <save type="property" name="holding_id" value="{$HOLDING_ID}" choice="first" overwrite="true" />
        <!-- There can be multiple save tags configured. -->
        <save type="property" name="item_pid" value="{$ITEM_PID}" choice="first" overwrite="true" />

    </config>

    <config>
        <!-- which projects to use for (can be more then one, otherwise use *) -->
        <project>*</project>
        <step>upload marc and portfolio</step>

        <!-- Base URL -->
        <url>https://api-eu.hosted.exlibrisgroup.com</url>

        <!-- API key -->
        <api-key>CHANGE_ME</api-key>

        <variable name="MARC_FOLDER" value="/opt/digiverso/g2g/workspace/workflow/marcexport/{processid}" />
        <variable name="PORTFOLIO_PATH" value="/opt/digiverso/g2g/workspace/demo_content/portfolio.xml" />

        <command method="post" accept="json" content-type="xml" endpoint="/almaws/v1/bibs">
            <parameter name="normalization" value="43588" />
            <parameter name="validate" value="false" />
            <parameter name="override_warning" value="true" />
            <parameter name="check_match" value="false" />

            <!-- Set up request body. OPTIONAL.
                 @src: absolute path to the file whose contents shall be used as request body. OPTIONAL. It accepts one of the following both:
                          - a plain text value stating the absolute path
                          - a predefined variable containing the absolute path
                          ATTENTION: one can also use the absolute path of the folder containing this file here, in which case the content of the first file in that folder will be used.
                 @wrapper: wrappers separated by space that shall be wrapped around the content read from the file. ONLY applicable if @src is configured. OPTIONAL.
                                  The precise way of wrapping depends on the setting of @content-type of the command tag.
                                  For example, say one has wrapper configured as "wrapper1 wrapper2 wrapper3" then:
                                  - If @content-type="json", the file content will be wrapped as {wrapper1: {wrapper2: {wrapper3: {FILE_CONTENT}}}}
                                  - If @content-type="xml", the file content will be wrapped as <wrapper1><wrapper2><wrapper3>{FILE_CONTENT}</wrapper3></wrapper2></wrapper1>
                 @value: use a plain text value or a predefined variable instead of the content of a file. OPTIONAL. ATTENTION:
                              - to use a plain text value one has to assure that its format matches the configured @content-type correctly
                              - to use a variable one has to assure that the format of the variable's value matches the configured @content-type correctly
              -->
            <body src="{$MARC_FOLDER}" wrapper="bib" />

            <target var="MMS_ID" path="mms_id" />
        </command>

        <command method="post" accept="json" content-type="xml" endpoint="/almaws/v1/bibs/{mms_id}/portfolios">
            <mms_id>{$MMS_ID}</mms_id>
            <!-- use content of the file as request body, no wrapper needed -->
            <body src="{$PORTFOLIO_PATH}" wrapper="" />
            <!-- create a new variable to hold the value on this JSON path  -->
            <target var="PORTFOLIO_ID" path="id" />         
        </command>       

        <!-- save values of variables as process properties  -->
        <save type="metadata" name="MMS_ID" value="{$MMS_ID}" choice="first" overwrite="true" />
        <save type="metadata" name="PortfolioId" value="{$PORTFOLIO_ID}" choice="first" overwrite="true" />
    </config>

    <config>
        <!-- which projects to use for (can be more then one, otherwise use *) -->
        <project>*</project>
        <step>update portfolio</step>

        <!-- Base URL -->
        <url>https://api-eu.hosted.exlibrisgroup.com</url>

        <!-- API key -->
        <api-key>CHANGE_ME</api-key>

        <!-- create variables using values read from process properties -->
        <variable name="MMS_ID" value="{meta.MMS_ID}" />
        <variable name="PORTFOLIO_ID" value="{meta.PortfolioId}" />

        <command method="get" accept="json" content-type="json" endpoint="/almaws/v1/bibs/{mms_id}/portfolios/{portfolio_id}">
            <!-- define the value of the placeholder {mms_id} using the value of the variable named MMS_ID -->
            <mms_id>{$MMS_ID}</mms_id>
            <!-- define the value of the placeholder {portfolio_id} using the value of the variable named PORTFOLIO_ID -->
            <portfolio_id>{$PORTFOLIO_ID}</portfolio_id>      

            <!-- An update tag is used to save the MAYBE updated response JSONObject as an variable. AT MOST ONE allowed. OPTIONAL.
                   ATTENTION: to use this tag, one has to assure that the @accept configured in the command tag is json.
                   @var: name of the variable that shall be used to save the JSONObject.
               -->
            <update var="UPDATED_PORTFOLIO" >
                <!-- An entry tag is used to define modifications that shall be made on the response JSONObject. OPTIONAL. Multiple entry sub-tags possible.
                       @path: JSON path(s) of the item(s) that shall be modified, where plural comes into use when there is a JSONArray with multiple elements mixed in this JSON path
                       @value: new value of the item(s) selected by the JSON path
                    -->
                <entry path="availability.value" value="11" />
            </update>
        </command>

        <!-- use default settings of @accept and @content-type, which are both json -->
        <command method="put" endpoint="/almaws/v1/bibs/{mms_id}/portfolios/{portfolio_id}">
            <mms_id>{$MMS_ID}</mms_id>
            <portfolio_id>{$PORTFOLIO_ID}</portfolio_id>        
            <!-- use the JSONObject saved by the last command as the request body --> 	
            <body value="{$UPDATED_PORTFOLIO}" />        	
        </command>                     
    </config>

</config_plugin>
```


### General configurations
The plugin is configured as described here:

| Value | Description |
| :--- | :--- |
| `project` | This parameter defines which project the current block `<config>` should apply to. The name of the project is used here. This parameter can occur multiple times per `<config>` block. |
| `step` | This parameter controls which work steps the `<config>` block should apply to. The name of the work step is used here. This parameter can occur multiple times per `<config>` block. |
| `url` | The base URL of the REST API is specified here. |
| `api-key` | The API key for the connection to the REST API is configured here. |
| `variable` | This tag can be used to define a variable that can be used by all subsequent commands. This tag has two attributes, where `@name` defines the name and `@value` the value. `@value` expects a simple text value or a Goobi variable. |
| `command` | A command block defines a command that is to be executed in the job. It has two mandatory attributes itself, where `@method` specifies the method to be used and `@endpoint` specifies the path to the endpoint, where all placeholders are not replaced. It also has two optional attributes, `@accept` and `@content-type`, which are used to specify the request parameters `accept` and `content-type`. Both expect either `json` or `xml`. If one of the two parameters is omitted, the default value `json` is used. Further details can be found in the table below and in the example configuration above. |
| `save` | An optional `save` element defines a value to be saved after all commands have been executed. It has three mandatory attributes, where `type` specifies whether the value is to be saved as an operation property or as a metadata. The attribute `@name` defines the name of the process property or metadata type. The `@value` attribute determines the value, which can be a simple text value or a previously defined variable. It has two optional attributes, where `@choice` specifies which value should be saved if several are found, and `@overwrite` determines whether a previously created process property or a metadata of the same name should be reused. |


### Configurations within command blocks
The configuration within the command blocks is carried out as described here:

| Value | Description |
| :--- | :--- |
| `filter` | This specifies which parts of the JSON response should be used to search for the `target` values. It has four attributes, where `@key` and `@value` are mandatory, while `@fallback` and `@alt` are optional. Further details can be found in the comments in the sample configuration. |
| `target` | This specifies which values are to be saved as variables for later use. The parameter has two attributes, where `@var` specifies the variable name and `@path` specifies the JSON path to retrieve the values. |
| `parameter` | A parameter is specified here that is to be sent to the REST API together with a request. It has two attributes, where `@name` is used for the parameter name and `@value` for the parameter value, which can consist exclusively of pure text values. |
| `body` | The request body is defined here. It has three attributes, whereby one of `@src` and `@value` must be specified. If `@src` is set, `@wrapper` is also applicable. The file whose content is to be used as the request body is specified with `@src`, while `@value` specifies the value of a variable that has been received from previous commands. When using `@wrapper`, it is advisable to consider the comments in the sample configuration.  |
| `update` | This element is used to save the JSON object of the response as a variable. It has an attribute `@var` that specifies the name of the variable. Each `command` tag can have at most one `update` sub-element. Within the `update` subelement, there can be multiple `entry` subelements, each of which specifies a change to the JSON response object. |
