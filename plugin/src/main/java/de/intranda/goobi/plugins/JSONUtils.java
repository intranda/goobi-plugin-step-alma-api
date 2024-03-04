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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class JSONUtils {

    private JSONUtils() {
        // hide the implicit one
    }

    /**
     * parse the input JSON string to get a JSONObject
     * 
     * @param s JSON string
     * @return JSONObject
     * @throws ParseException
     */
    public static Object getJSONObjectFromString(String s) throws InvalidJsonException {
        return Configuration.defaultConfiguration().jsonProvider().parse(s);
    }

    /**
     * a general version to get values from a JSON path from an object
     * 
     * @param source JSON path
     * @param obj either JSONArray or JSONObject
     * @return a list of values found
     */
    public static List<Object> getValuesFromSourceGeneral(String source, Object obj) {
        List<Object> results = new ArrayList<>();

        if (obj instanceof List) {
            List<?> valueList = (List<?>) obj;
            for (Object element : valueList) {
                results.addAll(getValuesFromSource(source, element));

            }
        } else {
            results.addAll(getValuesFromSource(source, obj));
        }

        return results;
    }

    /**
     * a general version to get values from a JSON path from a list of objects
     * 
     * @param source JSON path
     * @param objects a list of JSONArrays or JSONObjects or mixed
     * @return a list of values found
     */
    public static List<Object> getValuesFromSourceGeneral(String source, List<Object> objects) {
        List<Object> results = new ArrayList<>();
        for (Object obj : objects) {
            results.addAll(getValuesFromSourceGeneral(source, obj));
        }

        return results;
    }

    /**
     * get values from a JSON path from a JSONObject
     * 
     * @param source JSON path
     * @param jsonObject JSONObject
     * @return a list of values found
     */
    public static List<Object> getValuesFromSource(String source, Object document) {
        List<Object> results = new ArrayList<>();
        // base case: no source specified
        if (StringUtils.isBlank(source)) {
            results.add(document);
            return results;
        }
        try {
            Object object = JsonPath.read(document, source);
            if (object != null) {
                if (object instanceof List) {
                    List<?> valueList = (List<?>) object;
                    for (Object element : valueList) {
                        results.add(element);

                    }
                } else {
                    results.add(object);
                }
            }
        } catch (PathNotFoundException e) {
            // metadata not found, ignore this error
        }

        return results;
    }

    /**
     * filter out the input JSONObject and retrieve values from multiple target paths at the same time
     * 
     * @param targetVariablePathList a map with its keys being names of target variables and its values being corresponding JSON paths
     * @param jsonObject JSONObject
     * @return a list of values found
     */
    public static Map<String, List<Object>> getFilteredValuesFromSource(List<Target> targetVariablePathList,
            Object jsonObject) {

        log.debug("======= getting filtered values from a map =======");
        Map<String, List<Object>> results = new HashMap<>();
        List<String> targetPaths = new ArrayList<>(targetVariablePathList.size());
        for (Target t : targetVariablePathList) {
            targetPaths.add(t.getPath());
        }

        // use the tailing part of every targetPath to retrieve a list of targeted values
        for (Target target : targetVariablePathList) {
            String targetVariable = target.getVariableName();
            String targetPath = target.getPath();
            List<Object> targetResults = getValuesFromSourceGeneral(targetPath, jsonObject);
            results.put(targetVariable, targetResults);
        }

        return results;
    }

    public static String getValueAsString(Object value) {
        String anwer = null;
        if (value instanceof String) {
            anwer = (String) value;
        } else if (value instanceof Integer) {
            anwer = ((Integer) value).toString();
        } else if (value instanceof Boolean) {
            return (boolean) value ? "true" : "false";
        } else if (value instanceof LinkedHashMap) {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) value;
            for (Entry<String, String> entry : map.entrySet()) {
                log.error("not mapped: " + entry.getKey() + ": " + entry.getValue());
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (!array.isEmpty()) {
                return (String) array.get(0);
            }
        }

        else {
            log.error("Type not mapped: " + value.getClass());
        }

        return anwer;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void updateJsonObject(Map<String, String> pathValueMap, Object jsonObject) {
        if (pathValueMap == null || pathValueMap.isEmpty()) {
            // no need to update anything
            return;
        }
        for (Map.Entry<String, String> entry : pathValueMap.entrySet()) {
            String jsonPath = entry.getKey();
            String newValue = entry.getValue();

            // split jsonPath on "."

            Map map = (LinkedHashMap) jsonObject;

            while (jsonPath.contains(".")) {
                String current = jsonPath.substring(0, jsonPath.indexOf("."));
                jsonPath = jsonPath.substring(jsonPath.indexOf(".") + 1);

                if (map.get(current) == null) { //NOSONAR
                    map.put(current, new LinkedHashMap());
                }
                map = (LinkedHashMap) map.get(current);

            }

            // add/overwrite value, if we have the last element

            map.put(jsonPath, newValue);
        }
    }

    public static String convertJsonToString(Object jsonObject) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) jsonObject;

        StringBuilder sb = new StringBuilder();
        int counter = 0;

        sb.append("{");
        for (Entry<String, Object> entry : map.entrySet()) {
            if (counter > 0) {
                sb.append(",");
            }
            counter++;
            sb.append("\"");
            sb.append(entry.getKey());
            sb.append("\":");

            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                String stringValue = (String) val;
                // empty
                if (StringUtils.isBlank(stringValue)) {
                    sb.append("\"\"");
                } else {
                    sb.append("\"");
                    sb.append(StringEscapeUtils.escapeJson(stringValue));
                    sb.append("\"");

                }

            } else if (val instanceof Boolean) {
                sb.append(val);
            }

            else if (val instanceof net.minidev.json.JSONArray) {
                net.minidev.json.JSONArray arr = (net.minidev.json.JSONArray) val;
                sb.append(arr.toJSONString());

            } else if (val instanceof Map) {
                String sub = convertJsonToString(val);
                sb.append(sub);
            } else {
                log.error("json type is not mapped: " + val.getClass());
            }

        }

        sb.append("}");

        return sb.toString();
    }

}
