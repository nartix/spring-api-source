package com.ferozfaiz.uniapi.catalogue.application.command;

public record UpsertMerchantOfferCommand(
  Long id,
  Long productId,
  Long merchantStoreId,
  String merchantSku,
  String merchantProductCode,
  String offerUrl,
  boolean active
) {}
