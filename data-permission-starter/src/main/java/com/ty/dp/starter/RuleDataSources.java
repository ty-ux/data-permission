package com.ty.dp.starter;

import javax.sql.DataSource;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class RuleDataSources {
    private RuleDataSources() { }
    static Supplier<NamedParameterJdbcTemplate> lazy(ApplicationContext context, ObjectProvider<DataSource> sources,
            DataPermissionProperties properties) {
        return () -> {
            String name = properties.getDatasourceBean();
            DataSource target = name != null && !name.trim().isEmpty()
                    ? context.getBean(name.trim(), DataSource.class) : sources.getObject();
            return new NamedParameterJdbcTemplate(target);
        };
    }
}
