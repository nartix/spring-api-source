package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresCatalogueSlugWriteLockIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueSlugWriteLock slugWriteLock;

  @Autowired
  private TransactionTemplate transactions;

  @Test
  void sameResourceAndSlugLockWaitsUntilOwningTransactionEnds() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try {
      var first = executor.submit(() ->
        transactions.executeWithoutResult(status -> {
          slugWriteLock.lock(ResourceType.PRODUCT, "shared-slug");
          locked.countDown();
          await(release);
        })
      );

      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

      var second = executor.submit(() ->
        transactions.executeWithoutResult(status ->
          slugWriteLock.lock(ResourceType.PRODUCT, "shared-slug")
        )
      );

      Thread.sleep(Duration.ofMillis(200));
      assertThat(second).isNotDone();

      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
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
