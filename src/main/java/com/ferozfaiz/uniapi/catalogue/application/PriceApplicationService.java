package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.command.RecordCurrentPriceCommand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CataloguePriceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PriceApplicationService {

  private final CataloguePriceRepository prices;
  private final Counter recordedPrices;

  public PriceApplicationService(CataloguePriceRepository prices, MeterRegistry meterRegistry) {
    this.prices = prices;
    this.recordedPrices = Counter.builder("catalogue.price.recorded")
      .description("Catalogue current price recording outcomes")
      .register(meterRegistry);
  }

  public CataloguePrice record(RecordCurrentPriceCommand command) {
    CataloguePrice price = new CataloguePrice(
      null,
      command.merchantProductOfferId(),
      null,
      null,
      command.marketplace(),
      command.currency(),
      command.amount(),
      command.availability(),
      command.source(),
      command.capturedAt(),
      null
    );
    CataloguePrice saved = prices.recordCurrentPrice(price);
    recordedPrices.increment();
    return saved;
  }
}
