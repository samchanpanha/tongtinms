package com.tongtin.ledger.service;

import com.tongtin.cycles.entity.Cycle;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.ledger.engine.CycleResult;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only ledger obligations for a settled cycle.
 * Module boundary: no HTTP, no engine math — amounts come from FormulaEngine results.
 *
 * Per cycle: one CONTRIBUTION (IN) per paying share, one HOST_FEE (OUT) if T > 0,
 * one PAYOUT (OUT) to the winner if netPayout > 0. All start UNPAID with the
 * cycle due date; payments mark them PAID in later steps.
 */
@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void writeCycleSettlement(Group group, Cycle cycle, List<GroupShare> shares,
                                     long winnerShareId, CycleResult result) {
        for (GroupShare share : shares) {
            if (share.getId() == winnerShareId) {
                continue;
            }
            boolean dead = "DEAD".equals(share.getStatus()) || "DEFAULTED".equals(share.getStatus());
            long amount = dead ? result.deadPay() : result.alivePay();
            if (amount <= 0) {
                continue;
            }
            save(group, cycle, share, share.getMemberProfileId(),
                    "CONTRIBUTION", "IN", amount, cycle.getDueAt());
        }

        if (result.T() > 0) {
            save(group, cycle, null, null, "HOST_FEE", "OUT", result.T(), cycle.getDueAt());
        }
        if (result.netPayout() > 0) {
            GroupShare winner = shares.stream()
                    .filter(s -> s.getId() == winnerShareId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Winner share missing from share list"));
            save(group, cycle, winner, winner.getMemberProfileId(),
                    "PAYOUT", "OUT", result.netPayout(), cycle.getDueAt());
        }
    }

    @Transactional
    public void markCyclePayoutPaid(Long cycleId) {
        ledgerEntryRepository.markCyclePayoutPaid(cycleId);
    }

    private void save(Group group, Cycle cycle, GroupShare share, Long memberProfileId,
                      String type, String direction, long amount, Instant dueAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.setGroupId(group.getId());
        entry.setCycleId(cycle.getId());
        entry.setShareId(share != null ? share.getId() : null);
        entry.setMemberProfileId(memberProfileId);
        entry.setType(type);
        entry.setDirection(direction);
        entry.setAmountMinor(amount);
        entry.setCurrency(group.getCurrency());
        entry.setStatus("UNPAID");
        entry.setDueAt(dueAt);
        ledgerEntryRepository.save(entry);
    }
}
