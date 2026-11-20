package com.ferozfaiz.uniapi.catalogue.application.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.catalogue.list")
public class CatalogueListPolicyProperties {

  private int defaultPageSize = 24;
  private int maxPageSize = 100;
  private int maxSortOrders = 5;
  private String defaultMarketplace = "CA";
  private int maxSearchLength = 120;

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  public void setDefaultPageSize(int defaultPageSize) {
    requirePositive(defaultPageSize, "defaultPageSize");
    this.defaultPageSize = defaultPageSize;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(int maxPageSize) {
    requirePositive(maxPageSize, "maxPageSize");
    this.maxPageSize = maxPageSize;
  }

  public int getMaxSortOrders() {
    return maxSortOrders;
  }

  public void setMaxSortOrders(int maxSortOrders) {
    requirePositive(maxSortOrders, "maxSortOrders");
    this.maxSortOrders = maxSortOrders;
  }

  public String getDefaultMarketplace() {
    return defaultMarketplace;
  }

  public void setDefaultMarketplace(String defaultMarketplace) {
    if (defaultMarketplace == null || defaultMarketplace.isBlank()) {
      throw new IllegalArgumentException("defaultMarketplace is required");
    }
    this.defaultMarketplace = defaultMarketplace.trim().toUpperCase(java.util.Locale.ROOT);
  }

  public int getMaxSearchLength() {
    return maxSearchLength;
  }

  public void setMaxSearchLength(int maxSearchLength) {
    requirePositive(maxSearchLength, "maxSearchLength");
    this.maxSearchLength = maxSearchLength;
  }

  private static void requirePositive(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be at least 1");
    }
  }
}
