package com.ferozfaiz.uniapi.platform.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LiquibaseMigrationChainTest {

  private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
  private static final String LAST_CHANGESET = "feroz:20260531-001-create-auth-session-audit-table";
  private static final String PRE_AUDIT_TAG = "before-auth-session-audit";

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
    DockerImageName.parse("postgres:latest")
  );

  @Test
  void fullChainAppliesFromEmptyDatabaseAndLatestMigrationRollsBackAndReapplies() throws Exception {
    Contexts contexts = new Contexts("test");
    LabelExpression labels = new LabelExpression();

    try (
      Connection connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword()
      );
      ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
      Liquibase liquibase = new Liquibase(
        CHANGELOG,
        resources,
        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
          new JdbcConnection(connection)
        )
      )
    ) {
      int changesetCount = liquibase.listUnrunChangeSets(contexts, labels).size();
      assertThat(changesetCount).isGreaterThan(1);

      liquibase.update(changesetCount - 1, contexts, labels);
      liquibase.tag(PRE_AUDIT_TAG);

      assertThat(tableExists(connection, "auth_session_audit")).isFalse();
      assertThat(liquibase.listUnrunChangeSets(contexts, labels))
        .extracting(changeSet -> changeSet.getAuthor() + ":" + changeSet.getId())
        .containsExactly(LAST_CHANGESET);

      liquibase.update(contexts, labels);

      assertThat(liquibase.listUnrunChangeSets(contexts, labels)).isEmpty();
      assertThat(tableExists(connection, "auth_session_audit")).isTrue();

      liquibase.rollback(PRE_AUDIT_TAG, contexts, labels);

      assertThat(tableExists(connection, "auth_session_audit")).isFalse();
      assertThat(liquibase.listUnrunChangeSets(contexts, labels))
        .extracting(changeSet -> changeSet.getAuthor() + ":" + changeSet.getId())
        .containsExactly(LAST_CHANGESET);

      liquibase.update(contexts, labels);

      assertThat(tableExists(connection, "auth_session_audit")).isTrue();
      assertThat(liquibase.listUnrunChangeSets(contexts, labels)).isEmpty();
    }
  }

  private static boolean tableExists(Connection connection, String table) throws Exception {
    try (
      var statement = connection.prepareStatement("SELECT to_regclass('public.' || ?) IS NOT NULL")
    ) {
      statement.setString(1, table);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getBoolean(1);
      }
    }
  }
}
