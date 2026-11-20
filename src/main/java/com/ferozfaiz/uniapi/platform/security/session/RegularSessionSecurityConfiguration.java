package com.ferozfaiz.uniapi.platform.security.session;

import com.ferozfaiz.uniapi.platform.security.authentication.IamAuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class RegularSessionSecurityConfiguration {

  @Bean
  @Order(3)
  SecurityFilterChain regularSessionSecurityFilterChain(
    HttpSecurity http,
    @Value("${spring.data.rest.base-path}") String basePath,
    IamAuthenticationProvider iamAuthenticationProvider,
    SessionCookieProperties properties,
    SessionJsonAuthenticationHandlers authenticationHandlers,
    SessionSecurityExceptionHandlers exceptionHandlers
  ) throws Exception {
    return http
      .securityMatcher(basePath + "/auth/**")
      .authenticationProvider(iamAuthenticationProvider)
      .csrf(csrf ->
        csrf.csrfTokenRepository(
          ScopedCookieCsrfTokenRepository.create(properties, SessionCookieScope.USER)
        )
      )
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      )
      .requestCache(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(authorize ->
        authorize
          .requestMatchers(HttpMethod.GET, basePath + "/auth/csrf")
          .permitAll()
          .requestMatchers(HttpMethod.POST, basePath + "/auth/login")
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .formLogin(form ->
        form
          .loginProcessingUrl(basePath + "/auth/login")
          .successHandler(authenticationHandlers.regularLoginSuccessHandler())
          .failureHandler(authenticationHandlers.regularLoginFailureHandler())
          .permitAll()
      )
      .logout(logout ->
        logout
          .logoutUrl(basePath + "/auth/logout")
          .deleteCookies(properties.userCookieName(), properties.userCsrfCookieName())
          .logoutSuccessHandler(authenticationHandlers.regularLogoutSuccessHandler())
      )
      .exceptionHandling(exceptions ->
        exceptions
          .authenticationEntryPoint(exceptionHandlers.authenticationEntryPoint())
          .accessDeniedHandler(exceptionHandlers.accessDeniedHandler())
      )
      .build();
  }
}
