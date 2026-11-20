package com.ty.dp.sample;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.core.subject.SimpleDataPermissionSubject;
import com.ty.dp.starter.CachedDataPermissionRuleResolver;
import com.ty.dp.starter.DataPermissionProperties;
import com.ty.dp.starter.admin.DataPermissionAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheInvalidationSimulationTest {

    @Test
    void loaderStartedBeforeInvalidationCannotPublishStaleRules() throws Exception {
        final AtomicReference<List<DataPermissionRule>> repositoryRules =
                new AtomicReference<List<DataPermissionRule>>(
                        Collections.singletonList(rule("100")));
        final CountDownLatch loaded = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean firstLoad = new AtomicBoolean(true);

        DataPermissionRuleRepository repository = new DataPermissionRuleRepository() {
            @Override
            public List<DataPermissionRule> findByRoles(Collection<String> roleIds, String appPoint) {
                List<DataPermissionRule> snapshot =
                        new ArrayList<DataPermissionRule>(repositoryRules.get());
                if (firstLoad.compareAndSet(true, false)) {
                    loaded.countDown();
                    await(release);
                }
                return snapshot;
            }
        };
        final CachedDataPermissionRuleResolver resolver =
                new CachedDataPermissionRuleResolver(repository, 300);
        final SimpleDataPermissionSubject subject = subject();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<DataPermissionRule>> stale = executor.submit(
                    new Callable<List<DataPermissionRule>>() {
                        @Override
                        public List<DataPermissionRule> call() {
                            return resolver.resolve(subject, "sim");
                        }
                    });
            assertTrue(loaded.await(5, TimeUnit.SECONDS));

            repositoryRules.set(Collections.singletonList(rule("200")));
            resolver.invalidateAll();
            release.countDown();

            assertEquals("100", stale.get(5, TimeUnit.SECONDS).get(0).getConditionValue());
            assertEquals("200", resolver.resolve(subject, "sim").get(0).getConditionValue());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void adminChangeInvalidatesAgainAfterCommit() {
        CountingResolver resolver = new CountingResolver();
        DataPermissionAdminService adminService = adminService(resolver);

        transactionTemplate().execute(status -> {
            adminService.saveRule(rule("100"));
            return null;
        });

        assertEquals(2, resolver.invalidations);
    }

    @Test
    void adminChangeInvalidatesAgainOnRollback() {
        CountingResolver resolver = new CountingResolver();
        DataPermissionAdminService adminService = adminService(resolver);

        transactionTemplate().execute(status -> {
            adminService.saveRule(rule("100"));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(2, resolver.invalidations);
    }

    @Test
    void rollbackEvictsUncommittedRulesFromTheCache() {
        MutableRepository repository = new MutableRepository(rule("100"));
        CachedDataPermissionRuleResolver resolver =
                new CachedDataPermissionRuleResolver(repository, 300);
        DataPermissionAdminService adminService = adminService(resolver);
        SimpleDataPermissionSubject subject = subject();

        assertEquals("100", resolver.resolve(subject, "sim").get(0).getConditionValue());

        transactionTemplate().execute(status -> {
            repository.value = rule("200");
            adminService.saveRule(rule("200"));
            assertEquals("200", resolver.resolve(subject, "sim").get(0).getConditionValue());
            status.setRollbackOnly();
            return null;
        });

        repository.value = rule("100");
        assertEquals("100", resolver.resolve(subject, "sim").get(0).getConditionValue());
    }

    private static DataPermissionAdminService adminService(DataPermissionRuleResolver resolver) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        return new DataPermissionAdminService(jdbc, new DataPermissionProperties(), resolver);
    }

    private static TransactionTemplate transactionTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dp-tx;DB_CLOSE_DELAY=-1", "sa", "");
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static SimpleDataPermissionSubject subject() {
        return SimpleDataPermissionSubject.builder()
                .subjectId("cache-tester")
                .roleId("1")
                .build();
    }

    private static DataPermissionRule rule(String value) {
        DataPermissionRule rule = new DataPermissionRule();
        rule.setRuleName("cache-rule-" + value);
        rule.setAppPoint("sim");
        rule.setTableName("sim_project");
        rule.setFieldName("dept_id");
        rule.setConditionType("=");
        rule.setConditionValue(value);
        rule.setEnabled(true);
        return rule;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for latch", ex);
        }
    }

    private static final class MutableRepository implements DataPermissionRuleRepository {
        private DataPermissionRule value;

        private MutableRepository(DataPermissionRule value) {
            this.value = value;
        }

        @Override
        public List<DataPermissionRule> findByRoles(Collection<String> roleIds, String appPoint) {
            return Collections.singletonList(value);
        }
    }

    private static final class CountingResolver extends CachedDataPermissionRuleResolver {
        private int invalidations;

        private CountingResolver() {
            super(new DataPermissionRuleRepository() {
                @Override
                public List<DataPermissionRule> findByRoles(Collection<String> roleIds, String appPoint) {
                    return Collections.emptyList();
                }
            }, 300);
        }

        @Override
        public void invalidateAll() {
            invalidations++;
            super.invalidateAll();
        }
    }
}
