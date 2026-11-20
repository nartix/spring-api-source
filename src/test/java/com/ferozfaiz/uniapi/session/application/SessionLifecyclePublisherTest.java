package com.ferozfaiz.uniapi.session.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionLifecyclePublisherTest {

  @Test
  void publishesLifecycleEventsThroughSpringEventPublisher() {
    List<Object> published = new ArrayList<>();
    SessionLifecyclePublisher publisher = new SessionLifecyclePublisher(published::add);
    SessionLifecycleEvent event = SessionLifecycleEvent.now(
      SessionLifecycleEvent.Type.USER_LOGIN_SUCCESS,
      SessionLifecycleEvent.Scope.USER,
      1L,
      "feroz",
      "hashed-session",
      "127.0.0.1",
      "test-agent",
      null
    );

    publisher.publish(event);

    assertThat(published).containsExactly(event);
  }
}
