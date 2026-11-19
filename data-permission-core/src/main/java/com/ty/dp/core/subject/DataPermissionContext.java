package com.ty.dp.core.subject;

import java.util.function.Supplier;

/**
 * Request scoped holder plus a per-thread bypass switch.
 *
 * <p>The filter sets the subject at the start of a request and clears it in a
 * finally block. Non-web callers (Quartz jobs, tests) can use
 * {@link #callAs(DataPermissionSubject, Supplier)} or set the subject
 * explicitly.</p>
 */
public final class DataPermissionContext {

    private static final ThreadLocal<DataPermissionSubject> SUBJECT =
            new ThreadLocal<DataPermissionSubject>();
    private static final ThreadLocal<Integer> BYPASS_DEPTH = new ThreadLocal<Integer>();

    private DataPermissionContext() {
    }

    public static void setSubject(DataPermissionSubject subject) {
        if (subject == null) {
            SUBJECT.remove();
        } else {
            SUBJECT.set(subject);
        }
    }

    public static DataPermissionSubject getSubject() {
        return SUBJECT.get();
    }

    public static void clear() {
        SUBJECT.remove();
        BYPASS_DEPTH.remove();
    }

    public static boolean isBypassed() {
        Integer depth = BYPASS_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static <T> T without(Supplier<T> supplier) {
        enterBypass();
        try {
            return supplier.get();
        } finally {
            exitBypass();
        }
    }

    public static void without(Runnable runnable) {
        enterBypass();
        try {
            runnable.run();
        } finally {
            exitBypass();
        }
    }

    public static <T> T callAs(DataPermissionSubject subject, Supplier<T> supplier) {
        DataPermissionSubject previous = SUBJECT.get();
        SUBJECT.set(subject);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                SUBJECT.remove();
            } else {
                SUBJECT.set(previous);
            }
        }
    }

    public static void runAs(DataPermissionSubject subject, Runnable runnable) {
        callAs(subject, () -> {
            runnable.run();
            return null;
        });
    }

    private static void enterBypass() {
        Integer depth = BYPASS_DEPTH.get();
        BYPASS_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    private static void exitBypass() {
        Integer depth = BYPASS_DEPTH.get();
        if (depth == null || depth <= 1) {
            BYPASS_DEPTH.remove();
        } else {
            BYPASS_DEPTH.set(depth - 1);
        }
    }
}
