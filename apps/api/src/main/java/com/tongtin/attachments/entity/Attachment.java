package com.tongtin.attachments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One evidence file attached to a PAYMENT (receipt) or a MEMBER profile.
 * Bytes are stored in PostgreSQL (BYTEA); money/math never depend on these rows.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}