package com.ty.dp.starter.admin;

/**
 * Optional host-provided authorization for the management API.
 * Implementations obtain the current login state from the host's own context.
 */
public interface DataPermissionAdminAuthorizer {

    enum AdminAction { LIST, SAVE, EDIT, REMOVE }

    boolean isAllowed(AdminAction action);
}
