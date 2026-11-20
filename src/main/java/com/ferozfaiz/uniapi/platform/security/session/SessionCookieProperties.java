package com.ferozfaiz.uniapi.platform.security.session;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.session")
public class SessionCookieProperties {

  @NotBlank
  private String userCookieName = "TGP_USER_SESSION";

  @NotBlank
  private String userCookiePath = "/";

  @NotBlank
  private String userCsrfCookieName = "TGP_USER_XSRF_TOKEN";

  @NotBlank
  private String userCsrfHeaderName = "X-TGP-USER-XSRF-TOKEN";

  @NotBlank
  private String adminCookieName = "TGP_ADMIN_SESSION";

  @NotBlank
  private String adminCookiePath = "/api/v1/admin";

  @NotBlank
  private String adminCsrfCookieName = "TGP_ADMIN_XSRF_TOKEN";

  @NotBlank
  private String adminCsrfHeaderName = "X-TGP-ADMIN-XSRF-TOKEN";

  @NotBlank
  private String sameSite = "Lax";

  private boolean secure = true;

  private String auditHmacSecret = "";

  public String userCookieName() {
    return userCookieName;
  }

  public void setUserCookieName(String userCookieName) {
    this.userCookieName = userCookieName;
  }

  public String userCookiePath() {
    return userCookiePath;
  }

  public void setUserCookiePath(String userCookiePath) {
    this.userCookiePath = userCookiePath;
  }

  public String userCsrfCookieName() {
    return userCsrfCookieName;
  }

  public void setUserCsrfCookieName(String userCsrfCookieName) {
    this.userCsrfCookieName = userCsrfCookieName;
  }

  public String userCsrfHeaderName() {
    return userCsrfHeaderName;
  }

  public void setUserCsrfHeaderName(String userCsrfHeaderName) {
    this.userCsrfHeaderName = userCsrfHeaderName;
  }

  public String adminCookieName() {
    return adminCookieName;
  }

  public void setAdminCookieName(String adminCookieName) {
    this.adminCookieName = adminCookieName;
  }

  public String adminCookiePath() {
    return adminCookiePath;
  }

  public void setAdminCookiePath(String adminCookiePath) {
    this.adminCookiePath = adminCookiePath;
  }

  public String adminCsrfCookieName() {
    return adminCsrfCookieName;
  }

  public void setAdminCsrfCookieName(String adminCsrfCookieName) {
    this.adminCsrfCookieName = adminCsrfCookieName;
  }

  public String adminCsrfHeaderName() {
    return adminCsrfHeaderName;
  }

  public void setAdminCsrfHeaderName(String adminCsrfHeaderName) {
    this.adminCsrfHeaderName = adminCsrfHeaderName;
  }

  public String sameSite() {
    return sameSite;
  }

  public void setSameSite(String sameSite) {
    this.sameSite = sameSite;
  }

  public boolean secure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public String auditHmacSecret() {
    return auditHmacSecret;
  }

  public void setAuditHmacSecret(String auditHmacSecret) {
    this.auditHmacSecret = auditHmacSecret;
  }

  public String sessionCookieName(SessionCookieScope scope) {
    return scope == SessionCookieScope.ADMIN ? adminCookieName : userCookieName;
  }

  public String sessionCookiePath(SessionCookieScope scope) {
    return scope == SessionCookieScope.ADMIN ? adminCookiePath : userCookiePath;
  }

  public String csrfCookieName(SessionCookieScope scope) {
    return scope == SessionCookieScope.ADMIN ? adminCsrfCookieName : userCsrfCookieName;
  }

  public String csrfHeaderName(SessionCookieScope scope) {
    return scope == SessionCookieScope.ADMIN ? adminCsrfHeaderName : userCsrfHeaderName;
  }

  @AssertTrue
  public boolean hasDistinctSessionCookieNames() {
    return !userCookieName.equals(adminCookieName);
  }

  @AssertTrue
  public boolean hasDistinctCsrfCookieNames() {
    return !userCsrfCookieName.equals(adminCsrfCookieName);
  }

  @AssertTrue
  public boolean hasDistinctCsrfHeaderNames() {
    return !userCsrfHeaderName.equalsIgnoreCase(adminCsrfHeaderName);
  }
}
