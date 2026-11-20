package com.ferozfaiz.uniapi.platform.infrastructure.persistence.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@EnableJdbcHttpSession
public class JdbcSessionConfig {}
