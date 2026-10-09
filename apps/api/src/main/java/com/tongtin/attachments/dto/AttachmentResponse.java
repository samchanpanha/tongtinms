package com.tongtin.attachments.dto;

import com.tongtin.attachments.entity.Attachment;
import java.time.Instant;

public record AttachmentResponse(
        Long id,
        String entityType,
        Long entityId,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getEntityType(),
                attachment.getEntityId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedAt());
    }
}