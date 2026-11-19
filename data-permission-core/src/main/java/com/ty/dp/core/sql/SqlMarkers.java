package com.ty.dp.core.sql;

/**
 * Marker prefixed to rewritten SQL. It prevents double injection when the same
 * SQL passes through the interceptor again (for example PageHelper count
 * queries that embed the original statement).
 */
public final class SqlMarkers {

    public static final String MARKER = "/*data-permission*/";

    private SqlMarkers() {
    }
}
