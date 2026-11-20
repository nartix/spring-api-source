package com.ferozfaiz.uniapi.catalogue.application.command;

public record UpsertMerchantStoreCommand(Long id, String name, String slug, boolean active) {}
