package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogueReferenceDataSlugConcurrencyIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueReferenceDataApplicationService referenceData;

  @Autowired
  private TransactionTemplate transactions;

  @Test
  void concurrentBrandCreatesForSameSlugDoNotBothSucceed() throws Exception {
    var results = runRace(() ->
      referenceData.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true))
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(countRows("catalogue_brand")).isEqualTo(1L);
  }

  @Test
  void concurrentBrandUpdatesForSameSlugDoNotBothSucceed() throws Exception {
    Long firstBrandId = insertBrand("Samsung", "samsung");
    Long secondBrandId = insertBrand("Sabrent", "sabrent");

    var results = runRace(
      () ->
        referenceData.createOrUpdateBrand(
          new UpsertBrandCommand(firstBrandId, "Shared Brand", "shared-brand", true)
        ),
      () ->
        referenceData.createOrUpdateBrand(
          new UpsertBrandCommand(secondBrandId, "Shared Brand", "shared-brand", true)
        )
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_brand WHERE slug = ?",
        Long.class,
        "shared-brand"
      )
    ).isEqualTo(1L);
  }

  @Test
  void concurrentManufacturerCreatesForSameSlugDoNotBothSucceed() throws Exception {
    var results = runRace(() ->
      referenceData.createOrUpdateManufacturer(
        new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
      )
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(countRows("catalogue_manufacturer")).isEqualTo(1L);
  }

  @Test
  void concurrentManufacturerUpdatesForSameSlugDoNotBothSucceed() throws Exception {
    Long firstManufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    Long secondManufacturerId = insertManufacturer("Micron", "micron");

    var results = runRace(
      () ->
        referenceData.createOrUpdateManufacturer(
          new UpsertManufacturerCommand(
            firstManufacturerId,
            "Shared Manufacturer",
            "shared-manufacturer",
            true
          )
        ),
      () ->
        referenceData.createOrUpdateManufacturer(
          new UpsertManufacturerCommand(
            secondManufacturerId,
            "Shared Manufacturer",
            "shared-manufacturer",
            true
          )
        )
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_manufacturer WHERE slug = ?",
        Long.class,
        "shared-manufacturer"
      )
    ).isEqualTo(1L);
  }

  private List<Throwable> runRace(Runnable operation) throws Exception {
    return runRace(operation, operation);
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

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
