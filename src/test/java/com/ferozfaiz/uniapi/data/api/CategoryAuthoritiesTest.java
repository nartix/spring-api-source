package com.ferozfaiz.uniapi.data.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CategoryAuthoritiesTest {

  @Test
  void categoryReadContractAcceptsRawAndScopeAuthoritiesOnly() {
    assertThat(CategoryAuthorities.CATEGORIES_READ_AUTHORITIES)
      .containsExactlyInAnyOrder(
        CategoryAuthorities.CATEGORIES_READ,
        CategoryAuthorities.SCOPE_CATEGORIES_READ
      )
      .doesNotContain("ROLE_ADMIN");

    assertThat(
      CategoryAuthorities.hasCategoriesRead(authentication(CategoryAuthorities.CATEGORIES_READ))
    ).isTrue();
    assertThat(
      CategoryAuthorities.hasCategoriesRead(
        authentication(CategoryAuthorities.SCOPE_CATEGORIES_READ)
      )
    ).isTrue();
    assertThat(CategoryAuthorities.hasCategoriesRead(authentication("ROLE_ADMIN"))).isFalse();
    assertThat(
      CategoryAuthorities.hasCategoriesRead(authentication(CategoryAuthorities.CATEGORIES_WRITE))
    ).isFalse();
  }

  @Test
  void categoryWriteContractAcceptsRawAndScopeAuthoritiesOnly() {
    assertThat(CategoryAuthorities.CATEGORIES_WRITE_AUTHORITIES)
      .containsExactlyInAnyOrder(
        CategoryAuthorities.CATEGORIES_WRITE,
        CategoryAuthorities.SCOPE_CATEGORIES_WRITE
      )
      .doesNotContain("ROLE_ADMIN");

    assertThat(
      CategoryAuthorities.hasCategoriesWrite(authentication(CategoryAuthorities.CATEGORIES_WRITE))
    ).isTrue();
    assertThat(
      CategoryAuthorities.hasCategoriesWrite(
        authentication(CategoryAuthorities.SCOPE_CATEGORIES_WRITE)
      )
    ).isTrue();
    assertThat(CategoryAuthorities.hasCategoriesWrite(authentication("ROLE_ADMIN"))).isFalse();
    assertThat(
      CategoryAuthorities.hasCategoriesWrite(authentication(CategoryAuthorities.CATEGORIES_READ))
    ).isFalse();
  }

  @Test
  void missingAuthenticationOrAuthoritiesAreDenied() {
    assertThat(CategoryAuthorities.hasCategoriesRead(null)).isFalse();
    assertThat(CategoryAuthorities.hasCategoriesWrite(authentication())).isFalse();
  }

  private static Authentication authentication(String... authorities) {
    return UsernamePasswordAuthenticationToken.authenticated(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
