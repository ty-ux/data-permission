package com.ty.dp.core.model;

/**
 * How a rule is combined with the previous rule on the same table.
 */
public enum LogicOperator {
    AND,
    OR;

    public static LogicOperator from(String value) {
        if (value != null && "OR".equalsIgnoreCase(value.trim())) {
            return OR;
        }
        return AND;
    }
}
