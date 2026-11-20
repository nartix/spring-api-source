package com.ferozfaiz.uniapi.catalogue.application.command;

public record UpsertBrandCommand(Long id, String name, String slug, boolean active) {}
