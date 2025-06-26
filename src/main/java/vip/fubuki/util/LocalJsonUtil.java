package vip.fubuki.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalJsonUtil {
    public static Map<String,String> StringToMap(String param) {
        Map<String,String> map = new HashMap<>();
        String s1 = param.substring(1,param.length()-1);
        String s2 = s1.trim();
        String[] split = s2.split(",");
        for (int i = split.length - 1; i >= 0; i--) {
            String trim = split[i].trim();
            String[] split1 = trim.split("=");
            map.put(split1[0],split1[1]);
        }
        return map;
    }

//    public static Map<String,Object> mapDeserialize(String mapStr) {
//        Map<String, Object> map = new HashMap<>();
//        Pattern pattern = Pattern.compile("(\\w+)=([^,}]+)");
//        Matcher matcher = pattern.matcher(mapStr);
//        while (matcher.find()) {
//            map.put(matcher.group(1), matcher.group(2));
//        }
//        return map;
//    }

    public static Map<String, Object> mapDeserialize(String mapStr) {
        Map<String, Object> map = new HashMap<>();
        Pattern pattern = Pattern.compile("(\\w+)=\\{?([^{},]+|\\{[^{}]*})}?");
        Matcher matcher = pattern.matcher(mapStr);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if (value.startsWith("{") && value.endsWith("}")) {
                map.put(key, mapDeserialize(value.substring(1, value.length()-1)));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }


    public static Map<Integer,String> StringToEntryMap(String param) {
        Map<Integer,String> map = new HashMap<>();
        String s1 = param.substring(1,param.length()-1);
        String s2 = s1.trim();
        String[] split = s2.split(",");
        for (int i = split.length - 1; i >= 0; i--) {
            String trim = split[i].trim();
            String[] split1 = trim.split("=");
            map.put(Integer.parseInt(split1[0]),split1[1]);
        }
        return map;
    }
}
