package com.ty.dp.core.spi;

import com.ty.dp.core.model.DataPermissionRule;

import java.util.Collection;
import java.util.List;

/**
 * Loads rules for a set of roles. Implementations may read a database, Redis,
 * or a remote rule service. The default implementation in the starter reads
 * the bundled tables through JdbcTemplate.
 */
public interface DataPermissionRuleRepository {

    /**
     * @param roleIds  role identifiers, never null
     * @param appPoint application point, may be null/blank to load all rules
     */
    List<DataPermissionRule> findByRoles(Collection<String> roleIds, String appPoint);
}
