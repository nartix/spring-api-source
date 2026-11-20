import org.cyclonedx.model.Component

plugins {
	java
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.cyclonedx.bom") version "3.2.4"
}

group = "com.ferozfaiz"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.0.2"
extra["springModulithVersion"] = "1.4.10"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.session:spring-session-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-hateoas")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("org.liquibase:liquibase-core")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("com.tngtech.archunit:archunit:1.4.1")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:vault")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("spring.profiles.active", "test")
	if (project.hasProperty("catalogueParentOptionsLargeProfile")) {
		systemProperty(
			"catalogueParentOptionsLargeProfile",
			project.property("catalogueParentOptionsLargeProfile").toString(),
		)
	}
}

tasks.test {
	testLogging {
		events("started", "passed", "skipped", "failed")
		showStandardStreams = true
	}
}

tasks.cyclonedxBom {
	projectType.set(Component.Type.APPLICATION)
	componentName.set(rootProject.name)
	componentGroup.set(project.group.toString())
	componentVersion.set(project.version.toString())
	jsonOutput.set(file("build/reports/sbom/${rootProject.name}-bom.json"))
	xmlOutput.set(file("build/reports/sbom/${rootProject.name}-bom.xml"))
}

val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

tasks.register<Exec>("format") {
	group = "formatting"
	description = "Formats Java and project files with Prettier."
	commandLine(npmCommand, "run", "format")
}

tasks.register<Exec>("formatCheck") {
	group = "verification"
	description = "Checks Java and project file formatting with Prettier."
	commandLine(npmCommand, "run", "format:check")
}

// Resolve compiler and runtime dependencies without starting tests or application services.
val developmentRuntime = files(configurations.runtimeClasspath, configurations.testRuntimeClasspath)
tasks.register("prepareDevelopmentDependencies") {
	group = "build setup"
	description = "Resolves development/test runtime dependencies without running tests or services."
	inputs.files(developmentRuntime)
	doLast {
		logger.lifecycle("Development runtime dependencies prepared.")
	}
}
