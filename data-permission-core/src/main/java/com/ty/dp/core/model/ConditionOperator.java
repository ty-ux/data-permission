package com.ty.dp.core.model;

/**
 * Supported comparison operators. Synonyms are normalized to one canonical form.
 */
public enum ConditionOperator {
    EQ("="),
    NE("<>"),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    LIKE("LIKE"),
    IN("IN"),
    IS_NULL("IS_NULL"),
    IS_NOT_NULL("IS_NOT_NULL");

    private final String symbol;

    ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static ConditionOperator from(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        if ("=".equals(v) || "EQ".equals(v)) {
            return EQ;
        }
        if ("!=".equals(v) || "<>".equals(v) || "NE".equals(v) || "!=".equals(v)) {
            return NE;
        }
        if (">".equals(v) || "GT".equals(v)) {
            return GT;
        }
        if (">=".equals(v) || "GE".equals(v)) {
            return GE;
        }
        if ("<".equals(v) || "LT".equals(v)) {
            return LT;
        }
        if ("<=".equals(v) || "LE".equals(v)) {
            return LE;
        }
        if ("LIKE".equals(v)) {
            return LIKE;
        }
        if ("IN".equals(v)) {
            return IN;
        }
        if ("IS_NULL".equals(v)) {
            return IS_NULL;
        }
        if ("IS_NOT_NULL".equals(v)) {
            return IS_NOT_NULL;
        }
        return null;
    }
}
