package com.tongtin.demo;

import com.tongtin.common.errors.ConflictException;
import com.tongtin.cycles.bids.dto.BidSubmitRequest;
import com.tongtin.cycles.bids.service.BidService;
import com.tongtin.cycles.dto.CycleResponse;
import com.tongtin.cycles.service.CycleCloseService;
import com.tongtin.cycles.service.CycleService;
import com.tongtin.groups.dto.GroupCreateRequest;
import com.tongtin.groups.dto.GroupResponse;
import com.tongtin.groups.shares.ShareService;
import com.tongtin.groups.shares.dto.ShareAssignRequest;
import com.tongtin.groups.shares.dto.ShareResponse;
import com.tongtin.groups.service.GroupService;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.members.dto.MemberCreateRequest;
import com.tongtin.members.dto.MemberResponse;
import com.tongtin.members.service.MemberService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Step 16 demo seeder. Enabled ONLY by --app.seed-demo=true (default OFF).
 * Builds the exact STATUS.md / 01-DOMAIN fixture: host 0900111001, one BIDDING
 * group, N=10, C=1_000_000, hostFeeType FIXED_PER_CYCLE 100_000, ten cycles
 * played to COMPLETED through the REAL services (so all business invariants,
 * rounding, audit and ledger writes apply):
 *   cycle 1: B=200_000 -> net 7_100_000
 *   cycle 2: B=150_000 -> net 7_700_000
 *   cycles 3-9: B=100_000
 *   cycle 10: OPEN, B=0 -> net 8_900_000  (lowest ALIVE share #10 wins)
 * Idempotent: if the demo host phone already exists the run logs and exits.
 * All demo rows belong to that host, so the normal DB-clean procedure removes
 * them.
 *
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--app.seed-demo=true
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    static final String DEMO_HOST_PHONE = "0900111001";
    static final int N = 10;

    private final AuthService authService;
    private final MemberService memberService;
    private final GroupService groupService;
    private final ShareService shareService;
    private final CycleService cycleService;
    private final BidService bidService;
    private final CycleCloseService cycleCloseService;
    private final UserRepository userRepository;
    private final OwnerAccountRepository ownerAccountRepository;

    public DemoSeeder(AuthService authService,
                      MemberService memberService,
                      GroupService groupService,
                      ShareService shareService,
                      CycleService cycleService,
                      BidService bidService,
                      CycleCloseService cycleCloseService,
                      UserRepository userRepository,
                      OwnerAccountRepository ownerAccountRepository) {
        this.authService = authService;
        this.memberService = memberService;
        this.groupService = groupService;
        this.shareService = shareService;
        this.cycleService = cycleService;
        this.bidService = bidService;
        this.cycleCloseService = cycleCloseService;
        this.userRepository = userRepository;
        this.ownerAccountRepository = ownerAccountRepository;
    }

    @Override
    public void run(String... args) {
        try {
            authService.registerOwner(new RegisterOwnerRequest(
                    "Chu Hoi Demo", DEMO_HOST_PHONE, "demo1234", "demo1234", null, null, true));
        } catch (ConflictException ex) {
            log.info("Demo host {} already exists - skipping demo seed", DEMO_HOST_PHONE);
            return;
        }

        User user = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(DEMO_HOST_PHONE))
                .orElseThrow(() -> new IllegalStateException("demo host user not found"));
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("demo owner account not found"));
        Long userId = user.getId();
        Long ownerId = owner.getId();

        List<Long> memberIds = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            MemberResponse member = memberService.create(ownerId, new MemberCreateRequest(
                    "Thanh Vien " + String.format("%02d", i),
                    "0900" + String.format("%06d", 100_000 + i), null));
            memberIds.add(member.id());
        }

        GroupResponse group = groupService.create(ownerId, new GroupCreateRequest(
                "Hoi Dem Giao (10 ky)",
                "BIDDING",
                1_000_000,
                N,
                "WEEK",
                N,
                "VND",
                null,
                "FIXED_PER_CYCLE",
                100_000L,
                null,
                0L,
                900_000L,
                10_000L,
                null,
                null,
                null,
                null,
                7,
                null));
        Long groupId = group.id();

        List<Long> shareIds = new ArrayList<>();
        for (long memberId : memberIds) {
            List<ShareResponse> assigned = shareService.assign(ownerId, groupId,
                    new ShareAssignRequest(memberId, 1));
            shareIds.add(assigned.get(assigned.size() - 1).id());
        }
        memberService.setLogin(ownerId, memberIds.get(0), "demo1234");
        shareService.start(ownerId, groupId);
        log.info("Demo group {} created with {} shares; playing 10 cycles...", groupId, N);

        for (int cycleNo = 1; cycleNo <= N; cycleNo++) {
            CycleResponse opened = cycleService.open(userId, ownerId, groupId);
            if (cycleNo < N) {
                long bid = switch (cycleNo) {
                    case 1 -> 200_000;
                    case 2 -> 150_000;
                    default -> 100_000;
                };
                bidService.submit(userId, ownerId, opened.id(),
                        new BidSubmitRequest(shareIds.get(cycleNo - 1), bid));
            }
            CycleResponse summary = cycleCloseService.closeAndCalculate(userId, ownerId, opened.id(), null);
            cycleCloseService.confirmPayout(userId, ownerId, opened.id());
            log.info("Cycle {} done: winnerShareId={} netPayout={}",
                    cycleNo, summary.winnerShareId(), summary.netPayout());
        }
        log.info("Demo seed complete: host {}, group {}, {} cycles played to COMPLETED",
                DEMO_HOST_PHONE, groupId, N);
    }
}