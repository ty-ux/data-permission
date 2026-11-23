package com.ty.dp.starter.admin;

import com.ty.dp.core.model.ConditionOperator;
import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.DataPermissionRuleItem;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RightValueType;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.starter.CachedDataPermissionRuleResolver;
import com.ty.dp.starter.DataPermissionProperties;
import com.ty.dp.starter.SqlIdentifiers;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional rule administration service. It is deliberately independent of the
 * host project's mappers and entities so it can be enabled in any application
 * that has the three tables.
 */
public class DataPermissionAdminService {

    private final java.util.function.Supplier<NamedParameterJdbcTemplate> jdbc;

    private NamedParameterJdbcTemplate jdbc() { return jdbc.get(); }
    private final DataPermissionProperties properties;
    private final DataPermissionRuleResolver ruleResolver;

    public DataPermissionAdminService(NamedParameterJdbcTemplate jdbc,
                                      DataPermissionProperties properties,
                                      DataPermissionRuleResolver ruleResolver) {
        this.jdbc = () -> jdbc;
        this.properties = properties;
        this.ruleResolver = ruleResolver;
    }

    public DataPermissionAdminService(java.util.function.Supplier<NamedParameterJdbcTemplate> jdbc,
            DataPermissionProperties properties, DataPermissionRuleResolver resolver) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.ruleResolver = resolver;
    }

    public List<DataPermissionRule> listRules(String appPoint, String ruleName) {
        String table = SqlIdentifiers.table(properties.getTable().getRule());
        StringBuilder sql = new StringBuilder(256);
        sql.append("select id, rule_name, app_point, table_name, field_name, condition_type, ")
                .append("condition_value, rule_mode, logic_operator, sort_order, priority, enabled, description ")
                .append("from ").append(table).append(" where is_del = '0' ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (appPoint != null && !appPoint.trim().isEmpty()) {
            sql.append("and app_point = :appPoint ");
            params.addValue("appPoint", appPoint.trim());
        }
        if (ruleName != null && !ruleName.trim().isEmpty()) {
            sql.append("and rule_name like :ruleName ");
            params.addValue("ruleName", "%" + ruleName.trim() + "%");
        }
        sql.append("order by priority desc, sort_order asc, id asc");

        List<DataPermissionRule> rules = jdbc().query(sql.toString(), params, new RowMapper<DataPermissionRule>() {
            @Override
            public DataPermissionRule mapRow(ResultSet rs, int rowNum) throws SQLException {
                return mapRule(rs);
            }
        });
        loadItems(rules);
        return rules;
    }

    public DataPermissionRule getRule(String id) {
        String table = SqlIdentifiers.table(properties.getTable().getRule());
        String sql = "select id, rule_name, app_point, table_name, field_name, condition_type, "
                + "condition_value, rule_mode, logic_operator, sort_order, priority, enabled, description "
                + "from " + table + " where id = :id and is_del = '0'";
        List<DataPermissionRule> rules = jdbc().query(sql,
                new MapSqlParameterSource("id", id), new RowMapper<DataPermissionRule>() {
                    @Override
                    public DataPermissionRule mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return mapRule(rs);
                    }
                });
        if (rules.isEmpty()) {
            return null;
        }
        loadItems(rules);
        return rules.get(0);
    }

    @Transactional
    public String saveRule(DataPermissionRule rule) {
        validate(rule);
        return transaction().execute(status -> saveRuleInTransaction(rule));
    }

    private org.springframework.transaction.support.TransactionTemplate transaction() {
        return new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc().getJdbcTemplate().getDataSource()));
    }

    private String saveRuleInTransaction(DataPermissionRule rule) {
        validate(rule);
        String ruleTable = SqlIdentifiers.table(properties.getTable().getRule());
        String itemTable = SqlIdentifiers.table(properties.getTable().getRuleItem());
        if (isBlank(rule.getId())) {
            rule.setId(newId());
            insertRule(ruleTable, rule);
        } else {
            if (updateRule(ruleTable, rule) == 0) {
                throw new IllegalArgumentException("Rule not found: " + rule.getId());
            }
            jdbc().update("delete from " + itemTable + " where rule_id = :ruleId",
                    new MapSqlParameterSource("ruleId", rule.getId()));
        }
        insertItems(itemTable, rule);
        invalidate();
        return rule.getId();
    }

    @Transactional
    public void deleteRules(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        transaction().execute(status -> { deleteRulesInTransaction(ids); return null; });
    }

    private void deleteRulesInTransaction(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String ruleTable = SqlIdentifiers.table(properties.getTable().getRule());
        String itemTable = SqlIdentifiers.table(properties.getTable().getRuleItem());
        String roleRuleTable = SqlIdentifiers.table(properties.getTable().getRoleRule());
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        jdbc().update("update " + ruleTable + " set is_del = '1', update_time = now() where id in (:ids)", params);
        jdbc().update("update " + itemTable + " set is_del = '1' where rule_id in (:ids)", params);
        jdbc().update("delete from " + roleRuleTable + " where rule_id in (:ids)", params);
        invalidate();
    }

    public List<String> listRoleRuleIds(String roleId) {
        String roleRuleTable = SqlIdentifiers.table(properties.getTable().getRoleRule());
        String sql = "select rule_id from " + roleRuleTable
                + " where role_id = :roleId and is_del = '0'";
        return jdbc().queryForList(sql, new MapSqlParameterSource("roleId", roleId), String.class);
    }

    @Transactional
    public void assignRoleRules(String roleId, List<String> ruleIds) {
        transaction().execute(status -> { assignRoleRulesInTransaction(roleId, ruleIds); return null; });
    }

    private void assignRoleRulesInTransaction(String roleId, List<String> ruleIds) {
        String roleRuleTable = SqlIdentifiers.table(properties.getTable().getRoleRule());
        jdbc().update("delete from " + roleRuleTable + " where role_id = :roleId",
                new MapSqlParameterSource("roleId", roleId));
        if (ruleIds != null) {
            for (String ruleId : ruleIds) {
                if (isBlank(ruleId)) {
                    continue;
                }
                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("id", newId())
                        .addValue("roleId", roleId)
                        .addValue("ruleId", ruleId);
                jdbc().update("insert into " + roleRuleTable
                        + " (id, role_id, rule_id, create_time, is_del) values (:id, :roleId, :ruleId, now(), '0')",
                        params);
            }
        }
        invalidate();
    }

    private void insertRule(String table, DataPermissionRule rule) {
        String sql = "insert into " + table
                + " (id, rule_name, app_point, table_name, field_name, condition_type, condition_value, "
                + "rule_mode, logic_operator, sort_order, priority, enabled, description, create_time, is_del) "
                + "values (:id, :ruleName, :appPoint, :tableName, :fieldName, :conditionType, :conditionValue, "
                + ":ruleMode, :logicOperator, :sortOrder, :priority, :enabled, :description, now(), '0')";
        jdbc().update(sql, ruleParams(rule));
    }

    private int updateRule(String table, DataPermissionRule rule) {
        String sql = "update " + table
                + " set rule_name = :ruleName, app_point = :appPoint, table_name = :tableName, "
                + "field_name = :fieldName, condition_type = :conditionType, condition_value = :conditionValue, "
                + "rule_mode = :ruleMode, logic_operator = :logicOperator, sort_order = :sortOrder, "
                + "priority = :priority, enabled = :enabled, description = :description, update_time = now() "
                + "where id = :id and is_del = '0'";
        return jdbc().update(sql, ruleParams(rule));
    }

    private MapSqlParameterSource ruleParams(DataPermissionRule rule) {
        return new MapSqlParameterSource()
                .addValue("id", rule.getId())
                .addValue("ruleName", rule.getRuleName())
                .addValue("appPoint", rule.getAppPoint())
                .addValue("tableName", rule.getTableName())
                .addValue("fieldName", rule.getFieldName())
                .addValue("conditionType", rule.getConditionType())
                .addValue("conditionValue", rule.getConditionValue())
                .addValue("ruleMode", rule.getRuleMode() == null ? RuleMode.SIMPLE.name() : rule.getRuleMode().name())
                .addValue("logicOperator", rule.getLogicOperator() == null ? LogicOperator.AND.name() : rule.getLogicOperator().name())
                .addValue("sortOrder", rule.getSortOrder() == null ? Integer.valueOf(0) : rule.getSortOrder())
                .addValue("priority", rule.getPriority() == null ? Integer.valueOf(0) : rule.getPriority())
                .addValue("enabled", rule.isEnabled() ? "1" : "0")
                .addValue("description", rule.getDescription());
    }

    private void insertItems(String table, DataPermissionRule rule) {
        if (rule.getRuleMode() != RuleMode.COMPLEX || rule.getItems() == null) {
            return;
        }
        int index = 0;
        for (DataPermissionRuleItem item : rule.getItems()) {
            if (item == null || isBlank(item.getLeftExpr()) || isBlank(item.getOperator())) {
                continue;
            }
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", isBlank(item.getId()) ? newId() : item.getId())
                    .addValue("ruleId", rule.getId())
                    .addValue("sortOrder", item.getSortOrder() == null ? Integer.valueOf(index) : item.getSortOrder())
                    .addValue("groupNo", item.getGroupNo() == null ? Integer.valueOf(1) : item.getGroupNo())
                    .addValue("subGroupNo", item.getSubGroupNo() == null ? Integer.valueOf(1) : item.getSubGroupNo())
                    .addValue("logicOperator", item.getLogicOperator() == null ? LogicOperator.AND.name() : item.getLogicOperator().name())
                    .addValue("leftType", isBlank(item.getLeftType()) ? "FIELD" : item.getLeftType())
                    .addValue("leftExpr", item.getLeftExpr())
                    .addValue("operator", item.getOperator())
                    .addValue("rightType", item.getRightType() == null ? RightValueType.CONST.name() : item.getRightType().name())
                    .addValue("rightExpr", item.getRightExpr());
            jdbc().update("insert into " + table
                    + " (id, rule_id, sort_order, group_no, sub_group_no, logic_operator, left_type, left_expr, "
                    + "operator, right_type, right_expr, create_time, is_del) "
                    + "values (:id, :ruleId, :sortOrder, :groupNo, :subGroupNo, :logicOperator, :leftType, "
                    + ":leftExpr, :operator, :rightType, :rightExpr, now(), '0')", params);
            index++;
        }
    }

    private void loadItems(List<DataPermissionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        final Map<String, DataPermissionRule> byId = new LinkedHashMap<String, DataPermissionRule>();
        List<String> complexIds = new ArrayList<String>();
        for (DataPermissionRule rule : rules) {
            if (rule.getRuleMode() == RuleMode.COMPLEX) {
                complexIds.add(rule.getId());
                byId.put(rule.getId(), rule);
            }
        }
        if (complexIds.isEmpty()) {
            return;
        }
        String itemTable = SqlIdentifiers.table(properties.getTable().getRuleItem());
        String sql = "select id, rule_id, sort_order, group_no, sub_group_no, logic_operator, left_type, "
                + "left_expr, operator, right_type, right_expr from " + itemTable
                + " where is_del = '0' and rule_id in (:ruleIds) "
                + "order by group_no asc, sub_group_no asc, sort_order asc, id asc";
        jdbc().query(sql, new MapSqlParameterSource("ruleIds", complexIds), new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                DataPermissionRule rule = byId.get(rs.getString("rule_id"));
                if (rule == null) {
                    return;
                }
                DataPermissionRuleItem item = new DataPermissionRuleItem();
                item.setId(rs.getString("id"));
                item.setRuleId(rs.getString("rule_id"));
                item.setSortOrder(readInt(rs, "sort_order"));
                item.setGroupNo(readInt(rs, "group_no"));
                item.setSubGroupNo(readInt(rs, "sub_group_no"));
                item.setLogicOperator(LogicOperator.from(rs.getString("logic_operator")));
                item.setLeftType(rs.getString("left_type"));
                item.setLeftExpr(rs.getString("left_expr"));
                item.setOperator(rs.getString("operator"));
                item.setRightType(RightValueType.from(rs.getString("right_type")));
                item.setRightExpr(rs.getString("right_expr"));
                rule.getItems().add(item);
            }
        });
    }

    private DataPermissionRule mapRule(ResultSet rs) throws SQLException {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setId(rs.getString("id"));
        rule.setRuleName(rs.getString("rule_name"));
        rule.setAppPoint(rs.getString("app_point"));
        rule.setTableName(rs.getString("table_name"));
        rule.setFieldName(rs.getString("field_name"));
        rule.setConditionType(rs.getString("condition_type"));
        rule.setConditionValue(rs.getString("condition_value"));
        rule.setRuleMode(RuleMode.from(rs.getString("rule_mode")));
        rule.setLogicOperator(LogicOperator.from(rs.getString("logic_operator")));
        rule.setSortOrder(readInt(rs, "sort_order"));
        rule.setPriority(readInt(rs, "priority"));
        String enabled = rs.getString("enabled");
        rule.setEnabled(!"0".equals(enabled) && !"false".equalsIgnoreCase(enabled));
        rule.setDescription(rs.getString("description"));
        return rule;
    }

    public void validate(DataPermissionRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule is required");
        }
        if (isBlank(rule.getTableName())) {
            throw new IllegalArgumentException("tableName is required");
        }
        if (rule.getRuleMode() == RuleMode.COMPLEX) {
            validateComplex(rule);
        } else {
            validateSimple(rule);
        }
    }

    private void validateSimple(DataPermissionRule rule) {
        if (isBlank(rule.getFieldName()) || isBlank(rule.getConditionType())) {
            throw new IllegalArgumentException("A simple rule needs fieldName and conditionType");
        }
        ConditionOperator operator = ConditionOperator.from(rule.getConditionType());
        if (operator == null) {
            throw new IllegalArgumentException("Unsupported conditionType: " + rule.getConditionType());
        }
        if (operator != ConditionOperator.IS_NULL && operator != ConditionOperator.IS_NOT_NULL
                && isBlank(rule.getConditionValue())) {
            throw new IllegalArgumentException("A simple rule needs conditionValue for operator "
                    + rule.getConditionType());
        }
    }

    private void validateComplex(DataPermissionRule rule) {
        if (rule.getItems() == null || rule.getItems().isEmpty()) {
            throw new IllegalArgumentException("A complex rule needs at least one item");
        }
        int index = 0;
        for (DataPermissionRuleItem item : rule.getItems()) {
            index++;
            if (item == null || isBlank(item.getLeftExpr()) || isBlank(item.getOperator())) {
                throw new IllegalArgumentException("Complex item " + index + " needs leftExpr and operator");
            }
            ConditionOperator operator = ConditionOperator.from(item.getOperator());
            if (operator == null) {
                throw new IllegalArgumentException("Complex item " + index
                        + " has an unsupported operator: " + item.getOperator());
            }
            if (item.getRightType() == RightValueType.SUBQUERY) {
                if (operator != ConditionOperator.IN) throw new IllegalArgumentException("SUBQUERY requires IN");
                com.ty.dp.core.sql.SubqueryTemplate.validate(item.getRightExpr());
            }
            if (operator == ConditionOperator.IS_NULL || operator == ConditionOperator.IS_NOT_NULL) {
                continue;
            }
            RightValueType rightType = item.getRightType() == null
                    ? RightValueType.CONST : item.getRightType();
            if (rightType == RightValueType.NONE) {
                throw new IllegalArgumentException("Complex item " + index
                        + " cannot use rightType NONE with operator " + item.getOperator());
            }
            if (isBlank(item.getRightExpr())) {
                throw new IllegalArgumentException("Complex item " + index + " needs rightExpr");
            }
        }
    }

    private void invalidate() {
        if (ruleResolver instanceof CachedDataPermissionRuleResolver) {
            ((CachedDataPermissionRuleResolver) ruleResolver).invalidateAll();
        }
    }

    private static Integer readInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? Integer.valueOf(0) : Integer.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
