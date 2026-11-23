package com.ty.dp.starter;

import com.ty.dp.core.DataPermissionMode;
import com.ty.dp.core.DataPermissionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = "data-permission")
public class DataPermissionProperties {

    /**
     * Master switch.
     */
    private boolean enabled = true;

    /**
     * OFF / LOG_ONLY / ENFORCE.
     */
    private DataPermissionMode mode = DataPermissionMode.ENFORCE;

    /**
     * Policy when a subject has no rules for a protected mapper.
     */
    private DataPermissionPolicy defaultPolicy = DataPermissionPolicy.DENY;

    /**
     * Policy when no subject can be resolved. Defaults to ALLOW so scheduled
     * jobs that call protected mappers keep working; set DENY for strict
     * deployments.
     */
    private DataPermissionPolicy noSubjectPolicy = DataPermissionPolicy.ALLOW;

    /**
     * User ids treated as super administrators. Defaults to the RuoYi admin id.
     */
    private List<String> adminUserIds = new ArrayList<String>(Collections.singletonList("1"));

    /**
     * Rule cache TTL in seconds.
     */
    private long cacheTtlSeconds = 300;

    private String datasourceBean = "";
    public String getDatasourceBean() { return datasourceBean; }
    public void setDatasourceBean(String value) { this.datasourceBean = value; }

    private Table table = new Table();

    private Admin admin = new Admin();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DataPermissionMode getMode() {
        return mode;
    }

    public void setMode(DataPermissionMode mode) {
        this.mode = mode;
    }

    public DataPermissionPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(DataPermissionPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public DataPermissionPolicy getNoSubjectPolicy() {
        return noSubjectPolicy;
    }

    public void setNoSubjectPolicy(DataPermissionPolicy noSubjectPolicy) {
        this.noSubjectPolicy = noSubjectPolicy;
    }

    public List<String> getAdminUserIds() {
        return adminUserIds;
    }

    public void setAdminUserIds(List<String> adminUserIds) {
        this.adminUserIds = adminUserIds;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public static class Table {

        private String rule = "sys_permission_rule";
        private String ruleItem = "sys_permission_rule_item";
        private String roleRule = "sys_role_permission_rule";

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

        public String getRuleItem() {
            return ruleItem;
        }

        public void setRuleItem(String ruleItem) {
            this.ruleItem = ruleItem;
        }

        public String getRoleRule() {
            return roleRule;
        }

        public void setRoleRule(String roleRule) {
            this.roleRule = roleRule;
        }
    }

    public static class Admin {

        private java.util.Map<String, String> authorization = new java.util.LinkedHashMap<String, String>();
        public java.util.Map<String, String> getAuthorization() { return authorization; }
        public void setAuthorization(java.util.Map<String, String> value) { authorization = value; }

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
