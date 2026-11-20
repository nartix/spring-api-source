package com.ferozfaiz.uniapi.catalogue.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductIdentifierFamily;
import org.junit.jupiter.api.Test;

class ProductIdentifierRulesTest {

  @Test
  void normalizesSupportedGtinFamiliesToCanonicalGtin14() {
    assertThat(ProductIdentifierRules.normalize("UPC", "036000291452", true))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.UPC, "036000291452", "00036000291452", true);
    assertThat(ProductIdentifierRules.normalize("EAN", "4006381333931", false))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.EAN, "4006381333931", "04006381333931", false);
    assertThat(ProductIdentifierRules.normalize("JAN", "4901234567894", false))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.JAN, "4901234567894", "04901234567894", false);
    assertThat(ProductIdentifierRules.normalize("GTIN-14", "10012345678902", false))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.GTIN_14, "10012345678902", "10012345678902", false);
  }

  @Test
  void convertsIsbn10AndAcceptsIsbn13() {
    assertThat(ProductIdentifierRules.normalize("ISBN", "0-306-40615-2", true))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.ISBN, "9780306406157", "09780306406157", true);
    assertThat(ProductIdentifierRules.normalize("ISBN", "9780306406157", false))
      .extracting("family", "value", "canonicalGtin14", "primary")
      .containsExactly(ProductIdentifierFamily.ISBN, "9780306406157", "09780306406157", false);
  }

  @Test
  void rejectsBadChecksumAndFamilySpecificPrefixes() {
    assertThatThrownBy(() -> ProductIdentifierRules.normalize("UPC", "036000291453", false))
      .isInstanceOf(CatalogueException.class)
      .hasMessage("product identifier checksum is invalid");
    assertThatThrownBy(() -> ProductIdentifierRules.normalize("JAN", "4006381333931", false))
      .isInstanceOf(CatalogueException.class)
      .hasMessage("JAN must start with 45 or 49");
    assertThatThrownBy(() -> ProductIdentifierRules.normalize("ISBN", "9770306406158", false))
      .isInstanceOf(CatalogueException.class)
      .hasMessage("ISBN-13 must start with 978 or 979");
  }
}
