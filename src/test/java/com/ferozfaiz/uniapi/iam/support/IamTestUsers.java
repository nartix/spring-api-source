package com.ferozfaiz.uniapi.iam.support;

import com.ferozfaiz.uniapi.iam.domain.model.User;
import java.time.OffsetDateTime;

public final class IamTestUsers {

  public static final Long USER_ID = 1L;
  public static final String USERNAME = "feroz";
  public static final String EMAIL = "feroz@example.com";
  public static final String RAW_PASSWORD = "secret";
  public static final String PASSWORD_HASH = "hashed:secret";
  public static final OffsetDateTime FIXED_TIMESTAMP = OffsetDateTime.parse("2026-05-01T06:30:00Z");

  private static final String FIRST_NAME = "Feroz";
  private static final String LAST_NAME = "Faiz";
  private static final String SYSTEM_ACTOR = "system";

  private IamTestUsers() {}

  public static User testUser() {
    return activeUser(USER_ID, USERNAME, EMAIL);
  }

  public static User unsavedUser(String username, String email) {
    return activeUser(null, username, email);
  }

  public static User activeUser(Long id, String username, String email) {
    return user(id, username, email, true, true, true, true, FIRST_NAME, LAST_NAME);
  }

  public static User inactiveUser(Long id, String firstName, String lastName) {
    return user(id, USERNAME, EMAIL, false, true, true, true, firstName, lastName);
  }

  public static User lockedUser() {
    return user(USER_ID, USERNAME, EMAIL, true, true, false, true, FIRST_NAME, LAST_NAME);
  }

  public static User user(
    Long id,
    String username,
    String email,
    boolean active,
    boolean accountNonExpired,
    boolean accountNonLocked,
    boolean credentialsNonExpired,
    String firstName,
    String lastName
  ) {
    return new User(
      id,
      username,
      email,
      PASSWORD_HASH,
      firstName,
      lastName,
      active,
      accountNonExpired,
      accountNonLocked,
      credentialsNonExpired,
      null,
      null,
      FIXED_TIMESTAMP,
      FIXED_TIMESTAMP,
      SYSTEM_ACTOR,
      SYSTEM_ACTOR
    );
  }
}
