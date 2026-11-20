package com.ferozfaiz.uniapi.shared.pagination;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

public final class PageLinksAssembler {

  private PageLinksAssembler() {}

  public static PageLinks fromCurrentRequest(PageResult<?> page) {
    Link self = Link.of(pageHref(page, page.page()), IanaLinkRelations.SELF);
    Link next = page.last()
      ? null
      : Link.of(pageHref(page, page.page() + 1), IanaLinkRelations.NEXT);
    Link previous = page.first()
      ? null
      : Link.of(pageHref(page, page.page() - 1), IanaLinkRelations.PREV);

    return new PageLinks(
      self.getHref(),
      next == null ? null : next.getHref(),
      previous == null ? null : previous.getHref()
    );
  }

  private static String pageHref(PageResult<?> page, int pageNumber) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(
      ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri().getRawPath()
    )
      .replaceQueryParam("page", pageNumber)
      .replaceQueryParam("size", page.size())
      .replaceQueryParam("sort");

    copyFilterParameters(builder);

    for (SortOrder order : page.sort().orders()) {
      builder.queryParam(
        "sort",
        order.property() + "," + order.direction().name().toLowerCase(Locale.ROOT)
      );
    }

    return builder.build().toUriString();
  }

  private static void copyFilterParameters(UriComponentsBuilder builder) {
    ServletRequestAttributes attributes =
      (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    request.getParameterMap().forEach((name, values) -> {
      if ("page".equals(name) || "size".equals(name) || "sort".equals(name)) {
        return;
      }
      for (String value : values) {
        builder.queryParam(name, value);
      }
    });
  }
}
