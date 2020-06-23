package org.dynmap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Arrays;

public class JSONUtils {
    /**
     * Gets a value at the specified path.
     * @deprecated Use {@link JSONUtils#getValue(JSONObject, String)} instead.
     * @param o the object
     * @param path the path, joined by '/'.
     * @return the value
     */
    @Deprecated
    public static Object g(JSONObject o, String path) {
        return getValue(o, path);
    }

    /**
     * Gets a value at the specified path.
     * @param o the object
     * @param path the path, joined by '/'.
     * @return the value
     */
    public static Object getValue(JSONObject o, String path) {
        int index = path.indexOf('/');
        if (index == -1) {
            return o.get(path);
        } else {
            String key = path.substring(0, index);
            String subpath = path.substring(index+1);
            Object oo = o.get(key);
            JSONObject subobject;
            if (oo == null) {
                return null;
            } else /*if (oo instanceof JSONObject)*/ {
                subobject = o;
            }
            return getValue(subobject, subpath);
        }
    }
    /**
     * Sets a value on the specified path. If JSONObjects inside the path are missing, they'll be created.
     * @deprecated Use {@link JSONUtils#setValue(JSONObject, String, Object)} instead.
     */
    @Deprecated
    public static void s(JSONObject o, String path, Object value) {
        setValue(o, path, value);
    }

    @SuppressWarnings("unchecked")
    public static void setValue(JSONObject o, String path, Object value) {
        int index = path.indexOf('/');
        if (index == -1) {
            o.put(path, value);
        } else {
            String key = path.substring(0, index);
            String subpath = path.substring(index+1);
            Object oo = o.get(key);
            JSONObject subobject;
            if (oo == null) {
                subobject = new JSONObject();
                o.put(key, subobject);
            } else /*if (oo instanceof JSONObject)*/ {
                subobject = (JSONObject)oo;
            }
            setValue(subobject, subpath, value);
        }
    }

    /**
     * Adds a value to the list at the specified path. If the list does not exist, it will be created.
     * @deprecated Use {@link JSONUtils#array(JSONObject, String, Object)} instead.
     */
    @Deprecated
    public static void a(JSONObject o, String path, Object value) {
        array(o, path, value);
    }

    /**
     * Adds a value to the list at the specified path. If the list does not exist, it will be created.
     */
    @SuppressWarnings("unchecked")
    public static void array(JSONObject o, String path, Object value) {
        Object oo = getValue(o, path);
        JSONArray array;
        if (oo == null) {
            array = new JSONArray();
            setValue(o, path, array);
        } else {
            array = (JSONArray)oo;
        }
        if(value != null)
            array.add(value);
    }

    /**
     * Simply creates a JSONArray.
     * @deprecated Use {@link JSONUtils#makeJSONArray(Object...)} instead.
     */
    @Deprecated
    public static JSONArray l(Object... items) {
        return makeJSONArray(items);
    }

    /**
     * Simply creates a JSONArray.
     */
    @SuppressWarnings("unchecked")
    public static JSONArray makeJSONArray(Object... items) {
        JSONArray arr = new JSONArray();
        arr.addAll(Arrays.asList(items));
        return arr;
    }
}
