package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

@Configuration
public class AuthorizationServerPersistenceConfiguration {

  @Bean
  RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
    return new JdbcRegisteredClientRepository(jdbcOperations);
  }

  @Bean
  OAuth2AuthorizationService authorizationService(
    JdbcOperations jdbcOperations,
    RegisteredClientRepository registeredClientRepository
  ) {
    var authorizationService = new JdbcOAuth2AuthorizationService(
      jdbcOperations,
      registeredClientRepository
    );
    var objectMapper = oauthAuthorizationObjectMapper();
    var rowMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(
      registeredClientRepository
    );
    rowMapper.setObjectMapper(objectMapper);
    var parametersMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
    parametersMapper.setObjectMapper(objectMapper);
    authorizationService.setAuthorizationRowMapper(rowMapper);
    authorizationService.setAuthorizationParametersMapper(parametersMapper);
    return authorizationService;
  }

  @Bean
  OAuth2AuthorizationConsentService authorizationConsentService(
    JdbcOperations jdbcOperations,
    RegisteredClientRepository registeredClientRepository
  ) {
    return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
  }

  @Bean
  AuthorizationServerSettings authorizationServerSettings(OAuthSecurityProperties properties) {
    return AuthorizationServerSettings.builder().issuer(properties.getIssuer()).build();
  }

  @Bean
  PasswordEncoder oauthClientPasswordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  OAuthRegisteredClientSeeder oauthRegisteredClientSeeder(
    RegisteredClientRepository clients,
    PasswordEncoder oauthClientPasswordEncoder,
    OAuthSecurityProperties properties
  ) {
    return new OAuthRegisteredClientSeeder(clients, oauthClientPasswordEncoder, properties);
  }

  static ObjectMapper oauthAuthorizationObjectMapper() {
    var objectMapper = new ObjectMapper();
    ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();
    List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
    objectMapper.registerModules(securityModules);
    objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    objectMapper.addMixIn(AuthenticatedPrincipal.class, AuthenticatedPrincipalMixin.class);
    // Persisted JWT claims can contain either List.of implementation. Reuse Spring's
    // trusted immutable-list deserializer; do not widen default type acceptance.
    Class<?> listMixin = Objects.requireNonNull(
      objectMapper.findMixInClassFor(
        Collections.unmodifiableList(Collections.emptyList()).getClass()
      ),
      "Spring Security immutable-list mixin must be registered"
    );
    objectMapper.addMixIn(List.of().getClass(), listMixin);
    objectMapper.addMixIn(List.of("audience").getClass(), listMixin);
    return objectMapper;
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  private abstract static class AuthenticatedPrincipalMixin {

    @JsonIgnore
    abstract String getName();
  }
}
