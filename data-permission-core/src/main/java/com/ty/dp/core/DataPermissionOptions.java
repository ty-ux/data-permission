package com.ty.dp.core;

/**
 * Runtime options for the interceptor. Immutable and independent of Spring so
 * the core module stays usable outside a Spring application.
 */
public final class DataPermissionOptions {

    private final DataPermissionMode mode;
    private final DataPermissionPolicy defaultPolicy;
    private final DataPermissionPolicy noSubjectPolicy;

    public DataPermissionOptions(DataPermissionMode mode,
                                 DataPermissionPolicy defaultPolicy,
                                 DataPermissionPolicy noSubjectPolicy) {
        this.mode = mode == null ? DataPermissionMode.ENFORCE : mode;
        this.defaultPolicy = defaultPolicy == null ? DataPermissionPolicy.DENY : defaultPolicy;
        this.noSubjectPolicy = noSubjectPolicy == null ? DataPermissionPolicy.ALLOW : noSubjectPolicy;
    }

    public static DataPermissionOptions defaults() {
        return new DataPermissionOptions(
                DataPermissionMode.ENFORCE,
                DataPermissionPolicy.DENY,
                DataPermissionPolicy.ALLOW);
    }

    public DataPermissionMode getMode() {
        return mode;
    }

    public DataPermissionPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public DataPermissionPolicy getNoSubjectPolicy() {
        return noSubjectPolicy;
    }
}
