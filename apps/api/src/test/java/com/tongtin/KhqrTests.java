package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongtin.khqr.KhqrGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class KhqrTests {

    private static final String SETTING = "payments_khqr_enabled";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> phones = new ArrayList<>();
    private String hostAToken;
    private String hostBToken;

    @BeforeEach
    void registerHosts() throws Exception {
        hostAToken = registerHost(uniquePhone("0988"));
        hostBToken = registerHost(uniquePhone("0977"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM system_settings WHERE key = ?", SETTING);
        String filter = phonesFilter();
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM payment_allocations WHERE ledger_entry_id IN " + ledgerIds);
        jdbcTemplate.update("DELETE FROM payments WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("DELETE FROM bids WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("UPDATE group_shares SET won_cycle_id = NULL WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM cycles WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM group_shares WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
        jdbcTemplate.update("DELETE FROM member_profiles WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
    }

    private String phonesFilter() {
        return "phone IN (" + phones.stream()
                .flatMap(p -> List.of(p, "+84" + p.substring(1)).stream())
                .map(p -> "'" + p + "'")
                .reduce((a, b) -> a + "," + b).orElse("''") + ")";
    }

    private String uniquePhone(String prefix) {
        String phone = prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        phones.add(phone);
        return phone;
    }

    private String uniqueIp() {
        return "10.19." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String registerHost(String phone) throws Exception {
        String body = """
                {"fullName":"Chu Hoi","phone":"%s","password":"password123","confirmPassword":"password123","acceptTerms":true}
                """.formatted(phone);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private long createGroup(String token, int shareCount) throws Exception {
        String groupBody = """
                {"name":"Hoi QR","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """.formatted(shareCount, shareCount);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
        for (int i = 0; i < shareCount; i++) {
            JsonNode member = createMember(token, "TV %d".formatted(i));
            mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberProfileId\":%d,\"count\":1}".formatted(member.path("id").asLong())))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return groupId;
    }

    private JsonNode createMember(String token, String name) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"%s\",\"phone\":\"%s\"}".formatted(name, uniquePhone("0966"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(m.getResponse().getContentAsString());
    }

    private List<Long> memberIdsOf(String token, long groupId) throws Exception {
        MvcResult s = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode shares = objectMapper.readTree(s.getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        for (JsonNode share : shares) {
            ids.add(share.path("memberProfileId").asLong());
        }
        return ids;
    }

    private String memberToken(String hostToken, long memberId) throws Exception {
        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        String memberPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(memberPhone)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    /**
     * Settled 3-share cycle: member0 wins (pay-out PAID), member1+member2 owe an
     * 800000 contribution each (UNPAID). Returns entry ids plus member ids in
     * share_no order: memberIds[0] owns the winning share.
     */
    private Fixture settledCycle(String token) throws Exception {
        long groupId = createGroup(token, 3);
        List<Long> memberIds = memberIdsOf(token, groupId);

        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();

        MvcResult shares = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode shareList = objectMapper.readTree(shares.getResponse().getContentAsString());
        long winnerShare = shareList.path(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(winnerShare)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Map<String, Object>> contributions = jdbcTemplate.queryForList(
                "SELECT id, share_id FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION' ORDER BY share_id",
                cycleId);
        long payoutEntry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'PAYOUT'", Long.class, cycleId);
        return new Fixture(groupId, cycleId,
                ((Number) contributions.get(0).get("id")).longValue(),
                ((Number) contributions.get(1).get("id")).longValue(),
                payoutEntry, memberIds);
    }

    private record Fixture(long groupId, long cycleId, long entryShare2, long entryShare3,
                           long payoutEntry, List<Long> memberIds) {
    }

    private void backdateDue(long entryId) {
        jdbcTemplate.update("UPDATE ledger_entries SET due_at = now() - interval '3 days' WHERE id = ?", entryId);
    }

    private void putSetting(String value) {
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES (?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                SETTING, value);
    }

    private JsonNode debts(String token, long groupId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/groups/" + groupId + "/debts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode statement(String token, long groupId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/me/groups/" + groupId + "/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult render(String token, long entryId) throws Exception {
        return mockMvc.perform(get("/api/v1/obligations/" + entryId + "/khqr")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private long pay(String token, long groupId, long entryId, long amountMinor) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountMinor":%d,"currency":"VND","method":"CASH",
                                 "allocations":[{"ledgerEntryId":%d,"amountMinor":%d}]}
                                """.formatted(amountMinor, entryId, amountMinor)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private static String tagValue(String tlv, String tag) {
        int idx = tlv.indexOf(tag);
        if (idx < 0) {
            return null;
        }
        int len = Integer.parseInt(tlv.substring(idx + 2, idx + 4));
        return tlv.substring(idx + 4, idx + 4 + len);
    }

    private static String billReference(String payload) {
        int cursor = 0;
        while (cursor + 4 <= payload.length()) {
            String tag = payload.substring(cursor, cursor + 2);
            int len = Integer.parseInt(payload.substring(cursor + 2, cursor + 4));
            String value = payload.substring(cursor + 4, cursor + 4 + len);
            if ("62".equals(tag)) {
                for (int c = 0; c + 4 <= value.length(); ) {
                    String subTag = value.substring(c, c + 2);
                    int subLen = Integer.parseInt(value.substring(c + 2, c + 4));
                    if ("01".equals(subTag)) {
                        return value.substring(c + 4, c + 4 + subLen);
                    }
                    c += 4 + subLen;
                }
                return null;
            }
            cursor += 4 + len;
        }
        return null;
    }

    @Test
    void crcGoldenVectorMatchesEmvco() {
        assertThat(KhqrGenerator.crc16Of("123456789")).isEqualTo(0x29B1);
    }

    @Test
    void generatorEmitsValidKhqrWithReference() {
        String payload = KhqrGenerator.generate("Hoi QR", "PHNOM PENH",
                300_000, "VND", (short) 0, "TONGTIN HOI-2026-001-O123");
        assertThat(payload).startsWith("000201010212");
        assertThat(payload).contains("0011com.tongtin");
        assertThat(payload).contains("0106Hoi QR");
        assertThat(payload).contains("52045999");
        assertThat(payload).contains("5303704");
        assertThat(payload).contains("5802KH");
        assertThat(tagValue(payload, "54")).isEqualTo("300000");
        assertThat(KhqrGenerator.isValid(payload)).isTrue();
        assertThat(billReference(payload)).endsWith("HOI-2026-001-O123");
    }

    @Test
    void amountRenderingHonorsCurrencyExponent() {
        String vnd = KhqrGenerator.generate("Hoi", "PHNOM PENH", 800_000, "VND", (short) 0, "R1");
        assertThat(tagValue(vnd, "54")).isEqualTo("800000");
        assertThat(tagValue(vnd, "53")).isEqualTo("704");

        String usd = KhqrGenerator.generate("Hoi", "PHNOM PENH", 1_250_45, "USD", (short) 2, "R2");
        assertThat(tagValue(usd, "54")).isEqualTo("1250.45");
        assertThat(tagValue(usd, "53")).isEqualTo("840");

        String kh = KhqrGenerator.generate("Hoi", "PHNOM PENH", 1234, "KHR", (short) 0, "R3");
        assertThat(tagValue(kh, "54")).isEqualTo("1234");
        assertThat(tagValue(kh, "53")).isEqualTo("116");

        String thb = KhqrGenerator.generate("Hoi", "PHNOM PENH", 42, "THB", (short) 2, "R4");
        assertThat(tagValue(thb, "54")).isEqualTo("0.42");
    }

    @Test
    void unsupportedCurrencyRejected() {
        assertThatThrownBy(() -> KhqrGenerator.generate("Hoi", "PHNOM PENH", 100, "CNY", (short) 2, "R"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tamperedPayloadFailsCrc() {
        String payload = KhqrGenerator.generate("Hoi QR", "PHNOM PENH",
                100_000, "VND", (short) 0, "TONGTIN HOI-2026-001-O123");
        String withBadCrc = payload.substring(0, payload.length() - 4) + "FFFF";
        assertThat(KhqrGenerator.isValid(withBadCrc)).isFalse();

        String withTweakedAmount = payload.replace("100000", "999999");
        assertThat(KhqrGenerator.isValid(withTweakedAmount)).isFalse();
    }

    @Test
    void debtsCarryKhqrReflectingRemaining() throws Exception {
        Fixture f = settledCycle(hostAToken);
        backdateDue(f.entryShare2());

        JsonNode list = debts(hostAToken, f.groupId());
        assertThat(list).hasSize(1);
        String khqr = list.path(0).path("khqr").asText();
        assertThat(khqr).isNotBlank();
        assertThat(KhqrGenerator.isValid(khqr)).isTrue();
        assertThat(tagValue(khqr, "54")).isEqualTo("800000");
        assertThat(billReference(khqr)).endsWith("-O" + f.entryShare2());

        pay(hostAToken, f.groupId(), f.entryShare2(), 500_000);
        JsonNode partial = debts(hostAToken, f.groupId());
        assertThat(partial.path(0).path("remainingMinor").asLong()).isEqualTo(300_000);
        assertThat(tagValue(partial.path(0).path("khqr").asText(), "54")).isEqualTo("300000");
    }

    @Test
    void statementCarriesKhqrOnlyForUnpaidObligations() throws Exception {
        Fixture f = settledCycle(hostAToken);
        String member1Token = memberToken(hostAToken, f.memberIds().get(1));
        String member0Token = memberToken(hostAToken, f.memberIds().get(0));

        JsonNode lines = statement(member1Token, f.groupId())
                .path("shares").path(0).path("entries");
        JsonNode share2 = findByEntry(lines, f.entryShare2());
        assertThat(share2 != null).isTrue();
        assertThat(share2.path("khqr").asText()).isNotBlank();
        assertThat(share2.path("remaining").path("amountMinor").asLong()).isEqualTo(800_000);

        JsonNode winnerLines = statement(member0Token, f.groupId())
                .path("shares").path(0).path("entries");
        JsonNode payout = findByEntry(winnerLines, f.payoutEntry());
        assertThat(payout != null).isTrue();
        assertThat(payout.path("khqr").isNull()).isTrue();
    }

    @Test
    void renderScopeBookendAndMime() throws Exception {
        Fixture f = settledCycle(hostAToken);
        backdateDue(f.entryShare2());

        MvcResult hostRender = render(hostAToken, f.entryShare2());
        assertThat(hostRender.getResponse().getStatus()).isEqualTo(200);
        assertThat(hostRender.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        byte[] png = hostRender.getResponse().getContentAsByteArray();
        assertThat(png.length).isGreaterThan(100);
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');

        assertThat(render(hostBToken, f.entryShare2()).getResponse().getStatus()).isEqualTo(404);
        mockMvc.perform(get("/api/v1/obligations/" + f.entryShare2() + "/khqr"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberRendersOwnShareOnly() throws Exception {
        Fixture f = settledCycle(hostAToken);
        backdateDue(f.entryShare2());
        String member1Token = memberToken(hostAToken, f.memberIds().get(1));
        String member2Token = memberToken(hostAToken, f.memberIds().get(2));

        MvcResult own = render(member1Token, f.entryShare2());
        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        assertThat(own.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);

        assertThat(render(member2Token, f.entryShare2()).getResponse().getStatus()).isEqualTo(404);
        assertThat(render(member1Token, f.payoutEntry()).getResponse().getStatus()).isEqualTo(404);
        assertThat(render(member1Token, 999_999L).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void disabledSettingOmitsKhqrAndBlocksRender() throws Exception {
        Fixture f = settledCycle(hostAToken);
        backdateDue(f.entryShare2());
        String member1Token = memberToken(hostAToken, f.memberIds().get(1));

        putSetting("false");
        JsonNode list = debts(hostAToken, f.groupId());
        assertThat(list.path(0).path("khqr").isNull()).isTrue();
        assertThat(statement(member1Token, f.groupId())
                .path("shares").path(0).path("entries").findValues("khqr")
                .stream().noneMatch(JsonNode::isTextual)).isTrue();
        assertThat(render(hostAToken, f.entryShare2()).getResponse().getStatus()).isEqualTo(404);

        putSetting("true");
        JsonNode reEnabled = debts(hostAToken, f.groupId());
        assertThat(reEnabled.path(0).path("khqr").asText()).isNotBlank();
        assertThat(render(hostAToken, f.entryShare2()).getResponse().getStatus()).isEqualTo(200);
    }

    private static JsonNode findByEntry(JsonNode entries, long entryId) {
        for (JsonNode entry : entries) {
            if (entry.path("entryId").asLong() == entryId) {
                return entry;
            }
        }
        return null;
    }
}