package com.ty.dp.starter;

import com.ty.dp.core.DataPermissionOptions;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.DataPermissionSubjectProvider;
import com.ty.dp.mybatis.DataPermissionInterceptor;
import com.ty.dp.starter.admin.DataPermissionAdminController;
import com.ty.dp.starter.admin.DataPermissionAdminService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * Auto-configuration is registered after PageHelper so this interceptor sits
 * outside the pagination plugin: it rewrites the original SQL and PageHelper
 * derives count/page SQL from the rewritten statement.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataPermissionProperties.class)
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration"
})
public class DataPermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataPermissionOptions dataPermissionOptions(DataPermissionProperties properties) {
        return new DataPermissionOptions(properties.getMode(),
                properties.getDefaultPolicy(), properties.getNoSubjectPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(DataPermissionRuleRepository.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public DataPermissionRuleRepository dataPermissionRuleRepository(DataSource dataSource,
                                                                     DataPermissionProperties properties) {
        return new JdbcDataPermissionRuleRepository(new NamedParameterJdbcTemplate(dataSource), properties);
    }

    @Bean
    @ConditionalOnMissingBean(DataPermissionRuleResolver.class)
    @ConditionalOnBean(DataPermissionRuleRepository.class)
    public CachedDataPermissionRuleResolver dataPermissionRuleResolver(
            DataPermissionRuleRepository repository, DataPermissionProperties properties) {
        return new CachedDataPermissionRuleResolver(repository, properties.getCacheTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataPermissionRuleResolver.class)
    public DataPermissionInterceptor dataPermissionInterceptor(
            DataPermissionRuleResolver ruleResolver,
            DataPermissionSubjectProvider subjectProvider,
            DataPermissionOptions options) {
        return new DataPermissionInterceptor(ruleResolver, subjectProvider, options);
    }

    @Bean
    @ConditionalOnBean(DataPermissionInterceptor.class)
    public DataPermissionSqlSessionFactoryRegistrar dataPermissionSqlSessionFactoryRegistrar(
            List<SqlSessionFactory> sqlSessionFactories, DataPermissionInterceptor interceptor) {
        return new DataPermissionSqlSessionFactoryRegistrar(sqlSessionFactories, interceptor);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"org.springframework.web.filter.OncePerRequestFilter", "javax.servlet.Filter"})
    static class ServletFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public DataPermissionContextFilter dataPermissionContextFilter(DataPermissionSubjectProvider subjectProvider) {
            return new DataPermissionContextFilter(subjectProvider);
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SecuritySubjectConfiguration {
        @Bean
        @ConditionalOnMissingBean(DataPermissionSubjectProvider.class)
        public DataPermissionSubjectProvider dataPermissionSubjectProvider(DataPermissionProperties properties) {
            return new SpringSecurityDataPermissionSubjectProvider(properties.getAdminUserIds());
        }
    }

    @Configuration
    @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
    static class FallbackSubjectConfiguration {
        @Bean
        @ConditionalOnMissingBean(DataPermissionSubjectProvider.class)
        public DataPermissionSubjectProvider dataPermissionSubjectProvider() {
            return new DataPermissionSubjectProvider() {
                @Override
                public com.ty.dp.core.subject.DataPermissionSubject getCurrent() {
                    return DataPermissionContext.getSubject();
                }
            };
        }
    }

    @Configuration
    @ConditionalOnClass(name = {"org.springframework.web.bind.annotation.RestController", "javax.servlet.Servlet"})
    @ConditionalOnProperty(prefix = "data-permission.admin", name = "enabled", havingValue = "true")
    @ConditionalOnSingleCandidate(DataSource.class)
    @Import(DataPermissionAdminController.class)
    static class AdminConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public DataPermissionAdminService dataPermissionAdminService(
                DataSource dataSource,
                DataPermissionProperties properties,
                DataPermissionRuleResolver ruleResolver) {
            return new DataPermissionAdminService(new NamedParameterJdbcTemplate(dataSource),
                    properties, ruleResolver);
        }
    }
}
