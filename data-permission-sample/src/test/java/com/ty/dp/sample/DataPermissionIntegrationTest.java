package com.ty.dp.sample;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.SimpleDataPermissionSubject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataPermissionIntegrationTest {

    @Autowired
    private DemoProjectMapper mapper;

    @AfterEach
    void tearDown() {
        DataPermissionContext.clear();
        PageHelper.clearPage();
    }

    @Test
    void simpleRuleFiltersRows() {
        DataPermissionContext.setSubject(subject("10", 100L, "u1"));

        List<DemoProject> rows = mapper.selectAll();

        assertEquals(2, rows.size());
        assertEquals(Long.valueOf(1L), rows.get(0).getId());
        assertEquals(Long.valueOf(2L), rows.get(1).getId());
    }

    @Test
    void complexRuleCombinesItems() {
        DataPermissionContext.setSubject(subject("20", 100L, "u1"));

        List<DemoProject> rows = mapper.selectAll();

        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(1L), rows.get(0).getId());
    }

    @Test
    void superAdminBypassesRules() {
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("admin")
                .superAdmin(true)
                .build());

        assertEquals(3, mapper.selectAll().size());
    }

    @Test
    void noRulesDeniesByDefault() {
        DataPermissionContext.setSubject(subject("99", 100L, "u1"));

        assertTrue(mapper.selectAll().isEmpty());
    }

    @Test
    void ignoreAnnotationSkipsRules() {
        DataPermissionContext.setSubject(subject("99", 100L, "u1"));

        assertEquals(3L, mapper.countAll());
    }

    @Test
    void pageHelperCountUsesRewrittenSql() {
        DataPermissionContext.setSubject(subject("10", 100L, "u1"));

        PageHelper.startPage(1, 1);
        List<DemoProject> rows = mapper.selectAll();
        PageInfo<DemoProject> page = new PageInfo<DemoProject>(rows);

        assertEquals(2L, page.getTotal());
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(1L), rows.get(0).getId());
    }

    private static SimpleDataPermissionSubject subject(String roleId, Long deptId, String userNo) {
        return SimpleDataPermissionSubject.builder()
                .subjectId("tester")
                .roleId(roleId)
                .field("userId", 1L)
                .field("deptId", deptId)
                .field("userNo", userNo)
                .build();
    }
}
