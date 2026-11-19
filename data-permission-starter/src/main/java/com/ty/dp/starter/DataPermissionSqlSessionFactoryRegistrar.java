package com.ty.dp.starter;

import com.ty.dp.mybatis.DataPermissionInterceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Adds the interceptor to every SqlSessionFactory in the application context.
 *
 * <p>RuoYi builds its own SqlSessionFactory, so MyBatis Spring Boot's
 * automatic Interceptor bean collection is bypassed. Iterating the factories
 * (the same approach PageHelper uses) works for plain MyBatis, MyBatis-Plus
 * and multiple data sources.</p>
 */
public class DataPermissionSqlSessionFactoryRegistrar implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionSqlSessionFactoryRegistrar.class);

    private final List<SqlSessionFactory> sqlSessionFactories;
    private final DataPermissionInterceptor interceptor;
    private final Set<Configuration> registered =
            Collections.newSetFromMap(new IdentityHashMap<Configuration, Boolean>());

    public DataPermissionSqlSessionFactoryRegistrar(List<SqlSessionFactory> sqlSessionFactories,
                                                    DataPermissionInterceptor interceptor) {
        this.sqlSessionFactories = sqlSessionFactories;
        this.interceptor = interceptor;
    }

    @Override
    public void afterPropertiesSet() {
        if (sqlSessionFactories == null) {
            return;
        }
        for (SqlSessionFactory factory : sqlSessionFactories) {
            if (factory == null) {
                continue;
            }
            Configuration configuration = factory.getConfiguration();
            if (configuration == null || !registered.add(configuration)) {
                continue;
            }
            configuration.addInterceptor(interceptor);
            log.info("[data-permission] interceptor registered on SqlSessionFactory {}", factory);
        }
    }
}
