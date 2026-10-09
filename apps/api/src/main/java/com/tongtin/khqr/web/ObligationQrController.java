package com.tongtin.khqr.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.khqr.ObligationQrService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 34: renders one obligation's KHQR as a PNG. Hosts (own group) and
 * members (own share) both reach it; unknown/cross-scope/disabling -> 404.
 */
@RestController
@RequestMapping("/api/v1/obligations")
@PreAuthorize("hasAnyRole('HOST','MEMBER')")
public class ObligationQrController {

    private static final int QR_SIZE = 480;

    private final ObligationQrService obligationQrService;

    public ObligationQrController(ObligationQrService obligationQrService) {
        this.obligationQrService = obligationQrService;
    }

    @GetMapping("/{entryId}/khqr")
    public ResponseEntity<byte[]> khqr(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable("entryId") Long entryId) {
        String role = principal.roles() != null && principal.roles().contains("HOST")
                ? "HOST" : "MEMBER";
        String payload = obligationQrService.renderable(role, principal.userId(),
                principal.ownerId(), entryId);
        if (payload == null) {
            throw new NotFoundException("khqr not available");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(renderPng(payload));
    }

    private static byte[] renderPng(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("failed to render KHQR", ex);
        }
    }
}