package com.ty.dp.core.subject;

import java.util.Set;

/**
 * The current user as seen by the rule engine. Implementations adapt the host
 * application's principal (RuoYi LoginUser, Sa-Token login id, a job context,
 * a test fixture, ...) to this small surface.
 */
public interface DataPermissionSubject {

    /**
     * Stable identifier used in logs and previews.
     */
    String getSubjectId();

    /**
     * Super administrators bypass data permission entirely.
     */
    boolean isSuperAdmin();

    /**
     * Role identifiers used to look up rules. Role ids are strings so Long
     * ids, role keys and Sa-Token ids can all be represented.
     */
    Set<String> getRoleIds();

    /**
     * Value lookup for CURRENT_USER_FIELD and {@code #{field}} expressions.
     * Returns null when the field is not available.
     */
    Object getField(String name);
}
