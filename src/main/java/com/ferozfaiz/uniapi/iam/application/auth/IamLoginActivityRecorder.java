package com.ferozfaiz.uniapi.iam.application.auth;

import com.ferozfaiz.uniapi.iam.api.auth.LoginActivityRecorder;
import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class IamLoginActivityRecorder implements LoginActivityRecorder {

  private final UserRepository users;
  private final Clock clock;

  @Autowired
  IamLoginActivityRecorder(UserRepository users) {
    this(users, Clock.systemUTC());
  }

  IamLoginActivityRecorder(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Override
  public void recordSuccessfulLogin(Long userId) {
    User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    users.save(withLastLoginAt(user, OffsetDateTime.now(clock)));
  }

  private static User withLastLoginAt(User user, OffsetDateTime loggedInAt) {
    return new User(
      user.id(),
      user.username(),
      user.email(),
      user.passwordHash(),
      user.firstName(),
      user.lastName(),
      user.active(),
      user.accountNonExpired(),
      user.accountNonLocked(),
      user.credentialsNonExpired(),
      loggedInAt,
      user.lastActivityAt(),
      user.createdAt(),
      user.updatedAt(),
      user.createdBy(),
      user.updatedBy()
    );
  }
}
