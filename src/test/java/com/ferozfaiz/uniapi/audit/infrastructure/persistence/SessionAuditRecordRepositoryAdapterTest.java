package com.ferozfaiz.uniapi.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.audit.domain.SessionAuditRecord;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionAuditRecordRepositoryAdapterTest {

  @Test
  void adapterPersistsBoundedAuditRecordsWithoutRawSessionIds() {
    SessionAuditRecordRepository repository = mock(SessionAuditRecordRepository.class);
    when(repository.save(any(JpaSessionAuditRecord.class))).thenAnswer(invocation ->
      invocation.getArgument(0)
    );
    SessionAuditRecordRepositoryAdapter adapter = new SessionAuditRecordRepositoryAdapter(
      repository
    );

    adapter.save(
      new SessionAuditRecord(
        SessionLifecycleEvent.Scope.USER,
        SessionLifecycleEvent.Type.USER_LOGIN_SUCCESS,
        1L,
        "feroz",
        "hashed-session-id",
        "127.0.0.1",
        "test-agent",
        null,
        Instant.parse("2026-05-31T00:00:00Z")
      )
    );

    when(repository.findAll()).thenReturn(repositoryRecords(repository));

    var records = adapter.findAll();
    assertThat(records).hasSize(1);
    assertThat(records.getFirst().sessionIdHash()).isEqualTo("hashed-session-id");
    assertThat(records.getFirst().sessionIdHash()).doesNotContain("raw-session-id");
  }

  private static java.util.List<JpaSessionAuditRecord> repositoryRecords(
    SessionAuditRecordRepository repository
  ) {
    return org.mockito.Mockito.mockingDetails(repository)
      .getInvocations()
      .stream()
      .filter(invocation -> invocation.getMethod().getName().equals("save"))
      .map(invocation -> (JpaSessionAuditRecord) invocation.getArgument(0))
      .toList();
  }
}
