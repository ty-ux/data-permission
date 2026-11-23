package com.ty.dp.starter;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** A named source is sufficient even in a multi-source host. */
public class RuleDataSourceCondition extends AnyNestedCondition {
    public RuleDataSourceCondition() { super(ConfigurationPhase.REGISTER_BEAN); }
    @ConditionalOnSingleCandidate(DataSource.class)
    static class Single { }
    @Conditional(NamedCondition.class)
    static class Named { }
    public static class NamedCondition implements Condition {
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String name = context.getEnvironment().getProperty("data-permission.datasource-bean", "");
            return !name.trim().isEmpty();
        }
    }
}
