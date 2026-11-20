package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
import com.ferozfaiz.uniapi.category.application.registration.internal.AdminCategoryRegistrationFailures;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public final class AdminCategoryRuntimePolicyBindings {

  private final List<AdminCategoryRuntimePolicyBinding> values;
  private final Map<AdminCategoryPolicyReference, AdminCategoryRuntimePolicyBinding> byReference;

  public AdminCategoryRuntimePolicyBindings(
    Collection<AdminCategoryRuntimePolicyBinding> bindings
  ) {
    if (bindings == null) throw invalid();
    var snapshot = new ArrayList<AdminCategoryRuntimePolicyBinding>(bindings.size());
    var lookup = new LinkedHashMap<
      AdminCategoryPolicyReference,
      AdminCategoryRuntimePolicyBinding
    >();
    for (AdminCategoryRuntimePolicyBinding binding : bindings) {
      if (
        binding == null || lookup.putIfAbsent(binding.reference(), binding) != null
      ) throw invalid();
      snapshot.add(binding);
    }
    snapshot.sort((left, right) -> {
      int result = Integer.compare(
        left.reference().obligation().ordinal(),
        right.reference().obligation().ordinal()
      );
      if (result != 0) return result;
      result = left
        .reference()
        .logicalId()
        .moduleKey()
        .compareTo(right.reference().logicalId().moduleKey());
      return result != 0
        ? result
        : left
            .reference()
            .logicalId()
            .policyKey()
            .compareTo(right.reference().logicalId().policyKey());
    });
    this.values = List.copyOf(snapshot);
    this.byReference = Map.copyOf(lookup);
  }

  public static AdminCategoryRuntimePolicyBindings empty() {
    return new AdminCategoryRuntimePolicyBindings(List.of());
  }

  public Optional<AdminCategoryRuntimePolicyBinding> find(AdminCategoryPolicyReference reference) {
    if (reference == null) throw invalid();
    return Optional.ofNullable(byReference.get(reference));
  }

  public List<AdminCategoryRuntimePolicyBinding> values() {
    return values;
  }

  private static AdminCategoryRegistrationValidationException invalid() {
    return AdminCategoryRegistrationFailures.failure(
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      AdminCategoryValidationComponent.POLICY_REFERENCES
    );
  }
}
