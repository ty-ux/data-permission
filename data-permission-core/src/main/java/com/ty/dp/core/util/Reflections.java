package com.ty.dp.core.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection helper used to read subject fields and query parameters.
 * Results are cached per class + property name.
 */
public final class Reflections {

    private static final Object MISS = new Object();
    private static final ConcurrentHashMap<String, Object> CACHE =
            new ConcurrentHashMap<String, Object>();

    private Reflections() {
    }

    public static Object getProperty(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) {
            return null;
        }
        String[] parts = name.split("\\.");
        Object current = target;
        for (String part : parts) {
            current = getSimpleProperty(current, part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object getSimpleProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(name);
        }
        Method method = findGetter(target.getClass(), name);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    public static Method findGetter(Class<?> type, String name) {
        if (type == null || name == null || name.isEmpty()) {
            return null;
        }
        String key = type.getName() + "#" + name;
        Object cached = CACHE.get(key);
        if (cached != null) {
            return cached == MISS ? null : (Method) cached;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String[] candidates = new String[]{"get" + suffix, "is" + suffix, name};
        for (String candidate : candidates) {
            try {
                Method method = type.getMethod(candidate);
                if (method.getParameterTypes().length == 0) {
                    CACHE.put(key, method);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next candidate
            }
        }
        CACHE.put(key, MISS);
        return null;
    }

    public static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        Method method = findGetter(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }
}
