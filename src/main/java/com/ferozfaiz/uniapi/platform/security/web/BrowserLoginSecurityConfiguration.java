package com.ferozfaiz.uniapi.platform.security.web;

import com.ferozfaiz.uniapi.platform.security.authentication.IamAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class BrowserLoginSecurityConfiguration {

  @Bean
  @Order(5)
  SecurityFilterChain browserSecurityFilterChain(
    HttpSecurity http,
    IamAuthenticationProvider iamAuthenticationProvider
  ) throws Exception {
    return http
      .authenticationProvider(iamAuthenticationProvider)
      .authorizeHttpRequests(authorize ->
        authorize
          .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
          .permitAll()
          .requestMatchers("/login", "/error", "/actuator/health")
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .formLogin(Customizer.withDefaults())
      .logout(Customizer.withDefaults())
      .build();
  }
}
