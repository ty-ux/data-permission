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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AdminCrudSimulationTest {

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
    void simpleRuleCrudTakesEffectInTheEngine() {
        DataPermissionRule rule = simple("dept_id", "=", "#{deptId}", 10, LogicOperator.AND);
        String id = adminService.saveRule(rule);
        assertNotNull(id);

        DataPermissionRule loaded = adminService.getRule(id);
        assertEquals("sim_project", loaded.getTableName());
        assertEquals("dept_id", loaded.getFieldName());
        assertEquals("#{deptId}", loaded.getConditionValue());
        assertEquals(Integer.valueOf(10), loaded.getPriority());
        assertTrue(loaded.isEnabled());
        assertEquals(1, adminService.listRules("sim", "sim-1").size());
        assertTrue(adminService.listRules("other", null).isEmpty());

        adminService.assignRoleRules("sim-role-crud", Collections.singletonList(id));
        DataPermissionContext.setSubject(subject("sim-role-crud", 100L, "u1"));
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAll()));

        loaded.setFieldName("owner_no");
        loaded.setConditionValue("#{userNo}");
        loaded.setPriority(30);
        adminService.saveRule(loaded);

        DataPermissionRule updated = adminService.getRule(id);
        assertEquals("owner_no", updated.getFieldName());
        assertEquals(Integer.valueOf(30), updated.getPriority());
        assertEquals(Arrays.asList(101L, 102L), ids(mapper.selectAll()));

        adminService.deleteRules(Collections.singletonList(id));
        assertNull(adminService.getRule(id));
        assertTrue(adminService.listRules("sim", null).isEmpty());
        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void complexRuleCrudReplacesItemsAndFeedsTheEngine() {
        DataPermissionRule rule = complex("sim-complex",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, "deptId"),
                item(1, 1, 1, LogicOperator.AND, "sim_project.owner_no", "=",
                        RightValueType.CURRENT_USER_FIELD, "userNo"),
                item(2, 1, 0, LogicOperator.OR, "sim_project.create_by", "=",
                        RightValueType.CURRENT_USER_FIELD, "userNo"));
        String id = adminService.saveRule(rule);

        DataPermissionRule loaded = adminService.getRule(id);
        assertEquals(RuleMode.COMPLEX, loaded.getRuleMode());
        assertEquals(3, loaded.getItems().size());
        assertEquals(Arrays.asList(
                        "sim_project.dept_id", "sim_project.owner_no", "sim_project.create_by"),
                loaded.getItems().stream()
                        .map(DataPermissionRuleItem::getLeftExpr)
                        .collect(Collectors.toList()));
        assertEquals(3, adminService.listRules("sim", "sim-complex").get(0).getItems().size());

        loaded.setDescription("replaced items");
        loaded.setItems(new ArrayList<>(Arrays.asList(
                item(1, 1, 0, LogicOperator.AND, "sim_project.status", "=",
                        RightValueType.CONST, "A"),
                item(1, 1, 1, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, "deptId"))));
        adminService.saveRule(loaded);

        DataPermissionRule updated = adminService.getRule(id);
        assertEquals(2, updated.getItems().size());
        assertEquals("replaced items", updated.getDescription());
        Set<String> itemIds = updated.getItems().stream()
                .map(DataPermissionRuleItem::getId)
                .collect(Collectors.toSet());
        assertEquals(2, itemIds.size());

        adminService.assignRoleRules("sim-role-complex", Collections.singletonList(id));
        DataPermissionContext.setSubject(subject("sim-role-complex", 100L, "u1"));
        assertEquals(Arrays.asList(101L, 103L), ids(mapper.selectAll()));

        adminService.deleteRules(Collections.singletonList(id));
        assertNull(adminService.getRule(id));
        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void roleAssignmentOverwritesAndDeleteCascades() {
        String first = adminService.saveRule(simple("dept_id", "=", "#{deptId}", 0, LogicOperator.AND));
        String second = adminService.saveRule(simple("owner_no", "=", "#{userNo}", 0, LogicOperator.AND));

        adminService.assignRoleRules("sim-role-bind", Arrays.asList(first, second));
        assertEquals(new HashSet<String>(Arrays.asList(first, second)),
                new HashSet<String>(adminService.listRoleRuleIds("sim-role-bind")));
        adminService.assignRoleRules("another-role", Collections.singletonList(second));
        assertEquals(Arrays.asList("another-role", "sim-role-bind"), adminService.listRuleRoleIds(second));

        adminService.assignRoleRules("sim-role-bind", Collections.singletonList(second));
        assertEquals(Collections.singletonList(second), adminService.listRoleRuleIds("sim-role-bind"));
        assertTrue(adminService.listRuleRoleIds(first).isEmpty());

        DataPermissionRule disabled = adminService.getRule(second);
        disabled.setEnabled(false);
        adminService.saveRule(disabled);
        assertEquals(Arrays.asList("another-role", "sim-role-bind"), adminService.listRuleRoleIds(second));

        adminService.deleteRules(Collections.singletonList(second));
        assertTrue(adminService.listRoleRuleIds("sim-role-bind").isEmpty());
        assertTrue(adminService.listRuleRoleIds(second).isEmpty());

        adminService.assignRoleRules("sim-role-bind", Collections.emptyList());
        assertTrue(adminService.listRoleRuleIds("sim-role-bind").isEmpty());
    }

    @Test
    void savingIncompleteSimpleRuleIsRejected() {
        DataPermissionRule blankValue = simple("dept_id", "=", "   ", 0, LogicOperator.AND);
        assertThrows(IllegalArgumentException.class, () -> adminService.saveRule(blankValue));

        DataPermissionRule isNull = simple("create_by", "IS_NULL", null, 0, LogicOperator.AND);
        String id = adminService.saveRule(isNull);
        assertNotNull(adminService.getRule(id));
    }

    @Test
    void savingIncompleteComplexItemIsRejected() {
        DataPermissionRule blankLeft = complex("sim-bad-left",
                item(1, 1, 0, LogicOperator.AND, "   ", "=", RightValueType.CONST, "A"));
        assertThrows(IllegalArgumentException.class, () -> adminService.saveRule(blankLeft));

        DataPermissionRule blankRight = complex("sim-bad-right",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, null));
        assertThrows(IllegalArgumentException.class, () -> adminService.saveRule(blankRight));

        DataPermissionRule noneWithEquals = complex("sim-bad-none",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.NONE, null));
        assertThrows(IllegalArgumentException.class, () -> adminService.saveRule(noneWithEquals));

        DataPermissionRule noneWithNull = complex("sim-good-none",
                item(1, 1, 0, LogicOperator.AND, "sim_project.create_by", "IS_NULL",
                        RightValueType.NONE, null));
        String id = adminService.saveRule(noneWithNull);
        DataPermissionRule loaded = adminService.getRule(id);
        assertEquals(1, loaded.getItems().size());
        assertEquals(RightValueType.NONE, loaded.getItems().get(0).getRightType());
    }

    @Test
    void updatingDeletedRuleIsRejectedWithoutOrphanItems() {
        DataPermissionRule rule = complex("sim-deleted",
                item(1, 1, 0, LogicOperator.AND, "sim_project.dept_id", "=",
                        RightValueType.CURRENT_USER_FIELD, "deptId"));
        String id = adminService.saveRule(rule);
        adminService.deleteRules(Collections.singletonList(id));
        assertNull(adminService.getRule(id));

        DataPermissionRule stale = rule;
        stale.setId(id);
        stale.setDescription("stale update");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adminService.saveRule(stale));
        assertTrue(error.getMessage().contains("not found"));

        Integer activeItems = jdbc.queryForObject(
                "select count(*) from sys_permission_rule_item where rule_id = :ruleId and is_del = '0'",
                new MapSqlParameterSource("ruleId", id), Integer.class);
        assertEquals(Integer.valueOf(0), activeItems);
    }

    @Test
    void listFiltersByAppPointAndName() {
        adminService.saveRule(simple("dept_id", "=", "#{deptId}", 0, LogicOperator.AND));
        DataPermissionRule otherPoint = simple("dept_id", "=", "#{deptId}", 0, LogicOperator.AND);
        otherPoint.setAppPoint("other");
        adminService.saveRule(otherPoint);

        assertEquals(1, adminService.listRules("sim", "sim-1").size());
        assertEquals(2, adminService.listRules(null, "sim-").size());
        assertTrue(adminService.listRules("sim", "sim-1").get(0).isEnabled());
        assertFalse(adminService.listRules("sim", "sim-1").isEmpty());
    }

    private DataPermissionRule simple(String field, String operator, String value,
                                      int priority, LogicOperator logic) {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName("sim-" + (counter++));
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

    private static SimpleDataPermissionSubject subject(String roleId, Long deptId, String userNo) {
        return SimpleDataPermissionSubject.builder()
                .subjectId("sim-admin-tester")
                .roleId(roleId)
                .field("deptId", deptId)
                .field("userNo", userNo)
                .build();
    }

    private static List<Long> ids(List<SimProject> rows) {
        return rows.stream().map(SimProject::getId).collect(Collectors.toList());
    }

    private int counter = 1;
}
