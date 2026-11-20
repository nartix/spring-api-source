package com.ferozfaiz.uniapi.data.application.policy;

import com.ferozfaiz.uniapi.data.api.CategoryAuthorities;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("categoryReadAuthorizationPolicy")
public class CategoryReadAuthorizationPolicy {

  public boolean canRead(Authentication authentication) {
    return CategoryAuthorities.hasCategoriesRead(authentication);
  }

  public boolean canRead(Authentication authentication, Long categoryId) {
    return canRead(authentication);
  }
}
