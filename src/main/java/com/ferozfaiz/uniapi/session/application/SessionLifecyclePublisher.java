package com.ferozfaiz.uniapi.session.application;

import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SessionLifecyclePublisher {

  private final ApplicationEventPublisher events;

  public SessionLifecyclePublisher(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void publish(SessionLifecycleEvent event) {
    events.publishEvent(event);
  }
}
