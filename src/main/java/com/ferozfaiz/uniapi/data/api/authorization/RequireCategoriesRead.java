package com.ferozfaiz.uniapi.data.api.authorization;

import com.ferozfaiz.uniapi.data.api.CategoryAuthorities;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize(CategoryAuthorities.CATEGORIES_READ_EXPRESSION)
public @interface RequireCategoriesRead {}
