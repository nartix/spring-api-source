package com.ferozfaiz.uniapi.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(OAuthSecurityProperties.class)
public class SecurityConfiguration {}
