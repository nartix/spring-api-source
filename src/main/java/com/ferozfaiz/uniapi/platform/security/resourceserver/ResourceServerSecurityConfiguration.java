package com.ferozfaiz.uniapi.platform.security.resourceserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.platform.security.session.JwtSessionCredentialConflictFilter;
import com.ferozfaiz.uniapi.platform.security.session.ScopedCookieCsrfTokenRepository;
import com.ferozfaiz.uniapi.platform.security.session.SessionCookieProperties;
import com.ferozfaiz.uniapi.platform.security.session.SessionCookieScope;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(SessionCookieProperties.class)
public class ResourceServerSecurityConfiguration {

  @Bean
  @Order(4)
  SecurityFilterChain apiSecurityFilterChain(
    HttpSecurity http,
    @Value("${spring.data.rest.base-path}") String basePath,
    JwtAuthenticationConverter jwtAuthenticationConverter,
    ObjectMapper objectMapper,
    SessionCookieProperties sessionCookieProperties,
    MessageResolver messages
  ) throws Exception {
    return http
      .securityMatcher(basePath + "/**")
      .csrf(csrf ->
        csrf
          .csrfTokenRepository(
            ScopedCookieCsrfTokenRepository.create(sessionCookieProperties, SessionCookieScope.USER)
          )
          .ignoringRequestMatchers(
            request ->
              hasBearerAuthorization(request) ||
              !hasCookie(request, sessionCookieProperties.userCookieName())
          )
      )
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      )
      .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
      .exceptionHandling(exceptions ->
        exceptions
          .authenticationEntryPoint((request, response, exception) ->
            writeError(
              response,
              HttpStatus.UNAUTHORIZED,
              "UNAUTHENTICATED",
              "Authentication is required",
              request,
              objectMapper
            )
          )
          .accessDeniedHandler((request, response, exception) ->
            writeError(
              response,
              HttpStatus.FORBIDDEN,
              "FORBIDDEN",
              "Access is denied",
              request,
              objectMapper
            )
          )
      )
      .oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
      )
      .addFilterAfter(
        new JwtSessionCredentialConflictFilter(
          sessionCookieProperties.userCookieName(),
          objectMapper,
          messages
        ),
        BearerTokenAuthenticationFilter.class
      )
      .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("username");
    converter.setJwtGrantedAuthoritiesConverter(new JwtAuthorityConverter());
    return converter;
  }

  private static void writeError(
    HttpServletResponse response,
    HttpStatus status,
    String code,
    String message,
    HttpServletRequest request,
    ObjectMapper objectMapper
  ) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
      response.getOutputStream(),
      ApiErrorResponse.of(code, message, request.getRequestURI())
    );
  }

  private static boolean hasBearerAuthorization(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return (
      StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
    );
  }

  private static boolean hasCookie(HttpServletRequest request, String cookieName) {
    if (request.getCookies() == null) {
      return false;
    }
    for (var cookie : request.getCookies()) {
      if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
        return true;
      }
    }
    return false;
  }
}
