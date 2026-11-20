package com.ferozfaiz.uniapi.catalogue.application.policy;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CatalogueListPolicy {

  private static final Set<String> STORAGE_FILTERS = Set.of(
    "storageType",
    "capacityGbMin",
    "capacityGbMax",
    "formFactor",
    "readSpeedMbpsMin",
    "writeSpeedMbpsMin",
    "rpmMin",
    "cacheMbMin"
  );
  private static final Set<String> CPU_FILTERS = Set.of(
    "socket",
    "coreCountMin",
    "threadCountMin",
    "baseClockGhzMin",
    "boostClockGhzMin",
    "architecture",
    "integratedGraphics"
  );
  private static final Set<String> GPU_FILTERS = Set.of(
    "gpuChipset",
    "vramGbMin",
    "memoryType",
    "boostClockMhzMin",
    "lengthMmMax"
  );
  private static final Set<String> CPU_OR_GPU_FILTERS = Set.of("tdpWattsMax");

  private final CatalogueListPolicyProperties properties;

  public CatalogueListPolicy(CatalogueListPolicyProperties properties) {
    this.properties = properties;
  }

  public int defaultPageSize() {
    return properties.getDefaultPageSize();
  }

  public String defaultMarketplace() {
    return properties.getDefaultMarketplace();
  }

  public void enforce(
    PageCriteria page,
    SortCriteria sort,
    String search,
    ProductFamily family,
    Map<String, ?> filters
  ) {
    if (page.size() > properties.getMaxPageSize()) {
      throw invalid(
        "PAGE_SIZE_TOO_LARGE",
        "catalogue.api.list.size.max",
        properties.getMaxPageSize()
      );
    }
    if (sort.orders().size() > properties.getMaxSortOrders()) {
      throw invalid(
        "INVALID_SORT_FIELD",
        "catalogue.api.list.sort.max",
        properties.getMaxSortOrders()
      );
    }
    if (search != null && search.length() > properties.getMaxSearchLength()) {
      throw invalid(
        "INVALID_FILTER_FIELD",
        "catalogue.api.list.search.size",
        properties.getMaxSearchLength()
      );
    }
    enforceFamilyFilters(family, filters);
  }

  private static void enforceFamilyFilters(ProductFamily family, Map<String, ?> filters) {
    for (Map.Entry<String, ?> entry : filters.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      String filter = entry.getKey();
      if (CPU_OR_GPU_FILTERS.contains(filter)) {
        if (family == ProductFamily.STORAGE || family == null) {
          throw invalid(
            "INCOMPATIBLE_FILTER_COMBINATION",
            "catalogue.api.list.filter.incompatible",
            filter,
            family == null ? "unspecified" : family
          );
        }
        continue;
      }
      if (
        family == ProductFamily.STORAGE &&
        (CPU_FILTERS.contains(filter) || GPU_FILTERS.contains(filter))
      ) {
        throw invalid(
          "INCOMPATIBLE_FILTER_COMBINATION",
          "catalogue.api.list.filter.incompatible",
          filter,
          family
        );
      }
      if (
        family == ProductFamily.CPU &&
        (STORAGE_FILTERS.contains(filter) || GPU_FILTERS.contains(filter))
      ) {
        throw invalid(
          "INCOMPATIBLE_FILTER_COMBINATION",
          "catalogue.api.list.filter.incompatible",
          filter,
          family
        );
      }
      if (
        family == ProductFamily.GPU &&
        (STORAGE_FILTERS.contains(filter) || CPU_FILTERS.contains(filter))
      ) {
        throw invalid(
          "INCOMPATIBLE_FILTER_COMBINATION",
          "catalogue.api.list.filter.incompatible",
          filter,
          family
        );
      }
      if (
        family == null &&
        (STORAGE_FILTERS.contains(filter) ||
          CPU_FILTERS.contains(filter) ||
          GPU_FILTERS.contains(filter))
      ) {
        throw invalid(
          "INCOMPATIBLE_FILTER_COMBINATION",
          "catalogue.api.list.filter.incompatible",
          filter,
          "unspecified"
        );
      }
    }
  }

  private static InvalidRequestException invalid(String code, String key, Object... args) {
    return new CatalogueInvalidRequestException(code, key, args);
  }

  public static class CatalogueInvalidRequestException extends InvalidRequestException {

    private final String code;

    CatalogueInvalidRequestException(String code, String key, Object... args) {
      super(key, key, args);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}
