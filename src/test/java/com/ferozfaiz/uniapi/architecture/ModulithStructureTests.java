package com.ferozfaiz.uniapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.UniapiApplication;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.Query;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTests {

  private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
    .withImportOption(new ImportOption.DoNotIncludeTests())
    .importPackages("com.ferozfaiz.uniapi");
  private static final Path CATALOGUE_MAIN = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/catalogue"
  );
  private static final Path CATEGORY_MAIN = Path.of("src/main/java/com/ferozfaiz/uniapi/category");
  private static final Path REGISTRATION_MAIN = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/category/application/registration"
  );
  private static final String REGISTRATION_FIXTURE_CONFIGURATION =
    "com.ferozfaiz.uniapi.registrationfixture.RegistrationFixtureConfiguration";
  private static final Set<String> CATALOGUE_REGISTRATION_CONTRIBUTIONS = Set.of(
    "com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryRegistrationConfiguration",
    "com.ferozfaiz.uniapi.catalogue.application.provider.policy.CatalogueAdminCategoryRuntimePolicies"
  );
  private static final Set<String> DIRECT_PORT_TYPES = Set.of(
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort"
  );
  private static final Set<String> REGISTRATION_EXPORTED_TYPES = Set.of(
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicy",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$DeletionPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$GeneralValidationPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$HierarchyPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$LifecyclePolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$MutationValidationPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$OrderingPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding$QueryPolicyBinding",
    "com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort",
    "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile$HardMaxima",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping$Binding",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReferences",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryProviderCapability",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationAggregateValidationException",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$FieldQueryDeclaration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$HierarchyDeclaration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$HierarchySemantics",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$LifecycleDeclaration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$LifecycleState",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$LogicalField",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$LogicalOrder",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$Operation",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$OperationDeclaration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$OperationalLimits",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$OrderingDeclaration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$PlacementMode",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$RootModel",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition$SortDirection",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistrationSource",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCapability",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationDiagnostic"
  );
  private static final Set<String> REGISTRATION_FORBIDDEN_TYPES = Set.of(
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationConfiguration",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationDiagnostics",
    "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidator",
    "com.ferozfaiz.uniapi.category.application.registration.BoundedRegistrationDiagnosticAccumulator",
    "com.ferozfaiz.uniapi.category.application.registration.StandardNumericV1AdminCategoryIdentifierCodec",
    "com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProviderFactory",
    "com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider",
    "com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor",
    "com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry"
  );
  // CatalogueCategoryRepositoryAdapter is allow-listed for hierarchy/tree recursive reads.
  private static final Set<String> CATALOGUE_CUSTOM_PERSISTENCE_ALLOW_LIST = Set.of(
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CatalogueCategoryRepositoryAdapter.java",
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CatalogueMerchantRepositoryAdapter.java",
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CataloguePriceRepositoryAdapter.java",
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CatalogueProductListingQueryAdapter.java",
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/CatalogueProductRepositoryAdapter.java",
    "src/main/java/com/ferozfaiz/uniapi/catalogue/infrastructure/persistence/adapter/PostgresCatalogueSlugWriteLock.java"
  );

  @Test
  void verifyModules() {
    ApplicationModules.of(UniapiApplication.class).verify();
  }

  @Test
  void cataloguePersistenceEntitiesStayInsideInfrastructure() {
    noClasses()
      .that()
      .resideInAnyPackage("..catalogue.api..", "..catalogue.application..", "..catalogue.domain..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..catalogue.infrastructure.persistence.entity..")
      .check(PRODUCTION_CLASSES);
  }

  @Test
  void catalogueCustomPersistenceUsageStaysAllowListed() {
    var rawPersistenceFiles = PRODUCTION_CLASSES.stream()
      .filter(type -> type.getPackageName().startsWith("com.ferozfaiz.uniapi.catalogue"))
      .filter(ModulithStructureTests::usesCustomPersistenceAccess)
      .map(ModulithStructureTests::sourcePath)
      .toList();

    assertThat(rawPersistenceFiles).containsExactlyInAnyOrderElementsOf(
      CATALOGUE_CUSTOM_PERSISTENCE_ALLOW_LIST
    );
  }

  @Test
  void genericCategoryModuleDoesNotDependOnCatalogueModule() {
    noClasses()
      .that()
      .resideInAPackage("..category..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..catalogue..")
      .check(PRODUCTION_CLASSES);
  }

  @Test
  void registrationNamedInterfaceExposesOnlyTheFrozenContract() {
    ApplicationModules modules = ApplicationModules.of(UniapiApplication.class);
    var category = modules.getModuleByName("category").orElseThrow();
    var registration = category.getNamedInterfaces().getByName("registration").orElseThrow();
    Set<String> exposedTypes = registration
      .asJavaClasses()
      .map(type -> type.getName())
      .collect(java.util.stream.Collectors.toUnmodifiableSet());

    assertThat(exposedTypes).containsExactlyInAnyOrderElementsOf(REGISTRATION_EXPORTED_TYPES);
    assertThat(exposedTypes).doesNotContainAnyElementsOf(REGISTRATION_FORBIDDEN_TYPES);
    assertThat(category.getNamedInterfaces().getByName("application")).isEmpty();
    assertThat(category.getNamedInterfaces().getByName("provider")).isEmpty();
  }

  @Test
  void registrationFixtureDependsOnlyOnTheNarrowNamedInterface() throws ClassNotFoundException {
    JavaClass fixtureConfiguration = new ClassFileImporter()
      .importClasses(Class.forName(REGISTRATION_FIXTURE_CONFIGURATION))
      .get(REGISTRATION_FIXTURE_CONFIGURATION);
    List<Dependency> categoryDependencies = fixtureConfiguration
      .getDirectDependenciesFromSelf()
      .stream()
      .filter(dependency ->
        dependency.getTargetClass().getPackageName().startsWith("com.ferozfaiz.uniapi.category")
      )
      .toList();

    assertThat(allowedDependencies("com.ferozfaiz.uniapi.registrationfixture")).containsExactly(
      "category :: registration"
    );
    assertThat(categoryDependencies)
      .isNotEmpty()
      .allMatch(dependency ->
        REGISTRATION_EXPORTED_TYPES.contains(dependency.getTargetClass().getName())
      )
      .noneMatch(dependency ->
        REGISTRATION_FORBIDDEN_TYPES.contains(dependency.getTargetClass().getName())
      );
    assertThat(fixtureConfiguration.isAssignableTo(AdminCategoryIdentifierCodec.class)).isFalse();
  }

  @Test
  void catalogueRegistrationContributionUsesOnlyTheFrozenNamedInterface() {
    Set<String> matchedContributions = PRODUCTION_CLASSES.stream()
      .filter(type -> CATALOGUE_REGISTRATION_CONTRIBUTIONS.contains(type.getName()))
      .map(JavaClass::getName)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
    assertThat(matchedContributions).containsExactlyInAnyOrderElementsOf(
      CATALOGUE_REGISTRATION_CONTRIBUTIONS
    );

    for (String contribution : CATALOGUE_REGISTRATION_CONTRIBUTIONS) {
      List<Dependency> categoryDependencies = PRODUCTION_CLASSES.get(contribution)
        .getDirectDependenciesFromSelf()
        .stream()
        .filter(dependency ->
          dependency.getTargetClass().getPackageName().startsWith("com.ferozfaiz.uniapi.category")
        )
        .toList();
      assertThat(categoryDependencies)
        .as(contribution)
        .isNotEmpty()
        .allMatch(dependency ->
          REGISTRATION_EXPORTED_TYPES.contains(dependency.getTargetClass().getName())
        );
    }
  }

  @Test
  void registrationFoundationHasNoSpecialKey() throws IOException {
    // Literal tenant/system keys have no bytecode-level dependency or annotation model.
    var registrationFiles = Files.walk(REGISTRATION_MAIN)
      .filter(path -> path.toString().endsWith(".java"))
      .toList();

    assertThat(registrationFiles).isNotEmpty();
    for (Path file : registrationFiles) {
      assertThat(Files.readString(file))
        .as(file.toString())
        .doesNotContain("\"catalogue\"", "\"data\"", "\"gallery\"");
    }
  }

  @Test
  void catalogueUsesExactlyTheSixRetainedCategoryNamedInterfaces() throws ClassNotFoundException {
    assertThat(allowedDependencies("com.ferozfaiz.uniapi.catalogue")).containsExactlyInAnyOrder(
      "category :: registration",
      "category :: command",
      "category :: query",
      "category :: model",
      "category :: error",
      "category :: policy",
      "platform :: hierarchy",
      "shared :: error",
      "shared :: i18n",
      "shared :: pagination",
      "shared :: text"
    );

    Set<String> exposedCategoryTypes = categoryExposedTypes();
    assertThat(
      PRODUCTION_CLASSES.stream()
        .filter(type -> type.getPackageName().startsWith("com.ferozfaiz.uniapi.catalogue"))
        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
        .filter(dependency ->
          dependency.getTargetClass().getPackageName().startsWith("com.ferozfaiz.uniapi.category")
        )
    ).allMatch(dependency -> exposedCategoryTypes.contains(dependency.getTargetClass().getName()));
  }

  @Test
  void categoryNamedInterfacesAndRemovedMigrationTypesAreExact() throws IOException {
    assertThat(categoryNamedInterfaceNames()).containsExactlyInAnyOrder(
      "registration",
      "command",
      "query",
      "model",
      "error",
      "policy"
    );
    // Exact source membership is the frozen migration contract, rather than a dependency rule.
    try (var providerFiles = Files.list(CATEGORY_MAIN.resolve("application/provider"))) {
      assertThat(
        providerFiles
          .filter(path -> path.toString().endsWith(".java"))
          .map(path -> path.getFileName().toString())
          .sorted()
          .toList()
      ).containsExactly(
        "AdminCategoryBasicMutationPort.java",
        "AdminCategoryHierarchyMutationPort.java",
        "AdminCategoryHierarchyReadPort.java",
        "AdminCategoryLifecyclePort.java",
        "AdminCategoryOperationPort.java",
        "AdminCategoryOrderingPort.java",
        "AdminCategoryReadPort.java"
      );
    }
  }

  @Test
  void testModulesDeclareOnlyTheirExactNamedInterfaceDependencies() throws ClassNotFoundException {
    assertThat(allowedDependencies("com.ferozfaiz.uniapi.registrationfixture")).containsExactly(
      "category :: registration"
    );
    assertThat(
      allowedDependencies("com.ferozfaiz.uniapi.admincategorycontractfixture")
    ).containsExactlyInAnyOrder(
      "category :: registration",
      "category :: command",
      "category :: query",
      "category :: model",
      "category :: error",
      "category :: policy",
      "shared :: pagination"
    );
  }

  @Test
  void categoryHasNoCatalogueSpecialCaseAndPlatformRetainsStorageAuthorityOnly()
    throws IOException {
    // The forbidden system key is a literal policy with no semantic dependency equivalent.
    try (var files = Files.walk(CATEGORY_MAIN)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertThat(Files.readString(file)).as(file.toString()).doesNotContain("\"catalogue\"");
      }
    }

    noClasses()
      .that()
      .resideInAPackage("..platform.infrastructure.persistence.hierarchy..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..category.application..")
      .check(PRODUCTION_CLASSES);
  }

  @Test
  void catalogueAndDescriptorContributionsUseDirectPortsWithoutCompositeProvenance() {
    Set<String> requiredDirectPorts = DIRECT_PORT_TYPES.stream()
      .filter(name -> !name.endsWith("AdminCategoryOperationPort"))
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
    for (String adapter : List.of(
      "com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider",
      "com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProvider"
    )) {
      JavaClass adapterClass = PRODUCTION_CLASSES.get(adapter);
      Set<String> directlyImplementedInterfaces = adapterClass
        .getRawInterfaces()
        .stream()
        .map(JavaClass::getName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
      assertThat(directlyImplementedInterfaces).containsAll(requiredDirectPorts);
      assertThat(directlyImplementedInterfaces).doesNotContain(
        "com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort"
      );
    }
    for (String factory : List.of(
      "com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryRegistrationConfiguration",
      "com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProviderFactory"
    )) {
      assertThat(PRODUCTION_CLASSES.get(factory).getConstructorCallsFromSelf()).anyMatch(call ->
        call
          .getTargetOwner()
          .getName()
          .equals(
            "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts"
          )
      );
    }
  }

  @Test
  void categoryOwnsTheSoleNumericCodecImplementationAndBean() {
    var codecImplementations = PRODUCTION_CLASSES.stream()
      .filter(type -> !type.isInterface())
      .filter(type -> type.isAssignableTo(AdminCategoryIdentifierCodec.class))
      .map(type -> "src/main/java/" + type.getName().replace('.', '/') + ".java")
      .toList();
    var codecBeanDeclarations = PRODUCTION_CLASSES.stream()
      .flatMap(type -> type.getMethods().stream())
      .filter(method -> method.isAnnotatedWith(Bean.class))
      .filter(method ->
        method.getRawReturnType().isEquivalentTo(AdminCategoryIdentifierCodec.class)
      )
      .map(method -> "src/main/java/" + method.getOwner().getName().replace('.', '/') + ".java")
      .toList();

    assertThat(codecImplementations).containsExactly(
      "src/main/java/com/ferozfaiz/uniapi/category/application/registration/StandardNumericV1AdminCategoryIdentifierCodec.java"
    );
    assertThat(codecBeanDeclarations).containsExactly(
      "src/main/java/com/ferozfaiz/uniapi/category/application/registration/AdminCategoryRegistrationConfiguration.java"
    );
  }

  private static boolean usesCustomPersistenceAccess(JavaClass type) {
    boolean persistenceTypeDependency = type
      .getDirectDependenciesFromSelf()
      .stream()
      .map(dependency -> dependency.getTargetClass().getName())
      .anyMatch(name ->
        Set.of(
          "org.springframework.jdbc.core.JdbcTemplate",
          "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
          "java.sql.ResultSet"
        ).contains(name)
      );
    boolean nativeEntityManagerCall = type
      .getMethodCallsFromSelf()
      .stream()
      .anyMatch(call -> call.getName().equals("createNativeQuery"));
    boolean nativeRepositoryQuery = type
      .getMethods()
      .stream()
      .filter(method -> method.isAnnotatedWith(Query.class))
      .anyMatch(method -> method.getAnnotationOfType(Query.class).nativeQuery());
    return persistenceTypeDependency || nativeEntityManagerCall || nativeRepositoryQuery;
  }

  private static Set<String> allowedDependencies(String packageName) throws ClassNotFoundException {
    ApplicationModule module = Class.forName(packageName + ".package-info")
      .getPackage()
      .getAnnotation(ApplicationModule.class);
    assertThat(module).as("@ApplicationModule on %s", packageName).isNotNull();
    return Set.copyOf(Arrays.asList(module.allowedDependencies()));
  }

  private static Set<String> categoryNamedInterfaceNames() {
    return ApplicationModules.of(UniapiApplication.class)
      .getModuleByName("category")
      .orElseThrow()
      .getNamedInterfaces()
      .stream()
      .filter(namedInterface -> namedInterface.isNamed())
      .map(namedInterface -> namedInterface.getName())
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<String> categoryExposedTypes() {
    var category = ApplicationModules.of(UniapiApplication.class)
      .getModuleByName("category")
      .orElseThrow();
    return category
      .getNamedInterfaces()
      .stream()
      .filter(namedInterface ->
        Set.of("registration", "command", "query", "model", "error", "policy").contains(
          namedInterface.getName()
        )
      )
      .flatMap(namedInterface -> namedInterface.asJavaClasses().map(type -> type.getName()))
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static String sourcePath(JavaClass type) {
    return "src/main/java/" + type.getName().replace('.', '/') + ".java";
  }
}
