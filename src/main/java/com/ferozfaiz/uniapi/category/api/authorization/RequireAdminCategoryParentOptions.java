package com.ferozfaiz.uniapi.category.api.authorization;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Documented
@Target(METHOD)
@Retention(RUNTIME)
@PreAuthorize(
  "@adminCategorySystemAuthorizationPolicy.canAccess(authentication,#systemKey,T(com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation).PARENT_OPTIONS)"
)
public @interface RequireAdminCategoryParentOptions {}
