package com.ferozfaiz.uniapi.platform.security.session;

import com.ferozfaiz.uniapi.platform.security.authentication.AdminAuthenticationProvider;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
public class AdminSessionSecurityConfiguration {

  @Bean
  @Order(2)
  SecurityFilterChain adminSessionSecurityFilterChain(
    HttpSecurity http,
    @Value("${spring.data.rest.base-path}") String basePath,
    AdminAuthenticationProvider adminAuthenticationProvider,
    SessionCookieProperties properties,
    SessionJsonAuthenticationHandlers authenticationHandlers,
    SessionSecurityExceptionHandlers exceptionHandlers,
    ApplicationEventPublisher events,
    SessionIdHashingService sessionIdHashing
  ) throws Exception {
    return http
      .securityMatcher(basePath + "/admin/**")
      .authenticationProvider(adminAuthenticationProvider)
      .csrf(csrf ->
        csrf.csrfTokenRepository(
          ScopedCookieCsrfTokenRepository.create(properties, SessionCookieScope.ADMIN)
        )
      )
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      )
      .requestCache(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(authorize ->
        authorize
          .requestMatchers(HttpMethod.GET, basePath + "/admin/auth/csrf")
          .permitAll()
          .requestMatchers(HttpMethod.POST, basePath + "/admin/auth/login")
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .formLogin(form ->
        form
          .loginProcessingUrl(basePath + "/admin/auth/login")
          .successHandler(authenticationHandlers.adminLoginSuccessHandler())
          .failureHandler(authenticationHandlers.adminLoginFailureHandler())
          .permitAll()
      )
      .logout(logout ->
        logout
          .logoutUrl(basePath + "/admin/auth/logout")
          .deleteCookies(properties.adminCookieName(), properties.adminCsrfCookieName())
          .logoutSuccessHandler(authenticationHandlers.adminLogoutSuccessHandler())
      )
      .exceptionHandling(exceptions ->
        exceptions
          .authenticationEntryPoint(exceptionHandlers.authenticationEntryPoint())
          .accessDeniedHandler(
            adminAccessDeniedHandler(exceptionHandlers, events, sessionIdHashing)
          )
      )
      .build();
  }

  AccessDeniedHandler adminAccessDeniedHandler(
    SessionSecurityExceptionHandlers exceptionHandlers,
    ApplicationEventPublisher events,
    SessionIdHashingService sessionIdHashing
  ) {
    return (request, response, exception) -> {
      events.publishEvent(
        SessionLifecycleEvent.now(
          SessionLifecycleEvent.Type.ADMIN_ACCESS_DENIED,
          SessionLifecycleEvent.Scope.ADMIN,
          null,
          null,
          sessionIdHashing.hash(request.getRequestedSessionId()),
          request.getRemoteAddr(),
          request.getHeader("User-Agent"),
          exception.getClass().getSimpleName()
        )
      );
      exceptionHandlers.adminAccessDeniedHandler().handle(request, response, exception);
    };
  }
}
