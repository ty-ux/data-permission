package com.ty.dp.starter;

import com.ty.dp.core.subject.DataPermissionSubject;
import com.ty.dp.core.subject.DataPermissionSubjectProvider;
import com.ty.dp.core.util.Reflections;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adapts a Spring Security principal to {@link DataPermissionSubject} by
 * reflection, so the same jar works with RuoYi's LoginUser, RuoYi-Vue-Plus
 * principals and custom login users without a compile time dependency on any
 * of them.
 */
public class SpringSecurityDataPermissionSubjectProvider implements DataPermissionSubjectProvider {

    private final Set<String> adminUserIds;

    public SpringSecurityDataPermissionSubjectProvider(Collection<String> adminUserIds) {
        this.adminUserIds = adminUserIds == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new HashSet<String>(adminUserIds));
    }

    @Override
    public DataPermissionSubject getCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null || principal instanceof String) {
            return null;
        }
        return new PrincipalSubject(principal, adminUserIds);
    }

    private static final class PrincipalSubject implements DataPermissionSubject {

        private final Object principal;
        private final Set<String> adminUserIds;
        private String subjectId;
        private boolean subjectIdResolved;
        private Set<String> roleIds;
        private Object user;
        private boolean userResolved;

        private PrincipalSubject(Object principal, Set<String> adminUserIds) {
            this.principal = principal;
            this.adminUserIds = adminUserIds;
        }

        @Override
        public String getSubjectId() {
            if (!subjectIdResolved) {
                Object value = Reflections.invoke(principal, "userId");
                if (value == null) {
                    value = Reflections.invoke(principal, "id");
                }
                if (value == null) {
                    value = Reflections.invoke(principal, "username");
                }
                subjectId = value == null ? null : String.valueOf(value);
                subjectIdResolved = true;
            }
            return subjectId;
        }

        @Override
        public boolean isSuperAdmin() {
            String id = getSubjectId();
            return id != null && adminUserIds.contains(id);
        }

        @Override
        public Set<String> getRoleIds() {
            if (roleIds == null) {
                Set<String> values = new LinkedHashSet<String>();
                Object roles = Reflections.invoke(getUser(), "roles");
                if (roles == null) {
                    roles = Reflections.invoke(principal, "roles");
                }
                if (roles instanceof Iterable) {
                    for (Object role : (Iterable<?>) roles) {
                        Object id = Reflections.invoke(role, "roleId");
                        if (id == null) {
                            id = Reflections.invoke(role, "roleKey");
                        }
                        if (id == null) {
                            id = Reflections.invoke(role, "id");
                        }
                        if (id != null) {
                            values.add(String.valueOf(id));
                        }
                    }
                }
                roleIds = Collections.unmodifiableSet(values);
            }
            return roleIds;
        }

        @Override
        public Object getField(String name) {
            if (name == null || name.isEmpty()) {
                return null;
            }
            Object value = Reflections.getProperty(principal, name);
            if (value != null) {
                return value;
            }
            Object userObject = getUser();
            if (userObject != null) {
                value = Reflections.getProperty(userObject, name);
                if (value != null) {
                    return value;
                }
            }
            if ("userName".equals(name) || "username".equals(name)) {
                return Reflections.invoke(principal, "username");
            }
            if ("deptId".equals(name)) {
                Object deptId = Reflections.invoke(principal, "deptId");
                return deptId != null || userObject == null
                        ? deptId
                        : Reflections.invoke(userObject, "deptId");
            }
            return null;
        }

        private Object getUser() {
            if (!userResolved) {
                user = Reflections.invoke(principal, "user");
                userResolved = true;
            }
            return user;
        }
    }
}
