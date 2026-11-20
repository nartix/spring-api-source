package com.ferozfaiz.uniapi.platform.security.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration
@EnableConfigurationProperties(SessionCookieProperties.class)
public class SessionCookieConfiguration {

  @Bean
  HttpSessionIdResolver httpSessionIdResolver(SessionCookieProperties properties) {
    return new ScopedCookieHttpSessionIdResolver(properties);
  }
}
