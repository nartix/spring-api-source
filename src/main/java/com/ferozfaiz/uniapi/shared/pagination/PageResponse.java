package com.ferozfaiz.uniapi.shared.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(description = "Paged API response")
public record PageResponse<T>(
  @Schema(description = "Records on the current page") List<T> data,
  @Schema(description = "Page metadata") PageMetadata page,
  @Schema(description = "Applied sort orders") List<SortResponse> sort,
  @Schema(description = "Pagination navigation links") PageLinks links
) {
  public PageResponse {
    data = List.copyOf(data);
    sort = List.copyOf(sort);
  }

  public static <T> PageResponse<T> from(PageResult<T> page, PageLinks links) {
    return new PageResponse<>(
      page.content(),
      PageMetadata.from(page),
      SortResponse.fromAll(page.sort()),
      links
    );
  }

  public static <S, T> PageResponse<T> from(
    PageResult<S> page,
    Function<S, T> mapper,
    PageLinks links
  ) {
    return from(page.map(mapper), links);
  }
}
