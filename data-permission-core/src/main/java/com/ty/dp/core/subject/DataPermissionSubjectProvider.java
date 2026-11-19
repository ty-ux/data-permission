package com.ty.dp.core.subject;

/**
 * Resolves the current subject. Called once per request by the web filter and
 * lazily by the interceptor when no context is set.
 */
public interface DataPermissionSubjectProvider {

    DataPermissionSubject getCurrent();
}
