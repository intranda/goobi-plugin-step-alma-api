package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JSONUtils {
    private static final JSONParser JSON_PARSER = new JSONParser();

    public static JSONObject getJSONObjectFromString(String s) throws ParseException {
        return (JSONObject) JSON_PARSER.parse(s);
    }

    public static List<Object> getValuesFromSource(String source, JSONObject jsonObject) {
        List<Object> results = new ArrayList<>();
        // base case: source is the wanted node, no futher
        if (!source.contains(".")) {
            Object value = jsonObject.get(source);
            results.add(value);
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
        // base case: source is the wanted node, no further
        if (!source.contains(".")) {
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

    public static List<Object> getFilteredObjectsFromSource(String targetPath, String filterPath, String filterValue, String filterAlternativeOption,
            JSONObject jsonObject) {
        List<Object> results = new ArrayList<>();

        // get the common heading of targetPath and filterPath

        // use the common heading to get a list of common parents

        // use the tailing part of filterPath to filter out the list of common parents

        // use the tailing part of targetPath to retrieve a list of targeted values

        return results;
    }

}
