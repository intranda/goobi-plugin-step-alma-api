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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;

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
            for (String key : map.keySet()) {
                log.error("not mapped: " + key + ": " + map.get(key));
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
}
