package com.ty.dp.sample;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.DataPermissionRuleItem;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RightValueType;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.sql.SqlConditionBuilder;
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
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void selfJoinFiltersEveryTableInstance() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectSelfJoin()));
        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectSelfJoinThree()));
    }

    @Test
    void selfJoinExpandsAnAliasQualifiedFieldPerInstance() {
        useSimple("a.dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectSelfJoin()));
    }

    @Test
    void outerTableIsFilteredWhenFromIsASubquery() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L),
                ids(mapper.selectFromSubqueryWithOuterTable()));
    }

    @Test
    void scalarSubqueryInSelectIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Long.valueOf(3L), mapper.selectScalarSubqueryCount());
    }

    @Test
    void subqueryInWhereIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertTrue(mapper.selectExistsSubquery().isEmpty());
    }

    @Test
    void cteBodyIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectCte()));
    }

    @Test
    void cteWithTheSameNameAsAPhysicalTableStillGetsThePredicate() {
        // H2 2.1.214 ignores predicates inside a CTE that shadows a physical
        // table, even 1 = 0, so this case is checked at the rewrite level.
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with sim_project as (select * from PUBLIC.sim_project) select id from sim_project",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM PUBLIC.sim_project WHERE"));
        assertTrue(sql.contains("PUBLIC.sim_project.dept_id = '100'"));
        assertFalse(sql.contains("FROM sim_project WHERE 1 = 0"));
    }

    @Test
    void laterCteSeesTheFilteredEarlierCte() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectEarlierCteFromLaterCte()));
    }

    @Test
    void cteNestedInsideCteBodyIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectCteNestedInsideCte()));
    }

    @Test
    void cteInsideScalarSubqueryIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Long.valueOf(3L), mapper.selectCteInsideScalarSubquery());
    }

    @Test
    void recursiveCteFiltersEveryPhysicalTableReference() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), mapper.selectRecursiveCte());
    }

    @Test
    void nonFirstRecursiveCteSeesItself() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), mapper.selectRecursiveCteNotFirst());
    }

    @Test
    void nonRecursiveSelfReferenceStaysPhysical() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with sim_project as (select * from sim_project) select id from sim_project",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("sim_project.dept_id = '100'"));
    }

    @Test
    void subJoinIsDeniedWithoutRules() {
        setSubject(100L, "u1");

        assertNull(mapper.selectSubJoinScalarTotal());
    }

    @Test
    void subJoinFiltersEveryNestedTableInstance() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Collections.singletonList(101L), mapper.selectSubJoinIds());
    }

    @Test
    void constantCteBodyIsNotDenied() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Long.valueOf(101L), mapper.selectConstantCte());
    }

    @Test
    void recursiveConstantAnchorIsNotDenied() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(1L, 2L, 3L), mapper.selectRecursiveConstantAnchor());
    }

    @Test
    void tableFreeSelectIsNotDenied() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Integer.valueOf(1), mapper.selectConstant());
    }

    @Test
    void valuesFromItemIsNotDenied() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(1, 2), mapper.selectValuesFromItem());
    }

    @Test
    void valuesFromItemSubqueryIsFiltered() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select * from (values ((select count(*) from sim_project))) v",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void tableFunctionIsDeniedUnderTheDefaultPolicy() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select * from my_func(1)",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("1 = 0"));
    }

    @Test
    void tableFunctionIsDeniedEvenWhenAnotherTableMatched() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select p.id from sim_project p, my_func(1)",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("p.dept_id = '100'"));
        assertTrue(sql.contains("1 = 0"));
    }

    @Test
    void withInsideSubJoinIsFiltered() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Collections.singletonList(101L), mapper.selectWithInsideSubJoin());
    }

    @Test
    void databaseQualifiedTableStaysPhysicalBehindASameNameCte() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with sim_project as (select 1 as id) "
                        + "select * from sim_project c, db..sim_project",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("db..sim_project.dept_id = '100'"));
        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void dbLinkQualifiedTableStaysPhysicalBehindASameNameCte() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with sim_project as (select 1 as id) "
                        + "select * from sim_project c, sim_project@link",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        // Oracle rejects @link in a column qualifier; the implicit correlation
        // name of the FROM item is the bare table name.
        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertFalse(sql.contains("sim_project@link.dept_id"));
        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void annotationAliasIsIgnoredWithoutAnExplicitAlias() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        Map<String, String> aliases = Collections.singletonMap("sim_project", "p");
        String sql = SqlConditionBuilder.apply(
                "select * from sim_project",
                Collections.singletonList(rule), aliases, null, null, true);

        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertFalse(sql.contains("p.dept_id = '100'"));
    }

    @Test
    void ignoredAliasDoesNotRedirectAQualifiedRuleField() {
        DataPermissionRule rule = simpleOnTable("sim_project", "p.dept_id", "=", "100");
        Map<String, String> aliases = Collections.singletonMap("sim_project", "p");
        String sql = SqlConditionBuilder.apply(
                "select s.id from sim_project s join sim_other p on p.id = s.dept_id",
                Collections.singletonList(rule), aliases, null, null, true);

        assertTrue(sql.contains("1 = 0"));
        assertFalse(sql.contains("p.dept_id = '100'"));
    }

    @Test
    void quantifiedSubqueryIsFiltered() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select 1 where 1 = any (select dept_id from sim_project)",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("dept_id = '100'"));
    }

    @Test
    void quantifiedSubqueryIsDeniedWithoutRules() {
        String sql = SqlConditionBuilder.apply(
                "select 1 where 1 = any (select dept_id from sim_project)",
                Collections.emptyList(), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("1 = 0"));
    }

    @Test
    void distinctOnSubqueryIsFiltered() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select distinct on ((select max(dept_id) from sim_project)) id "
                        + "from (select 1 as id) t",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("dept_id = '100'"));
    }

    @Test
    void hierarchicalStartWithSubqueryIsFiltered() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select id from (select 1 as id) t "
                        + "start with (select count(*) from sim_project) >= 0 "
                        + "connect by prior id = id",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("dept_id = '100'"));
    }

    @Test
    void dualIsNotTreatedAsAProtectedTable() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select 1 from dual",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void forwardReferenceInsideACteStaysPhysical() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with a as (select id from b), b as (select * from sim_project) select id from a",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM b WHERE 1 = 0"));
        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertFalse(sql.contains("FROM a WHERE 1 = 0"));
    }

    @Test
    void innerCteNameShadowsTheOuterOneOnlyInTheMainBody() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with c as (select id from sim_project) "
                        + "select * from (with c as (select id from c) select id from c) q",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertEquals(1, countOf(sql, "dept_id = '100'"));
        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void annotationAliasDoesNotLeakIntoACteBody() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        Map<String, String> aliases = Collections.singletonMap("sim_project", "p");
        String sql = SqlConditionBuilder.apply(
                "with c as (select * from sim_project) "
                        + "select c.id from c join sim_project p on p.id = c.id",
                Collections.singletonList(rule), aliases, null, null, true);

        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertTrue(sql.contains("p.dept_id = '100'"));
    }

    @Test
    void annotationAliasDoesNotFollowAnotherTable() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        Map<String, String> aliases = Collections.singletonMap("sim_project", "p");
        String sql = SqlConditionBuilder.apply(
                "select p.id from sim_project s join sim_other p on p.id = s.dept_id",
                Collections.singletonList(rule), aliases, null, null, true);

        assertTrue(sql.contains("s.dept_id = '100'"));
        assertFalse(sql.contains("p.dept_id = '100'"));
    }

    @Test
    void unruledJoinTableIsLeftOpenWithinAMatchedScope() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select p.id from sim_project p join sim_dept d on d.id = p.dept_id",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("p.dept_id = '100'"));
        assertFalse(sql.contains("d.dept_id"));
        assertFalse(sql.contains("1 = 0"));
    }

    @Test
    void unruledSubqueryScopeIsDenied() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "select p.id from sim_project p where exists "
                        + "(select 1 from sim_dept d where d.id = p.dept_id)",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("p.dept_id = '100'"));
        assertTrue(sql.contains("1 = 0"));
    }

    @Test
    void nestedCteIsFilteredWithoutDenyingItsMainQuery() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectNestedCte()));
    }

    @Test
    void valuesCteSubqueryIsFiltered() {
        DataPermissionRule rule = simpleOnTable("sim_project", "dept_id", "=", "100");
        String sql = SqlConditionBuilder.apply(
                "with c as (values ((select count(*) from sim_project))) select * from c",
                Collections.singletonList(rule), Collections.emptyMap(), null, null, true);

        assertTrue(sql.contains("FROM sim_project WHERE"));
        assertTrue(sql.contains("sim_project.dept_id = '100'"));
        assertFalse(sql.contains("FROM c WHERE 1 = 0"));
    }

    @Test
    void noRulesDeniesTheOuterTableEvenWhenASubqueryWasRewritten() {
        setSubject(100L, "u1");

        assertNull(mapper.selectScalarSubqueryCount());
    }

    @Test
    void foreachParametersSurviveRewriting() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L),
                ids(mapper.selectByIds(Arrays.asList(101L, 102L, 104L))));
    }

    @Test
    void bindParametersSurviveRewriting() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(mapper.selectWithBind(100L)));
    }

    @Test
    void foreachParametersSurvivePageHelperCount() {
        useSimple("dept_id", "=", "#{deptId}");
        setSubject(100L, "u1");

        PageHelper.startPage(1, 1);
        List<SimProject> rows = mapper.selectByIds(Arrays.asList(101L, 102L, 104L));
        PageInfo<SimProject> page = new PageInfo<SimProject>(rows);

        assertEquals(2L, page.getTotal());
        assertEquals(Collections.singletonList(101L), ids(rows));
    }

    @Test
    void quotedStringConstantIsEmittedOnce() {
        useSimple("status", "=", "'A'");
        setSubject(100L, "u1");

        assertEquals(Arrays.asList(101L, 103L, 105L, 106L), ids(mapper.selectAll()));
    }

    @Test
    void quotedConstantWithEmbeddedQuoteIsEscaped() {
        jdbc.update("update sim_project set name = 'O''Brien' where id = 101",
                new MapSqlParameterSource());
        useSimple("name", "=", "'O''Brien'");
        setSubject(100L, "u1");

        assertEquals(Collections.singletonList(101L), ids(mapper.selectAll()));
    }

    @Test
    void quotedConstantWinsOverAUserFieldWithTheSameName() {
        useSimple("create_by", "=", "'u1'");
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("quoted-constant-tester")
                .roleId(ROLE)
                .field("u1", "u9")
                .build());

        assertEquals(Collections.singletonList(104L), ids(mapper.selectAll()));
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

    private static int countOf(String sql, String needle) {
        return sql.split(Pattern.quote(needle), -1).length - 1;
    }

    private int counter = 1;
}
