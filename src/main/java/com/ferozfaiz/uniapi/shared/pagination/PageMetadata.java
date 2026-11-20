package com.ferozfaiz.uniapi.shared.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Page metadata")
public record PageMetadata(
  @Schema(description = "Zero-based page index", example = "0") int number,
  @Schema(description = "Requested page size", example = "20") int size,
  @Schema(description = "Total matching records", example = "42") long totalElements,
  @Schema(description = "Total number of pages", example = "3") int totalPages,
  @Schema(description = "Number of records on the current page", example = "2")
  int numberOfElements,
  @Schema(description = "Whether this is the first page", example = "true") boolean first,
  @Schema(description = "Whether this is the last page", example = "false") boolean last
) {
  public static PageMetadata from(PageResult<?> page) {
    return new PageMetadata(
      page.page(),
      page.size(),
      page.totalElements(),
      page.totalPages(),
      page.content().size(),
      page.first(),
      page.last()
    );
  }
}
