package com.ty.dp.mybatis;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables data permission on a mapper method or mapper interface. The
 * annotation is intentionally placed on mappers rather than controllers: it
 * does not depend on the call stack, works on async threads and is resolved
 * once per MappedStatement.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * Logical application point used to select rules. May be blank when rules
     * are matched by table name only.
     */
    String appPoint() default "";

    /**
     * Optional alias overrides written as {@code table:alias}.
     */
    String[] tableAlias() default {};

    /**
     * Set to false to temporarily disable the rule engine for this mapper.
     */
    boolean enabled() default true;
}
