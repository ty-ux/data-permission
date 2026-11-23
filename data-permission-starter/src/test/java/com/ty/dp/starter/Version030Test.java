package com.ty.dp.starter;

import com.ty.dp.core.sql.SubqueryTemplate;
import com.ty.dp.core.exception.DataPermissionException;
import com.ty.dp.starter.admin.*;
import com.ty.dp.core.subject.DataPermissionSubject;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class Version030Test {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataPermissionAutoConfiguration.class));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    public static class User { public Long getUserId() { return 1L; } }
    public static class Login {
        public Long getUserId() { return null; }
        public User getUser() { return new User(); }
        public String getUsername() { return "admin"; }
        public Long getId() { return 999L; }
    }
    @Test void nestedUserIdPrecedesIdAndUsername() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Login(), "", Collections.emptyList()));
        DataPermissionSubject subject = new SpringSecurityDataPermissionSubjectProvider(Collections.singleton("1")).getCurrent();
        assertThat(subject.getSubjectId()).isEqualTo("1");
        assertThat(subject.getField("userId")).isEqualTo(1L);
        assertThat(subject.isSuperAdmin()).isTrue();
    }
    @Test void ruleContentKeyIncludesSubquerySql() {
        com.ty.dp.core.model.DataPermissionRule rule = new com.ty.dp.core.model.DataPermissionRule();
        com.ty.dp.core.model.DataPermissionRuleItem item = new com.ty.dp.core.model.DataPermissionRuleItem();
        item.setRightType(com.ty.dp.core.model.RightValueType.SUBQUERY);
        item.setRightExpr("select id from t where user_id=#{userId}");
        rule.setItems(Collections.singletonList(item));
        String first = CachedDataPermissionRuleResolver.withSubqueryContent("role|point", Collections.singletonList(rule));
        assertThat(first).contains(item.getRightExpr());
        item.setRightExpr("select id from t where dept_id=#{deptId}");
        assertThat(CachedDataPermissionRuleResolver.withSubqueryContent("role|point", Collections.singletonList(rule))).isNotEqualTo(first);
    }
    @Test void placeholderValuesRemainOutOfSql() {
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        DataPermissionSubject subject = com.ty.dp.core.subject.SimpleDataPermissionSubject.builder()
                .subjectId("42").field("userNo", "x' OR 1=1 --")
                .field("visibleTaskIds", Arrays.asList(7L, 8L)).build();
        String sql = SubqueryTemplate.compile("select id from t where owner=#{userNo} or other=#{userNo} and id in (#{visibleTaskIds})", subject, bindings);
        assertThat(sql).doesNotContain("x'", "#{").contains(":__dp_sq_");
        assertThat(new ArrayList<Object>(bindings.values())).containsExactly("x' OR 1=1 --", "x' OR 1=1 --", 7L, 8L);
        assertThatThrownBy(() -> SubqueryTemplate.compile("select id from t where dept=#{deptId}", subject, new LinkedHashMap<>()))
                .hasMessageContaining("unresolved");
    }
    @Test void rejectsUnsafeTemplates() {
        for (String sql : Arrays.asList("delete from t", "select id from t; select id from t", "select id from t -- x",
                "select id from t # x", "select id from t /* x */", "select id into outfile 'x' from t",
                "select id from t where id=#{unknown}", "select id from t where id='#{userId}'",
                "select sleep(1)", "select `evil`(1)", "select public.count(1)", "select évil(1)", "select evil_udf(id) from t", "select id from t for update",
                "select id from t where id=?", "select id from t where id=${userId}", "select #{userId,jdbcType=VARCHAR}")) {
            assertThatThrownBy(() -> SubqueryTemplate.validate(sql)).as(sql).isInstanceOf(DataPermissionException.class);
        }
        assertThatThrownBy(() -> SubqueryTemplate.validate(String.join("", Collections.nCopies(4001, "x"))))
                .hasMessageContaining("4000");
        SubqueryTemplate.validate("SELECT task_id FROM ppm_task_coworker WHERE user_id=#{userId}");
    }
    @Test void expressionsAndHostPrecedence() {
        runner.withPropertyValues("data-permission.admin.enabled=true",
                "data-permission.admin.authorization.list=@permission.allowed()",
                "data-permission.admin.authorization.save=false",
                "data-permission.admin.authorization.edit=@missing.broken()",
                "data-permission.admin.authorization.remove=(")
                .withBean("permission", Permission.class, Permission::new).run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(DataPermissionAdminAuthorizer.class);
                    DataPermissionAdminAuthorizer a = c.getBean(DataPermissionAdminAuthorizer.class);
                    assertThat(a.isAllowed(DataPermissionAdminAuthorizer.AdminAction.LIST)).isTrue();
                    for (DataPermissionAdminAuthorizer.AdminAction action : Arrays.asList(DataPermissionAdminAuthorizer.AdminAction.SAVE,
                            DataPermissionAdminAuthorizer.AdminAction.EDIT, DataPermissionAdminAuthorizer.AdminAction.REMOVE))
                        assertThat(a.isAllowed(action)).isFalse();
                });
        runner.withPropertyValues("data-permission.admin.enabled=true", "data-permission.admin.authorization.list=false")
                .withBean(DataPermissionAdminAuthorizer.class, () -> a -> true)
                .run(c -> assertThat(c.getBean(DataPermissionAdminAuthorizer.class).isAllowed(DataPermissionAdminAuthorizer.AdminAction.LIST)).isTrue());
    }
    public static class Permission { public boolean allowed() { return true; } }
    @Test void namedDataSourceResolvesLazilyAndIsSharedByAdminAndRepository() throws Exception {
        DataSource master = mock(DataSource.class), other = mock(DataSource.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        when(master.getConnection()).thenReturn(connection);
        runner.withPropertyValues("data-permission.datasource-bean=masterDataSource", "data-permission.admin.enabled=true")
                .withBean("masterDataSource", DataSource.class, () -> master)
                .withBean("routingDataSource", DataSource.class, () -> other).run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(DataPermissionRuleRepository.class).hasSingleBean(DataPermissionAdminService.class);
                    verifyNoInteractions(other);
                    verify(master, never()).getConnection();
                    DataPermissionProperties properties = c.getBean(DataPermissionProperties.class);
                    assertThat(RuleDataSources.lazy(c, c.getBeanProvider(DataSource.class), properties).get().getJdbcTemplate().getDataSource()).isSameAs(master);
                });
    }
}
