package com.ty.dp.core.subject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable subject backed by a map of fields. Useful for tests, scheduled
 * jobs and adapters that already resolved the values they need.
 */
public final class SimpleDataPermissionSubject implements DataPermissionSubject {

    private final String subjectId;
    private final boolean superAdmin;
    private final Set<String> roleIds;
    private final Map<String, Object> fields;

    private SimpleDataPermissionSubject(String subjectId, boolean superAdmin,
                                        Set<String> roleIds, Map<String, Object> fields) {
        this.subjectId = subjectId;
        this.superAdmin = superAdmin;
        this.roleIds = roleIds == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(roleIds));
        this.fields = fields == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fields));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    @Override
    public Set<String> getRoleIds() {
        return roleIds;
    }

    @Override
    public Object getField(String name) {
        return name == null ? null : fields.get(name);
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public static final class Builder {
        private String subjectId;
        private boolean superAdmin;
        private Set<String> roleIds = new LinkedHashSet<String>();
        private Map<String, Object> fields = new LinkedHashMap<String, Object>();

        public Builder subjectId(String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder superAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
            return this;
        }

        public Builder roleIds(Set<String> roleIds) {
            this.roleIds = roleIds;
            return this;
        }

        public Builder roleId(String roleId) {
            this.roleIds.add(roleId);
            return this;
        }

        public Builder field(String name, Object value) {
            this.fields.put(name, value);
            return this;
        }

        public SimpleDataPermissionSubject build() {
            return new SimpleDataPermissionSubject(subjectId, superAdmin, roleIds, fields);
        }
    }
}
