package com.ty.dp.sample;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.DataPermissionRuleItem;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RightValueType;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.SimpleDataPermissionSubject;
import com.ty.dp.starter.admin.DataPermissionAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ComplexRuleSimulationTest {

    private static final String ROLE = "sim-complex-role";

    @Autowired
    private DataPermissionAdminService adminService;

    @Autowired
    private SimProjectMapper mapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        DataPermissionContext.clear();
    }

    @Test
    void groupsAreOredAndSubGroupsAreAnded() {
        DataPermissionRule rule = complex("complex-groups",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, "deptId"),
                item(1, 1, 1, LogicOperator.AND, "sim_project.status", "=",
                        RightValueType.CONST, "A"),
                item(1, 2, 0, LogicOperator.AND, "sim_project.owner_no", "=",
                        RightValueType.CURRENT_USER_FIELD, "userNo"),
                item(2, 1, 0, LogicOperator.OR, "sim_project.create_by", "=",
                        RightValueType.CURRENT_USER_FIELD, "userNo"));
        assign(ROLE, rule);
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 104L), ids(mapper.selectAll()));
    }

    @Test
    void itemOperatorsFollowSortOrderInsideASubGroup() {
        DataPermissionRule rule = complex("complex-item-operators",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, "deptId"),
                item(1, 1, 1, LogicOperator.OR, "sim_project.owner_no", "=",
                        RightValueType.CONST, "u3"),
                item(1, 1, 2, LogicOperator.AND, "sim_project.status", "=",
                        RightValueType.CONST, "A"));
        assign(ROLE, rule);
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 103L, 105L), ids(mapper.selectAll()));

        rule.getItems().get(0).setSortOrder(1);
        rule.getItems().get(1).setSortOrder(0);
        adminService.saveRule(rule);
        assertEquals(Collections.emptyList(), ids(mapper.selectAll()));
    }

    @Test
    void sameTableRulesCombineByPriorityAndOwnOperator() {
        DataPermissionRule department = simple("complex-rule-a", "dept_id", "=",
                "#{deptId}", 30, LogicOperator.AND);
        DataPermissionRule owner = simple("complex-rule-b", "owner_no", "=",
                "#{userNo}", 20, LogicOperator.OR);
        DataPermissionRule status = simple("complex-rule-c", "status", "=",
                "A", 10, LogicOperator.AND);
        assign(ROLE, department, owner, status);
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 103L), ids(mapper.selectAll()));

        owner.setPriority(40);
        adminService.saveRule(owner);
        assertEquals(Collections.singletonList(101L), ids(mapper.selectAll()));
    }

    @Test
    void disabledRuleDoesNotWidenTheResult() {
        DataPermissionRule department = simple("complex-enabled", "dept_id", "=",
                "#{deptId}", 10, LogicOperator.AND);
        DataPermissionRule owner = simple("complex-disabled", "owner_no", "=",
                "#{userNo}", 20, LogicOperator.OR);
        owner.setEnabled(false);
        assign(ROLE, department, owner);
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAll()));
    }

    @Test
    void unresolvedDynamicValueDeniesAllRows() {
        DataPermissionRule rule = simple("complex-missing", "dept_id", "=",
                "#{missingDept}", 0, LogicOperator.AND);
        assign(ROLE, rule);
        setSubject(100L, "u1");

        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void emptyInListDeniesAllRows() {
        DataPermissionRule rule = simple("complex-empty-in", "dept_id", "IN",
                "#{emptyDeptIds}", 0, LogicOperator.AND);
        assign(ROLE, rule);
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("complex-tester")
                .roleId(ROLE)
                .field("emptyDeptIds", Collections.emptyList())
                .build());

        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void invalidPersistedComplexItemIsFailClosed() {
        jdbc.update("insert into sys_permission_rule "
                        + "(id, rule_name, app_point, table_name, rule_mode, logic_operator, "
                        + "sort_order, priority, enabled, is_del) "
                        + "values ('sim-invalid-rule', 'invalid item', 'sim', 'sim_project', "
                        + "'COMPLEX', 'AND', 0, 0, '1', '0')",
                new MapSqlParameterSource());
        jdbc.update("insert into sys_permission_rule_item "
                        + "(id, rule_id, sort_order, group_no, sub_group_no, logic_operator, "
                        + "left_type, left_expr, operator, right_type, right_expr, is_del) "
                        + "values ('sim-invalid-item', 'sim-invalid-rule', 0, 1, 1, 'AND', "
                        + "'FIELD', 'sim_project.dept_id', '=', 'NONE', null, '0')",
                new MapSqlParameterSource());
        jdbc.update("insert into sys_role_permission_rule (id, role_id, rule_id, is_del) "
                        + "values ('sim-invalid-bind', '" + ROLE + "', 'sim-invalid-rule', '0')",
                new MapSqlParameterSource());

        setSubject(100L, "u1");
        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void subqueryBindingAliasCountAndCacheIsolation() {
        DataPermissionRule rule = complex("subquery",
                item(1, 1, 0, LogicOperator.AND, "sim_project.id", "IN", RightValueType.SUBQUERY,
                        "select id from sim_project where dept_id=#{deptId} and (owner_no=#{userNo} or create_by=#{userNo})"));
        assign(ROLE, rule);
        setSubject(100L, "u1");
        List<Long> expected = ids(mapper.selectAliased());
        assertTrue(!expected.isEmpty());
        assertEquals(expected, ids(mapper.selectByDeptParam(100L)));
        assertEquals(expected, ids(mapper.selectIds(Arrays.asList(101L, 102L, 103L, 104L, 105L))));
        com.github.pagehelper.PageHelper.startPage(1, 1);
        com.github.pagehelper.PageInfo<SimProject> page = new com.github.pagehelper.PageInfo<SimProject>(mapper.selectAliased());
        assertEquals(expected.size(), page.getTotal());
        assertEquals(1, page.getList().size());
        setSubject(100L, "nobody' OR '1'='1");
        assertTrue(mapper.selectAliased().isEmpty());
        setSubject(100L, "u1");
        rule.getItems().get(0).setRightExpr("select id from sim_project where dept_id=#{deptId} and id=-1");
        adminService.saveRule(rule);
        assertTrue(mapper.selectAliased().isEmpty());
    }

    @Test
    void subqueryUnionAndCountWrapperBindRepeatedFields() {
        assign(ROLE, complex("sq-union", item(1, 1, 0, LogicOperator.AND, "id", "IN", RightValueType.SUBQUERY,
                "select id from sim_project where dept_id=#{deptId} and dept_id=#{deptId}")));
        setSubject(100L, "u1");
        List<Long> expected = ids(mapper.selectAll());
        assertEquals(expected, mapper.selectUnionIds());
        assertEquals(expected, ids(mapper.selectFromSubquery()));
        assertEquals(Collections.emptyList(), ids(mapper.selectByDeptParam(200L)));
    }

    private void assign(String roleId, DataPermissionRule... rules) {
        List<String> ids = new ArrayList<String>();
        for (DataPermissionRule rule : rules) {
            ids.add(adminService.saveRule(rule));
        }
        adminService.assignRoleRules(roleId, ids);
    }

    private DataPermissionRule simple(String name, String field, String operator, String value,
                                      int priority, LogicOperator logic) {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName(name);
        rule.setAppPoint("sim");
        rule.setTableName("sim_project");
        rule.setFieldName(field);
        rule.setConditionType(operator);
        rule.setConditionValue(value);
        rule.setRuleMode(RuleMode.SIMPLE);
        rule.setPriority(priority);
        rule.setLogicOperator(logic);
        rule.setEnabled(true);
        return rule;
    }

    private DataPermissionRule complex(String name, DataPermissionRuleItem... items) {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName(name);
        rule.setAppPoint("sim");
        rule.setTableName("sim_project");
        rule.setRuleMode(RuleMode.COMPLEX);
        rule.setLogicOperator(LogicOperator.AND);
        rule.setItems(new ArrayList<DataPermissionRuleItem>(Arrays.asList(items)));
        rule.setEnabled(true);
        return rule;
    }

    private DataPermissionRuleItem item(int group, int subGroup, int sortOrder,
                                        LogicOperator logic, String left, String operator,
                                        RightValueType rightType, String right) {
        DataPermissionRuleItem item = new DataPermissionRuleItem();
        item.setGroupNo(group);
        item.setSubGroupNo(subGroup);
        item.setSortOrder(sortOrder);
        item.setLogicOperator(logic);
        item.setLeftType("FIELD");
        item.setLeftExpr(left);
        item.setOperator(operator);
        item.setRightType(rightType);
        item.setRightExpr(right);
        return item;
    }

    private void setSubject(Long deptId, String userNo) {
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("sim-complex-tester")
                .roleId(ROLE)
                .field("deptId", deptId)
                .field("userNo", userNo)
                .build());
    }

    private static List<Long> ids(List<SimProject> rows) {
        return rows.stream().map(SimProject::getId).collect(Collectors.toList());
    }
}
