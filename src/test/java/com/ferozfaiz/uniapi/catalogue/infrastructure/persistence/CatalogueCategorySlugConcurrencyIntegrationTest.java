package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogueCategorySlugConcurrencyIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueCategoryApplicationService categories;

  @Autowired
  private TransactionTemplate transactions;

  @Test
  void concurrentCategoryCreatesForSameSlugDoNotBothSucceed() throws Exception {
    List<Throwable> results = runRace(
      () ->
        categories.create(
          new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
        ),
      () ->
        categories.create(
          new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
        )
    );

    assertThat(results.stream().filter(result -> result == null)).hasSize(1);
    assertThat(results.stream().filter(DuplicateCatalogueSlugException.class::isInstance)).hasSize(
      1
    );
    assertThat(countRows("catalogue_category")).isEqualTo(1L);
  }

  @Test
  void concurrentCategoryUpdatesForSameSlugDoNotBothSucceed() throws Exception {
    Long firstCategoryId = insertCategory("Storage", "storage", null);
    insertClosure(firstCategoryId, firstCategoryId, 0);
    Long secondCategoryId = insertCategory("Processors", "processors", null);
    insertClosure(secondCategoryId, secondCategoryId, 0);

    List<Throwable> results = runRace(
      () ->
        categories.update(
          new UpsertCatalogueCategoryCommand(
            firstCategoryId,
            null,
            "Components",
            "components",
            true
          )
        ),
      () ->
        categories.update(
          new UpsertCatalogueCategoryCommand(
            secondCategoryId,
            null,
            "Components",
            "components",
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
        "SELECT COUNT(*) FROM catalogue_category WHERE slug = ?",
        Long.class,
        "components"
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

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
