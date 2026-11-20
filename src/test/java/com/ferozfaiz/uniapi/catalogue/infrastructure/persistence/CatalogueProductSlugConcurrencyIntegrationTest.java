package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogueProductSlugConcurrencyIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private ProductApplicationService products;

  @Autowired
  private TransactionTemplate transactions;

  @Test
  void concurrentProductCreatesForSameSlugDoNotBothSucceed() throws Exception {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    List<Throwable> results = runRace(
      () -> products.create(productCommand(null, brandId, manufacturerId)),
      () -> products.create(productCommand(null, brandId, manufacturerId))
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(countRows("catalogue_product")).isEqualTo(1L);
  }

  @Test
  void concurrentProductUpdatesForSameSlugDoNotBothSucceed() throws Exception {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    Long firstProductId = insertProduct(
      "Samsung 980 Pro 1TB",
      "samsung-980-pro-1tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );
    Long secondProductId = insertProduct(
      "Samsung 970 Evo Plus 1TB",
      "samsung-970-evo-plus-1tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );

    List<Throwable> results = runRace(
      () -> products.update(productCommand(firstProductId, brandId, manufacturerId)),
      () -> products.update(productCommand(secondProductId, brandId, manufacturerId))
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_product WHERE slug = ?",
        Long.class,
        "samsung-990-pro-2tb"
      )
    ).isEqualTo(1L);
  }

  private List<Throwable> runRace(Runnable firstOperation, Runnable secondOperation)
    throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> runConcurrently(ready, start, firstOperation));
      var second = executor.submit(() -> runConcurrently(ready, start, secondOperation));

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      return Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private Throwable runConcurrently(
    CountDownLatch ready,
    CountDownLatch start,
    Runnable operation
  ) {
    try {
      transactions.executeWithoutResult(status -> {
        ready.countDown();
        await(start);
        operation.run();
      });
      return null;
    } catch (Throwable exception) {
      return exception;
    }
  }

  private static UpsertProductCommand productCommand(Long id, Long brandId, Long manufacturerId) {
    return new UpsertProductCommand(
      id,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      null,
      null,
      true
    );
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
