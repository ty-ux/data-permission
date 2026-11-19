package com.ty.dp.core.model;

/**
 * Right-hand side value type for complex rule items.
 */
public enum RightValueType {
    CONST,
    CURRENT_USER_FIELD,
    FIELD,
    NONE;

    public static RightValueType from(String value) {
        if (value == null) {
            return CONST;
        }
        String v = value.trim().toUpperCase();
        try {
            return valueOf(v);
        } catch (IllegalArgumentException ex) {
            return CONST;
        }
    }
}
