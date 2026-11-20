package com.ferozfaiz.uniapi.category.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.api.mapper.AdminCategoryApiMapper;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.AdminCategoryJdbcProviderProperties;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.AdminCategoryParentOptionsCursorCodec;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProvider;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProviderFactory;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemProperties;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@JsonTest
class AdminCategoryCapabilitySerializationCompatibilityTest {

  private static final String CATALOGUE_PAYLOAD = """
    {"rankingEnabled":true,"placementModes":["APPEND","BEFORE_ID","AFTER_ID","TARGET_INDEX"],"sortRankExposed":true,"fallbackOrder":[],"supportedOperations":["capabilities","list","tree","parent-options","get","breadcrumb","create","replace","patch","delete","move","reorder","activate","deactivate"],"hierarchy":{"enabled":true,"readOperations":["tree","parent-options","breadcrumb"],"mutationOperations":["move"],"rootModel":"IMPLICIT_NULL_PARENT"},"limits":{"pageSize":1000,"searchInputLength":120,"filterSelections":4,"sortSelections":5,"treeSize":10000,"parentOptionPageSize":50,"includeIdCount":25,"hierarchyDepth":2147483647},"lifecycle":{"enabled":true,"states":["ACTIVE","INACTIVE"],"actions":["activate","deactivate"]},"identifierProfile":"NUMERIC_LONG_V1","fields":["identifier","parent","name","slug","active","created-at","updated-at","rank","hierarchy-context"],"searchableFields":["name","slug"],"allowedFilters":["parent","slug","active"],"allowedSorts":["identifier","parent","name","slug","active","created-at","updated-at","rank"],"ordering":{"orderingEnabled":true,"rankingEnabled":true,"rankExposed":true,"reorderSupported":true,"placementModes":["APPEND","BEFORE_ID","AFTER_ID","TARGET_INDEX"],"fallbackOrder":[]}}
    """.strip();

  private static final String DESCRIPTOR_PAYLOAD = """
    {"rankingEnabled":true,"placementModes":["APPEND","BEFORE_ID","AFTER_ID","TARGET_INDEX"],"sortRankExposed":true,"fallbackOrder":[{"property":"name","direction":"ASC"},{"property":"id","direction":"ASC"}],"supportedOperations":["capabilities","list","tree","parent-options","get","breadcrumb","create","replace","patch","delete","move","reorder","activate","deactivate"],"hierarchy":{"enabled":true,"readOperations":["tree","parent-options","breadcrumb"],"mutationOperations":["move"],"rootModel":"IMPLICIT_NULL_PARENT"},"limits":{"pageSize":1000,"searchInputLength":120,"filterSelections":4,"sortSelections":5,"treeSize":10000,"parentOptionPageSize":50,"includeIdCount":25,"hierarchyDepth":2147483647},"lifecycle":{"enabled":true,"states":["ACTIVE","INACTIVE"],"actions":["activate","deactivate"]},"identifierProfile":"NUMERIC_LONG_V1","fields":["identifier","parent","name","slug","active","created-at","updated-at","rank","hierarchy-context"],"searchableFields":["name","slug"],"allowedFilters":["parent","slug","active"],"allowedSorts":["identifier","parent","name","slug","active","created-at","updated-at","rank"],"ordering":{"orderingEnabled":true,"rankingEnabled":true,"rankExposed":true,"reorderSupported":true,"placementModes":["APPEND","BEFORE_ID","AFTER_ID","TARGET_INDEX"],"fallbackOrder":[{"property":"name","direction":"ASC"},{"property":"id","direction":"ASC"}]}}
    """.strip();

  private static final List<String> TOP_LEVEL_ORDER = List.of(
    "rankingEnabled",
    "placementModes",
    "sortRankExposed",
    "fallbackOrder",
    "supportedOperations",
    "hierarchy",
    "limits",
    "lifecycle",
    "identifierProfile",
    "fields",
    "searchableFields",
    "allowedFilters",
    "allowedSorts",
    "ordering"
  );

  private static final List<String> PROHIBITED_METADATA_TOKENS = List.of(
    "catalogue",
    "descriptor-a",
    "category-jdbc",
    "admin:",
    "systemkey",
    "owningmodule",
    "permission",
    "policy",
    "obligation",
    "collaborator",
    "provider",
    "adapter",
    "\"port\"",
    "\"ports\"",
    "storage",
    "descriptor",
    "jdbc",
    "sql",
    "cursor",
    "registry",
    "bean",
    "configuration",
    "table",
    "column",
    "credential",
    "password",
    "token",
    "secret"
  );

  private final ObjectMapper json;

  @Autowired
  AdminCategoryCapabilitySerializationCompatibilityTest(ObjectMapper json) {
    this.json = json;
  }

  @Test
  void catalogueCapabilityMatchesTheFrozenBytesWithoutRuntimeInteractions() throws Exception {
    Assembly assembly = catalogueAssembly();
    clearInvocations(assembly.collaborators());

    JsonNode document = assertFrozenCapability(
      assembly.registration(),
      CATALOGUE_PAYLOAD,
      1232,
      "3010f22601d2ad05ef57cc335546975f030555f6c1e2405536548412b04ca712"
    );

    assertThat(document.path("fallbackOrder")).isEmpty();
    assertThat(document.at("/ordering/fallbackOrder")).isEmpty();
    verifyNoInteractions(assembly.collaborators());
  }

  @Test
  void descriptorCapabilityMatchesTheFrozenBytesWithoutRuntimeInteractions() throws Exception {
    Assembly assembly = descriptorAssembly();
    clearInvocations(assembly.collaborators());

    JsonNode document = assertFrozenCapability(
      assembly.registration(),
      DESCRIPTOR_PAYLOAD,
      1378,
      "e08a645a873c51c6fcd25e78f11413ac57b4b2acaf4b220edb15bb3d24f6e9d6"
    );

    assertFallbackOrder(document.path("fallbackOrder"));
    assertFallbackOrder(document.at("/ordering/fallbackOrder"));
    verifyNoInteractions(assembly.collaborators());
  }

  private JsonNode assertFrozenCapability(
    AdminCategorySystemRegistration registration,
    String expectedPayload,
    int expectedLength,
    String expectedSha256
  ) throws Exception {
    var response = AdminCategoryApiMapper.toCapabilityResponse(
      registration.definition().orElseThrow()
    );
    byte[] first = json.writeValueAsBytes(response);
    byte[] second = json.writeValueAsBytes(response);
    byte[] expected = expectedPayload.getBytes(UTF_8);
    JsonNode document = json.readTree(first);

    assertThat(first).containsExactly(expected);
    assertThat(first).hasSize(expectedLength);
    assertThat(sha256(first)).isEqualTo(expectedSha256);
    assertThat(first).containsExactly(second);
    assertThat(new String(first, UTF_8)).isEqualTo(expectedPayload);

    assertThat(propertyNames(document)).containsExactlyElementsOf(TOP_LEVEL_ORDER);
    assertNestedPropertyOrder(document);
    assertAliasEquality(document);
    assertClosedArrayOrder(document);
    assertArraysAreDuplicateFree(document, "$");
    assertThat(new String(first, UTF_8).toLowerCase(Locale.ROOT)).doesNotContain(
      PROHIBITED_METADATA_TOKENS.toArray(String[]::new)
    );
    return document;
  }

  private static void assertNestedPropertyOrder(JsonNode document) {
    document
      .path("fallbackOrder")
      .forEach(item -> assertThat(propertyNames(item)).containsExactly("property", "direction"));
    assertThat(propertyNames(document.path("hierarchy"))).containsExactly(
      "enabled",
      "readOperations",
      "mutationOperations",
      "rootModel"
    );
    assertThat(propertyNames(document.path("limits"))).containsExactly(
      "pageSize",
      "searchInputLength",
      "filterSelections",
      "sortSelections",
      "treeSize",
      "parentOptionPageSize",
      "includeIdCount",
      "hierarchyDepth"
    );
    assertThat(propertyNames(document.path("lifecycle"))).containsExactly(
      "enabled",
      "states",
      "actions"
    );
    assertThat(propertyNames(document.path("ordering"))).containsExactly(
      "orderingEnabled",
      "rankingEnabled",
      "rankExposed",
      "reorderSupported",
      "placementModes",
      "fallbackOrder"
    );
    document
      .at("/ordering/fallbackOrder")
      .forEach(item -> assertThat(propertyNames(item)).containsExactly("property", "direction"));
  }

  private static void assertAliasEquality(JsonNode document) {
    assertThat(document.path("rankingEnabled")).isEqualTo(document.at("/ordering/rankingEnabled"));
    assertThat(document.path("placementModes")).isEqualTo(document.at("/ordering/placementModes"));
    assertThat(document.path("sortRankExposed")).isEqualTo(document.at("/ordering/rankExposed"));
    assertThat(document.path("fallbackOrder")).isEqualTo(document.at("/ordering/fallbackOrder"));
  }

  private static void assertClosedArrayOrder(JsonNode document) {
    assertTextArray(
      document.path("supportedOperations"),
      "capabilities",
      "list",
      "tree",
      "parent-options",
      "get",
      "breadcrumb",
      "create",
      "replace",
      "patch",
      "delete",
      "move",
      "reorder",
      "activate",
      "deactivate"
    );
    assertTextArray(
      document.path("fields"),
      "identifier",
      "parent",
      "name",
      "slug",
      "active",
      "created-at",
      "updated-at",
      "rank",
      "hierarchy-context"
    );
    assertTextArray(document.path("searchableFields"), "name", "slug");
    assertTextArray(document.path("allowedFilters"), "parent", "slug", "active");
    assertTextArray(
      document.path("allowedSorts"),
      "identifier",
      "parent",
      "name",
      "slug",
      "active",
      "created-at",
      "updated-at",
      "rank"
    );
    assertTextArray(
      document.path("placementModes"),
      "APPEND",
      "BEFORE_ID",
      "AFTER_ID",
      "TARGET_INDEX"
    );
    assertTextArray(
      document.at("/ordering/placementModes"),
      "APPEND",
      "BEFORE_ID",
      "AFTER_ID",
      "TARGET_INDEX"
    );
    assertTextArray(
      document.at("/hierarchy/readOperations"),
      "tree",
      "parent-options",
      "breadcrumb"
    );
    assertTextArray(document.at("/hierarchy/mutationOperations"), "move");
    assertTextArray(document.at("/lifecycle/states"), "ACTIVE", "INACTIVE");
    assertTextArray(document.at("/lifecycle/actions"), "activate", "deactivate");
  }

  private static void assertFallbackOrder(JsonNode fallbackOrder) {
    assertThat(fallbackOrder).hasSize(2);
    assertThat(fallbackOrder.at("/0/property").asText()).isEqualTo("name");
    assertThat(fallbackOrder.at("/0/direction").asText()).isEqualTo("ASC");
    assertThat(fallbackOrder.at("/1/property").asText()).isEqualTo("id");
    assertThat(fallbackOrder.at("/1/direction").asText()).isEqualTo("ASC");
  }

  private static void assertTextArray(JsonNode array, String... expected) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asText()));
    assertThat(values).containsExactly(expected);
  }

  private static void assertArraysAreDuplicateFree(JsonNode node, String path) {
    if (node.isArray()) {
      List<String> values = new ArrayList<>();
      node.forEach(value -> values.add(value.toString()));
      assertThat(values).as("duplicate-free array at %s", path).doesNotHaveDuplicates();
      for (int index = 0; index < node.size(); index++) {
        assertArraysAreDuplicateFree(node.get(index), path + "[" + index + "]");
      }
      return;
    }
    node
      .propertyStream()
      .forEach(entry ->
        assertArraysAreDuplicateFree(entry.getValue(), path + "." + entry.getKey())
      );
  }

  private static List<String> propertyNames(JsonNode node) {
    return node.propertyStream().map(Map.Entry::getKey).toList();
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  @SuppressWarnings("unchecked")
  private static Assembly catalogueAssembly() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueCategoryRepository repository = mock(CatalogueCategoryRepository.class);
    CatalogueAdminCategoryProvider provider = spy(
      new CatalogueAdminCategoryProvider(service, repository)
    );
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    AdminCategoryGeneralValidationPolicy generalValidation = mock(
      AdminCategoryGeneralValidationPolicy.class
    );
    AdminCategoryQueryPolicy query = mock(AdminCategoryQueryPolicy.class);
    AdminCategoryHierarchyPolicy hierarchy = mock(AdminCategoryHierarchyPolicy.class);
    AdminCategoryMutationValidationPolicy mutationValidation = mock(
      AdminCategoryMutationValidationPolicy.class
    );
    AdminCategoryOrderingPolicy ordering = mock(AdminCategoryOrderingPolicy.class);
    AdminCategoryDeletionPolicy deletion = mock(AdminCategoryDeletionPolicy.class);
    AdminCategoryLifecyclePolicy lifecycle = mock(AdminCategoryLifecyclePolicy.class);
    AtomicReference<AdminCategorySystemRegistration> registration = new AtomicReference<>();

    new ApplicationContextRunner()
      .withUserConfiguration(catalogueRegistrationConfiguration())
      .withBean(CatalogueAdminCategoryProvider.class, () -> provider)
      .withBean(AdminCategoryIdentifierCodec.class, () -> codec)
      .withBean(AdminCategoryGeneralValidationPolicy.class, () -> generalValidation)
      .withBean(AdminCategoryQueryPolicy.class, () -> query)
      .withBean(AdminCategoryHierarchyPolicy.class, () -> hierarchy)
      .withBean(AdminCategoryMutationValidationPolicy.class, () -> mutationValidation)
      .withBean(AdminCategoryOrderingPolicy.class, () -> ordering)
      .withBean(AdminCategoryDeletionPolicy.class, () -> deletion)
      .withBean(AdminCategoryLifecyclePolicy.class, () -> lifecycle)
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(AdminCategorySystemRegistration.class);
        registration.set(context.getBean(AdminCategorySystemRegistration.class));
      });

    return new Assembly(registration.get(), new Object[] {
      provider,
      service,
      repository,
      codec,
      generalValidation,
      query,
      hierarchy,
      mutationValidation,
      ordering,
      deletion,
      lifecycle,
    });
  }

  @SuppressWarnings("unchecked")
  private static Assembly descriptorAssembly() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    ClosureTableOperations closureTables = mock(ClosureTableOperations.class);
    SparseRankOperations rankOperations = mock(SparseRankOperations.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    AdminCategoryGeneralValidationPolicy generalValidation = mock(
      AdminCategoryGeneralValidationPolicy.class
    );
    AdminCategoryQueryPolicy query = mock(AdminCategoryQueryPolicy.class);
    AdminCategoryHierarchyPolicy hierarchy = mock(AdminCategoryHierarchyPolicy.class);
    AdminCategoryMutationValidationPolicy mutationValidation = mock(
      AdminCategoryMutationValidationPolicy.class
    );
    AdminCategoryOrderingPolicy ordering = mock(AdminCategoryOrderingPolicy.class);
    AdminCategoryDeletionPolicy deletion = mock(AdminCategoryDeletionPolicy.class);
    AdminCategoryLifecyclePolicy lifecycle = mock(AdminCategoryLifecyclePolicy.class);
    CategorySystemDescriptor descriptor = descriptorRegistry().requireDescriptor("descriptor-a");
    CategorySystemRegistry categorySystems = mock(CategorySystemRegistry.class);
    when(categorySystems.findDescriptor("descriptor-a")).thenReturn(Optional.of(descriptor));
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of("descriptor-a"));

    try (
      MockedConstruction<AdminCategoryParentOptionsCursorCodec> cursors = mockConstruction(
        AdminCategoryParentOptionsCursorCodec.class
      );
      MockedConstruction<DescriptorDrivenAdminCategoryJdbcProvider> providers = mockConstruction(
        DescriptorDrivenAdminCategoryJdbcProvider.class
      )
    ) {
      DescriptorDrivenAdminCategoryJdbcProviderFactory factory =
        new DescriptorDrivenAdminCategoryJdbcProviderFactory(
          properties,
          categorySystems,
          jdbc,
          closureTables,
          rankOperations,
          transactions,
          codec,
          generalValidation,
          query,
          hierarchy,
          mutationValidation,
          ordering,
          deletion,
          lifecycle
        );
      assertThat(factory.registrations()).hasSize(1);
      assertThat(providers.constructed()).hasSize(1);
      assertThat(cursors.constructed()).hasSize(1);
      return new Assembly(factory.registrations().getFirst(), new Object[] {
        providers.constructed().getFirst(),
        cursors.constructed().getFirst(),
        categorySystems,
        jdbc,
        closureTables,
        rankOperations,
        transactions,
        codec,
        generalValidation,
        query,
        hierarchy,
        mutationValidation,
        ordering,
        deletion,
        lifecycle,
      });
    }
  }

  private static CategorySystemRegistry descriptorRegistry() {
    CategorySystemProperties.SystemProperties descriptor =
      new CategorySystemProperties.SystemProperties();
    descriptor.setOwningModule("category");
    descriptor.setNodeTable("descriptor_a_category");
    descriptor.setClosureTable("descriptor_a_category_closure");
    descriptor.getRanking().setEnabled(true);
    descriptor.setFallbackOrder(List.of(fallback("name", "name"), fallback("id", "id")));
    CategorySystemProperties properties = new CategorySystemProperties();
    properties.setCategorySystems(Map.of("descriptor-a", descriptor));
    return new CategorySystemRegistry(properties);
  }

  private static CategorySystemProperties.FallbackOrderProperties fallback(
    String property,
    String column
  ) {
    CategorySystemProperties.FallbackOrderProperties fallback =
      new CategorySystemProperties.FallbackOrderProperties();
    fallback.setProperty(property);
    fallback.setColumnName(column);
    fallback.setDirection(FallbackOrderDescriptor.Direction.ASC);
    return fallback;
  }

  private static Class<?> catalogueRegistrationConfiguration() {
    try {
      return Class.forName(
        "com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryRegistrationConfiguration"
      );
    } catch (ClassNotFoundException exception) {
      throw new AssertionError(
        "Catalogue registration configuration must remain available",
        exception
      );
    }
  }

  private record Assembly(AdminCategorySystemRegistration registration, Object[] collaborators) {}
}
