package com.ty.dp.starter.admin;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/** Expressions are trusted operator configuration, never rule/user input. */
public class ExpressionDataPermissionAdminAuthorizer implements DataPermissionAdminAuthorizer {
    private final ApplicationContext context;
    private final Map<String, String> expressions;
    public ExpressionDataPermissionAdminAuthorizer(ApplicationContext context, Map<String, String> expressions) {
        this.context = context;
        this.expressions = expressions;
    }
    public boolean isAllowed(AdminAction action) {
        try {
            String expression = expressions.get(action.name().toLowerCase(Locale.ROOT));
            if (expression == null || expression.trim().isEmpty()) return false;
            StandardEvaluationContext evaluation = new StandardEvaluationContext();
            evaluation.setBeanResolver(new BeanFactoryResolver(context));
            return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(expression).getValue(evaluation, Boolean.class));
        } catch (Exception ex) { return false; }
    }
}
