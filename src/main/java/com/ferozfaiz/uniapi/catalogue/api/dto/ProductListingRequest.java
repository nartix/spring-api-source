package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Schema(description = "Catalogue product listing filters, pagination, and sorting")
public record ProductListingRequest(
  @Min(value = 0, message = "{catalogue.api.list.page.min}") Integer page,
  @Min(value = 1, message = "{catalogue.api.list.size.min}") Integer size,
  List<String> sort,
  @Size(max = 120, message = "{catalogue.api.list.search.size}") String search,
  ProductFamily family,
  String category,
  String brand,
  String manufacturer,
  String merchantStore,
  String marketplace,
  Boolean hasCurrentPrice,
  String storageType,
  Integer capacityGbMin,
  Integer capacityGbMax,
  String interfaceType,
  String formFactor,
  Integer readSpeedMbpsMin,
  Integer writeSpeedMbpsMin,
  Integer rpmMin,
  Integer cacheMbMin,
  String socket,
  Integer coreCountMin,
  Integer threadCountMin,
  BigDecimal baseClockGhzMin,
  BigDecimal boostClockGhzMin,
  String architecture,
  Boolean integratedGraphics,
  Integer tdpWattsMax,
  String gpuChipset,
  Integer vramGbMin,
  String memoryType,
  Integer boostClockMhzMin,
  Integer lengthMmMax
) {
  private static final List<String> DEFAULT_SORTS = List.of("title,asc");
  private static final List<String> ALLOWED_SORTS = List.of(
    "title",
    "brand",
    "manufacturer",
    "currentPrice",
    "capacityGb",
    "storageType",
    "interfaceType",
    "readSpeedMbps",
    "writeSpeedMbps",
    "coreCount",
    "threadCount",
    "boostClockGhz",
    "vramGb",
    "createdAt",
    "updatedAt",
    "id"
  );

  public ListProductsQuery toQuery(
    int defaultSize,
    String defaultMarketplace,
    ProductFamily fixedFamily
  ) {
    ProductFamily effectiveFamily = fixedFamily == null ? family : fixedFamily;
    return new ListProductsQuery(
      new PageCriteria(page == null ? 0 : page, size == null ? defaultSize : size),
      toSortCriteria(),
      blankToNull(search),
      effectiveFamily,
      blankToNull(category),
      blankToNull(brand),
      blankToNull(manufacturer),
      blankToNull(merchantStore),
      blankToNull(marketplace) == null
        ? defaultMarketplace
        : marketplace.trim().toUpperCase(Locale.ROOT),
      hasCurrentPrice,
      familyFilters()
    );
  }

  public SortCriteria toSortCriteria() {
    List<String> requestedSorts =
      sort == null || sort.isEmpty() ? DEFAULT_SORTS : normalizeSortValues(sort);
    List<SortOrder> orders = new java.util.ArrayList<>();
    for (String value : requestedSorts) {
      orders.add(parseSort(value));
    }
    if (orders.stream().noneMatch(order -> "id".equals(order.property()))) {
      orders.add(new SortOrder("id", SortDirection.ASC));
    }
    return new SortCriteria(orders);
  }

  // Spring's @ModelAttribute binder splits a single ?sort=field,direction query param into two
  // list entries ["field", "direction"]. Recombine such pairs so the public field,direction
  // contract works for both single and repeated sort params.
  private static List<String> normalizeSortValues(List<String> sortValues) {
    if (sortValues.stream().anyMatch(value -> value != null && value.contains(","))) {
      return sortValues;
    }
    if (sortValues.size() % 2 != 0 || !everySecondValueIsDirection(sortValues)) {
      return sortValues;
    }
    List<String> combined = new java.util.ArrayList<>(sortValues.size() / 2);
    for (int index = 0; index < sortValues.size(); index += 2) {
      combined.add(sortValues.get(index) + "," + sortValues.get(index + 1));
    }
    return combined;
  }

  private static boolean everySecondValueIsDirection(List<String> values) {
    for (int index = 1; index < values.size(); index += 2) {
      String value = values.get(index);
      if (value == null) {
        return false;
      }
      String trimmed = value.trim();
      if (!"asc".equalsIgnoreCase(trimmed) && !"desc".equalsIgnoreCase(trimmed)) {
        return false;
      }
    }
    return true;
  }

  public Map<String, Object> familyFilters() {
    Map<String, Object> filters = new java.util.LinkedHashMap<>();
    filters.put("storageType", blankToNull(storageType));
    filters.put("capacityGbMin", capacityGbMin);
    filters.put("capacityGbMax", capacityGbMax);
    filters.put("interfaceType", blankToNull(interfaceType));
    filters.put("formFactor", blankToNull(formFactor));
    filters.put("readSpeedMbpsMin", readSpeedMbpsMin);
    filters.put("writeSpeedMbpsMin", writeSpeedMbpsMin);
    filters.put("rpmMin", rpmMin);
    filters.put("cacheMbMin", cacheMbMin);
    filters.put("socket", blankToNull(socket));
    filters.put("coreCountMin", coreCountMin);
    filters.put("threadCountMin", threadCountMin);
    filters.put("baseClockGhzMin", baseClockGhzMin);
    filters.put("boostClockGhzMin", boostClockGhzMin);
    filters.put("architecture", blankToNull(architecture));
    filters.put("integratedGraphics", integratedGraphics);
    filters.put("tdpWattsMax", tdpWattsMax);
    filters.put("gpuChipset", blankToNull(gpuChipset));
    filters.put("vramGbMin", vramGbMin);
    filters.put("memoryType", blankToNull(memoryType));
    filters.put("boostClockMhzMin", boostClockMhzMin);
    filters.put("lengthMmMax", lengthMmMax);
    filters.entrySet().removeIf(entry -> entry.getValue() == null);
    return filters;
  }

  private static SortOrder parseSort(String value) {
    if (value == null || value.isBlank()) {
      throw invalid("catalogue.api.list.sort.format");
    }
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw invalid("catalogue.api.list.sort.format");
    }
    String property = parts[0].trim();
    if (!ALLOWED_SORTS.contains(property)) {
      throw invalid("catalogue.api.list.sort.allowed", String.join(", ", ALLOWED_SORTS));
    }
    String direction = parts[1].trim();
    if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
      throw invalid("catalogue.api.list.sort.direction");
    }
    return new SortOrder(property, SortDirection.from(direction));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static InvalidRequestException invalid(String key, Object... args) {
    return new InvalidRequestException(key, key, args);
  }
}
