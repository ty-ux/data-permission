package com.ty.dp.core;

/**
 * OFF      - do not touch SQL at all.
 * LOG_ONLY - rewrite SQL for logging only, execute the original SQL.
 * ENFORCE  - rewrite SQL and execute it.
 */
public enum DataPermissionMode {
    OFF,
    LOG_ONLY,
    ENFORCE
}
