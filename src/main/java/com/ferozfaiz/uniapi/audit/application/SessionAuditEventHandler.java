package com.ferozfaiz.uniapi.audit.application;

import com.ferozfaiz.uniapi.audit.domain.SessionAuditRecord;
import com.ferozfaiz.uniapi.audit.infrastructure.persistence.SessionAuditRecordRepositoryAdapter;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SessionAuditEventHandler {

  private final SessionAuditRecordRepositoryAdapter records;

  public SessionAuditEventHandler(SessionAuditRecordRepositoryAdapter records) {
    this.records = records;
  }

  @EventListener
  public void record(SessionLifecycleEvent event) {
    records.save(SessionAuditRecord.from(event));
  }
}
