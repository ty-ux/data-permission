package com.ty.dp.starter;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.DataPermissionRuleItem;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RightValueType;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads rules from the bundled three-table schema. Table names are
 * configurable, columns are fixed, and the SQL lives here so host projects do
 * not need to add mappers or entities.
 */
public class JdbcDataPermissionRuleRepository implements DataPermissionRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcDataPermissionRuleRepository.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final DataPermissionProperties properties;

    public JdbcDataPermissionRuleRepository(NamedParameterJdbcTemplate jdbc,
                                           DataPermissionProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public List<DataPermissionRule> findByRoles(Collection<String> roleIds, String appPoint) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String ruleTable = SqlIdentifiers.table(properties.getTable().getRule());
        String roleRuleTable = SqlIdentifiers.table(properties.getTable().getRoleRule());
        String itemTable = SqlIdentifiers.table(properties.getTable().getRuleItem());

        StringBuilder sql = new StringBuilder(512);
        sql.append("select r.id, r.rule_name, r.app_point, r.table_name, r.field_name, ")
                .append("r.condition_type, r.condition_value, r.rule_mode, r.logic_operator, ")
                .append("r.sort_order, r.priority, r.enabled, r.description, rp.role_id ")
                .append("from ").append(ruleTable).append(" r ")
                .append("join ").append(roleRuleTable).append(" rp on rp.rule_id = r.id and rp.is_del = '0' ")
                .append("where r.is_del = '0' and r.enabled = '1' and rp.role_id in (:roleIds) ");
        MapSqlParameterSource params = new MapSqlParameterSource("roleIds", roleIds);
        if (appPoint != null && !appPoint.trim().isEmpty()) {
            sql.append("and (r.app_point = :appPoint or r.app_point is null or r.app_point = '') ");
            params.addValue("appPoint", appPoint.trim());
        }
        sql.append("order by r.priority desc, r.sort_order asc, r.id asc");

        final Map<String, DataPermissionRule> byId = new LinkedHashMap<String, DataPermissionRule>();
        jdbc.query(sql.toString(), params, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                DataPermissionRule rule = mapRule(rs);
                if (!byId.containsKey(rule.getId())) {
                    byId.put(rule.getId(), rule);
                }
            }
        });

        if (byId.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> complexIds = new ArrayList<String>();
        for (DataPermissionRule rule : byId.values()) {
            if (rule.getRuleMode() == RuleMode.COMPLEX) {
                complexIds.add(rule.getId());
            }
        }
        if (!complexIds.isEmpty()) {
            loadItems(itemTable, complexIds, byId);
        }
        return new ArrayList<DataPermissionRule>(byId.values());
    }

    private void loadItems(String itemTable, List<String> ruleIds,
                           final Map<String, DataPermissionRule> rules) {
        String sql = "select id, rule_id, sort_order, group_no, sub_group_no, logic_operator, "
                + "left_type, left_expr, operator, right_type, right_expr "
                + "from " + itemTable + " where is_del = '0' and rule_id in (:ruleIds) "
                + "order by group_no asc, sub_group_no asc, sort_order asc, id asc";
        MapSqlParameterSource params = new MapSqlParameterSource("ruleIds", ruleIds);
        jdbc.query(sql, params, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                String ruleId = rs.getString("rule_id");
                DataPermissionRule rule = rules.get(ruleId);
                if (rule == null) {
                    return;
                }
                DataPermissionRuleItem item = new DataPermissionRuleItem();
                item.setId(rs.getString("id"));
                item.setRuleId(ruleId);
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
        Object roleId = rs.getObject("role_id");
        rule.setRoleId(roleId == null ? null : String.valueOf(roleId));
        return rule;
    }

    private static Integer readInt(ResultSet rs, String column) {
        try {
            int value = rs.getInt(column);
            return rs.wasNull() ? Integer.valueOf(0) : Integer.valueOf(value);
        } catch (SQLException ex) {
            log.debug("Column {} is not available, defaulting to 0", column);
            return Integer.valueOf(0);
        }
    }
}
