package com.tongtin.groups.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.money.Currency;
import com.tongtin.common.money.CurrencyRepository;
import com.tongtin.groups.dto.GroupCreateRequest;
import com.tongtin.groups.dto.GroupPatchRequest;
import com.tongtin.groups.dto.GroupResponse;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Year;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final CurrencyRepository currencyRepository;
    private final com.tongtin.identity.repository.OwnerAccountRepository ownerAccountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public GroupService(
            GroupRepository groupRepository,
            CurrencyRepository currencyRepository,
            com.tongtin.identity.repository.OwnerAccountRepository ownerAccountRepository) {
        this.groupRepository = groupRepository;
        this.currencyRepository = currencyRepository;
        this.ownerAccountRepository = ownerAccountRepository;
    }

    @Transactional
    public GroupResponse create(Long ownerId, GroupCreateRequest request) {
        OwnerAccount owner = ownerAccountRepository.findById(ownerId).orElse(null);
        if (owner != null) {
            java.time.Instant now = java.time.Instant.now();
            boolean isLifetime = "LIFETIME".equalsIgnoreCase(owner.getSubscriptionStatus());
            if (!isLifetime) {
                java.time.Instant endsAt = owner.getSubscriptionEndsAt() != null ? owner.getSubscriptionEndsAt() : now;
                java.time.Instant graceEnd = endsAt.plus(java.time.Duration.ofDays(3));
                if (now.isAfter(graceEnd)) {
                    throw new com.tongtin.common.errors.ForbiddenException("Gói sử dụng của bạn đã hết hạn. Vui lòng nâng cấp gói để tạo thêm dây hụi mới.");
                }
            }
        }

        Group group = new Group();
        group.setOwnerId(ownerId);
        group.setCode(nextCode(ownerId));
        group.setName(request.name());
        group.setType(request.type());
        group.setBaseAmount(request.baseAmount());
        group.setShareCount(request.shareCount());
        group.setCycleUnit(request.cycleUnit());
        group.setCycleCount(request.cycleCount());
        group.setCurrency(request.currency().toUpperCase());
        group.setStartAt(request.startAt());
        group.setHostFeeType(request.hostFeeType() != null ? request.hostFeeType() : "NONE");
        group.setHostFeeMinor(request.hostFeeMinor() != null ? request.hostFeeMinor() : 0L);
        group.setHostFeeBps(request.hostFeeBps() != null ? request.hostFeeBps() : 0);
        group.setMinBid(request.minBid() != null ? request.minBid() : 0L);
        group.setMaxBid(request.maxBid() != null ? request.maxBid() : 0L);
        group.setBidStep(request.bidStep() != null ? request.bidStep() : 1000L);
        group.setTieBreak(request.tieBreak() != null ? request.tieBreak() : "EARLIEST_BID");
        group.setLateFeeType(request.lateFeeType() != null ? request.lateFeeType() : "NONE");
        group.setLateFeeValue(request.lateFeeValue() != null ? request.lateFeeValue() : 0L);
        group.setBidOpenOffset(request.bidOpenOffset() != null ? request.bidOpenOffset() : 0);
        group.setBidCloseOffset(request.bidCloseOffset() != null ? request.bidCloseOffset() : 0);
        group.setAllowMultiShare(request.allowMultiShare() != null ? request.allowMultiShare() : true);
        validate(group);
        groupRepository.save(group);
        groupRepository.flush();
        entityManager.refresh(group);
        return GroupResponse.from(group);
    }

    @Transactional
    public GroupResponse update(Long ownerId, Long id, GroupPatchRequest request) {
        Group group = findOwned(ownerId, id);
        if (!"DRAFT".equals(group.getStatus()) || group.isRulesFrozen()) {
            throw new BadRequestException("Only DRAFT groups can be edited");
        }
        if (request.name() != null) {
            group.setName(request.name());
        }
        if (request.type() != null) {
            group.setType(request.type());
        }
        if (request.baseAmount() != null) {
            group.setBaseAmount(request.baseAmount());
        }
        if (request.shareCount() != null) {
            group.setShareCount(request.shareCount());
        }
        if (request.cycleUnit() != null) {
            group.setCycleUnit(request.cycleUnit());
        }
        if (request.cycleCount() != null) {
            group.setCycleCount(request.cycleCount());
        }
        if (request.currency() != null) {
            group.setCurrency(request.currency().toUpperCase());
        }
        if (request.startAt() != null) {
            if (request.startAt().isEmpty()) {
                group.setStartAt(null);
            } else {
                group.setStartAt(java.time.LocalDate.parse(request.startAt()));
            }
        }
        if (request.hostFeeType() != null) {
            group.setHostFeeType(request.hostFeeType());
        }
        if (request.hostFeeMinor() != null) {
            group.setHostFeeMinor(request.hostFeeMinor());
        }
        if (request.hostFeeBps() != null) {
            group.setHostFeeBps(request.hostFeeBps());
        }
        if (request.minBid() != null) {
            group.setMinBid(request.minBid());
        }
        if (request.maxBid() != null) {
            group.setMaxBid(request.maxBid());
        }
        if (request.bidStep() != null) {
            group.setBidStep(request.bidStep());
        }
        if (request.tieBreak() != null) {
            group.setTieBreak(request.tieBreak());
        }
        if (request.lateFeeType() != null) {
            group.setLateFeeType(request.lateFeeType());
        }
        if (request.lateFeeValue() != null) {
            group.setLateFeeValue(request.lateFeeValue());
        }
        if (request.bidOpenOffset() != null) {
            group.setBidOpenOffset(request.bidOpenOffset());
        }
        if (request.bidCloseOffset() != null) {
            group.setBidCloseOffset(request.bidCloseOffset());
        }
        if (request.allowMultiShare() != null) {
            group.setAllowMultiShare(request.allowMultiShare());
        }
        validate(group);
        groupRepository.save(group);
        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(Long ownerId) {
        return groupRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(GroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long ownerId, Long id) {
        return GroupResponse.from(findOwned(ownerId, id));
    }

    private Group findOwned(Long ownerId, Long id) {
        return groupRepository.findByOwnerIdAndId(ownerId, id)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private void validate(Group group) {
        if (group.getShareCount() < 2) {
            throw new BadRequestException("shareCount must be >= 2");
        }
        if (group.getBaseAmount() <= 0) {
            throw new BadRequestException("baseAmount must be > 0");
        }
        if (group.getCycleCount() != group.getShareCount()) {
            throw new BadRequestException("cycleCount must equal shareCount");
        }
        if (group.getMaxBid() >= group.getBaseAmount()) {
            throw new BadRequestException("maxBid must be less than baseAmount");
        }
        if (group.getMinBid() > group.getMaxBid()) {
            throw new BadRequestException("minBid must be <= maxBid");
        }
        if ("BIDDING".equals(group.getType()) && group.getBidStep() <= 0) {
            throw new BadRequestException("bidStep must be > 0 for BIDDING groups");
        }
        Currency currency = currencyRepository.findById(group.getCurrency())
                .orElseThrow(() -> new BadRequestException("Currency not supported: " + group.getCurrency()));
        if (!currency.isActive()) {
            throw new BadRequestException("Currency is inactive: " + group.getCurrency());
        }
    }

    private String nextCode(Long ownerId) {
        int year = Year.now().getValue();
        String prefix = "HOI-" + year + "-";
        int max = groupRepository.findCodesByOwnerStartingWith(ownerId, prefix)
                .stream()
                .map(code -> code.substring(prefix.length()))
                .filter(suffix -> suffix.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return prefix + String.format("%03d", max + 1);
    }
}
