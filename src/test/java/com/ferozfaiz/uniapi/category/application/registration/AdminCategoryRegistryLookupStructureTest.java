package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.AdminCategoryParentOptionsCursorCodec;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdminCategoryRegistryLookupStructureTest {

  private static final Path MAIN_JAVA = Path.of("src/main/java");
  private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
  private static final Path CATEGORY_MAIN = MAIN_JAVA.resolve("com/ferozfaiz/uniapi/category");
  private static final Path CATALOGUE_MAIN = MAIN_JAVA.resolve("com/ferozfaiz/uniapi/catalogue");
  private static final Path CATALOGUE_CURSOR = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/catalogue/application/query/AdminCatalogueCategoryParentOptionsCursor.java"
  );
  private static final Path CATALOGUE_CATEGORY_APPLICATION_SERVICE = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/catalogue/application/CatalogueCategoryApplicationService.java"
  );
  private static final Path CATALOGUE_ADAPTER = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CatalogueCategoryRepositoryAdapter.java"
  );
  private static final Path CATALOGUE_ENTITY = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/entity/CatalogueCategoryEntity.java"
  );
  private static final Path CATALOGUE_SLUG_LOCK = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/PostgresCatalogueSlugWriteLock.java"
  );
  private static final Path HIERARCHY_MAIN = MAIN_JAVA.resolve(
    "com/ferozfaiz/uniapi/platform/infrastructure/persistence/hierarchy"
  );
  private static final Path REGISTRY = CATEGORY_MAIN.resolve(
    "application/registration/AdminCategorySystemRegistry.java"
  );
  private static final Path APPLICATION_SERVICE = CATEGORY_MAIN.resolve(
    "application/AdminCategoryApplicationService.java"
  );
  private static final Path AUTHORIZATION_POLICY = CATEGORY_MAIN.resolve(
    "application/authorization/AdminCategorySystemAuthorizationPolicy.java"
  );
  private static final Path CONTROLLER = CATEGORY_MAIN.resolve("api/AdminCategoryController.java");

  private static final Pattern MAP_DECLARATION = Pattern.compile(
    "\\b(?:Map|ConcurrentHashMap|EnumMap|HashMap|IdentityHashMap|LinkedHashMap)\\s*<(?:(?![;{}()]).)+?>\\s+([A-Za-z][A-Za-z0-9_]*)\\s*(?=[=;,)])",
    Pattern.DOTALL
  );
  private static final Pattern INFERRED_MAP_DECLARATION = Pattern.compile(
    "\\bvar\\s+([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(?:new\\s+(?:ConcurrentHashMap|EnumMap|HashMap|IdentityHashMap|LinkedHashMap)\\s*<|Map\\.(?:copyOf|of|ofEntries)\\s*\\()"
  );
  private static final Pattern DIRECT_KEY_STRUCTURE_EXPRESSION = Pattern.compile(
    "Map\\.(?:copyOf|ofEntries|of)\\s*\\(|Collectors\\.(?:groupingBy|toMap)\\s*\\(|\\.computeIfAbsent\\s*\\(|Collections\\.unmodifiableMap\\s*\\(|new\\s+(?:ConcurrentHashMap|EnumMap|HashMap|IdentityHashMap|LinkedHashMap)\\s*<|new\\s+MapSqlParameterSource\\s*\\("
  );
  private static final Pattern GLOBAL_MAP_OR_CACHE_FIELD = Pattern.compile(
    "(?m)^\\s*(?:(public|protected|private)\\s+)?static\\s+(final\\s+)?(?:java\\.util\\.)?(Map|ConcurrentMap|ConcurrentHashMap|EnumMap|HashMap|IdentityHashMap|LinkedHashMap|Cache|LoadingCache|CacheManager)\\s*(?:<(?:(?![;{}]).)+?>)?\\s+([A-Za-z][A-Za-z0-9_]*)\\s*(?=[=;])",
    Pattern.DOTALL
  );

  @Test
  void completeEffectiveKeyInventoryIsExecutableAndRejectsUnknownStructures() throws Exception {
    List<KeyInventoryEntry> inventory = inventory();

    assertThat(inventory)
      .extracting(KeyInventoryEntry::id)
      .containsExactlyInAnyOrderElementsOf(NORMATIVE_INVENTORY_IDS);
    assertThat(inventory).extracting(KeyInventoryEntry::id).doesNotHaveDuplicates();

    List<MapSite> discovered = discoverMapSites();
    assertThat(discovered).contains(
      new MapSite(relative(CATALOGUE_CATEGORY_APPLICATION_SERVICE), "nameCounts")
    );
    assertThat(discovered).isNotEmpty();
    assertThat(discovered).allSatisfy(site ->
      assertThat(inventoryIdFor(site))
        .as("unknown FR-030 key-bearing map site %s#%s", site.file(), site.identifier())
        .isPresent()
    );
    assertThat(discovered.stream().map(this::inventoryIdFor).flatMap(Optional::stream)).contains(
      "registry registrations by system key",
      "permission mapping by operation",
      "policy references by obligation",
      "runtime policy bindings by typed reference",
      "registration validator startup identity maps",
      "descriptor property and construction maps",
      "catalogue cursor payload",
      "include-ID lookup maps",
      "candidate path depth and label maps",
      "SQL parameter maps",
      "static sort-property allow-list maps"
    );
    List<ExpressionSite> directExpressions = discoverDirectKeyStructureExpressions();
    assertThat(directExpressions).isNotEmpty();
    assertThat(directExpressions).allSatisfy(site ->
      assertThat(inventoryIdFor(site))
        .as(
          "unaccounted direct key-structure expression %s:%s [%s]",
          site.file(),
          site.line(),
          site.expression()
        )
        .isPresent()
    );
    assertThat(
      directExpressions.stream().map(this::inventoryIdFor).flatMap(Optional::stream)
    ).contains(
      "catalogue cursor payload",
      "candidate path depth and label maps",
      "include-ID lookup maps",
      "SQL parameter maps",
      "static sort-property allow-list maps"
    );
  }

  @Test
  void everySystemSpecificInventoryEntryHasSourceLinkedExecutableBoundaryProof() throws Exception {
    Set<String> systemSpecificEntries = inventory()
      .stream()
      .filter(entry -> !entry.exemption().permitted())
      .map(KeyInventoryEntry::id)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
    Map<String, List<BoundaryEvidence>> proofs = boundaryProofs();

    assertThat(proofs.keySet()).containsExactlyInAnyOrderElementsOf(systemSpecificEntries);
    proofs.forEach((inventoryId, evidence) -> {
      assertThat(evidence).as("boundary evidence for %s", inventoryId).isNotEmpty();
      evidence.forEach(item -> {
        try {
          String subject =
            item.memberSignature() == null
              ? source(item.file())
              : methodBody(source(item.file()), item.memberSignature());
          assertThat(compact(subject))
            .as("canonical boundary for %s in %s", inventoryId, item.file())
            .contains(item.requiredCompactFragments().toArray(String[]::new));
        } catch (IOException exception) {
          throw new IllegalStateException(exception);
        }
      });
    });

    AdminCategorySystemRegistration alpha = capabilitiesRegistration("alpha-system");
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(List.of(alpha));
    assertThat(registry.findRegistration("alpha-system")).containsSame(alpha);
    assertThat(
      alpha.runtimeBinding().orElseThrow().permissionMapping().permissionFor(Operation.CAPABILITIES)
    ).contains("admin:alpha-system-categories:read");
    assertThat(
      java.util.Arrays.stream(AdminCategoryRegistrationValidator.class.getDeclaredFields()).filter(
        field -> Map.class.isAssignableFrom(field.getType())
      )
    )
      .as("validator maps are startup-local and discarded")
      .isEmpty();
  }

  @Test
  void registryUsesOneDirectMapGetAndPublishesOneImmutableDeterministicSnapshot() throws Exception {
    AdminCategorySystemRegistration beta = capabilitiesRegistration("beta-system");
    AdminCategorySystemRegistration alpha = capabilitiesRegistration("alpha-system");
    List<AdminCategorySystemRegistration> candidates = new ArrayList<>(List.of(beta, alpha));
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(candidates);
    candidates.clear();

    assertThat(registry.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("alpha-system", "beta-system");
    assertThat(registry.findRegistration("alpha-system")).containsSame(alpha);
    assertThatThrownBy(() -> registry.registrations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );

    Field lookupField = AdminCategorySystemRegistry.class.getDeclaredField(
      "registrationsBySystemKey"
    );
    lookupField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, AdminCategorySystemRegistration> lookup = (Map<
      String,
      AdminCategorySystemRegistration
    >) lookupField.get(registry);
    assertThat(lookup).containsOnlyKeys("alpha-system", "beta-system");
    assertThatThrownBy(() -> lookup.put("other-system", alpha)).isInstanceOf(
      UnsupportedOperationException.class
    );

    String findRegistration = methodBody(source(REGISTRY), "findRegistration");
    assertThat(occurrences(findRegistration, "registrationsBySystemKey.get("))
      .as("one direct request lookup")
      .isEqualTo(1);
    assertThat(findRegistration).doesNotContain(
      ".stream(",
      ".parallelStream(",
      ".filter(",
      ".iterator(",
      ".forEach(",
      "while (",
      "for (",
      "AdminCategorySystemRegistrationSource",
      "CategorySystemRegistry"
    );
    assertThat(compact(findRegistration)).contains(
      "returnOptional.ofNullable(registrationsBySystemKey.get(systemKey));"
    );
    assertThat(occurrences(findRegistration, "return"))
      .as("the lookup has one result path and cannot hide an alternate scan result")
      .isEqualTo(1);
    assertThat(invokedMethods(findRegistration))
      .as("the lookup cannot delegate to an uninspected scanning helper")
      .containsOnly(
        "if",
        "isCanonicalKey",
        "AdminCategoryRegistrationValidationException",
        "AdminCategoryValidationDiagnostic",
        "of",
        "empty",
        "ofNullable",
        "get"
      );
    assertThat(occurrences(source(REGISTRY), "registrationsBySystemKey"))
      .as("field declaration, immutable publication, and the one used lookup")
      .isEqualTo(3);
    assertThat(source(REGISTRY)).contains(
      "var lookup = new LinkedHashMap<String, AdminCategorySystemRegistration>();",
      "this.registrationsBySystemKey = Map.copyOf(lookup);",
      "this.registrations = List.copyOf(sorted);"
    );
  }

  @Test
  void requestPathsSelectTheRegistrationBeforeLocalPermissionPolicyPortAndCursorLookup()
    throws Exception {
    String application = source(APPLICATION_SERVICE);
    String authorization = source(AUTHORIZATION_POLICY);
    String controller = source(CONTROLLER);
    String requestPath = application + authorization + controller;

    assertThat(application.indexOf(".findRegistration(systemKey)"))
      .isGreaterThanOrEqualTo(0)
      .isLessThan(application.indexOf("registration.runtimeBinding().orElseThrow()"));
    assertThat(compact(application)).contains(
      "registration=registrations.findRegistration(systemKey).orElseThrow(()->unknown(systemKey));",
      "AdminCategorySystemDefinitiondefinition=registration.definition().orElseThrow();",
      "registration.runtimeBinding().orElseThrow()",
      "context.binding().policyBindings().values().stream()",
      ".filter(binding->binding.reference().obligation()==obligation)",
      "context.binding().ports()"
    );
    assertThat(authorization).contains(
      "registrations.findRegistration(",
      ".map(binding -> binding.permissionMapping())",
      ".flatMap(mapping -> mapping.permissionFor(operation))"
    );
    assertThat(requestPath).doesNotContain(
      "AdminCategorySystemRegistrationSource",
      "platform.infrastructure.persistence.hierarchy.CategorySystemRegistry",
      ".providersBySystemKey()",
      ".registrations()"
    );
    assertThat(registryMethodCalls(requestPath)).containsOnly("findRegistration");
    assertThat(registryMethodCalls(requestPath)).hasSize(2);

    String descriptorCursor = source(
      CATEGORY_MAIN.resolve(
        "infrastructure/persistence/jdbc/AdminCategoryParentOptionsCursorCodec.java"
      )
    );
    String contextHash = methodBody(descriptorCursor, "private static String contextHash");
    assertThat(descriptorCursor).contains(
      "requireText(systemKey, \"systemKey\")",
      "!requireText(systemKey, \"systemKey\").equals(parts[1])",
      "record RequestContext("
    );
    assertThat(contextHash)
      .contains(
        "normalizeText(context.q())",
        "context.includeInactive()",
        "context.excludeSubtreeId()",
        ".includeIds()",
        ".sorted()",
        ".distinct()"
      )
      .doesNotContain("limit");

    AdminCategoryParentOptionsCursorCodec codec = new AdminCategoryParentOptionsCursorCodec();
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      " Graphics ",
      true,
      9L,
      List.of(5L, 2L, 5L)
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(2, true, "graphics", 44L);
    String cursor = codec.encode("alpha-system", context, position);
    assertThat(codec.decode("alpha-system", context, cursor)).isEqualTo(position);
    assertThatThrownBy(() -> codec.decode("beta-system", context, cursor)).isInstanceOf(
      RuntimeException.class
    );

    assertThat(source(CATALOGUE_CURSOR))
      .contains("Map<String, Object> payload = new LinkedHashMap<>();")
      .doesNotContain("payload.put(\"systemKey\"");
    assertThat(source(CATALOGUE_ADAPTER)).contains(
      "private static final String CATALOGUE_CATEGORY_SYSTEM = \"catalogue\";",
      "Map<Long, ParentOptionCandidate> byId",
      "Map<Long, List<CatalogueCategoryBreadcrumbItem>> pathsByCategoryId"
    );
  }

  @Test
  void theOnlyGlobalExemptionIsAnImmutableDataFreeLogicalSortAllowList() throws Exception {
    List<KeyInventoryEntry> exemptions = inventory()
      .stream()
      .filter(entry -> entry.exemption().permitted())
      .toList();

    assertThat(exemptions)
      .singleElement()
      .satisfies(entry -> {
        assertThat(entry.id()).isEqualTo("static sort-property allow-list maps");
        assertThat(entry.exemption()).isEqualTo(new ExemptionProof(true, true, true, true));
      });

    Field sortProperties = Class.forName(
      "com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter.CatalogueCategoryRepositoryAdapter"
    ).getDeclaredField("TREE_SORT_PROPERTIES");
    sortProperties.setAccessible(true);
    assertThat(Modifier.isPrivate(sortProperties.getModifiers())).isTrue();
    assertThat(Modifier.isStatic(sortProperties.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(sortProperties.getModifiers())).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, String> allowList = (Map<String, String>) sortProperties.get(null);
    assertThat(allowList).containsExactlyInAnyOrderEntriesOf(
      Map.of(
        "id",
        "category_depth.id",
        "slug",
        "category_depth.slug",
        "name",
        "category_depth.name",
        "sortRank",
        "category_depth.sort_rank",
        "active",
        "category_depth.is_active",
        "createdAt",
        "category_depth.created_at",
        "updatedAt",
        "category_depth.updated_at"
      )
    );
    assertThat(allowList.keySet())
      .allMatch(key -> !key.toLowerCase().contains("system"))
      .allMatch(key -> !key.toLowerCase().contains("permission"))
      .allMatch(key -> !key.toLowerCase().contains("cursor"));
    assertThat(allowList.values()).allMatch(value -> value.matches("[a-z_]+\\.[a-z_]+"));
    assertThatThrownBy(() -> allowList.put("systemKey", "category_depth.system_key")).isInstanceOf(
      UnsupportedOperationException.class
    );

    assertThat(discoverGlobalMapFields()).containsExactly(
      new GlobalFieldSite(
        relative(CATALOGUE_ADAPTER),
        "private",
        true,
        "Map",
        "TREE_SORT_PROPERTIES"
      )
    );
  }

  @Test
  void categoryRuntimeHasNoApplicationCacheOrSecondLevelEntityCache() throws Exception {
    String categoryAndCatalogue =
      readTree(CATEGORY_MAIN) + readTree(MAIN_JAVA.resolve("com/ferozfaiz/uniapi/catalogue"));
    assertThat(categoryAndCatalogue).doesNotContain(
      "org.springframework.cache",
      "@EnableCaching",
      "@Cacheable",
      "@CachePut",
      "@CacheEvict",
      "CacheManager",
      "ConcurrentMapCache",
      "CaffeineCache",
      "com.github.benmanes.caffeine",
      "com.google.common.cache",
      "jakarta.cache",
      "javax.cache"
    );

    assertThat(source(CATALOGUE_ENTITY)).doesNotContain(
      "jakarta.persistence.Cacheable",
      "org.hibernate.annotations.Cache",
      "@Cacheable",
      "@Cache("
    );
    String configuration = readTree(MAIN_RESOURCES);
    assertThat(configuration).doesNotContain(
      "hibernate.cache.use_second_level_cache: true",
      "hibernate.cache.use_query_cache: true",
      "use_second_level_cache=true",
      "use_query_cache=true"
    );
  }

  private List<MapSite> discoverMapSites() throws IOException {
    Map<String, MapSite> sites = new LinkedHashMap<>();
    for (Path file : relevantSourceFiles()) {
      String text = source(file);
      Stream.of(MAP_DECLARATION, INFERRED_MAP_DECLARATION).forEach(pattern -> {
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
          MapSite site = new MapSite(relative(file), matcher.group(1));
          sites.put(site.file() + "#" + site.identifier(), site);
        }
      });
    }
    return List.copyOf(sites.values());
  }

  private List<ExpressionSite> discoverDirectKeyStructureExpressions() throws IOException {
    List<ExpressionSite> sites = new ArrayList<>();
    for (Path file : relevantSourceFiles()) {
      String text = source(file);
      var matcher = DIRECT_KEY_STRUCTURE_EXPRESSION.matcher(text);
      while (matcher.find()) {
        sites.add(
          new ExpressionSite(
            relative(file),
            lineNumber(text, matcher.start()),
            matcher.group().replaceAll("\\s+", " ").trim(),
            nearbyContext(text, matcher.start(), matcher.end())
          )
        );
      }
    }
    return List.copyOf(sites);
  }

  private List<GlobalFieldSite> discoverGlobalMapFields() throws IOException {
    List<GlobalFieldSite> sites = new ArrayList<>();
    for (Path file : relevantSourceFiles()) {
      var matcher = GLOBAL_MAP_OR_CACHE_FIELD.matcher(source(file));
      while (matcher.find()) {
        sites.add(
          new GlobalFieldSite(
            relative(file),
            matcher.group(1) == null ? "package" : matcher.group(1),
            matcher.group(2) != null,
            matcher.group(3),
            matcher.group(4)
          )
        );
      }
    }
    return List.copyOf(sites);
  }

  private static int lineNumber(String source, int offset) {
    return (
      1 +
      (int) source
        .substring(0, offset)
        .chars()
        .filter(value -> value == '\n')
        .count()
    );
  }

  private static String nearbyContext(String source, int start, int end) {
    int from = Math.max(0, start - 240);
    int to = Math.min(source.length(), end + 240);
    return source.substring(from, to).replaceAll("\\s+", " ").trim();
  }

  private static List<Path> relevantSourceFiles() throws IOException {
    List<Path> files = new ArrayList<>(javaFiles(CATEGORY_MAIN));
    files.addAll(javaFiles(HIERARCHY_MAIN));
    try (Stream<Path> catalogueFiles = Files.walk(CATALOGUE_MAIN)) {
      catalogueFiles
        .filter(path -> path.toString().endsWith(".java"))
        .filter(path ->
          path
            .getFileName()
            .toString()
            .matches(
              ".*(?:AdminCatalogueCategory|CatalogueAdminCategory|CatalogueCategory).*\\.java"
            )
        )
        .forEach(files::add);
    }
    files.add(CATALOGUE_SLUG_LOCK);
    return files.stream().distinct().sorted().toList();
  }

  private Optional<String> inventoryIdFor(MapSite site) {
    String file = site.file();
    String identifier = site.identifier();
    if (file.endsWith("AdminCategorySystemRegistry.java")) {
      return known(
        identifier,
        "registry registrations by system key",
        "lookup",
        "registrationsBySystemKey"
      );
    }
    if (file.endsWith("AdminCategoryPermissionMapping.java")) {
      return known(
        identifier,
        "permission mapping by operation",
        "lookup",
        "permissionsByOperation"
      );
    }
    if (file.endsWith("AdminCategoryPolicyReferences.java")) {
      return known(
        identifier,
        "policy references by obligation",
        "lookup",
        "referencesByObligation"
      );
    }
    if (file.endsWith("AdminCategoryRuntimePolicyBindings.java")) {
      return known(
        identifier,
        "runtime policy bindings by typed reference",
        "lookup",
        "byReference"
      );
    }
    if (file.endsWith("AdminCategoryRegistrationValidator.java")) {
      return known(
        identifier,
        "registration validator startup identity maps",
        "registrationsByKey",
        "registrationsByPort",
        "participatingCandidates",
        "fallbackOccurrences"
      );
    }
    if (
      file.endsWith("AdminCategoryJdbcProviderProperties.java") ||
      file.endsWith("CategorySystemProperties.java") ||
      file.endsWith("CategorySystemRegistry.java") ||
      file.endsWith("CategoryOrderingStrategyFactory.java")
    ) {
      return known(
        identifier,
        "descriptor property and construction maps",
        "systems",
        "categorySystems",
        "descriptors",
        "built",
        "strategies",
        "mapped"
      );
    }
    if (file.endsWith("AdminCatalogueCategoryParentOptionsCursor.java")) {
      return known(identifier, "catalogue cursor payload", "payload");
    }
    if (file.endsWith("CatalogueCategoryApplicationService.java")) {
      return known(identifier, "candidate path depth and label maps", "nameCounts");
    }
    if (file.endsWith("AdminCategoryJdbcSql.java")) {
      return known(identifier, "SQL parameter maps", "parameters");
    }
    if (file.endsWith("DescriptorDrivenAdminCategoryJdbcProvider.java")) {
      if (identifier.equals("byId")) return Optional.of("include-ID lookup maps");
      if (identifier.equals("breadcrumbsByParentOptionId")) return Optional.of(
        "candidate path depth and label maps"
      );
      if (
        identifier.matches(".*(?:[Pp]ath|[Dd]epth|[Cc]andidate|[Ll]abel|[Nn]ame).*By.*") ||
        identifier.matches(".*ById")
      ) return Optional.of("candidate path depth and label maps");
      return Optional.empty();
    }
    if (file.endsWith("CatalogueCategoryRepositoryAdapter.java")) {
      if (identifier.equals("TREE_SORT_PROPERTIES")) return Optional.of(
        "static sort-property allow-list maps"
      );
      if (identifier.equals("params")) return Optional.of("SQL parameter maps");
      if (identifier.equals("byId")) return Optional.of("include-ID lookup maps");
      if (Set.of("depths", "paths", "pathsByCategoryId").contains(identifier)) return Optional.of(
        "candidate path depth and label maps"
      );
      return Optional.empty();
    }
    return Optional.empty();
  }

  private Optional<String> inventoryIdFor(ExpressionSite site) {
    String file = site.file();
    String context = site.context();
    if (file.endsWith("AdminCategorySystemRegistry.java")) return Optional.of(
      "registry registrations by system key"
    );
    if (file.endsWith("AdminCategoryPermissionMapping.java")) return Optional.of(
      "permission mapping by operation"
    );
    if (file.endsWith("AdminCategoryPolicyReferences.java")) return Optional.of(
      "policy references by obligation"
    );
    if (file.endsWith("AdminCategoryRuntimePolicyBindings.java")) return Optional.of(
      "runtime policy bindings by typed reference"
    );
    if (file.endsWith("AdminCategoryRegistrationValidator.java")) return Optional.of(
      "registration validator startup identity maps"
    );
    if (
      file.endsWith("AdminCategoryJdbcProviderProperties.java") ||
      file.endsWith("CategoryOrderingStrategyFactory.java") ||
      file.endsWith("CategorySystemProperties.java") ||
      file.endsWith("CategorySystemRegistry.java")
    ) return Optional.of("descriptor property and construction maps");
    if (file.endsWith("AdminCatalogueCategoryParentOptionsCursor.java")) return Optional.of(
      "catalogue cursor payload"
    );
    if (file.endsWith("CatalogueCategoryApplicationService.java")) return Optional.of(
      "candidate path depth and label maps"
    );
    if (file.endsWith("DescriptorDrivenAdminCategoryJdbcProvider.java")) {
      if (context.contains("ParentOptionRow> byId")) return Optional.of("include-ID lookup maps");
      if (context.contains("breadcrumbsByParentOptionId")) return Optional.of(
        "candidate path depth and label maps"
      );
      if (
        context.contains("MapSqlParameterSource") || context.contains("statement.parameters()")
      ) return Optional.of("SQL parameter maps");
      if (
        context.matches(".*(?:[Pp]ath|[Dd]epth|[Cc]andidate|[Ll]abel|[Nn]ame).*By.*")
      ) return Optional.of("candidate path depth and label maps");
      return Optional.empty();
    }
    if (
      file.endsWith("AdminCategoryJdbcSql.java") ||
      file.endsWith("ClosureTableOperations.java") ||
      file.endsWith("SparseRankOperations.java") ||
      file.endsWith("PostgresCatalogueSlugWriteLock.java")
    ) return Optional.of("SQL parameter maps");
    if (file.endsWith("CatalogueCategoryRepositoryAdapter.java")) {
      if (context.contains("TREE_SORT_PROPERTIES")) return Optional.of(
        "static sort-property allow-list maps"
      );
      if (
        site.expression().startsWith("Collectors.toMap") &&
        context.contains("ParentOptionCandidate> byId")
      ) return Optional.of("include-ID lookup maps");
      if (
        context.contains("return Map.of();") ||
        site.expression().startsWith("Collectors.toMap") ||
        site.expression().startsWith(".computeIfAbsent")
      ) return Optional.of("candidate path depth and label maps");
      return Optional.of("SQL parameter maps");
    }
    return Optional.empty();
  }

  private static Optional<String> known(
    String actual,
    String inventoryId,
    String... knownIdentifiers
  ) {
    return Set.of(knownIdentifiers).contains(actual) ? Optional.of(inventoryId) : Optional.empty();
  }

  private static List<KeyInventoryEntry> inventory() {
    ExemptionProof none = ExemptionProof.none();
    return List.of(
      entry(
        "registry registrations by system key",
        "application lifetime",
        "canonical systemKey",
        none
      ),
      entry(
        "permission mapping by operation",
        "selected immutable registration",
        "(systemKey, operation)",
        none
      ),
      entry(
        "policy references by obligation",
        "selected immutable definition and binding",
        "(systemKey, obligation)",
        none
      ),
      entry(
        "runtime policy bindings by typed reference",
        "selected immutable runtime binding",
        "(systemKey, typed policy reference)",
        none
      ),
      entry(
        "runtime port slots",
        "selected immutable runtime binding",
        "(systemKey, operation-to-slot)",
        none
      ),
      entry(
        "registration validator startup identity maps",
        "startup-local and discarded",
        "canonical systemKey or object identity plus candidate key",
        none
      ),
      entry(
        "descriptor property and construction maps",
        "startup configuration and construction only",
        "canonical systemKey",
        none
      ),
      entry(
        "catalogue cursor payload",
        "request-local serialization",
        "selected catalogue registration plus frozen context and position",
        none
      ),
      entry(
        "descriptor cursor context and digest",
        "request-local",
        "exact systemKey, normalized query, inactive flag, excluded subtree, sorted-distinct include IDs, position",
        none
      ),
      entry("include-ID lookup maps", "request-local", "(selected systemKey, includeId)", none),
      entry(
        "candidate path depth and label maps",
        "request-local response assembly",
        "(selected systemKey, categoryId or bounded label)",
        none
      ),
      entry(
        "SQL parameter maps",
        "one statement invocation",
        "selected descriptor and table plus named parameter",
        none
      ),
      entry(
        "catalogue JPA persistence context",
        "one transaction",
        "fixed catalogue system and entity type plus identifier",
        none
      ),
      entry(
        "static sort-property allow-list maps",
        "class lifetime",
        "logical sort token",
        new ExemptionProof(true, true, true, true)
      )
    );
  }

  private static Map<String, List<BoundaryEvidence>> boundaryProofs() {
    Path runtimePolicies = CATEGORY_MAIN.resolve(
      "application/policy/runtime/AdminCategoryRuntimePolicyBindings.java"
    );
    Path permissionMapping = CATEGORY_MAIN.resolve(
      "application/registration/AdminCategoryPermissionMapping.java"
    );
    Path policyReferences = CATEGORY_MAIN.resolve(
      "application/registration/AdminCategoryPolicyReferences.java"
    );
    Path runtimePorts = CATEGORY_MAIN.resolve(
      "application/registration/AdminCategoryRuntimePorts.java"
    );
    Path validator = CATEGORY_MAIN.resolve(
      "application/registration/AdminCategoryRegistrationValidator.java"
    );
    Path descriptorFactory = CATEGORY_MAIN.resolve(
      "infrastructure/persistence/jdbc/DescriptorDrivenAdminCategoryJdbcProviderFactory.java"
    );
    Path descriptorProvider = CATEGORY_MAIN.resolve(
      "infrastructure/persistence/jdbc/DescriptorDrivenAdminCategoryJdbcProvider.java"
    );
    Path descriptorCursor = CATEGORY_MAIN.resolve(
      "infrastructure/persistence/jdbc/AdminCategoryParentOptionsCursorCodec.java"
    );
    Path jdbcSql = CATEGORY_MAIN.resolve(
      "infrastructure/persistence/jdbc/AdminCategoryJdbcSql.java"
    );
    Path catalogueRegistration = CATALOGUE_MAIN.resolve(
      "application/provider/CatalogueAdminCategoryRegistrationConfiguration.java"
    );
    return Map.ofEntries(
      Map.entry(
        "registry registrations by system key",
        List.of(
          evidence(
            REGISTRY,
            "findRegistration",
            "if(!isCanonicalKey(systemKey))",
            "returnOptional.ofNullable(registrationsBySystemKey.get(systemKey));"
          )
        )
      ),
      Map.entry(
        "permission mapping by operation",
        List.of(
          evidence(
            AUTHORIZATION_POLICY,
            "public boolean canAccess",
            "StringselectedKey=safeKey(systemKey);",
            "if(isCanonicalKey(selectedKey))",
            "registrations.findRegistration(selectedKey)",
            ".runtimeBinding().map(binding->binding.permissionMapping()).flatMap(mapping->mapping.permissionFor(operation))"
          ),
          evidence(permissionMapping, "permissionFor", "permissionsByOperation.get(operation)")
        )
      ),
      Map.entry(
        "policy references by obligation",
        List.of(
          evidence(
            validator,
            "private static void validateCandidate",
            "varregistration=fact.registration();",
            "vardefinition=registration.definition().orElseThrow();",
            "newAdminCategoryPolicyReferences(binding.policyBindings().values().stream().map(policyBinding->policyBinding.reference()).toList())",
            "validatePolicyReferences(definition.systemKey(),policyRequirements,references,diagnostics);"
          ),
          evidence(policyReferences, "find", "referencesByObligation.get(obligation)")
        )
      ),
      Map.entry(
        "runtime policy bindings by typed reference",
        List.of(
          evidence(
            APPLICATION_SERVICE,
            "private static java.util.Optional<AdminCategoryRuntimePolicy> optionalPolicy",
            "context.binding().policyBindings().values().stream()",
            ".filter(binding->binding.reference().obligation()==obligation)"
          ),
          evidence(runtimePolicies, "find", "byReference.get(reference)")
        )
      ),
      Map.entry(
        "runtime port slots",
        List.of(
          evidence(
            APPLICATION_SERVICE,
            "private OperationContext requireSupported",
            "registration.runtimeBinding().orElseThrow()"
          ),
          evidence(
            APPLICATION_SERVICE,
            "public PageResult<CategoryNode> list",
            "context.binding().ports().categoryRead().orElseThrow().list(query)"
          ),
          evidence(runtimePorts, "portFor", "switch(capability)")
        )
      ),
      Map.entry(
        "registration validator startup identity maps",
        List.of(
          evidence(
            REGISTRY,
            "AdminCategorySystemRegistry",
            "AdminCategoryRegistrationValidator.validate(immutableSnapshot);",
            "this.registrationsBySystemKey=Map.copyOf(lookup);"
          ),
          evidence(
            validator,
            "static void validate",
            "registrationsByKey.computeIfAbsent(definition.systemKey()",
            "newIdentityHashMap<Object,List<PortOwnership>>()"
          )
        )
      ),
      Map.entry(
        "descriptor property and construction maps",
        List.of(
          evidence(
            descriptorFactory,
            "private static List<AdminCategorySystemRegistration> buildRegistrations",
            "for(StringsystemKey:properties.enabledSystemKeys())",
            "categorySystems.findDescriptor(systemKey)",
            ".getSystems().getOrDefault(systemKey",
            "newDescriptorDrivenAdminCategoryJdbcProvider(systemKey"
          )
        )
      ),
      Map.entry(
        "catalogue cursor payload",
        List.of(
          evidence(
            APPLICATION_SERVICE,
            "private OperationContext requireSupported",
            "registrations.findRegistration(systemKey)",
            "registration.runtimeBinding().orElseThrow()"
          ),
          evidence(catalogueRegistration, null, "privatestaticfinalStringSYSTEM_KEY=\"catalogue\";")
        )
      ),
      Map.entry(
        "descriptor cursor context and digest",
        List.of(
          evidence(
            descriptorCursor,
            "decode",
            "!requireText(systemKey,\"systemKey\").equals(parts[1])",
            "!contextHash(context).equals(parts[2])"
          )
        )
      ),
      Map.entry(
        "include-ID lookup maps",
        List.of(
          evidence(descriptorProvider, null, "privatefinalStringsystemKey;"),
          evidence(
            descriptorProvider,
            "private List<AdminCategoryJdbcRowMapper.ParentOptionRow> includedParentOptions",
            "Map<Long,AdminCategoryJdbcRowMapper.ParentOptionRow>byId=newLinkedHashMap<>();",
            "for(LongincludeId:query.includeIds())",
            "AdminCategoryJdbcRowMapper.ParentOptionRowrow=byId.get(includeId);"
          ),
          evidence(
            CATALOGUE_ADAPTER,
            null,
            "privatestaticfinalStringCATALOGUE_CATEGORY_SYSTEM=\"catalogue\";"
          )
        )
      ),
      Map.entry(
        "candidate path depth and label maps",
        List.of(
          evidence(descriptorProvider, null, "privatefinalStringsystemKey;"),
          evidence(
            descriptorProvider,
            "private List<AdminCategoryModels.ParentOption> toParentOptions",
            "Map<Long,List<BreadcrumbItem>>breadcrumbsByParentOptionId=newLinkedHashMap<>();",
            ".computeIfAbsent(breadcrumbRow.parentOptionId(),ignored->newArrayList<>())",
            "breadcrumbsByParentOptionId.get(option.category().id())"
          ),
          evidence(
            catalogueRegistration,
            null,
            "privatestaticfinalStringSYSTEM_KEY=\"catalogue\";"
          ),
          evidence(
            CATALOGUE_CATEGORY_APPLICATION_SERVICE,
            "private static CatalogueCategoryParentOptionSlice withDisambiguatedLabels",
            "Map<String,Long>nameCounts=",
            "Collectors.groupingBy(option->option.category().name().toLowerCase(Locale.ROOT)",
            "nameCounts.getOrDefault(key,0L)"
          ),
          evidence(
            CATALOGUE_ADAPTER,
            null,
            "privatestaticfinalStringCATALOGUE_CATEGORY_SYSTEM=\"catalogue\";"
          )
        )
      ),
      Map.entry(
        "SQL parameter maps",
        List.of(
          evidence(
            descriptorProvider,
            "public DescriptorDrivenAdminCategoryJdbcProvider",
            "this.systemKey=Objects.requireNonNull(systemKey,\"systemKey\");",
            "this.sql=newAdminCategoryJdbcSql(node,categorySystem);"
          ),
          evidence(
            jdbcSql,
            "public AdminCategoryJdbcSql",
            "this.node=Objects.requireNonNull(node,\"node\");",
            "this.categorySystem=Objects.requireNonNull(categorySystem,\"categorySystem\");"
          ),
          evidence(
            CATALOGUE_ADAPTER,
            null,
            "privatestaticfinalStringCATALOGUE_CATEGORY_SYSTEM=\"catalogue\";"
          )
        )
      ),
      Map.entry(
        "catalogue JPA persistence context",
        List.of(
          evidence(CATALOGUE_ENTITY, null, "@Table(name=\"catalogue_category\")"),
          evidence(
            CATALOGUE_ADAPTER,
            null,
            "privatestaticfinalStringCATALOGUE_CATEGORY_SYSTEM=\"catalogue\";"
          )
        )
      )
    );
  }

  private static BoundaryEvidence evidence(
    Path file,
    String memberSignature,
    String... requiredCompact
  ) {
    return new BoundaryEvidence(file, memberSignature, List.of(requiredCompact));
  }

  private static AdminCategorySystemRegistration capabilitiesRegistration(String systemKey) {
    AdminCategorySystemDefinition definition = new AdminCategorySystemDefinition(
      systemKey,
      "contract-tests",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.CAPABILITIES),
        List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION)
      ),
      new HierarchyDeclaration(false, Optional.empty(), Optional.empty(), List.of()),
      new OrderingDeclaration(false, false, false, false, List.of(), List.of()),
      new LifecycleDeclaration(false, List.of(), List.of()),
      new FieldQueryDeclaration(
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT
        ),
        List.of(),
        List.of(),
        List.of()
      ),
      new OperationalLimits(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    );
    AdminCategoryPolicyReference reference = new AdminCategoryPolicyReference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      new AdminCategoryLogicalPolicyId("contract-tests", "general-validation")
    );
    AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
      new StandardNumericV1AdminCategoryIdentifierCodec(),
      new AdminCategoryPermissionMapping(
        List.of(new Binding(Operation.CAPABILITIES, "admin:" + systemKey + "-categories:read"))
      ),
      new AdminCategoryRuntimePolicyBindings(
        List.of(new GeneralValidationPolicyBinding(reference, ignored -> {}))
      ),
      AdminCategoryRuntimePorts.empty()
    );
    return AdminCategorySystemRegistration.of(definition, binding);
  }

  private static KeyInventoryEntry entry(
    String id,
    String lifetime,
    String effectiveKey,
    ExemptionProof exemption
  ) {
    return new KeyInventoryEntry(id, lifetime, effectiveKey, exemption);
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths
        .filter(path -> path.toString().endsWith(".java"))
        .sorted()
        .toList();
    }
  }

  private static String readTree(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      StringBuilder source = new StringBuilder();
      for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
        source.append(Files.readString(file)).append('\n');
      }
      return source.toString();
    }
  }

  private static String source(Path file) throws IOException {
    return Files.readString(file);
  }

  private static String relative(Path file) {
    return MAIN_JAVA.relativize(file).toString().replace('\\', '/');
  }

  private static String methodBody(String source, String methodName) {
    int name = source.indexOf(methodName + "(");
    if (name < 0) throw new IllegalArgumentException("method not found: " + methodName);
    int openingBrace = source.indexOf('{', name);
    if (openingBrace < 0) throw new IllegalArgumentException(
      "method body not found: " + methodName
    );
    int depth = 0;
    for (int index = openingBrace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') depth++;
      if (current == '}' && --depth == 0) return source.substring(openingBrace, index + 1);
    }
    throw new IllegalArgumentException("unterminated method: " + methodName);
  }

  private static int occurrences(String value, String fragment) {
    int count = 0;
    int from = 0;
    while ((from = value.indexOf(fragment, from)) >= 0) {
      count++;
      from += fragment.length();
    }
    return count;
  }

  private static String compact(String value) {
    return value.replaceAll("\\s+", "");
  }

  private static List<String> registryMethodCalls(String requestPathSource) {
    var matcher = Pattern.compile(
      "\\bregistrations\\s*\\.\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\("
    ).matcher(requestPathSource);
    List<String> calls = new ArrayList<>();
    while (matcher.find()) calls.add(matcher.group(1));
    return List.copyOf(calls);
  }

  private static List<String> invokedMethods(String methodBody) {
    var matcher = Pattern.compile("\\b([A-Za-z][A-Za-z0-9_]*)\\s*\\(").matcher(methodBody);
    List<String> calls = new ArrayList<>();
    while (matcher.find()) calls.add(matcher.group(1));
    return List.copyOf(calls);
  }

  private static final Set<String> NORMATIVE_INVENTORY_IDS = Set.of(
    "registry registrations by system key",
    "permission mapping by operation",
    "policy references by obligation",
    "runtime policy bindings by typed reference",
    "runtime port slots",
    "registration validator startup identity maps",
    "descriptor property and construction maps",
    "catalogue cursor payload",
    "descriptor cursor context and digest",
    "include-ID lookup maps",
    "candidate path depth and label maps",
    "SQL parameter maps",
    "catalogue JPA persistence context",
    "static sort-property allow-list maps"
  );

  private record KeyInventoryEntry(
    String id,
    String lifetime,
    String effectiveKey,
    ExemptionProof exemption
  ) {}

  private record MapSite(String file, String identifier) {}

  private record ExpressionSite(String file, int line, String expression, String context) {}

  private record GlobalFieldSite(
    String file,
    String visibility,
    boolean immutable,
    String structureType,
    String identifier
  ) {}

  private record BoundaryEvidence(
    Path file,
    String memberSignature,
    List<String> requiredCompactFragments
  ) {
    private BoundaryEvidence {
      requiredCompactFragments = List.copyOf(requiredCompactFragments);
    }
  }

  private record ExemptionProof(
    boolean permitted,
    boolean global,
    boolean immutable,
    boolean incapableOfSystemState
  ) {
    static ExemptionProof none() {
      return new ExemptionProof(false, false, false, false);
    }
  }
}
