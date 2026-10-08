package com.tongtin.subscription.service;

import com.tongtin.subscription.entity.PaymentEvent;
import com.tongtin.subscription.repository.PaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Append-only payment forensics (V12). Recording a row must never break the
 * payment flow itself: serialization and persistence failures are swallowed
 * and logged, mirroring the notification-service failure invariant.
 */
@Service
public class PaymentEventService {

    public static final int MAX_PAYLOAD_CHARS = 8000;
    public static final int MAX_TRAN_ID_CHARS = 64;
    public static final int MAX_IP_CHARS = 64;

    private static final Logger log = LoggerFactory.getLogger(PaymentEventService.class);

    private final PaymentEventRepository repository;

    public PaymentEventService(PaymentEventRepository repository) {
        this.repository = repository;
    }

    public void record(String tranId, String source, String outcome, String payload, String remoteIp) {
        try {
            PaymentEvent event = new PaymentEvent();
            event.setTranId(truncate(tranId, MAX_TRAN_ID_CHARS));
            event.setSource(source);
            event.setOutcome(outcome);
            event.setPayload(truncate(payload != null && !payload.isBlank() ? payload : "{}", MAX_PAYLOAD_CHARS));
            event.setRemoteIp(truncate(remoteIp, MAX_IP_CHARS));
            repository.save(event);
        } catch (Exception ex) {
            log.error("Failed to record payment event {}/{} for {}: {}",
                    source, outcome, tranId, ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
