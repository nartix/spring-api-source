package com.ferozfaiz.uniapi.catalogue.api.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@NotBlank(message = "{catalogue.api.slug.required}")
@Size(max = 180, message = "{catalogue.api.slug.pattern}")
@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "{catalogue.api.slug.pattern}")
@Documented
@Constraint(validatedBy = {})
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface CatalogueSlug {
  String message() default "{catalogue.api.slug.pattern}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
