package com.ty.dp.sample;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
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
class SqlConcatenationSimulationTest {

    private static final String ROLE = "sim-field-role";

    @Autowired
    private DataPermissionAdminService adminService;

    @Autowired
    private SimProjectMapper mapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        DataPermissionContext.clear();
        PageHelper.clearPage();
    }

    @Test
    void bareQualifiedAliasedAndQuotedColumnsFilterTheSameRows() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAll()));
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAliased()));

        useSimple("sim_project.dept_id", "=", "#{deptId}");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAliased()));

        useSimple("p.dept_id", "=", "#{deptId}");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAliased()));

        useSimple("`sim_project`.`dept_id`", "=", "#{deptId}");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAliased()));

        useSimple("dept_id", "=", "#{deptId}");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectWithAnnotationAlias()));
    }

    @Test
    void joinConditionUsesTheMainTableAlias() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectWithJoin()));
    }

    @Test
    void ruleOnJoinedTableUsesTheJoinAlias() {
        assign(ROLE, simpleOnTable("sim_dept", "manager_no", "=", "u3"));
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(104L, 105L), ids(mapper.selectWithJoin()));
    }

    @Test
    void fieldToFieldAcrossTablesMapsTheOtherTableAlias() {
        DataPermissionRule rule = complex("field-to-field",
                item(1, 1, 0, LogicOperator.AND, "sim_project.owner_no", "=",
                        RightValueType.FIELD, "sim_dept.manager_no"));
        assign(ROLE, rule);
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 104L, 105L), ids(mapper.selectWithJoin()));
    }

    @Test
    void originalOrConditionCannotBypassThePermissionCondition() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectWithOrCondition()));
    }

    @Test
    void everyUnionBranchIsRewritten() {
        useSimple("dept_id", "IN", "100,200");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L, 104L, 105L), mapper.selectUnionIds());
    }

    @Test
    void subqueryInFromIsRewritten() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectFromSubquery()));
    }

    @Test
    void pageHelperCountUsesTheRewrittenCondition() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        PageHelper.startPage(1, 2);
        List<SimProject> rows = mapper.selectAll();
        PageInfo<SimProject> page = new PageInfo<SimProject>(rows);

        assertEquals(3L, page.getTotal());
        assertEquals(Arrays.asList(101L, 102L), ids(rows));
    }

    @Test
    void mapperParameterResolvesWhenSubjectHasNoSuchField() {
        useSimple("dept_id", "=", "#{deptId}");
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("param-tester")
                .roleId(ROLE)
                .field("userNo", "u1")
                .build());

        assertEquals(Arrays.asList(104L, 105L), ids(mapper.selectByDeptParam(200L)));
    }

    @Test
    void schemaQualifiedTableStillMatchesTheRule() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectSchemaQualified()));
    }

    @Test
    void isNullOperatorIgnoresTheRightValue() {
        jdbc.update("update sim_project set create_by = null where id = 106",
                new MapSqlParameterSource());
        useSimple("create_by", "IS_NULL", null);
        setSubject(100L, "u1");

        assertEquals(Collections.singletonList(106L), ids(mapper.selectAll()));
    }

    @Test
    void likeOperatorWrapsTheValueWithPercent() {
        useSimple("name", "LIKE", "P104");
        setSubject(100L, "u1");

        assertEquals(Collections.singletonList(104L), ids(mapper.selectAll()));
    }

    @Test
    void inAcceptsCommaSeparatedText() {
        useSimple("dept_id", "IN", "100,300");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L, 106L), ids(mapper.selectAll()));
    }

    @Test
    void inAcceptsACollectionFromTheSubject() {
        useSimple("dept_id", "IN", "#{deptIds}");
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("collection-tester")
                .roleId(ROLE)
                .field("deptIds", Arrays.asList(100L, 300L))
                .build());

        assertEquals(Arrays.asList(101L, 102L, 103L, 106L), ids(mapper.selectAll()));
    }

    @Test
    void conditionsOnDifferentTablesAreAnded() {
        assign(ROLE,
                simpleOnTable("sim_project", "dept_id", "IN", "100,200"),
                simpleOnTable("sim_dept", "manager_no", "=", "u3"));
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(104L, 105L), ids(mapper.selectWithJoin()));
    }

    @Test
    void ruleForATableNotInTheSqlDenies() {
        assign(ROLE, simpleOnTable("missing_table", "id", "=", "1"));
        setSubject(100L, "u1");

        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void appPointMismatchDenies() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "#{deptId}");
        rule.setAppPoint("other");
        assign(ROLE, rule);
        setSubject(100L, "u1");

        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void emptyAppPointMatchesTheMapperAppPoint() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "#{deptId}");
        rule.setAppPoint("");
        assign(ROLE, rule);
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectAll()));
    }

    private void useSimple(String field, String operator, String value) {
        assign(ROLE, simpleOnTable("sim_project", field, operator, value));
    }

    private void assign(String roleId, DataPermissionRule... rules) {
        List<String> ids = new ArrayList<String>();
        for (DataPermissionRule rule : rules) {
            ids.add(adminService.saveRule(rule));
        }
        adminService.assignRoleRules(roleId, ids);
    }

    private DataPermissionRule simpleOnTable(String table, String field, String operator, String value) {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName("field-" + (counter++));
        rule.setAppPoint("sim");
        rule.setTableName(table);
        rule.setFieldName(field);
        rule.setConditionType(operator);
        rule.setConditionValue(value);
        rule.setRuleMode(RuleMode.SIMPLE);
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
                .subjectId("sim-field-tester")
                .roleId(ROLE)
                .field("deptId", deptId)
                .field("userNo", userNo)
                .build());
    }

    private static List<Long> ids(List<SimProject> rows) {
        return rows.stream().map(SimProject::getId).collect(Collectors.toList());
    }

    private int counter = 1;
}
