package com.ferozfaiz.uniapi.catalogue.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CatalogueAuthoritiesTest {

  @Test
  void catalogueReadContractAcceptsScopeRawAndServiceAuthoritiesOnly() {
    assertThat(CatalogueAuthorities.CATALOGUE_READ_AUTHORITIES)
      .containsExactlyInAnyOrder(
        CatalogueAuthorities.SCOPE_CATALOGUE_READ,
        CatalogueAuthorities.CATALOGUE_READ,
        CatalogueAuthorities.SERVICE_CATALOGUE_READ
      )
      .doesNotContain("ROLE_ADMIN");

    assertThat(
      CatalogueAuthorities.hasCatalogueRead(authentication(CatalogueAuthorities.CATALOGUE_READ))
    ).isTrue();
    assertThat(
      CatalogueAuthorities.hasCatalogueRead(
        authentication(CatalogueAuthorities.SCOPE_CATALOGUE_READ)
      )
    ).isTrue();
    assertThat(
      CatalogueAuthorities.hasCatalogueRead(
        authentication(CatalogueAuthorities.SERVICE_CATALOGUE_READ)
      )
    ).isTrue();
    assertThat(CatalogueAuthorities.hasCatalogueRead(authentication("ROLE_ADMIN"))).isFalse();
    assertThat(
      CatalogueAuthorities.hasCatalogueRead(authentication(CatalogueAuthorities.CATALOGUE_WRITE))
    ).isFalse();
  }

  @Test
  void catalogueWriteContractAcceptsScopeRawAndServiceAuthoritiesOnly() {
    assertThat(CatalogueAuthorities.CATALOGUE_WRITE_AUTHORITIES)
      .containsExactlyInAnyOrder(
        CatalogueAuthorities.SCOPE_CATALOGUE_WRITE,
        CatalogueAuthorities.CATALOGUE_WRITE,
        CatalogueAuthorities.SERVICE_CATALOGUE_WRITE
      )
      .doesNotContain("ROLE_ADMIN");

    assertThat(
      CatalogueAuthorities.hasCatalogueWrite(authentication(CatalogueAuthorities.CATALOGUE_WRITE))
    ).isTrue();
    assertThat(
      CatalogueAuthorities.hasCatalogueWrite(
        authentication(CatalogueAuthorities.SCOPE_CATALOGUE_WRITE)
      )
    ).isTrue();
    assertThat(
      CatalogueAuthorities.hasCatalogueWrite(
        authentication(CatalogueAuthorities.SERVICE_CATALOGUE_WRITE)
      )
    ).isTrue();
    assertThat(CatalogueAuthorities.hasCatalogueWrite(authentication("ROLE_ADMIN"))).isFalse();
    assertThat(
      CatalogueAuthorities.hasCatalogueWrite(authentication(CatalogueAuthorities.CATALOGUE_READ))
    ).isFalse();
  }

  @Test
  void missingAuthenticationOrAuthoritiesAreDenied() {
    assertThat(CatalogueAuthorities.hasCatalogueRead(null)).isFalse();
    assertThat(CatalogueAuthorities.hasCatalogueWrite(authentication())).isFalse();
  }

  private static Authentication authentication(String... authorities) {
    return UsernamePasswordAuthenticationToken.authenticated(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
