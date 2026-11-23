package com.ty.dp.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AuthorizationExpressionsCondition implements Condition {
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        for (String action : new String[]{"list", "save", "edit", "remove"})
            if (context.getEnvironment().containsProperty("data-permission.admin.authorization." + action)) return true;
        return false;
    }
}
