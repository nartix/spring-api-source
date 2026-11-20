package com.ferozfaiz.uniapi.category.api.authorization;

import static com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy.ADMIN_CATEGORY_SYSTEM_READ_EXPRESSION;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Documented
@Target(METHOD)
@Retention(RUNTIME)
@PreAuthorize(ADMIN_CATEGORY_SYSTEM_READ_EXPRESSION)
public @interface RequireAdminCategorySystemRead {}
