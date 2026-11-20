package com.ferozfaiz.uniapi.catalogue.domain.model;

public final class CatalogueEnums {

  private CatalogueEnums() {}

  public enum ProductFamily {
    STORAGE,
    CPU,
    GPU,
  }

  public enum ProductIdentifierFamily {
    UPC("UPC"),
    EAN("EAN"),
    JAN("JAN"),
    ISBN("ISBN"),
    GTIN_14("GTIN-14");

    private final String apiValue;

    ProductIdentifierFamily(String apiValue) {
      this.apiValue = apiValue;
    }

    public String apiValue() {
      return apiValue;
    }

    public static ProductIdentifierFamily fromApiValue(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String normalized = value.trim().replace('_', '-').toUpperCase(java.util.Locale.ROOT);
      for (ProductIdentifierFamily family : values()) {
        if (family.apiValue.equals(normalized) || family.name().equals(normalized)) {
          return family;
        }
      }
      return null;
    }
  }

  public enum Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    PREORDER,
    BACKORDER,
    UNKNOWN,
  }

  public enum Marketplace {
    CA,
    US,
  }

  public enum Currency {
    CAD,
    USD,
  }

  public enum PriceSource {
    MANUAL,
    MERCHANT_FEED,
    API,
    SCRAPE,
  }
}
