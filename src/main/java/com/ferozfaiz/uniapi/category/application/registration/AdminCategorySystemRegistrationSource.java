package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Collection;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public interface AdminCategorySystemRegistrationSource {
  Collection<AdminCategorySystemRegistration> registrations();
}
