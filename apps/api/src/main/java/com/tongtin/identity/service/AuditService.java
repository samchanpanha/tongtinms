package com.tongtin.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongtin.identity.entity.AuditEvent;
import com.tongtin.identity.repository.AuditEventRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Step 16 hardening: shared audit writer for every money/winner action.
 * Writes IN the caller's transaction (commits/rolls back with the financial
 * change). Serialization can never break the business write: a JSON failure
 * degrades to an empty payload {}.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    public void record(Long actorUserId, String entityType, Long entityId, String action, Map<String, Object> payload) {
        auditEventRepository.save(AuditEvent.of(
                actorUserId, entityType, String.valueOf(entityId), action, toJson(payload)));
    }

    public void record(Long actorUserId, String entityType, String entityId, String action, Map<String, Object> payload) {
        auditEventRepository.save(AuditEvent.of(
                actorUserId, entityType, entityId, action, toJson(payload)));
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }
}