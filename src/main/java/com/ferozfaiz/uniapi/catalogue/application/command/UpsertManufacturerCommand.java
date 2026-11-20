package com.ferozfaiz.uniapi.catalogue.application.command;

public record UpsertManufacturerCommand(Long id, String name, String slug, boolean active) {}
