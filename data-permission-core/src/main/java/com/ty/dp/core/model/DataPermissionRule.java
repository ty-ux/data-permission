package com.ty.dp.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One data permission rule. A rule targets one physical table and is bound to
 * one role through the role-rule relation table.
 */
public class DataPermissionRule {

    private String id;
    private String ruleName;
    private String appPoint;
    private String tableName;
    private String fieldName;
    private String conditionType;
    private String conditionValue;
    private RuleMode ruleMode = RuleMode.SIMPLE;
    private LogicOperator logicOperator = LogicOperator.AND;
    private Integer sortOrder = 0;
    private Integer priority = 0;
    private boolean enabled = true;
    private String roleId;
    private String description;
    private List<DataPermissionRuleItem> items = new ArrayList<DataPermissionRuleItem>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getAppPoint() {
        return appPoint;
    }

    public void setAppPoint(String appPoint) {
        this.appPoint = appPoint;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public String getConditionValue() {
        return conditionValue;
    }

    public void setConditionValue(String conditionValue) {
        this.conditionValue = conditionValue;
    }

    public RuleMode getRuleMode() {
        return ruleMode;
    }

    public void setRuleMode(RuleMode ruleMode) {
        this.ruleMode = ruleMode == null ? RuleMode.SIMPLE : ruleMode;
    }

    public LogicOperator getLogicOperator() {
        return logicOperator;
    }

    public void setLogicOperator(LogicOperator logicOperator) {
        this.logicOperator = logicOperator == null ? LogicOperator.AND : logicOperator;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority == null ? 0 : priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<DataPermissionRuleItem> getItems() {
        return items;
    }

    public void setItems(List<DataPermissionRuleItem> items) {
        this.items = items == null ? new ArrayList<DataPermissionRuleItem>() : items;
    }
}
