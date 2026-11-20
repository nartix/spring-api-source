package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminCategoryJdbcProviderPropertiesTest {

  @Test
  void defaultsDescriptorBackedJdbcProvidersToDisabled() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();

    assertThat(properties.enabledSystemKeys()).isEmpty();
    assertThat(properties.getEnabledSystems()).isEmpty();
    assertThat(properties.getSystems()).isEmpty();
  }

  @Test
  void normalizesEnabledKeysAndRejectsDuplicates() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of(" data ", "blog"));

    assertThat(properties.enabledSystemKeys()).containsExactly("data", "blog");

    properties.setEnabledSystems(List.of("data", " data "));

    assertThatThrownBy(properties::enabledSystemKeys)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Duplicate descriptor-backed category system");
  }

  @Test
  void rejectsBlankEnabledKeys() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of(" "));

    assertThatThrownBy(properties::enabledSystemKeys)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("enabled system");
  }

  @Test
  void createsDescriptorForSupportedStrategies() {
    AdminCategoryJdbcProviderProperties.SystemProperties system =
      new AdminCategoryJdbcProviderProperties.SystemProperties();

    AdminCategoryNodeTableDescriptor descriptor = AdminCategoryNodeTableDescriptor.from(
      descriptor("data", "data_category", "data_category_closure", true),
      system
    );

    assertThat(descriptor.systemKey()).isEqualTo("data");
    assertThat(descriptor.nodeTable()).isEqualTo("data_category");
    assertThat(descriptor.idColumn()).isEqualTo("id");
    assertThat(descriptor.parentIdColumn()).isEqualTo("parent_id");
    assertThat(descriptor.nameColumn()).isEqualTo("name");
    assertThat(descriptor.slugColumn()).isEqualTo("slug");
    assertThat(descriptor.activeColumn()).isEqualTo("active");
    assertThat(descriptor.createdAtColumn()).isEqualTo("created_at");
    assertThat(descriptor.updatedAtColumn()).isEqualTo("updated_at");
    assertThat(descriptor.sortRankColumn()).contains("sort_rank");
    assertThat(descriptor.updatedAtExpression()).isEqualTo("CURRENT_TIMESTAMP");
  }

  @Test
  void acceptsUnqualifiedAndSchemaQualifiedConfiguredTableIdentifiers() {
    AdminCategoryJdbcProviderProperties.SystemProperties system =
      new AdminCategoryJdbcProviderProperties.SystemProperties();

    assertThat(
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "data_category", "data_category_closure", false),
        system
      ).nodeTable()
    ).isEqualTo("data_category");
    assertThat(
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "app.data_category", "app.data_category_closure", false),
        system
      ).nodeTable()
    ).isEqualTo("app.data_category");
  }

  @Test
  void rejectsUnsafeConfiguredTableIdentifiers() {
    List<String> unsafeIdentifiers = List.of(
      "\"data_category\"",
      "data_category where id = 1",
      "data_category--comment",
      "data_category/*comment*/",
      "data category",
      "app.public.data_category"
    );
    AdminCategoryJdbcProviderProperties.SystemProperties system =
      new AdminCategoryJdbcProviderProperties.SystemProperties();

    for (String unsafeIdentifier : unsafeIdentifiers) {
      assertThatThrownBy(() ->
        AdminCategoryNodeTableDescriptor.from(
          descriptor("data", unsafeIdentifier, "data_category_closure", false),
          system
        )
      )
        .as("identifier %s should be rejected", unsafeIdentifier)
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsUnsafeConfiguredColumnIdentifiers() {
    List<String> unsafeIdentifiers = List.of(
      "\"name\"",
      "name || slug",
      "name--comment",
      "name/*comment*/",
      "display name",
      "request.name"
    );

    for (String unsafeIdentifier : unsafeIdentifiers) {
      AdminCategoryJdbcProviderProperties.SystemProperties system =
        new AdminCategoryJdbcProviderProperties.SystemProperties();
      system.setNameColumn(unsafeIdentifier);

      assertThatThrownBy(() ->
        AdminCategoryNodeTableDescriptor.from(
          descriptor("data", "data_category", "data_category_closure", false),
          system
        )
      )
        .as("identifier %s should be rejected", unsafeIdentifier)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nameColumn");
    }
  }

  @Test
  void rejectsUnsupportedStrategiesAndUnsafeTimestampExpression() {
    AdminCategoryJdbcProviderProperties.SystemProperties slugSystem =
      new AdminCategoryJdbcProviderProperties.SystemProperties();
    slugSystem.setSlugUniqueness(AdminCategoryJdbcProviderProperties.SlugUniqueness.SYSTEM_SCOPED);

    assertThatThrownBy(() ->
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "data_category", "data_category_closure", false),
        slugSystem
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("slugUniqueness");

    AdminCategoryJdbcProviderProperties.SystemProperties idSystem =
      new AdminCategoryJdbcProviderProperties.SystemProperties();
    idSystem.setIdGeneration(AdminCategoryJdbcProviderProperties.IdGeneration.APPLICATION_ASSIGNED);

    assertThatThrownBy(() ->
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "data_category", "data_category_closure", false),
        idSystem
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("idGeneration");

    AdminCategoryJdbcProviderProperties.SystemProperties timestampSystem =
      new AdminCategoryJdbcProviderProperties.SystemProperties();
    timestampSystem
      .getTimestamps()
      .setStrategy(AdminCategoryJdbcProviderProperties.TimestampStrategy.APPLICATION_MANAGED);

    assertThatThrownBy(() ->
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "data_category", "data_category_closure", false),
        timestampSystem
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("timestampStrategy");

    AdminCategoryJdbcProviderProperties.SystemProperties expressionSystem =
      new AdminCategoryJdbcProviderProperties.SystemProperties();
    expressionSystem
      .getTimestamps()
      .setUpdateExpression("CURRENT_TIMESTAMP; DROP TABLE data_category");

    assertThatThrownBy(() ->
      AdminCategoryNodeTableDescriptor.from(
        descriptor("data", "data_category", "data_category_closure", false),
        expressionSystem
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("updatedAtExpression");
  }

  private static CategorySystemDescriptor descriptor(
    String systemKey,
    String nodeTable,
    String closureTable,
    boolean ranked
  ) {
    RankingCapabilityDescriptor ranking = ranked
      ? RankingCapabilityDescriptor.ranked(
          "sort_rank",
          1_000_000_000L,
          1_000_000_000L,
          1_000L,
          DisabledRankingRequestPolicy.REJECT,
          List.of(PlacementMode.APPEND, PlacementMode.BEFORE_ID),
          true
        )
      : RankingCapabilityDescriptor.unranked(DisabledRankingRequestPolicy.REJECT);
    return new CategorySystemDescriptor(
      systemKey,
      "data",
      ClosureTableDescriptor.of(nodeTable, closureTable),
      ranking,
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      ranked
        ? Optional.of(new SparseRankTableDescriptor(nodeTable, "id", "parent_id", "sort_rank"))
        : Optional.empty()
    );
  }
}
