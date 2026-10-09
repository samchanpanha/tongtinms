package com.tongtin.attachments.service;

import com.tongtin.attachments.dto.AttachmentResponse;
import com.tongtin.attachments.entity.Attachment;
import com.tongtin.attachments.repository.AttachmentRepository;
import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.ledger.payments.entity.Payment;
import com.tongtin.ledger.payments.repository.PaymentRepository;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.settings.service.SettingsService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachment storage and metadata. Files attach to a PAYMENT (receipt) or a
 * MEMBER profile (paperwork). Legal size and content types come from SECURITY
 * settings, so limits need no migration. Every query is owner-scoped; a
 * cross-owner id is indistinguishable from a missing one.
 */
@Service
public class AttachmentService {

    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_MEMBER = "MEMBER";

    public static final String DEFAULT_ALLOWED_TYPES = "image/png,image/jpeg,image/gif,image/webp,"
            + "application/pdf,"
            + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
            + "application/vnd.ms-excel,text/csv";

    private static final int DEFAULT_MAX_MB = 10;

    private final AttachmentRepository attachmentRepository;
    private final PaymentRepository paymentRepository;
    private final GroupRepository groupRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             PaymentRepository paymentRepository,
                             GroupRepository groupRepository,
                             MemberProfileRepository memberProfileRepository,
                             SettingsService settingsService,
                             AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.paymentRepository = paymentRepository;
        this.groupRepository = groupRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    @Transactional
    public AttachmentResponse attachToPayment(Long userId, Long ownerId, Long paymentId, MultipartFile file) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        groupRepository.findByOwnerIdAndId(ownerId, payment.getGroupId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return store(userId, ownerId, TYPE_PAYMENT, paymentId, file);
    }

    @Transactional
    public AttachmentResponse attachToMember(Long userId, Long ownerId, Long memberId, MultipartFile file) {
        memberProfileRepository.findByOwnerIdAndId(ownerId, memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        return store(userId, ownerId, TYPE_MEMBER, memberId, file);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> metadataFor(Long ownerId, String entityType, Long entityId) {
        return attachmentRepository
                .findByOwnerIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(ownerId, entityType, entityId)
                .stream().map(AttachmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<AttachmentResponse>> metadataForMany(Long ownerId, String entityType,
                                                              Collection<Long> entityIds) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository
                .findByOwnerIdAndEntityTypeAndEntityIdInOrderByUploadedAtDesc(ownerId, entityType, entityIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Attachment::getEntityId,
                        LinkedHashMap::new,
                        Collectors.mapping(AttachmentResponse::from, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public AttachmentStream stream(Long ownerId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findByIdAndOwnerId(attachmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        return new AttachmentStream(attachment.getContent(), attachment.getContentType(),
                attachment.getOriginalName());
    }

    @Transactional
    public void delete(Long userId, Long ownerId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findByIdAndOwnerId(attachmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        attachmentRepository.delete(attachment);
        auditService.record(userId, "Attachment", attachmentId, "ATTACHMENT_DELETED",
                Map.of("entityType", attachment.getEntityType(),
                        "entityId", attachment.getEntityId(),
                        "originalName", attachment.getOriginalName(),
                        "sizeBytes", attachment.getSizeBytes()));
    }

    private AttachmentResponse store(Long userId, Long ownerId, String entityType, Long entityId,
                                     MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("File content type is required");
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if (!allowedTypes().contains(normalizedType)) {
            throw new BadRequestException("File type not allowed: " + contentType);
        }
        int maxMb = settingsService.getInt("storage_attachment_max_mb", DEFAULT_MAX_MB);
        long capBytes = (long) maxMb * 1024 * 1024;
        if (file.getSize() > capBytes) {
            throw new BadRequestException("File too large; maximum is " + maxMb + " MB");
        }

        Attachment attachment = new Attachment();
        attachment.setOwnerId(ownerId);
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setOriginalName(file.getOriginalFilename());
        attachment.setContentType(normalizedType);
        attachment.setSizeBytes(file.getSize());
        attachment.setContent(readBytes(file));
        attachment.setUploadedByUserId(userId);
        Attachment saved = attachmentRepository.save(attachment);

        auditService.record(userId, "Attachment", saved.getId(), "ATTACHMENT_UPLOADED",
                Map.of("entityType", entityType,
                        "entityId", entityId,
                        "originalName", safeName(saved.getOriginalName()),
                        "contentType", saved.getContentType(),
                        "sizeBytes", saved.getSizeBytes()));
        return AttachmentResponse.from(saved);
    }

    private Set<String> allowedTypes() {
        return Stream.of(settingsService
                        .getString("storage_attachment_allowed_types", DEFAULT_ALLOWED_TYPES)
                        .split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String trimmed = name.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    public record AttachmentStream(byte[] content, String contentType, String originalName) {
    }
}