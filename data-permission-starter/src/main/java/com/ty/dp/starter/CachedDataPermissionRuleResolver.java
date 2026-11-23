package com.ty.dp.starter;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.core.subject.DataPermissionSubject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Caches rules per role set + application point. Rule changes call
 * {@link #invalidateAll()}; the TTL is the safety net.
 */
public class CachedDataPermissionRuleResolver implements DataPermissionRuleResolver {

    private final DataPermissionRuleRepository repository;
    private final TtlCache<List<DataPermissionRule>> cache;
    // Role lookup locates the content key without another database read on cache hits.
    private final TtlCache<String> contentKeys;

    public CachedDataPermissionRuleResolver(DataPermissionRuleRepository repository, long ttlSeconds) {
        this.repository = repository;
        this.cache = new TtlCache<List<DataPermissionRule>>(ttlSeconds);
        this.contentKeys = new TtlCache<String>(ttlSeconds);
    }

    @Override
    public List<DataPermissionRule> resolve(DataPermissionSubject subject, String appPoint) {
        if (subject == null || subject.isSuperAdmin()) {
            return Collections.emptyList();
        }
        Set<String> roleIds = subject.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sorted = new ArrayList<String>(roleIds);
        Collections.sort(sorted);
        String key = buildKey(sorted, appPoint);

        String contentKey = contentKeys.get(key);
        List<DataPermissionRule> cached = contentKey == null ? null : cache.get(contentKey);
        if (cached != null) {
            return cached;
        }
        List<DataPermissionRule> loaded = repository.findByRoles(sorted, appPoint);
        List<DataPermissionRule> immutable = Collections.unmodifiableList(
                loaded == null ? new ArrayList<DataPermissionRule>() : new ArrayList<DataPermissionRule>(loaded));
        contentKey = withSubqueryContent(key, immutable);
        cache.put(contentKey, immutable);
        contentKeys.put(key, contentKey);
        return immutable;
    }

    public void invalidateAll() {
        contentKeys.clear();
        cache.clear();
    }

    static String withSubqueryContent(String key, List<DataPermissionRule> rules) {
        StringBuilder builder = new StringBuilder(key);
        for (DataPermissionRule rule : rules) {
            if (rule.getItems() == null) continue;
            for (com.ty.dp.core.model.DataPermissionRuleItem item : rule.getItems()) {
                if (item.getRightType() == com.ty.dp.core.model.RightValueType.SUBQUERY) {
                    String sql = item.getRightExpr() == null ? "" : item.getRightExpr();
                    builder.append("|SUBQUERY:").append(sql.length()).append(':').append(sql);
                }
            }
        }
        return builder.toString();
    }

    private static String buildKey(List<String> roleIds, String appPoint) {
        StringBuilder builder = new StringBuilder();
        for (String roleId : roleIds) {
            builder.append(roleId).append(',');
        }
        builder.append('|');
        if (appPoint != null) {
            builder.append(appPoint.trim());
        }
        return builder.toString();
    }
}
