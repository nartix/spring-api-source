package com.ferozfaiz.uniapi.catalogue.domain.validation;

import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductIdentifierFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductIdentifierRules {

  private ProductIdentifierRules() {}

  public static CatalogueProductIdentifier normalize(
    String familyValue,
    String value,
    boolean primary
  ) {
    ProductIdentifierFamily family = ProductIdentifierFamily.fromApiValue(familyValue);
    if (family == null) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.family.invalid",
        "product identifier family is invalid",
        familyValue
      );
    }
    return normalize(family, value, primary);
  }

  public static CatalogueProductIdentifier normalize(
    ProductIdentifierFamily family,
    String value,
    boolean primary
  ) {
    if (family == null) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.family.required",
        "product identifier family is required"
      );
    }
    if (value == null || value.isBlank()) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.value.required",
        "product identifier value is required"
      );
    }
    return switch (family) {
      case UPC -> normalizeUpc(value, primary);
      case EAN -> normalizeEan(value, primary);
      case JAN -> normalizeJan(value, primary);
      case ISBN -> normalizeIsbn(value, primary);
      case GTIN_14 -> normalizeGtin14(value, primary);
    };
  }

  private static CatalogueProductIdentifier normalizeUpc(String value, boolean primary) {
    String digits = digits(value);
    requireLength(ProductIdentifierFamily.UPC, digits, 12);
    requireMod10(ProductIdentifierFamily.UPC, digits);
    return new CatalogueProductIdentifier(
      ProductIdentifierFamily.UPC,
      digits,
      "00" + digits,
      primary
    );
  }

  private static CatalogueProductIdentifier normalizeEan(String value, boolean primary) {
    String digits = digits(value);
    requireLength(ProductIdentifierFamily.EAN, digits, 13);
    requireMod10(ProductIdentifierFamily.EAN, digits);
    return new CatalogueProductIdentifier(
      ProductIdentifierFamily.EAN,
      digits,
      "0" + digits,
      primary
    );
  }

  private static CatalogueProductIdentifier normalizeJan(String value, boolean primary) {
    String digits = digits(value);
    requireLength(ProductIdentifierFamily.JAN, digits, 13);
    if (!digits.startsWith("45") && !digits.startsWith("49")) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.jan.prefix",
        "JAN must start with 45 or 49",
        value
      );
    }
    requireMod10(ProductIdentifierFamily.JAN, digits);
    return new CatalogueProductIdentifier(
      ProductIdentifierFamily.JAN,
      digits,
      "0" + digits,
      primary
    );
  }

  private static CatalogueProductIdentifier normalizeIsbn(String value, boolean primary) {
    String normalized = alphanumeric(value);
    if (normalized.length() == 10) {
      requireIsbn10(normalized);
      String isbn13 = toIsbn13(normalized);
      return new CatalogueProductIdentifier(
        ProductIdentifierFamily.ISBN,
        isbn13,
        "0" + isbn13,
        primary
      );
    }
    String digits = digits(value);
    requireLength(ProductIdentifierFamily.ISBN, digits, 13);
    if (!digits.startsWith("978") && !digits.startsWith("979")) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.isbn.prefix",
        "ISBN-13 must start with 978 or 979",
        value
      );
    }
    requireMod10(ProductIdentifierFamily.ISBN, digits);
    return new CatalogueProductIdentifier(
      ProductIdentifierFamily.ISBN,
      digits,
      "0" + digits,
      primary
    );
  }

  private static CatalogueProductIdentifier normalizeGtin14(String value, boolean primary) {
    String digits = digits(value);
    requireLength(ProductIdentifierFamily.GTIN_14, digits, 14);
    requireMod10(ProductIdentifierFamily.GTIN_14, digits);
    return new CatalogueProductIdentifier(ProductIdentifierFamily.GTIN_14, digits, digits, primary);
  }

  private static String digits(String value) {
    String normalized = value.replace("-", "").replace(" ", "").trim();
    if (
      normalized.isEmpty() ||
      normalized.chars().anyMatch(character -> !Character.isDigit(character))
    ) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.value.invalid",
        "product identifier value is invalid",
        value
      );
    }
    return normalized;
  }

  private static String alphanumeric(String value) {
    String normalized = value.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.value.invalid",
        "product identifier value is invalid",
        value
      );
    }
    return normalized;
  }

  private static void requireLength(
    ProductIdentifierFamily family,
    String value,
    int expectedLength
  ) {
    if (value.length() != expectedLength) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.value.invalid",
        "product identifier value is invalid",
        family.apiValue(),
        expectedLength
      );
    }
  }

  private static void requireMod10(ProductIdentifierFamily family, String value) {
    if (!validMod10(value)) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.checksum.invalid",
        "product identifier checksum is invalid",
        family.apiValue()
      );
    }
  }

  private static boolean validMod10(String value) {
    int sum = 0;
    int weight = 3;
    for (int index = value.length() - 2; index >= 0; index--) {
      sum += Character.digit(value.charAt(index), 10) * weight;
      weight = weight == 3 ? 1 : 3;
    }
    int checkDigit = (10 - (sum % 10)) % 10;
    return checkDigit == Character.digit(value.charAt(value.length() - 1), 10);
  }

  private static void requireIsbn10(String value) {
    if (value.length() != 10) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.value.invalid",
        "product identifier value is invalid",
        ProductIdentifierFamily.ISBN.apiValue(),
        10
      );
    }
    int sum = 0;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      int digit;
      if (index == 9 && character == 'X') {
        digit = 10;
      } else if (Character.isDigit(character)) {
        digit = Character.digit(character, 10);
      } else {
        throw new CatalogueException(
          "catalogue.api.product.identifier.value.invalid",
          "product identifier value is invalid",
          value
        );
      }
      sum += (10 - index) * digit;
    }
    if (sum % 11 != 0) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.checksum.invalid",
        "product identifier checksum is invalid",
        ProductIdentifierFamily.ISBN.apiValue()
      );
    }
  }

  private static String toIsbn13(String isbn10) {
    String base = "978" + isbn10.substring(0, 9);
    return base + computeMod10CheckDigit(base);
  }

  private static int computeMod10CheckDigit(String valueWithoutCheckDigit) {
    int sum = 0;
    for (int index = 0; index < valueWithoutCheckDigit.length(); index++) {
      int weight = index % 2 == 0 ? 1 : 3;
      sum += Character.digit(valueWithoutCheckDigit.charAt(index), 10) * weight;
    }
    return (10 - (sum % 10)) % 10;
  }

  public static List<CatalogueProductIdentifier> normalizeAll(
    List<CatalogueProductIdentifier> identifiers
  ) {
    if (identifiers == null || identifiers.isEmpty()) {
      return List.of();
    }
    List<CatalogueProductIdentifier> normalized = new ArrayList<>(identifiers.size());
    for (CatalogueProductIdentifier identifier : identifiers) {
      normalized.add(normalize(identifier.family(), identifier.value(), identifier.primary()));
    }
    return List.copyOf(normalized);
  }
}
