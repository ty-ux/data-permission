package com.ty.dp.core.spi;

import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.subject.DataPermissionSubject;

import java.util.List;

/**
 * Resolves the effective rules for a subject. The starter provides a caching
 * implementation on top of a {@link DataPermissionRuleRepository}.
 */
public interface DataPermissionRuleResolver {

    List<DataPermissionRule> resolve(DataPermissionSubject subject, String appPoint);
}
