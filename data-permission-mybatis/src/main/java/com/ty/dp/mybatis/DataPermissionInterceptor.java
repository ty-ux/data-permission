package com.ty.dp.mybatis;

import com.ty.dp.core.DataPermissionMode;
import com.ty.dp.core.DataPermissionOptions;
import com.ty.dp.core.DataPermissionPolicy;
import com.ty.dp.core.exception.DataPermissionException;
import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.core.sql.SqlConditionBuilder;
import com.ty.dp.core.sql.SqlMarkers;
import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.DataPermissionSubject;
import com.ty.dp.core.subject.DataPermissionSubjectProvider;
import com.ty.dp.core.sql.SqlParameterTokens;
import org.apache.ibatis.mapping.ParameterMapping;
import java.util.ArrayList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis interceptor that rewrites SELECT statements.
 *
 * <p>It replaces the {@link MappedStatement} in the invocation with one whose
 * {@link SqlSource} returns the rewritten SQL. This is deliberate: it keeps
 * the rest of the plugin chain (PageHelper, MyBatis-Plus, ...) intact and lets
 * the executor compute a cache key from the rewritten statement.</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DataPermissionInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionInterceptor.class);

    private final DataPermissionRuleResolver ruleResolver;
    private final DataPermissionSubjectProvider subjectProvider;
    private final DataPermissionOptions options;
    private final ConcurrentHashMap<String, AnnotationMeta> annotationCache =
            new ConcurrentHashMap<String, AnnotationMeta>();

    public DataPermissionInterceptor(DataPermissionRuleResolver ruleResolver,
                                     DataPermissionSubjectProvider subjectProvider,
                                     DataPermissionOptions options) {
        this.ruleResolver = ruleResolver;
        this.subjectProvider = subjectProvider;
        this.options = options == null ? DataPermissionOptions.defaults() : options;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (options.getMode() == DataPermissionMode.OFF || DataPermissionContext.isBypassed()) {
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        Object parameter = args[1];
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        AnnotationMeta meta = resolveAnnotation(statement);
        if (!meta.enabled) {
            return invocation.proceed();
        }

        BoundSql boundSql = statement.getBoundSql(parameter);
        String sql = boundSql.getSql();
        if (sql == null || sql.trim().isEmpty() || sql.contains(SqlMarkers.MARKER)) {
            return invocation.proceed();
        }

        DataPermissionSubject subject = DataPermissionContext.getSubject();
        if (subject == null && subjectProvider != null) {
            subject = subjectProvider.getCurrent();
        }

        if (subject == null) {
            if (options.getNoSubjectPolicy() == DataPermissionPolicy.DENY) {
                return rewriteAndProceed(invocation, statement, boundSql, meta, sql, null, Collections.<DataPermissionRule>emptyList(), true);
            }
            return invocation.proceed();
        }
        if (subject.isSuperAdmin()) {
            return invocation.proceed();
        }

        List<DataPermissionRule> rules = ruleResolver == null
                ? Collections.<DataPermissionRule>emptyList()
                : ruleResolver.resolve(subject, meta.appPoint);

        boolean denyWhenEmpty = options.getDefaultPolicy() == DataPermissionPolicy.DENY;
        if (rules.isEmpty() && !denyWhenEmpty) {
            return invocation.proceed();
        }

        return rewriteAndProceed(invocation, statement, boundSql, meta, sql, subject, rules, denyWhenEmpty);
    }

    private Object rewriteAndProceed(Invocation invocation,
                                     MappedStatement statement,
                                     BoundSql boundSql,
                                     AnnotationMeta meta,
                                     String sql,
                                     DataPermissionSubject subject,
                                     List<DataPermissionRule> rules,
                                     boolean denyWhenEmpty) throws Throwable {
        String rewritten;
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        final int[] originalIndex = {0};
        String tokenized = SqlParameterTokens.replace(sql, token -> {
            if (!"?".equals(token)) throw new DataPermissionException("Reserved SQL parameter name: " + token);
            return ":__dp_original_" + originalIndex[0]++;
        });
        try {
            rewritten = SqlConditionBuilder.applyPrepared(tokenized, rules, meta.aliases, subject,
                    invocation.getArgs()[1], denyWhenEmpty, bindings);
        } catch (DataPermissionException ex) {
            if (options.getMode() == DataPermissionMode.LOG_ONLY) {
                log.error("[data-permission] cannot rewrite SQL, mode=LOG_ONLY, mapper={}", statement.getId(), ex);
                return invocation.proceed();
            }
            log.error("[data-permission] rejecting query, mapper={}, sql={}", statement.getId(), sql, ex);
            throw ex;
        }

        if (rewritten == null || rewritten.equals(tokenized)) {
            return invocation.proceed();
        }

        if (options.getMode() == DataPermissionMode.LOG_ONLY) {
            log.info("[data-permission] user={} mapper={} original={} rewritten={}",
                    subject == null ? "none" : subject.getSubjectId(), statement.getId(), sql, rewritten);
            return invocation.proceed();
        }

        if (log.isDebugEnabled()) {
            log.debug("[data-permission] user={} mapper={} rewritten={}",
                    subject == null ? "none" : subject.getSubjectId(), statement.getId(), rewritten);
        }

        final List<ParameterMapping> mappings = new ArrayList<ParameterMapping>();
        String preparedSql = SqlParameterTokens.replace(rewritten, token -> {
            if (token.startsWith(":__dp_original_")) {
                int index = Integer.parseInt(token.substring(15));
                mappings.add(boundSql.getParameterMappings().get(index));
            } else if (token.startsWith(":__dp_sq_")) {
                String name = token.substring(1);
                if (!bindings.containsKey(name)) throw new DataPermissionException("Unknown permission binding");
                if (boundSql.hasAdditionalParameter(name)) throw new DataPermissionException("Reserved permission binding");
                mappings.add(new ParameterMapping.Builder(statement.getConfiguration(), name, Object.class).build());
            } else throw new DataPermissionException("Untracked SQL parameter");
            return "?";
        });
        SqlSource sqlSource = ignored -> {
            BoundSql prepared = new BoundSql(statement.getConfiguration(), preparedSql, mappings, boundSql.getParameterObject());
            // Preserve dynamic SQL/foreach values; the SQL source is scoped to this invocation.
            @SuppressWarnings("unchecked")
            Map<String, Object> additional = (Map<String, Object>) statement.getConfiguration()
                    .newMetaObject(boundSql).getValue("additionalParameters");
            for (Map.Entry<String, Object> entry : additional.entrySet()) prepared.setAdditionalParameter(entry.getKey(), entry.getValue());
            for (Map.Entry<String, Object> entry : bindings.entrySet()) prepared.setAdditionalParameter(entry.getKey(), entry.getValue());
            return prepared;
        };
        invocation.getArgs()[0] = copyWithSqlSource(statement, sqlSource);
        return invocation.proceed();
    }

    private MappedStatement copyWithSqlSource(MappedStatement source, SqlSource sqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                source.getConfiguration(), source.getId(), sqlSource, source.getSqlCommandType())
                .resource(source.getResource())
                .fetchSize(source.getFetchSize())
                .statementType(source.getStatementType())
                .timeout(source.getTimeout())
                .parameterMap(source.getParameterMap())
                .resultMaps(source.getResultMaps())
                .resultSetType(source.getResultSetType())
                .cache(source.getCache())
                .flushCacheRequired(source.isFlushCacheRequired())
                .useCache(source.isUseCache())
                .keyGenerator(source.getKeyGenerator())
                .keyProperty(join(source.getKeyProperties()))
                .keyColumn(join(source.getKeyColumns()))
                .databaseId(source.getDatabaseId())
                .lang(source.getLang())
                .resultOrdered(source.isResultOrdered());
        return builder.build();
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private AnnotationMeta resolveAnnotation(MappedStatement statement) {
        String id = statement.getId();
        String key = id.endsWith("_COUNT") ? id.substring(0, id.length() - 6) : id;
        AnnotationMeta cached = annotationCache.get(key);
        if (cached != null) {
            return cached;
        }
        AnnotationMeta meta = loadAnnotation(statement, key);
        annotationCache.put(key, meta);
        return meta;
    }

    private AnnotationMeta loadAnnotation(MappedStatement statement, String id) {
        int dot = id.lastIndexOf('.');
        if (dot <= 0) {
            return AnnotationMeta.NONE;
        }
        String className = id.substring(0, dot);
        String methodName = id.substring(dot + 1);
        Class<?> mapperType;
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            mapperType = classLoader == null
                    ? Class.forName(className)
                    : Class.forName(className, false, classLoader);
        } catch (Throwable ex) {
            log.debug("[data-permission] cannot load mapper class {}", className);
            return AnnotationMeta.NONE;
        }

        if (mapperType.isAnnotationPresent(DataPermissionIgnore.class)) {
            return AnnotationMeta.NONE;
        }
        DataPermission typeAnnotation = mapperType.getAnnotation(DataPermission.class);

        Method[] methods = mapperType.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.isAnnotationPresent(DataPermissionIgnore.class)) {
                return AnnotationMeta.NONE;
            }
            DataPermission annotation = method.getAnnotation(DataPermission.class);
            if (annotation == null) {
                annotation = typeAnnotation;
            }
            if (annotation == null) {
                return AnnotationMeta.NONE;
            }
            return new AnnotationMeta(annotation.enabled(), annotation.appPoint(),
                    parseAliases(annotation.tableAlias()));
        }
        return AnnotationMeta.NONE;
    }

    private static Map<String, String> parseAliases(String[] entries) {
        if (entries == null || entries.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon >= entry.length() - 1) {
                continue;
            }
            String table = entry.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String alias = entry.substring(colon + 1).trim();
            if (!table.isEmpty() && !alias.isEmpty()) {
                aliases.put(table, alias);
            }
        }
        return aliases;
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private static final class AnnotationMeta {
        private static final AnnotationMeta NONE =
                new AnnotationMeta(false, "", Collections.<String, String>emptyMap());

        private final boolean enabled;
        private final String appPoint;
        private final Map<String, String> aliases;

        private AnnotationMeta(boolean enabled, String appPoint, Map<String, String> aliases) {
            this.enabled = enabled;
            this.appPoint = appPoint == null ? "" : appPoint;
            this.aliases = aliases;
        }
    }
}
