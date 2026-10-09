package com.tongtin.attachments.repository;

import com.tongtin.attachments.entity.Attachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByOwnerIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
            Long ownerId, String entityType, Long entityId);

    List<Attachment> findByOwnerIdAndEntityTypeAndEntityIdInOrderByUploadedAtDesc(
            Long ownerId, String entityType, Collection<Long> entityIds);

    Optional<Attachment> findByIdAndOwnerId(Long id, Long ownerId);
}