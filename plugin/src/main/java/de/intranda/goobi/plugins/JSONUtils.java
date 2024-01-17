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
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class JSONUtils {
    private static final JSONParser JSON_PARSER = new JSONParser();

    private static Random random = new Random();

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
    public static JSONObject getJSONObjectFromString(String s) throws ParseException {
        return (JSONObject) JSON_PARSER.parse(s);
    }

    /**
     * a general version to get values from a JSON path from an object
     * 
     * @param source JSON path
     * @param obj either JSONArray or JSONObject
     * @return a list of values found
     */
    public static List<Object> getValuesFromSourceGeneral(String source, Object obj) {
        if (obj instanceof JSONArray) {
            return getValuesFromSource(source, (JSONArray) obj);
        }

        if (obj instanceof JSONObject) {
            return getValuesFromSource(source, (JSONObject) obj);
        }

        // unknown obj, report error
        return new ArrayList<>();
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
    public static List<Object> getValuesFromSource(String source, JSONObject jsonObject) {
        List<Object> results = new ArrayList<>();
        // base case: no source specified
        if (StringUtils.isBlank(source)) {
            results.add(jsonObject);
            return results;
        }

        // base case: source is the wanted node, no futher
        if (!source.contains(".")) {
            Object value = jsonObject.get(source);
            if (value instanceof JSONArray) {
                // save all values in this array
                JSONArray jsonArray = (JSONArray) value;
                for (Object element : jsonArray) {
                    results.add(element);
                }
            } else {
                // no array, just save the value
                results.add(value);
            }

            return results;
        }

        // other cases: one step further towards the leaf node
        int splitterIndex = source.indexOf(".");
        String key = source.substring(0, splitterIndex);
        String newSource = source.substring(splitterIndex + 1);

        Object obj = jsonObject.get(key);
        if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            results.addAll(getValuesFromSource(newSource, jsonArray));

        } else {
            // JSONObject
            JSONObject subJsonObject = (JSONObject) obj;
            results.addAll(getValuesFromSource(newSource, subJsonObject));
        }

        return results;
    }

    /**
     * get values from a JSON path from a JSONArray
     * 
     * @param source JSON path
     * @param jsonArray JSONArray
     * @return a list of values found
     */
    public static List<Object> getValuesFromSource(String source, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();
        // base case: no source specified
        // base case: source is the wanted node, no further
        if (StringUtils.isBlank(source) || !source.contains(".")) {
            // add all plain values
            for (Object element : jsonArray) {
                JSONObject jsonObject = (JSONObject) element;
                results.add(jsonObject.get(source));
            }

            return results;
        }

        // other cases: one step further towards the leaf node
        int splitterIndex = source.indexOf(".");
        String key = source.substring(0, splitterIndex);
        String newSource = source.substring(splitterIndex + 1);

        for (Object element2 : jsonArray) {
            JSONObject element = (JSONObject) element2;
            Object obj = element.get(key);
            if (obj instanceof JSONArray) {
                results.addAll(getValuesFromSource(newSource, (JSONArray) obj));
            } else {
                results.addAll(getValuesFromSource(newSource, (JSONObject) obj));
            }
        }

        return results;
    }

    /**
     * get common parents of the input two paths on the input JSONObject
     * 
     * @param targetPath
     * @param filterPath
     * @param jsonObject JSONObject
     * @return a list of parent objects found
     */
    public static List<Object> getCommonParents(String targetPath, String filterPath, JSONObject jsonObject) {
        List<Object> results = new ArrayList<>();
        // base case: either one goes no further, hence the current JSONObject is their common parent
        if (!targetPath.contains(".") || !filterPath.contains(".")) {
            results.add(jsonObject);
            return results;
        }

        // otherwise check their headings
        int targetSplittingIndex = targetPath.indexOf(".");
        String targetPathHead = targetPath.substring(0, targetSplittingIndex);

        int filterSplittingIndex = filterPath.indexOf(".");
        String filterPathHead = filterPath.substring(0, filterSplittingIndex);

        // targetPath and filterPath have no common heading, hence the current JSONObject is their common parent
        if (!targetPathHead.equals(filterPathHead)) {
            results.add(jsonObject);
            return results;
        }

        // move forward
        String newTargetPath = targetPath.substring(targetSplittingIndex + 1);
        String newFilterPath = filterPath.substring(filterSplittingIndex + 1);
        Object obj = jsonObject.get(targetPathHead);
        if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            results.addAll(getCommonParents(newTargetPath, newFilterPath, jsonArray));
        } else {
            JSONObject subJsonObject = (JSONObject) obj;
            results.addAll(getCommonParents(newTargetPath, newFilterPath, subJsonObject));
        }

        return results;
    }

    /**
     * get common parents of the input two paths in the input JSONArray
     * 
     * @param targetPath
     * @param filterPath
     * @param jsonArray JSONArray
     * @return a list of parent objects found
     */
    public static List<Object> getCommonParents(String targetPath, String filterPath, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();
        // base case: either one goes no further, then all JSONObjects in jsonArray are their common parents
        if (!targetPath.contains(".") || !filterPath.contains(".")) {
            for (Object element : jsonArray) {
                results.add(element);
            }

            return results;
        }

        // otherwise check their headings
        int targetSplittingIndex = targetPath.indexOf(".");
        String targetPathHead = targetPath.substring(0, targetSplittingIndex);

        int filterSplittingIndex = filterPath.indexOf(".");
        String filterPathHead = filterPath.substring(0, filterSplittingIndex);

        // targetPath and filterPath have no common heading, hence all JSONObjects in this JSONArray are their common parents
        if (!targetPathHead.equals(filterPathHead)) {
            for (Object element : jsonArray) {
                results.add(element);
            }

            return results;
        }

        // move forward
        String newTargetPath = targetPath.substring(targetSplittingIndex + 1);
        String newFilterPath = filterPath.substring(filterSplittingIndex + 1);
        for (Object element : jsonArray) {
            JSONObject jsonObject = (JSONObject) element;
            Object obj = jsonObject.get(targetPathHead);
            if (obj instanceof JSONArray) {
                JSONArray subJsonArray = (JSONArray) obj;
                results.addAll(getCommonParents(newTargetPath, newFilterPath, subJsonArray));
            } else {
                JSONObject subJsonObject = (JSONObject) obj;
                results.addAll(getCommonParents(newTargetPath, newFilterPath, subJsonObject));
            }
        }

        return results;
    }

    /**
     * get common parents on the JSONObject based on the input common heading string
     * 
     * @param commonHeading common heading string of multiple paths
     * @param jsonObject JSONObject
     * @return a list of parent objects found
     */
    public static List<Object> getCommonParents(String commonHeading, JSONObject jsonObject) {
        List<Object> results = new ArrayList<>();

        // base case 1: no commonHeading available, then the current JSONObject is their common parent
        if (StringUtils.isBlank(commonHeading)) {
            results.add(jsonObject);
            return results;
        }

        // base case 2: commonHeading does not contain dot
        if (!commonHeading.contains(".")) {
            results.add(jsonObject.get(commonHeading));
            return results;
        }

        // otherwise, move forward by one step
        int splittingIndex = commonHeading.indexOf(".");
        String head = commonHeading.substring(0, splittingIndex);
        String tail = commonHeading.substring(splittingIndex + 1);

        Object obj = jsonObject.get(head);
        if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            results.addAll(getCommonParents(tail, jsonArray));
        } else {
            JSONObject subJsonObject = (JSONObject) obj;
            results.addAll(getCommonParents(tail, subJsonObject));
        }

        return results;
    }

    /**
     * get common parents in the JSONArray based on the input common heading string
     * 
     * @param commonHeading common heading string of multiple paths
     * @param jsonArray JSONArray
     * @return a list of parent objects found
     */
    public static List<Object> getCommonParents(String commonHeading, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();

        // base case 1: no commonHeading availabe
        // base case 2: commonHeading does not contain dot
        if (StringUtils.isBlank(commonHeading) || !commonHeading.contains(".")) {
            // in both cases, every element of the current JSONArray is a common parent
            for (Object element : jsonArray) {
                results.add(element);
            }

            return results;
        }

        // otherwise, move forward by one step
        int splittingIndex = commonHeading.indexOf(".");
        String head = commonHeading.substring(0, splittingIndex);
        String tail = commonHeading.substring(splittingIndex + 1);

        for (Object element : jsonArray) {
            JSONObject jsonObject = (JSONObject) element;
            Object obj = jsonObject.get(head);
            if (obj instanceof JSONArray) {
                JSONArray subJsonArray = (JSONArray) obj;
                results.addAll(getCommonParents(tail, subJsonArray));
            } else {
                JSONObject subJsonObject = (JSONObject) obj;
                results.addAll(getCommonParents(tail, subJsonObject));
            }
        }

        return results;
    }

    /**
     * filter out the input JSONObject and retrieve a list of values from the input target path
     * 
     * @param targetPath JSON path where aimed values are to be found
     * @param filterPath JSON path where values needed for filtering are to be found
     * @param filterValue value needed for comparison
     * @param filterAlternativeOption alternative option when there is no match found for the filtering condition, options are all | first | last |
     *            random | none, DEFAULT none
     * @param jsonObject JSONObject
     * @return a list of values found
     */
    public static List<Object> getFilteredValuesFromSource(String targetPath, String filterPath, String filterValue, String filterAlternativeOption,
            JSONObject jsonObject) {

        List<Object> results = new ArrayList<>();

        // get the common heading of targetPath and filterPath
        String commonHeading = getCommonHeading(targetPath, filterPath);

        // use the common heading to get a list of common parents
        List<Object> commonParents = getCommonParents(commonHeading, jsonObject);

        // use the tailing part of filterPath to filter out the list of common parents
        // at the same time, use the tailing part of targetPath to retrieve a list of targeted values
        String filterTail = getPathTail(filterPath, commonHeading);
        String targetTail = getPathTail(targetPath, commonHeading);
        for (Object obj : commonParents) {
            // check existence of filterValue, and if so retrieve the targetValue
            boolean filterValueMatches = isJsonValueAMatch(filterTail, filterValue, obj);
            if (filterValueMatches) {
                results.addAll(getValuesFromSourceGeneral(targetTail, obj));
            }
        }

        // in case of empty results, check filterAlternativeOption
        if (results.isEmpty() && !StringUtils.isBlank(filterAlternativeOption)) {
            switch (StringUtils.lowerCase(filterAlternativeOption)) {
                case "all":
                    return getValuesFromSourceGeneral(targetTail, commonParents);
                case "first":
                    return getValuesFromSourceGeneral(targetTail, commonParents.get(0));
                case "last":
                    return getValuesFromSourceGeneral(targetTail, commonParents.get(commonParents.size() - 1));
                case "random":
                    return getValuesFromSourceGeneral(targetTail, commonParents.get(random.nextInt(commonParents.size())));
                default:
                    // nothing special
            }
        }

        return results;
    }

    /**
     * filter out the input JSONObject and retrieve values from multiple target paths at the same time
     * 
     * @param targets a map with its keys being names of target variables and its values being corresponding JSON paths
     * @param filterPath JSON path where values needed for filtering are to be found
     * @param filterFallbackPath JSON path from where values are to be retrieved when values retrieved from filterPath are all blank
     * @param filterValue value needed for comparison
     * @param filterAlternativeOption alternative option when there is no match found for the filtering condition, options are all | first | last |
     *            random | none, DEFAULT none
     * @param jsonObject JSONObject
     * @return a list of values found
     */
    public static Map<String, List<Object>> getFilteredValuesFromSource(Map<String, String> targets, String filterPath, String filterFallbackPath,
            String filterValue, String filterAlternativeOption, JSONObject jsonObject) {

        log.debug("======= getting filtered values from a map =======");
        Map<String, List<Object>> results = new HashMap<>();
        List<String> targetPaths = new ArrayList<>(targets.values());

        // get the common heading of targetPath and filterPath
        String commonHeadingTarget = getCommonHeading(targetPaths);
        String commonHeadingFilter = getCommonHeading(filterPath, filterFallbackPath);
        String commonHeading = getCommonHeading(commonHeadingTarget, commonHeadingFilter);

        // use the common heading to get a list of common parents
        List<Object> commonParents = getCommonParents(commonHeading, jsonObject);

        // use the tailing part of filterPath to filter out the list of common parents
        String filterTail = getPathTail(filterPath, commonHeading);
        String filterFallbackTail = getPathTail(filterFallbackPath, commonHeading);

        for (Object obj : commonParents) {
            // check existence of filterValue, and if so retrieve the targetValue
            boolean filterValueMatches = isJsonValueAMatch(filterTail, filterFallbackTail, filterValue, obj);
            if (filterValueMatches) {
                // use the tailing part of every targetPath to retrieve a list of targeted values
                for (Map.Entry<String, String> target : targets.entrySet()) {
                    String targetVariable = target.getKey();
                    String targetPath = target.getValue();
                    String targetTail = getPathTail(targetPath, commonHeading);
                    List<Object> targetResults = getValuesFromSourceGeneral(targetTail, obj);
                    results.put(targetVariable, targetResults);
                    log.debug("targetVariable = " + targetVariable);
                    log.debug("targetTail = " + targetTail);
                }
            }
        }

        for (Map.Entry<String, String> target : targets.entrySet()) {
            String variable = target.getKey();
            // in case of empty sub-results, check filterAlternativeOption
            if (!results.containsKey(variable) && !StringUtils.isBlank(filterAlternativeOption)) {
                String pathTail = getPathTail(target.getValue(), commonHeading);
                switch (StringUtils.lowerCase(filterAlternativeOption)) {
                    case "all":
                        results.put(variable, getValuesFromSourceGeneral(pathTail, commonParents));
                        break;
                    case "first":
                        results.put(variable, getValuesFromSourceGeneral(pathTail, commonParents.get(0)));
                        break;
                    case "last":
                        results.put(variable, getValuesFromSourceGeneral(pathTail, commonParents.get(commonParents.size() - 1)));
                        break;
                    case "random":
                        results.put(variable, getValuesFromSourceGeneral(pathTail, commonParents.get(random.nextInt(commonParents.size()))));
                        break;
                    default:
                        // otherwise create an empty list as value
                        results.put(variable, new ArrayList<>());
                }
            }
        }

        return results;
    }

    /**
     * filter out the input JSONObject and retrieve values from multiple target paths at the same time
     * 
     * @param targets a map with its keys being names of target variables and its values being corresponding JSON paths
     * @param filterPath JSON path where values needed for filtering are to be found
     * @param filterValue value needed for comparison
     * @param filterAlternativeOption alternative option when there is no match found for the filtering condition, options are all | first | last |
     *            random | none, DEFAULT none
     * @param jsonObject JSONObject
     * @return a list of values found
     */
    public static Map<String, List<Object>> getFilteredValuesFromSource(Map<String, String> targets, String filterPath, String filterValue,
            String filterAlternativeOption, JSONObject jsonObject) {

        return getFilteredValuesFromSource(targets, filterPath, filterPath, filterValue, filterAlternativeOption, jsonObject);
    }

    /**
     * get the common heading string of the two input JSON paths
     * 
     * @param path1 JSON path string
     * @param path2 JSON path string
     * @return the common heading string of the two paths
     */
    public static String getCommonHeading(String path1, String path2) {
        if (StringUtils.isAnyBlank(path1, path2)) {
            return "";
        }

        // path1 and path2 are both dot-separated strings
        String[] parts1 = path1.split("\\.");
        String[] parts2 = path2.split("\\.");
        int minLength = Math.min(parts1.length, parts2.length);

        StringBuilder headingBuilder = new StringBuilder();
        for (int i = 0; i < minLength; ++i) {
            if (!parts1[i].equals(parts2[i])) {
                // no more common heading
                break;
            }

            // common heading continued
            headingBuilder.append(parts1[i]);
            headingBuilder.append(".");
        }

        // get rid of the last dot
        String commongHeading = headingBuilder.toString();

        return StringUtils.strip(commongHeading, ".");
    }

    /**
     * get the common heading string of a list of JSON paths
     * 
     * @param paths a list of JSON paths
     * @return the common heading string of all paths in the input list
     */
    public static String getCommonHeading(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }

        if (paths.size() < 2) {
            return paths.get(0);
        }

        String commonHeading = paths.get(0);
        for (int i = 1; i < paths.size(); ++i) {
            commonHeading = getCommonHeading(commonHeading, paths.get(i));
        }

        return commonHeading;
    }

    /**
     * update the input JSONObject or JSONArray using records delivered by the input map
     * 
     * @param pathValueMap map between JSON paths that shall be updated and new values accordingly
     * @param obj JSONObject or JSONArray
     */
    public static void updateJSONObjectOrArray(Map<String, String> pathValueMap, Object obj) {
        if (pathValueMap == null) {
            // no need to update anything
            return;
        }
        if (obj instanceof JSONArray) {
            updateJSONArray(pathValueMap, (JSONArray) obj);
        } else if (obj instanceof JSONObject) {
            updateJSONObject(pathValueMap, (JSONObject) obj);
        } else {
            // invalid obj
            log.debug("The input Object can only be either JSONArray or JSONObject.");
        }
    }

    /**
     * update the input JSONObject or JSONArray by replacing the value found in the input JSON path with the input new value
     * 
     * @param jsonPath JSON path where the value shall be updated
     * @param newValue new value to use
     * @param obj JSONObject or JSONArray
     */
    public static void updateJSONObjectOrArray(String jsonPath, String newValue, Object obj) {
        if (obj instanceof JSONArray) {
            updateJSONArray(jsonPath, newValue, (JSONArray) obj);
        } else if (obj instanceof JSONObject) {
            updateJSONObject(jsonPath, newValue, (JSONObject) obj);
        } else {
            // invalid obj
            log.debug("The input Object can only be either JSONArray or JSONObject.");
        }
    }

    /**
     * update the input JSONObject using records delivered by the input map
     * 
     * @param pathValueMap map between JSON paths that shall be updated and new values accordingly
     * @param jsonObject JSONObject
     */
    public static void updateJSONObject(Map<String, String> pathValueMap, JSONObject jsonObject) {
        if (pathValueMap == null) {
            // no need to update anything
            return;
        }
        for (Map.Entry<String, String> entry : pathValueMap.entrySet()) {
            String jsonPath = entry.getKey();
            String newValue = entry.getValue();
            updateJSONObject(jsonPath, newValue, jsonObject);
        }
    }

    /**
     * update the input JSONObject by replacing the value found in the input JSON path with the input new value
     * 
     * @param jsonPath JSON path where the value shall be updated
     * @param newValue new value to use
     * @param jsonObject JSONObject
     */
    @SuppressWarnings("unchecked")
    public static void updateJSONObject(String jsonPath, String newValue, JSONObject jsonObject) {
        // base case: jsonPath has no children any more
        if (!jsonPath.contains(".")) {
            jsonObject.put(jsonPath, newValue);
            return;
        }

        // otherwise, update JSONObject or JSONArray according to the actual type
        int splitterIndex = jsonPath.indexOf(".");
        String step = jsonPath.substring(0, splitterIndex);
        String pathTail = jsonPath.substring(splitterIndex + 1);
        updateJSONObjectOrArray(pathTail, newValue, jsonObject.get(step));
    }

    /**
     * update the input JSONArray using records delivered by the input map
     * 
     * @param pathValueMap map between JSON paths that shall be updated and new values accordingly
     * @param jsonArray JSONArray
     */
    public static void updateJSONArray(Map<String, String> pathValueMap, JSONArray jsonArray) {
        if (pathValueMap == null) {
            // no need to update anything
            return;
        }
        // update all elements in this JSONArray
        for (Object element : jsonArray) {
            updateJSONObject(pathValueMap, (JSONObject) element);
        }
    }

    /**
     * update the input JSONArray by replacing the value found in the input JSON path with the input new value
     * 
     * @param jsonPath JSON path where the value shall be updated
     * @param newValue new value to use
     * @param jsonArray JSONArray
     */
    public static void updateJSONArray(String jsonPath, String newValue, JSONArray jsonArray) {
        // update all elements in this JSONArray
        for (Object element : jsonArray) {
            updateJSONObject(jsonPath, newValue, (JSONObject) element);
        }
    }

    /**
     * get the chopped tail of the input JSON path
     * 
     * @param path JSON path string
     * @param heading the heading string that is to be chopped out from path
     * @return the chopped tail of path
     */
    private static String getPathTail(String path, String heading) {
        log.debug("Getting path tail from: '" + path + "' where heading = " + heading);

        if (StringUtils.isBlank(heading)) {
            log.debug("Heading is blank, returning the whole path instead...");
            return path;
        }

        String filterTail = StringUtils.removeStart(path, heading);

        return StringUtils.strip(filterTail, ".");
    }

    /**
     * checks whether the value retrieved from the input JSON path matches the input value
     * 
     * @param path JSON path
     * @param value value to match
     * @param obj either JSONObject or JSONArray
     * @return true if the value from path matches the input value, false otherwise
     */
    private static boolean isJsonValueAMatch(String path, String value, Object obj) {
        return isJsonValueAMatch(path, null, value, obj);
    }

    /**
     * checks whether the value from the input JSON path matches the input value
     * 
     * @param path JSON path
     * @param fallbackPath JSON path, from where the values are to be used instead if values from path are all blank
     * @param value value to match
     * @param obj either JSONObject or JSONArray
     * @return true if the value from path (OR if it is blank there, from fallbackPath) matches the input value, false otherwise
     */
    private static boolean isJsonValueAMatch(String path, String fallbackPath, String value, Object obj) {
        log.debug("comparing value from the path: " + path);
        int result = compareJsonValue(path, value, obj);
        if (result == 2 && !StringUtils.isBlank(fallbackPath)) {
            log.debug("comparing value from the fallback path: " + fallbackPath);
            result = compareJsonValue(fallbackPath, value, obj);
        }

        return result == 0;
    }

    /**
     * compare values retrieved from the JSON path to the input value
     * 
     * @param path JSON path
     * @param value value to compare with
     * @param obj either JSONObject or JSONArray
     * @return 0 if the values are equal, 2 if the values retrieved from path are all blank, 1 otherwise
     */
    private static int compareJsonValue(String path, String value, Object obj) {
        // a blank path or value matches everything
        if (StringUtils.isAnyBlank(path, value)) {
            return 0;
        }

        // get a list of values from the path
        List<Object> values = getValuesFromSourceGeneral(path, obj);
        boolean valueEmpty = true;
        for (Object v : values) {
            String valueString = v == null ? "" : String.valueOf(v);
            if (value.equals(valueString)) {
                return 0;
            }
            valueEmpty = valueEmpty && StringUtils.isBlank(valueString);
        }

        if (valueEmpty) {
            // all values are blank, signify this to use fallback
            return 2;
        }

        // other cases
        return 1;
    }

}
