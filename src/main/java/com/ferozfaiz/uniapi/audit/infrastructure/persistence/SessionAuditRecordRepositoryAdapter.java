package com.ferozfaiz.uniapi.audit.infrastructure.persistence;

import com.ferozfaiz.uniapi.audit.domain.SessionAuditRecord;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SessionAuditRecordRepositoryAdapter {

  private final SessionAuditRecordRepository records;

  public SessionAuditRecordRepositoryAdapter(SessionAuditRecordRepository records) {
    this.records = records;
  }

  @Transactional
  public void save(SessionAuditRecord record) {
    records.save(JpaSessionAuditRecord.from(record));
  }

  @Transactional(readOnly = true)
  public List<SessionAuditRecord> findAll() {
    return records.findAll().stream().map(JpaSessionAuditRecord::toDomain).toList();
  }
}
