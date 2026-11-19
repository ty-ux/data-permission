package com.ty.dp.core.exception;

/**
 * Thrown when SQL cannot be rewritten. The interceptor fails closed in ENFORCE
 * mode: the query is rejected instead of silently returning unfiltered data.
 */
public class DataPermissionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataPermissionException(String message) {
        super(message);
    }

    public DataPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
