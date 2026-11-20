package com.ty.dp.starter;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.spi.DataPermissionRuleRepository;
import com.ty.dp.core.spi.DataPermissionRuleResolver;
import com.ty.dp.core.subject.DataPermissionSubject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches rules per role set + application point. Rule changes call
 * {@link #invalidateAll()}; the TTL is the safety net.
 */
public class CachedDataPermissionRuleResolver implements DataPermissionRuleResolver {

    private final DataPermissionRuleRepository repository;
    private final TtlCache<List<DataPermissionRule>> cache;
    private final AtomicLong generation = new AtomicLong();
    private final Object publishLock = new Object();

    public CachedDataPermissionRuleResolver(DataPermissionRuleRepository repository, long ttlSeconds) {
        this.repository = repository;
        this.cache = new TtlCache<List<DataPermissionRule>>(ttlSeconds);
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

        List<DataPermissionRule> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        long seenGeneration = generation.get();
        List<DataPermissionRule> loaded = repository.findByRoles(sorted, appPoint);
        List<DataPermissionRule> immutable = Collections.unmodifiableList(
                loaded == null ? new ArrayList<DataPermissionRule>() : new ArrayList<DataPermissionRule>(loaded));
        synchronized (publishLock) {
            // An invalidation between the repository read and the cache write
            // must not let the stale result be published.
            if (generation.get() == seenGeneration) {
                cache.put(key, immutable);
            }
        }
        return immutable;
    }

    public void invalidateAll() {
        synchronized (publishLock) {
            generation.incrementAndGet();
            cache.clear();
        }
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
