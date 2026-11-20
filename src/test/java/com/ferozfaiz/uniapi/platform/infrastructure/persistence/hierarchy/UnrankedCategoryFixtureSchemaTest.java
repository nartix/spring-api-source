package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class UnrankedCategoryFixtureSchemaTest {

  @Autowired
  private JdbcTemplate jdbc;

  private UnrankedCategoryFixtureSchema schema;

  @BeforeEach
  void setUp() {
    schema = new UnrankedCategoryFixtureSchema(jdbc);
    schema.recreate();
  }

  @Test
  void createsFixtureWithoutSortRankColumn() {
    assertThat(schema.hasColumn("sort_rank")).isFalse();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
