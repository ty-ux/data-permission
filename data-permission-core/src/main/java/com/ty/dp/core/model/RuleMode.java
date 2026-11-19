package com.ty.dp.core.model;

/**
 * Rule execution mode.
 */
public enum RuleMode {
    SIMPLE,
    COMPLEX;

    public static RuleMode from(String value) {
        if (value != null && "COMPLEX".equalsIgnoreCase(value.trim())) {
            return COMPLEX;
        }
        return SIMPLE;
    }
}
