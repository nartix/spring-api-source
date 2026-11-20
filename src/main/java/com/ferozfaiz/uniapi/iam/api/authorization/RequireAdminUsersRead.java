package com.ferozfaiz.uniapi.iam.api.authorization;

import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize(AdminAuthorities.ADMIN_USERS_READ_EXPRESSION)
public @interface RequireAdminUsersRead {}
