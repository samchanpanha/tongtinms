package com.tongtin.attachments.web;

import com.tongtin.attachments.dto.AttachmentResponse;
import com.tongtin.attachments.service.AttachmentService;
import com.tongtin.common.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HOST-only attachment endpoints. Uploads attach to a PAYMENT (receipt) or a
 * MEMBER profile; downloads stream the stored bytes back with the original
 * content type. Cross-owner identifiers return 404.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('HOST')")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/payments/{paymentId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> attachToPayment(@AuthenticationPrincipal AuthPrincipal principal,
                                                              @PathVariable("paymentId") Long paymentId,
                                                              @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                attachmentService.attachToPayment(principal.userId(), principal.ownerId(), paymentId, file));
    }

    @PostMapping(value = "/members/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> attachToMember(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @PathVariable("id") Long memberId,
                                                             @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                attachmentService.attachToMember(principal.userId(), principal.ownerId(), memberId, file));
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable("id") Long attachmentId) {
        AttachmentService.AttachmentStream stream = attachmentService.stream(principal.ownerId(), attachmentId);
        String safeName = stream.originalName() == null || stream.originalName().isBlank()
                ? "attachment" : stream.originalName();
        String disposition = ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .header("Content-Disposition", disposition)
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .contentLength(stream.content().length)
                .body(stream.content());
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable("id") Long attachmentId) {
        attachmentService.delete(principal.userId(), principal.ownerId(), attachmentId);
        return ResponseEntity.noContent().build();
    }
}