package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.validation.ProductCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateProductIdentifierException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import com.ferozfaiz.uniapi.catalogue.domain.validation.ProductIdentifierRules;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

  @Mock
  private CatalogueProductRepository products;

  @Mock
  private CatalogueReferenceDataRepository referenceData;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void createsUpdatesLifecycleAndCategoryAssignmentsThroughInternalService() {
    ProductApplicationService service = service();
    CatalogueProduct saved = product(1L, true);
    when(products.save(any())).thenReturn(saved, product(1L, false));
    when(products.findById(1L)).thenReturn(Optional.of(saved));
    when(products.categoryExists(100L)).thenReturn(true);
    when(referenceData.existsBrandId(10L)).thenReturn(true);
    when(referenceData.existsManufacturerId(20L)).thenReturn(true);

    CatalogueProduct created = service.create(
      new UpsertProductCommand(
        null,
        "Samsung 990 Pro 2TB",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        10L,
        20L,
        "https://example.test/990-pro.png",
        "Fast NVMe storage",
        true
      )
    );
    CatalogueProduct deactivated = service.deactivate(1L);
    service.assignCategory(1L, 100L);
    service.removeCategory(1L, 100L);

    assertThat(created).isEqualTo(saved);
    assertThat(deactivated.active()).isFalse();
    verify(products).assignCategory(1L, 100L);
    verify(products).removeCategory(1L, 100L);
  }

  @Test
  void rejectsDuplicateProductSlugBeforePersistence() {
    ProductApplicationService service = service();
    when(products.existsSlug("samsung-990-pro-2tb", null)).thenReturn(true);

    assertThatThrownBy(() ->
      service.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    ).isInstanceOf(DuplicateCatalogueSlugException.class);

    verify(products, never()).save(any());
    verify(referenceData, never()).existsBrandId(10L);
    verify(referenceData, never()).existsManufacturerId(20L);
  }

  @Test
  void rejectsMissingBrandBeforePersistence() {
    ProductApplicationService service = service();
    when(referenceData.existsBrandId(10L)).thenReturn(false);

    assertThatThrownBy(() ->
      service.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product brand was not found");

    verify(referenceData, never()).existsManufacturerId(20L);
    verify(products, never()).save(any());
  }

  @Test
  void rejectsMissingManufacturerBeforePersistence() {
    ProductApplicationService service = service();
    when(referenceData.existsBrandId(10L)).thenReturn(true);
    when(referenceData.existsManufacturerId(20L)).thenReturn(false);

    assertThatThrownBy(() ->
      service.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product manufacturer was not found");

    verify(products, never()).save(any());
  }

  @Test
  void rejectsMissingReferencesOnUpdateAfterProductExists() {
    ProductApplicationService service = service();
    when(products.findById(1L)).thenReturn(Optional.of(product(1L, true)));
    when(referenceData.existsBrandId(10L)).thenReturn(true);
    when(referenceData.existsManufacturerId(20L)).thenReturn(false);

    assertThatThrownBy(() ->
      service.update(
        new UpsertProductCommand(
          1L,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product manufacturer was not found");

    verify(products, never()).save(any());
  }

  @Test
  void rejectsDeleteWhenDependentMerchantOrPriceDataExists() {
    ProductApplicationService service = service();
    when(products.findById(1L)).thenReturn(Optional.of(product(1L, true)));
    when(products.hasMerchantOffers(1L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(1L))
      .isInstanceOf(CatalogueConflictException.class)
      .hasMessage("product cannot be deleted because dependent catalogue data exists");

    verify(products, never()).deleteById(1L);
  }

  @Test
  void deletesSafeProductsAndLogsStableLifecycleEvent() {
    ProductApplicationService service = service();
    ListAppender<ILoggingEvent> logs = captureLogs(ProductApplicationService.class);
    when(products.findById(1L)).thenReturn(Optional.of(product(1L, true)));

    service.delete(1L);

    verify(products).deleteById(1L);
    assertThat(logs.list).anySatisfy(event ->
      assertThat(event.getFormattedMessage()).isEqualTo("catalogue product deleted")
    );
  }

  @Test
  void repeatedDeleteAfterAbsenceReturnsNotFoundWithoutMutation() {
    ProductApplicationService service = service();
    when(products.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(CatalogueNotFoundException.class);

    verify(products, never()).deleteById(1L);
  }

  @Test
  void lifecycleOperationsAreStateLevelIdempotent() {
    ProductApplicationService service = service();
    when(products.findById(1L)).thenReturn(
      Optional.of(product(1L, true)),
      Optional.of(product(1L, false))
    );
    when(products.save(any())).thenReturn(product(1L, true), product(1L, false));

    assertThat(service.activate(1L).active()).isTrue();
    assertThat(service.deactivate(1L).active()).isFalse();
  }

  @Test
  void validatesMissingCategoryBeforeAssignmentMutation() {
    ProductApplicationService service = service();
    when(products.findById(1L)).thenReturn(Optional.of(product(1L, true)));
    when(products.categoryExists(100L)).thenReturn(false);

    assertThatThrownBy(() -> service.assignCategory(1L, 100L)).isInstanceOf(
      CatalogueNotFoundException.class
    );

    verify(products, never()).assignCategory(1L, 100L);
  }

  @Test
  void rejectsMissingProductCommandFieldsBeforePersistence() {
    ProductApplicationService service = service();

    assertThatThrownBy(() ->
      service.create(
        new UpsertProductCommand(
          null,
          " ",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueException.class)
      .hasMessage("product title is required");
  }

  @Test
  void rejectsInvalidProductSlugBeforePersistence() {
    ProductApplicationService service = service();

    assertThatThrownBy(() ->
      service.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "Bad Slug",
          ProductFamily.STORAGE,
          10L,
          20L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueException.class)
      .hasMessage("slug may contain lowercase letters, numbers, and hyphens");

    verify(products, never()).save(any());
  }

  @Test
  void persistsNormalizedProductIdentifiers() {
    ProductApplicationService service = service();
    CatalogueProductIdentifier identifier = ProductIdentifierRules.normalize(
      "UPC",
      "036000291452",
      true
    );
    when(referenceData.existsBrandId(10L)).thenReturn(true);
    when(referenceData.existsManufacturerId(20L)).thenReturn(true);
    when(products.save(any())).thenReturn(product(1L, true));

    service.create(productCommandWithIdentifiers(List.of(identifier)));

    verify(products).save(
      org.mockito.ArgumentMatchers.argThat(
        product ->
          product.identifiers().size() == 1 &&
          "00036000291452".equals(product.identifiers().getFirst().canonicalGtin14()) &&
          product.identifiers().getFirst().primary()
      )
    );
  }

  @Test
  void rejectsDuplicateIdentifiersBeforePersistence() {
    ProductApplicationService service = service();

    assertThatThrownBy(() ->
      service.create(
        productCommandWithIdentifiers(
          List.of(
            ProductIdentifierRules.normalize("UPC", "036000291452", true),
            ProductIdentifierRules.normalize("GTIN-14", "00036000291452", false)
          )
        )
      )
    )
      .isInstanceOf(CatalogueException.class)
      .hasMessage("product identifier is duplicated");

    verify(products, never()).save(any());
  }

  @Test
  void rejectsIdentifierAlreadyOwnedByAnotherProduct() {
    ProductApplicationService service = service();
    when(products.existsIdentifier("00036000291452", null)).thenReturn(true);

    assertThatThrownBy(() ->
      service.create(
        productCommandWithIdentifiers(
          List.of(ProductIdentifierRules.normalize("UPC", "036000291452", true))
        )
      )
    )
      .isInstanceOf(DuplicateProductIdentifierException.class)
      .hasMessage("product identifier already exists");

    verify(referenceData, never()).existsBrandId(10L);
    verify(products, never()).save(any());
  }

  private ProductApplicationService service() {
    return new ProductApplicationService(
      products,
      referenceData,
      new ProductCommandValidator(),
      slugWriteLock
    );
  }

  private static CatalogueProduct product(Long id, boolean active) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueProduct(
      id,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      new CatalogueBrand(10L, "Samsung", "samsung", true, now, now),
      new CatalogueManufacturer(20L, "Samsung Electronics", "samsung-electronics", true, now, now),
      "https://example.test/990-pro.png",
      "Fast NVMe storage",
      active,
      now,
      now
    );
  }

  private static UpsertProductCommand productCommandWithIdentifiers(
    List<CatalogueProductIdentifier> identifiers
  ) {
    return new UpsertProductCommand(
      null,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      10L,
      20L,
      null,
      null,
      true,
      identifiers
    );
  }

  private static ListAppender<ILoggingEvent> captureLogs(Class<?> type) {
    Logger logger = (Logger) LoggerFactory.getLogger(type);
    logger.setLevel(Level.INFO);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }
}
