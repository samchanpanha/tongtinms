package com.tongtin;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongtin.telegram.TelegramClient;
import com.tongtin.telegram.TelegramDigestJob;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 35 hermetic Telegram tests. The outbound {@link TelegramClient} is a
 * @MockBean capturing fake — the suite NEVER reaches api.telegram.org. Settings
 * are mutated directly in system_settings (catalog defaults cover the rest);
 * every test cleans up its own rows via SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelegramTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TelegramDigestJob digestJob;

    @MockBean
    private TelegramClient telegramClient;

    private final List<String> phones = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private String hostPhone;

    @BeforeEach
    void configureMock() {
        sent.clear();
        when(telegramClient.sendMessage(anyString(), anyLong(), anyString())).thenAnswer(inv -> {
            sent.add(inv.getArgument(2));
            return true;
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_events WHERE entity_type = 'OwnerAccount' "
                + "AND payload_json LIKE '%TELEGRAM%'");
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN "
                + "(SELECT id FROM users WHERE " + phonesFilter() + ")");
        List<String> keys = List.of("telegram_bot_token", "telegram_events_enabled",
                "telegram_daily_digest_enabled", "telegram_daily_digest_time");
        for (String key : keys) {
            jdbcTemplate.update("DELETE FROM system_settings WHERE key = ?", key);
        }
        String filter = phonesFilter();
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
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

    // ---------- helpers ----------

    private String uniquePhone(String prefix) {
        String phone = prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        phones.add(phone);
        return phone;
    }

    private String uniqueIp() {
        return "10.20." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String phonesFilter() {
        return "phone IN (" + phones.stream()
                .flatMap(p -> List.of(p, "+84" + p.substring(1)).stream())
                .map(p -> "'" + p + "'")
                .reduce((a, b) -> a + "," + b).orElse("''") + ")";
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

    private String registerHostAndStore() throws Exception {
        String raw = uniquePhone("0992");
        String token = registerHost(raw);
        hostPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM users WHERE phone IN (?, ?)", String.class,
                raw, "+84" + raw.substring(1));
        return token;
    }

    private void putSetting(String key, String value) {
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES (?, ?) "
                + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value", key, value);
    }

    private void linkChat(long chatId) {
        jdbcTemplate.update("UPDATE owner_accounts SET telegram_chat_id = ? "
                + "WHERE user_id = (SELECT id FROM users WHERE phone = ?)", chatId, hostPhone);
    }

    private long createGroup(String token, int shareCount, int cycleCount) throws Exception {
        String groupBody = """
                {"name":"Hoi TG","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """.formatted(shareCount, cycleCount);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
        for (int i = 0; i < shareCount; i++) {
            MvcResult m = mockMvc.perform(post("/api/v1/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"TV %d\",\"phone\":\"%s\"}".formatted(i, uniquePhone("0993"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            long memberId = objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
            mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return groupId;
    }

    private long openCycle(String token, long groupId) throws Exception {
        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();
    }

    private long firstShareId(String token, long groupId) throws Exception {
        MvcResult shares = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(shares.getResponse().getContentAsString()).path(0).path("id").asLong();
    }

    /** Settles cycle 1 of a 3-share BIDDING group: member0 wins, others owe 800000. */
    private long settleFirstCycle(String token, long groupId) throws Exception {
        long cycleId = openCycle(token, groupId);
        long winnerShare = firstShareId(token, groupId);
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
        return cycleId;
    }

    private JsonNode telegramStatus(String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/host/telegram")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private void backdateObligations() {
        jdbcTemplate.update("UPDATE ledger_entries SET due_at = now() - interval '3 days' "
                + "WHERE direction = 'IN' AND status IN ('UNPAID','PARTIAL')");
    }

    // ---------- tests ----------

    @Test
    @DisplayName("Host links and unlinks a Telegram chat id; audit rows are written")
    void linkUnlinkRoundTrip() throws Exception {
        String token = registerHostAndStore();

        JsonNode initial = telegramStatus(token);
        assertThat(initial.path("linked").asBoolean()).isFalse();
        assertThat(initial.path("chatId").isNull()).isTrue();

        mockMvc.perform(put("/api/v1/host/telegram/chat-id")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":123456789}"))
                .andExpect(status().isOk());

        JsonNode linked = telegramStatus(token);
        assertThat(linked.path("linked").asBoolean()).isTrue();
        assertThat(linked.path("chatId").asLong()).isEqualTo(123456789L);

        Integer linkAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'TELEGRAM_LINKED' "
                        + "AND actor_user_id = (SELECT id FROM users WHERE phone = ?)",
                Integer.class, hostPhone);
        assertThat(linkAudits).isEqualTo(1);

        mockMvc.perform(put("/api/v1/host/telegram/chat-id")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":null}"))
                .andExpect(status().isOk());

        JsonNode unlinked = telegramStatus(token);
        assertThat(unlinked.path("linked").asBoolean()).isFalse();
        Integer unlinkAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'TELEGRAM_UNLINKED' "
                        + "AND actor_user_id = (SELECT id FROM users WHERE phone = ?)",
                Integer.class, hostPhone);
        assertThat(unlinkAudits).isEqualTo(1);
    }

    @Test
    @DisplayName("Chat-id endpoint rejects bad ids and is host-only")
    void scopeAndValidation() throws Exception {
        String token = registerHostAndStore();

        mockMvc.perform(put("/api/v1/host/telegram/chat-id")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":-5}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/host/telegram"))
                .andExpect(status().isUnauthorized());

        String memberToken = memberTokenOfHost(token);
        mockMvc.perform(get("/api/v1/host/telegram")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test-send requires a linked chat and a configured bot token")
    void testSendGates() throws Exception {
        String token = registerHostAndStore();

        mockMvc.perform(post("/api/v1/host/telegram/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        linkChat(987654321L);
        mockMvc.perform(post("/api/v1/host/telegram/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        putSetting("telegram_bot_token", "123:TEST");
        mockMvc.perform(post("/api/v1/host/telegram/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("Kiểm tra Telegram");

        when(telegramClient.sendMessage(anyString(), anyLong(), anyString())).thenReturn(false);
        mockMvc.perform(post("/api/v1/host/telegram/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Daily digest messages only the host of a group with owed obligations")
    void dailyDigestCoversOwedGroupsOnly() throws Exception {
        String token = registerHostAndStore();
        linkChat(111222333L);
        putSetting("telegram_bot_token", "123:TEST");
        putSetting("telegram_daily_digest_enabled", "true");
        putSetting("telegram_daily_digest_time", "08:00");

        long groupId = createGroup(token, 3, 3);
        settleFirstCycle(token, groupId);
        backdateObligations();

        sent.clear();
        TelegramDigestJob.Result result = digestJob.process(java.time.Instant.now());
        assertThat(result.messagedOwners()).isEqualTo(1);
        assertThat(sent).hasSize(1);
        String text = sent.get(0);
        assertThat(text).contains("Hoi TG");
        assertThat(text).contains("2 khoản");
        assertThat(text).contains("1.600.000 đ");

        putSetting("telegram_daily_digest_enabled", "false");
        sent.clear();
        TelegramDigestJob.Result disabled = digestJob.process(java.time.Instant.now());
        assertThat(disabled.messagedOwners()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("Cycle lifecycle events reach the host chat (open, winner, payout)")
    void cycleLifecycleEventsReachHost() throws Exception {
        String token = registerHostAndStore();
        linkChat(555000111L);
        putSetting("telegram_bot_token", "123:TEST");

        long groupId = createGroup(token, 3, 3);

        long cycleId = openCycle(token, groupId);
        assertThat(sent).anyMatch(s -> s.contains("đã mở KỲ 1/3"));

        long winnerShare = firstShareId(token, groupId);
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(winnerShare)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(sent).anyMatch(s -> s.contains("thắng ký") && s.contains("nhận"));

        sent.clear();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(sent).anyMatch(s -> s.contains("thanh toán KỲ 1"));
    }

    @Test
    @DisplayName("Final-cycle payout raises the GROUP_COMPLETED event")
    void groupCompletedOnLastCycle() throws Exception {
        String token = registerHostAndStore();
        linkChat(555000222L);
        putSetting("telegram_bot_token", "123:TEST");

        long groupId = createGroup(token, 2, 2);
        long cycle1 = openCycle(token, groupId);
        long winnerShare = firstShareId(token, groupId);
        mockMvc.perform(post("/api/v1/cycles/" + cycle1 + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(winnerShare)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycle1 + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycle1 + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        sent.clear();
        long cycle2 = openCycle(token, groupId);
        mockMvc.perform(post("/api/v1/cycles/" + cycle2 + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycle2 + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(sent).anyMatch(s -> s.contains("HOÀN TẤT"));
    }

    @Test
    @DisplayName("Payment recording and late-fee assessment ping the host chat")
    void paymentAndLateFeeReachHost() throws Exception {
        String token = registerHostAndStore();
        linkChat(555000333L);
        putSetting("telegram_bot_token", "123:TEST");

        long groupId = createGroup(token, 3, 3);
        long cycleId = settleFirstCycle(token, groupId);

        long entryId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND direction = 'IN' "
                        + "AND type = 'CONTRIBUTION' AND status = 'UNPAID' ORDER BY id LIMIT 1",
                Long.class, cycleId);
        sent.clear();
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountMinor":800000,"currency":"VND","method":"CASH",
                                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                                """.formatted(entryId)))
                .andExpect(status().isCreated());
        assertThat(sent).anyMatch(s -> s.contains("ghi nhận") && s.contains("800.000 đ"));

        jdbcTemplate.update("UPDATE groups SET late_fee_type = 'FIXED', late_fee_value = 5000 WHERE id = ?", groupId);
        backdateObligations();
        sent.clear();
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/late-fees/assess")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(sent).anyMatch(s -> s.contains("phí trễ"));
    }

    private String memberTokenOfHost(String hostToken) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Thanh vien\",\"phone\":\"%s\"}".formatted(uniquePhone("0994"))))
                .andExpect(status().isCreated())
                .andReturn();
        long memberId = objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
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
}