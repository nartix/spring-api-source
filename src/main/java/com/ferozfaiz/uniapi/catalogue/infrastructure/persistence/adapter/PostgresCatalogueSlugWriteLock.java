package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class PostgresCatalogueSlugWriteLock implements CatalogueSlugWriteLock {

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresCatalogueSlugWriteLock(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void lock(ResourceType resourceType, String slug) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("catalogue slug lock requires an active transaction");
    }

    // Advisory locks serialize same-resource slug writes without adding schema objects.
    jdbc.query(
      "SELECT pg_advisory_xact_lock(:lockKey)",
      Map.of("lockKey", lockKey(resourceType, slug)),
      resultSet -> null
    );
  }

  static long lockKey(ResourceType resourceType, String slug) {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(slug, "slug");
    String material =
      "catalogue:slug:" + resourceType.name() + ":" + slug.toLowerCase(Locale.ROOT).trim();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        material.getBytes(StandardCharsets.UTF_8)
      );
      return ByteBuffer.wrap(digest).getLong();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }
}
