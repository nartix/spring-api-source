package com.ferozfaiz.uniapi.category.application.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class AdminCategorySystemRegistry {

  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

  private final Map<String, AdminCategorySystemRegistration> registrationsBySystemKey;
  private final List<AdminCategorySystemRegistration> registrations;

  AdminCategorySystemRegistry(Collection<AdminCategorySystemRegistration> candidates) {
    if (candidates == null) {
      throw invalidCollection();
    }
    List<AdminCategorySystemRegistration> immutableSnapshot;
    try {
      var snapshot = new ArrayList<AdminCategorySystemRegistration>(candidates.size());
      for (AdminCategorySystemRegistration candidate : candidates) {
        if (candidate == null) {
          throw invalidCollection();
        }
        snapshot.add(candidate);
      }
      immutableSnapshot = List.copyOf(snapshot);
    } catch (AdminCategoryRegistrationValidationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw invalidCollection();
    }
    AdminCategoryRegistrationValidator.validate(immutableSnapshot);

    var sorted = new ArrayList<>(immutableSnapshot);
    sorted.sort((left, right) ->
      left.systemKey().orElseThrow().compareTo(right.systemKey().orElseThrow())
    );
    var lookup = new LinkedHashMap<String, AdminCategorySystemRegistration>();
    for (AdminCategorySystemRegistration registration : sorted) {
      lookup.put(registration.systemKey().orElseThrow(), registration);
    }
    this.registrationsBySystemKey = Map.copyOf(lookup);
    this.registrations = List.copyOf(sorted);
  }

  public Optional<AdminCategorySystemRegistration> findRegistration(String systemKey) {
    if (!isCanonicalKey(systemKey)) {
      throw new AdminCategoryRegistrationValidationException(
        new AdminCategoryValidationDiagnostic(
          "<unavailable>",
          AdminCategoryValidationCategory.INVALID_SYSTEM_KEY,
          Optional.of(AdminCategoryValidationComponent.REGISTRY),
          Optional.empty(),
          Optional.empty()
        )
      );
    }
    return Optional.ofNullable(registrationsBySystemKey.get(systemKey));
  }

  public List<AdminCategorySystemRegistration> registrations() {
    return registrations;
  }

  private static boolean isCanonicalKey(String value) {
    return (value != null && value.length() <= 63 && KEY_PATTERN.matcher(value).matches());
  }

  private static AdminCategoryRegistrationValidationException invalidCollection() {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        "<unavailable>",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.of(AdminCategoryValidationComponent.REGISTRY),
        Optional.empty(),
        Optional.empty()
      )
    );
  }
}
