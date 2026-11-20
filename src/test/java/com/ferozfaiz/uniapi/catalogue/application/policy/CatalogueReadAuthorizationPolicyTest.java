package com.ferozfaiz.uniapi.catalogue.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.api.CatalogueAuthorities;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CatalogueReadAuthorizationPolicyTest {

  private final CatalogueReadAuthorizationPolicy policy = new CatalogueReadAuthorizationPolicy();

  @Test
  void rejectsMissingReadAuthorityAndAdminBypass() {
    assertCatalogueRead(authentication()).isFalse();
    assertCatalogueRead(authentication(CatalogueAuthorities.CATALOGUE_WRITE)).isFalse();
    assertCatalogueRead(authentication("ROLE_ADMIN")).isFalse();
    assertThat(policy.canReadProducts(null)).isFalse();
  }

  @Test
  void allowsRawScopeAndServiceReadAuthoritiesWithoutRecordRestriction() {
    assertCatalogueRead(authentication(CatalogueAuthorities.CATALOGUE_READ)).isTrue();
    assertCatalogueRead(authentication(CatalogueAuthorities.SCOPE_CATALOGUE_READ)).isTrue();
    assertCatalogueRead(authentication(CatalogueAuthorities.SERVICE_CATALOGUE_READ)).isTrue();
  }

  private org.assertj.core.api.AbstractBooleanAssert<?> assertCatalogueRead(
    Authentication authentication
  ) {
    assertThat(policy.canReadCategoryProducts(authentication, "processors")).isEqualTo(
      policy.canReadProducts(authentication)
    );
    assertThat(policy.canReadProduct(authentication, 42L)).isEqualTo(
      policy.canReadProducts(authentication)
    );
    assertThat(policy.canReadBrand(authentication, 7L)).isEqualTo(
      policy.canReadProducts(authentication)
    );
    assertThat(policy.canReadBrand(authentication, null)).isEqualTo(
      policy.canReadProducts(authentication)
    );
    assertThat(policy.canReadManufacturer(authentication, 8L)).isEqualTo(
      policy.canReadProducts(authentication)
    );
    assertThat(policy.canReadManufacturer(authentication, null)).isEqualTo(
      policy.canReadProducts(authentication)
    );
    return assertThat(policy.canReadProducts(authentication));
  }

  private static Authentication authentication(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
