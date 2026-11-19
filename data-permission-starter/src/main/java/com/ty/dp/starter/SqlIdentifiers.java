package com.ty.dp.starter;

import java.util.regex.Pattern;

public final class SqlIdentifiers {

    private static final Pattern TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)?$");

    private SqlIdentifiers() {
    }

    public static String table(String value) {
        if (value == null || !TABLE.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid SQL table name configured for data permission: " + value);
        }
        return value.trim();
    }
}
