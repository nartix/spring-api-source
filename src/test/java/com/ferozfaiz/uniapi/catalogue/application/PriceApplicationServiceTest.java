package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.RecordCurrentPriceCommand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Availability;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Currency;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Marketplace;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.PriceSource;
import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CataloguePriceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceApplicationServiceTest {

  @Mock
  private CataloguePriceRepository prices;

  @Test
  void recordsCurrentPriceFromCommandAndIncrementsMetric() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    PriceApplicationService service = new PriceApplicationService(prices, meterRegistry);
    OffsetDateTime capturedAt = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    CataloguePrice saved = new CataloguePrice(
      1L,
      10L,
      20L,
      30L,
      Marketplace.CA,
      Currency.CAD,
      new BigDecimal("149.99"),
      Availability.IN_STOCK,
      PriceSource.MANUAL,
      capturedAt,
      capturedAt
    );
    when(
      prices.recordCurrentPrice(argThat(price -> price.merchantProductOfferId().equals(10L)))
    ).thenReturn(saved);

    CataloguePrice result = service.record(
      new RecordCurrentPriceCommand(
        10L,
        Marketplace.CA,
        Currency.CAD,
        new BigDecimal("149.99"),
        Availability.IN_STOCK,
        PriceSource.MANUAL,
        capturedAt
      )
    );

    assertThat(result).isEqualTo(saved);
    assertThat(meterRegistry.counter("catalogue.price.recorded").count()).isEqualTo(1.0);
    verify(prices).recordCurrentPrice(
      argThat(
        price ->
          price.merchantProductOfferId().equals(10L) &&
          price.marketplace() == Marketplace.CA &&
          price.currency() == Currency.CAD &&
          price.amount().compareTo(new BigDecimal("149.99")) == 0 &&
          price.availability() == Availability.IN_STOCK &&
          price.source() == PriceSource.MANUAL &&
          price.capturedAt().equals(capturedAt)
      )
    );
  }
}
