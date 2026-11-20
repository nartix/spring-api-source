package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.registration.internal.AdminCategoryRegistrationFailures;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AdminCategoryRegistrationConfiguration {

  @Bean
  AdminCategoryDefinitionValidator adminCategoryDefinitionValidator() {
    return new AdminCategoryDefinitionValidator();
  }

  @Bean
  AdminCategoryIdentifierCodec<Long> standardNumericV1AdminCategoryIdentifierCodec() {
    return new StandardNumericV1AdminCategoryIdentifierCodec();
  }

  @Bean
  AdminCategorySystemRegistry adminCategorySystemRegistry(
    List<AdminCategorySystemRegistration> directCandidates,
    List<AdminCategorySystemRegistrationSource> sources
  ) {
    var candidates = new ArrayList<AdminCategorySystemRegistration>(directCandidates);
    for (AdminCategorySystemRegistrationSource source : sources) {
      Collection<AdminCategorySystemRegistration> contributed;
      try {
        contributed = source.registrations();
        candidates.addAll(List.copyOf(contributed));
      } catch (RuntimeException failure) {
        throw AdminCategoryRegistrationFailures.failure(
          "<unavailable>",
          AdminCategoryValidationCategory.INVALID_COLLECTION,
          AdminCategoryValidationComponent.REGISTRATION_SOURCE
        );
      }
    }
    return new AdminCategorySystemRegistry(List.copyOf(candidates));
  }
}
