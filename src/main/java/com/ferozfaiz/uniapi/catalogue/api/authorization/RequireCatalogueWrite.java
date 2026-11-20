package com.ferozfaiz.uniapi.catalogue.api.authorization;

import com.ferozfaiz.uniapi.catalogue.api.CatalogueAuthorities;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize(CatalogueAuthorities.CATALOGUE_WRITE_EXPRESSION)
public @interface RequireCatalogueWrite {}
