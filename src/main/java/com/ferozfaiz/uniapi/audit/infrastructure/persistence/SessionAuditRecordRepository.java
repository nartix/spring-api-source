package com.ferozfaiz.uniapi.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SessionAuditRecordRepository extends JpaRepository<JpaSessionAuditRecord, Long> {}
