package org.dynmap.web;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Json {
    public static String stringifyJson(Object o) {
        StringBuilder sb = new StringBuilder();
        appendJson(o, sb);
        return sb.toString();
    }

    public static void escape(String s, StringBuilder s2) {
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            switch(ch){
            case '"':
                s2.append("\\\"");
                break;
            case '\\':
                s2.append("\\\\");
                break;
            case '\b':
                s2.append("\\b");
                break;
            case '\f':
                s2.append("\\f");
                break;
            case '\n':
                s2.append("\\n");
                break;
            case '\r':
                s2.append("\\r");
                break;
            case '\t':
                s2.append("\\t");
                break;
            case '/':
                s2.append("\\/");
                break;
            default:
                if(ch <= '\u001F' || ch >= '\u007F'){
                    String ss=Integer.toHexString(ch);
                    s2.append(IntStream.range(0, 4 - ss.length()).mapToObj(k -> "0").collect(Collectors.joining("", "\\u", ss.toUpperCase())));
                }
                else{
                    s2.append(ch);
                }
            }
        }//for
    }

    public static void appendJson(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Boolean) {
            sb.append(((Boolean) o) ? "true" : "false");
        } else if (o instanceof String) {
            sb.append("\"");
            escape((String)o, sb);
            sb.append("\"");
        } else if (o instanceof Integer || o instanceof Long || o instanceof Float || o instanceof Double) {
            sb.append(o.toString());
        } else if (o instanceof Map<?, ?>) {
            Map<?, ?> m = (Map<?, ?>) o;
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (first)
                    first = false;
                else
                    sb.append(",");

                appendJson(entry.getKey(), sb);
                sb.append(": ");
                appendJson(entry.getValue(), sb);
            }
            sb.append("}");
        } else if (o instanceof List<?>) {
            List<?> l = (List<?>) o;
            sb.append("[");
            int count = 0;
            for (Object value : l) {
                if (count++ > 0) sb.append(",");
                appendJson(value, sb);
            }
            sb.append("]");
        } else if (o.getClass().isArray()) {
            int length = Array.getLength(o);
            sb.append("[");
            int count = 0;
            for (int i = 0; i < length; i++) {
                if (count++ > 0) sb.append(",");
                appendJson(Array.get(o, i), sb);
            }
            sb.append("]");
        } else if (o instanceof Object) /* TODO: Always true, maybe interface? */ {
            sb.append("{");
            boolean first = true;

            Class<?> c = o.getClass();
            for(Field field : c.getFields()) {
                if (!Modifier.isPublic(field.getModifiers()))
                    continue;
                String fieldName = field.getName();
                Object fieldValue;
                try {
                     fieldValue = field.get(o);
                } catch (IllegalArgumentException e) {
                    continue;
                } catch (IllegalAccessException e) {
                    continue;
                }

                if (first)
                    first = false;
                else
                    sb.append(",");
                appendJson(fieldName, sb);
                sb.append(": ");
                appendJson(fieldValue, sb);
            }
            sb.append("}");
        } else {
            sb.append("undefined");
        }
    }
}
