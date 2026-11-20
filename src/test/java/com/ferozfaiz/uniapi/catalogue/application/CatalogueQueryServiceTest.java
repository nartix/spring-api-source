package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicyProperties;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CatalogueQueryServiceTest {

  @Mock
  private CatalogueProductListingRepository products;

  @Test
  void rejectsPolicyViolationsBeforeRepositoryQuery() {
    CatalogueQueryService service = new CatalogueQueryService(
      products,
      new CatalogueListPolicy(new CatalogueListPolicyProperties()),
      new SimpleMeterRegistry()
    );

    assertThatThrownBy(() ->
      service.listProducts(
        new ListProductsQuery(
          new PageCriteria(0, 24),
          SortCriteria.of("title", SortDirection.ASC),
          null,
          ProductFamily.CPU,
          null,
          null,
          null,
          null,
          "CA",
          null,
          Map.of("capacityGbMin", 1000)
        )
      )
    ).isInstanceOf(InvalidRequestException.class);

    verify(products, never()).findActiveProducts(any());
  }

  @Test
  void readMethodsDeclareCatalogueReadPolicyAuthorization() throws Exception {
    assertPreAuthorize(
      "listProducts",
      new Class<?>[] { ListProductsQuery.class },
      "@catalogueReadAuthorizationPolicy.canReadProducts(authentication)"
    );
    assertPreAuthorize(
      "listProductsInCategory",
      new Class<?>[] { String.class, ListProductsQuery.class },
      "@catalogueReadAuthorizationPolicy.canReadCategoryProducts(authentication, #categorySlug)"
    );
    assertPreAuthorize(
      "productDetail",
      new Class<?>[] { Long.class, String.class, String.class, String.class },
      "@catalogueReadAuthorizationPolicy.canReadProduct(authentication, #id)"
    );
  }

  private static void assertPreAuthorize(
    String methodName,
    Class<?>[] parameterTypes,
    String expectedExpression
  ) throws NoSuchMethodException {
    Method method = CatalogueQueryService.class.getDeclaredMethod(methodName, parameterTypes);

    assertThat(method.getAnnotation(PreAuthorize.class))
      .extracting(PreAuthorize::value)
      .isEqualTo(expectedExpression);
  }
}
