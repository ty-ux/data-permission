package com.ty.dp.sample;

import com.ty.dp.starter.*;
import com.ty.dp.starter.admin.DataPermissionAdminService;
import com.ty.dp.core.model.*;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class NamedDataSourceIntegrationTest {
    @Test void repositoryAndAdminUseMasterAndRollbackPartialWrites() {
        JdbcDataSource master = new JdbcDataSource();
        master.setURL("jdbc:h2:mem:namedMaster;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-h2.sql")).execute(master);
        JdbcDataSource other = new JdbcDataSource();
        other.setURL("jdbc:h2:mem:namedOther;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataPermissionAutoConfiguration.class))
                .withPropertyValues("data-permission.datasource-bean=masterDataSource", "data-permission.admin.enabled=true")
                .withBean("masterDataSource", DataSource.class, () -> master)
                .withBean("routingDataSource", DataSource.class, () -> other).run(c -> {
                    assertThat(c).hasNotFailed();
                    DataPermissionAdminService service = c.getBean(DataPermissionAdminService.class);
                    DataPermissionRule rule = new DataPermissionRule();
                    rule.setRuleName("named source"); rule.setTableName("demo_project");
                    rule.setFieldName("dept_id"); rule.setConditionType("="); rule.setConditionValue("100");
                    String id = service.saveRule(rule);
                    service.assignRoleRules("namedRole", Collections.singletonList(id));
                    assertThat(c.getBean(DataPermissionRuleRepository.class).findByRoles(Collections.singleton("namedRole"), null))
                            .extracting(DataPermissionRule::getId).containsExactly(id);
                    // Failure after insertRule and the first item must roll back all writes on master.
                    DataPermissionRule broken = new DataPermissionRule();
                    broken.setRuleName("rollback"); broken.setTableName("demo_project"); broken.setRuleMode(RuleMode.COMPLEX);
                    DataPermissionRuleItem first = item("same-id"), second = item("same-id");
                    broken.setItems(Arrays.asList(first, second));
                    assertThatThrownBy(() -> service.saveRule(broken)).isInstanceOf(org.springframework.dao.DataAccessException.class);
                    assertThat(service.getRule(broken.getId())).isNull();
                    assertThat(new JdbcTemplate(master).queryForObject("select count(*) from sys_permission_rule_item where id='same-id'", Integer.class)).isZero();
                });
    }
    private DataPermissionRuleItem item(String id) {
        DataPermissionRuleItem item = new DataPermissionRuleItem(); item.setId(id);
        item.setLeftExpr("dept_id"); item.setOperator("="); item.setRightExpr("100"); return item;
    }
}
