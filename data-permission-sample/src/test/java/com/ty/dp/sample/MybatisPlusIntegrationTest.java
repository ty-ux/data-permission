package com.ty.dp.sample;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.SimpleDataPermissionSubject;
import com.ty.dp.starter.admin.DataPermissionAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MybatisPlusIntegrationTest {

    private static final String ROLE = "mp-role";

    @Autowired
    private DataPermissionAdminService adminService;

    @Autowired
    private SimProjectPlusMapper plusMapper;

    @AfterEach
    void tearDown() {
        DataPermissionContext.clear();
        PageHelper.clearPage();
    }

    @Test
    void baseMapperSelectListUsesThePermissionRule() {
        assignDepartmentRule();
        setSubject(false);

        List<SimProject> rows = plusMapper.selectList(
                new QueryWrapper<SimProject>().orderByAsc("id"));

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(rows));
    }

    @Test
    void wrapperOrConditionCannotBypassThePermissionRule() {
        assignDepartmentRule();
        setSubject(false);

        QueryWrapper<SimProject> wrapper = new QueryWrapper<SimProject>()
                .and(condition -> condition.eq("status", "A").or().eq("status", "B"))
                .orderByAsc("id");

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(plusMapper.selectList(wrapper)));
    }

    @Test
    void baseMapperSelectCountUsesThePermissionRule() {
        assignDepartmentRule();
        setSubject(false);

        assertEquals(Long.valueOf(3L),
                plusMapper.selectCount(new QueryWrapper<SimProject>()));
    }

    @Test
    void pageHelperStillWorksWithMybatisPlus() {
        assignDepartmentRule();
        setSubject(false);

        PageHelper.startPage(1, 2);
        List<SimProject> rows = plusMapper.selectList(
                new QueryWrapper<SimProject>().orderByAsc("id"));
        PageInfo<SimProject> page = new PageInfo<SimProject>(rows);

        assertEquals(3L, page.getTotal());
        assertEquals(Arrays.asList(101L, 102L), ids(rows));
    }

    @Test
    void changingTheRuleIsVisibleToTheNextBaseMapperQuery() {
        String ruleId = assignDepartmentRule();
        setSubject(false);

        assertEquals(Arrays.asList(101L, 102L, 103L), ids(plusMapper.selectList(
                new QueryWrapper<SimProject>().orderByAsc("id"))));

        DataPermissionRule rule = adminService.getRule(ruleId);
        rule.setFieldName("owner_no");
        rule.setConditionValue("#{userNo}");
        adminService.saveRule(rule);

        assertEquals(Arrays.asList(101L, 102L), ids(plusMapper.selectList(
                new QueryWrapper<SimProject>().orderByAsc("id"))));
    }

    @Test
    void superAdminBypassesTheRule() {
        assignDepartmentRule();
        setSubject(true);

        assertEquals(6, plusMapper.selectList(null).size());
    }

    private String assignDepartmentRule() {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName("mp-department");
        rule.setAppPoint("sim");
        rule.setTableName("sim_project");
        rule.setFieldName("dept_id");
        rule.setConditionType("=");
        rule.setConditionValue("#{deptId}");
        rule.setRuleMode(RuleMode.SIMPLE);
        rule.setLogicOperator(LogicOperator.AND);
        rule.setEnabled(true);
        String ruleId = adminService.saveRule(rule);
        adminService.assignRoleRules(ROLE, Collections.singletonList(ruleId));
        return ruleId;
    }

    private void setSubject(boolean superAdmin) {
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("mp-tester")
                .roleId(ROLE)
                .superAdmin(superAdmin)
                .field("deptId", 100L)
                .field("userNo", "u1")
                .build());
    }

    private static List<Long> ids(List<SimProject> rows) {
        return rows.stream().map(SimProject::getId).collect(Collectors.toList());
    }
}
