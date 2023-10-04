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

    public static JSONObject getJSONObjectFromString(String s) throws ParseException {
        return (JSONObject) JSON_PARSER.parse(s);
    }

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

    public static List<Object> getValuesFromSourceGeneral(String source, List<Object> objects) {
        List<Object> results = new ArrayList<>();
        for (Object obj : objects) {
            results.addAll(getValuesFromSourceGeneral(source, obj));
        }

        return results;
    }

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
                for (int i = 0; i < jsonArray.size(); ++i) {
                    results.add(jsonArray.get(i));
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

    public static List<Object> getValuesFromSource(String source, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();
        // base case: no source specified
        // base case: source is the wanted node, no further
        if (StringUtils.isBlank(source) || !source.contains(".")) {
            // add all plain values
            for (int i = 0; i < jsonArray.size(); ++i) {
                JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                results.add(jsonObject.get(source));
            }

            return results;
        }

        // other cases: one step further towards the leaf node
        int splitterIndex = source.indexOf(".");
        String key = source.substring(0, splitterIndex);
        String newSource = source.substring(splitterIndex + 1);

        for (int i = 0; i < jsonArray.size(); ++i) {
            JSONObject element = (JSONObject) jsonArray.get(i);
            Object obj = element.get(key);
            if (obj instanceof JSONArray) {
                results.addAll(getValuesFromSource(newSource, (JSONArray) obj));
            } else {
                results.addAll(getValuesFromSource(newSource, (JSONObject) obj));
            }
        }

        return results;
    }

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

    public static List<Object> getCommonParents(String targetPath, String filterPath, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();
        // base case: either one goes no further, then all JSONObjects in jsonArray are their common parents
        if (!targetPath.contains(".") || !filterPath.contains(".")) {
            for (int i = 0; i < jsonArray.size(); ++i) {
                results.add(jsonArray.get(i));
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
            for (int i = 0; i < jsonArray.size(); ++i) {
                results.add(jsonArray.get(i));
            }

            return results;
        }

        // move forward
        String newTargetPath = targetPath.substring(targetSplittingIndex + 1);
        String newFilterPath = filterPath.substring(filterSplittingIndex + 1);
        for (int i = 0; i < jsonArray.size(); ++i) {
            JSONObject jsonObject = (JSONObject) jsonArray.get(i);
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

    public static List<Object> getCommonParents(String commonHeading, JSONArray jsonArray) {
        List<Object> results = new ArrayList<>();

        // base case 1: no commonHeading availabe
        // base case 2: commonHeading does not contain dot
        if (StringUtils.isBlank(commonHeading) || !commonHeading.contains(".")) {
            // in both cases, every element of the current JSONArray is a common parent
            for (int i = 0; i < jsonArray.size(); ++i) {
                results.add(jsonArray.get(i));
            }

            return results;
        }

        // otherwise, move forward by one step
        int splittingIndex = commonHeading.indexOf(".");
        String head = commonHeading.substring(0, splittingIndex);
        String tail = commonHeading.substring(splittingIndex + 1);

        for (int i = 0; i < jsonArray.size(); ++i) {
            JSONObject jsonObject = (JSONObject) jsonArray.get(i);
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
                    return getValuesFromSourceGeneral(targetTail, commonParents.get(new Random().nextInt(commonParents.size())));
                default:
                    // nothing special
            }
        }

        return results;
    }

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
                        results.put(variable, getValuesFromSourceGeneral(pathTail, commonParents.get(new Random().nextInt(commonParents.size()))));
                        break;
                    default:
                        // otherwise create an empty list as value
                        results.put(variable, new ArrayList<>());
                }
            }
        }

        return results;
    }

    public static Map<String, List<Object>> getFilteredValuesFromSource(Map<String, String> targets, String filterPath, String filterValue,
            String filterAlternativeOption, JSONObject jsonObject) {

        return getFilteredValuesFromSource(targets, filterPath, filterPath, filterValue, filterAlternativeOption, jsonObject);
    }

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

    private static String getPathTail(String path, String heading) {
        log.debug("getting path tail from: '" + path + "' where heading = " + heading);

        if (StringUtils.isBlank(heading)) {
            log.debug("heading is blank, returning path...");
            return path;
        }

        String filterTail = StringUtils.removeStart(path, heading);

        return StringUtils.strip(filterTail, ".");
    }

    private static List<String> getPathTails(List<String> paths, String heading) {
        List<String> tails = new ArrayList<>(paths.size());
        for (String path : paths) {
            tails.add(getPathTail(path, heading));
        }

        return tails;
    }

    private static boolean isJsonValueAMatch(String path, String value, Object obj) {
        return isJsonValueAMatch(path, null, value, obj);
    }

    private static boolean isJsonValueAMatch(String path, String fallbackPath, String value, Object obj) {
        log.debug("comparing value from the path: " + path);
        int result = compareJsonValue(path, value, obj);
        if (result == 2 && !StringUtils.isBlank(fallbackPath)) {
            log.debug("comparing value from the fallback path: " + fallbackPath);
            result = compareJsonValue(fallbackPath, value, obj);
        }

        return result == 0;
    }

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
