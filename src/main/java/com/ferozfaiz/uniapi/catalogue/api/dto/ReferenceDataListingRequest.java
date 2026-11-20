package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Catalogue reference data listing filters, pagination, and sorting")
public record ReferenceDataListingRequest(
  @Min(value = 0, message = "{catalogue.api.list.page.min}") Integer page,
  @Min(value = 1, message = "{catalogue.api.list.size.min}") Integer size,
  List<String> sort,
  @Size(max = 120, message = "{catalogue.api.list.search.size}") String search,
  Boolean active
) {
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;
  private static final int MAX_SORT_ORDERS = 5;
  private static final List<String> DEFAULT_SORTS = List.of("name,asc");
  private static final List<String> ALLOWED_SORTS = List.of(
    "name",
    "slug",
    "createdAt",
    "updatedAt",
    "id"
  );

  public ListReferenceDataQuery toQuery() {
    PageCriteria pageCriteria = new PageCriteria(
      page == null ? 0 : page,
      size == null ? DEFAULT_SIZE : size
    );
    if (pageCriteria.size() > MAX_SIZE) {
      throw invalid("catalogue.api.list.size.max", MAX_SIZE);
    }
    SortCriteria sortCriteria = toSortCriteria();
    if (sortCriteria.orders().size() > MAX_SORT_ORDERS) {
      throw invalid("catalogue.api.list.sort.max", MAX_SORT_ORDERS);
    }
    return new ListReferenceDataQuery(pageCriteria, sortCriteria, blankToNull(search), active);
  }

  public SortCriteria toSortCriteria() {
    List<String> requestedSorts =
      sort == null || sort.isEmpty() ? DEFAULT_SORTS : normalizeSortValues(sort);
    List<SortOrder> orders = new ArrayList<>();
    for (String value : requestedSorts) {
      orders.add(parseSort(value));
    }
    if (orders.stream().noneMatch(order -> "id".equals(order.property()))) {
      orders.add(new SortOrder("id", SortDirection.ASC));
    }
    return new SortCriteria(orders);
  }

  private static List<String> normalizeSortValues(List<String> sortValues) {
    if (sortValues.stream().anyMatch(value -> value != null && value.contains(","))) {
      return sortValues;
    }
    if (sortValues.size() % 2 != 0 || !everySecondValueIsDirection(sortValues)) {
      return sortValues;
    }
    List<String> combined = new ArrayList<>(sortValues.size() / 2);
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
