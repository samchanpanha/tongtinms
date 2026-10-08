package com.tongtin.ledger.payments.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.ledger.dto.LateFeeAssessResponse;
import com.tongtin.ledger.payments.dto.DebtResponse;
import com.tongtin.ledger.payments.dto.PaymentCreateRequest;
import com.tongtin.ledger.payments.dto.PaymentResponse;
import com.tongtin.ledger.payments.service.PaymentService;
import com.tongtin.ledger.service.LateFeeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('HOST')")
public class PaymentController {

    private final PaymentService paymentService;
    private final LateFeeService lateFeeService;

    public PaymentController(PaymentService paymentService, LateFeeService lateFeeService) {
        this.paymentService = paymentService;
        this.lateFeeService = lateFeeService;
    }

    @PostMapping("/groups/{id}/payments")
    public ResponseEntity<PaymentResponse> record(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody PaymentCreateRequest request,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentResponse replay = paymentService.findReplayOrConflict(principal.ownerId(), id, request, idempotencyKey);
        if (replay != null) {
            return ResponseEntity.ok(replay);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.record(principal.userId(), principal.ownerId(), id, request, idempotencyKey));
    }

    @GetMapping("/groups/{id}/debts")
    public List<DebtResponse> debts(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long id) {
        return paymentService.debts(principal.ownerId(), id);
    }

    @PostMapping("/groups/{id}/late-fees/assess")
    public ResponseEntity<LateFeeAssessResponse> assessLateFees(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @PathVariable Long id) {
        return ResponseEntity.ok(lateFeeService.assess(principal.userId(), principal.ownerId(), id));
    }
}
