package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class Phase3FeatureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostAPhone;
    private String hostBPhone;
    private String hostAToken;
    private String hostBToken;

    @BeforeEach
    void registerTwoHosts() throws Exception {
        hostAPhone = "0988" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostBPhone = "0977" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostAToken = registerHost(hostAPhone);
        hostBToken = registerHost(hostBPhone);
    }

    @AfterEach
    void cleanUp() {
        String filter = ownerFilter();
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
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

    private String ownerFilter() {
        return "phone IN ('" + hostAPhone + "','" + hostBPhone + "','+84" + hostAPhone.substring(1) + "','+84" + hostBPhone.substring(1) + "')";
    }

    private String uniqueIp() {
        return "10.20." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        return "0966" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String registerHost(String phone) throws Exception {
        String body = """
                {"fullName":"Chu Hoi","phone":"%s","password":"password123","confirmPassword":"password123","acceptTerms":true}
                """.formatted(phone);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("tokens").path("accessToken").asText();
    }

    private long createGroup(String token, int shareCount) throws Exception {
        String groupBody = """
                {"name":"Hoi Test","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
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
            String memberBody = "{\"fullName\":\"TV %d\",\"phone\":\"%s\"}".formatted(i, randomPhone());
            MvcResult m = mockMvc.perform(post("/api/v1/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(memberBody))
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

    @Test
    void step40_randomEqualMaxWinnerSelection() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        // Open cycle 1
        MvcResult cycleRes = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(cycleRes.getResponse().getContentAsString()).path("id").asLong();

        // Close with RANDOM_EQUAL_MAX mode
        String closeBody = """
                {"winnerSelectionMode":"RANDOM_EQUAL_MAX"}
                """;
        MvcResult closeRes = mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode closeResult = objectMapper.readTree(closeRes.getResponse().getContentAsString());
        assertThat(closeResult.path("status").asText()).isEqualTo("PAYOUT_PENDING");
        assertThat(closeResult.path("winningBid").asLong()).isEqualTo(500_000L);
        assertThat(closeResult.path("winnerShareId").asLong()).isPositive();
    }

    @Test
    void step38_paymentInvoiceReturnsFullDetails() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        // Open and close cycle
        MvcResult cycleRes = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(cycleRes.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerSelectionMode\":\"RANDOM_EQUAL_MAX\"}"))
                .andExpect(status().isOk());

        // Find a CONTRIBUTION entry to pay
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
                "SELECT id, amount_minor FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION'",
                cycleId);
        assertThat(entries).isNotEmpty();
        long entryId = ((Number) entries.get(0).get("id")).longValue();
        long amt = ((Number) entries.get(0).get("amount_minor")).longValue();

        // Pay it
        String payBody = """
                {"amountMinor":%d,"currency":"VND","method":"BANK_TRANSFER",
                 "paidAt":"2026-10-09T10:00:00Z","note":"Chuyen khoan dot 1",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":%d}]}
                """.formatted(amt, entryId, amt);
        MvcResult payRes = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody))
                .andExpect(status().isCreated())
                .andReturn();
        long paymentId = objectMapper.readTree(payRes.getResponse().getContentAsString()).path("id").asLong();

        // Request invoice
        MvcResult invRes = mockMvc.perform(get("/api/v1/groups/" + groupId + "/payments/" + paymentId + "/invoice")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode invoice = objectMapper.readTree(invRes.getResponse().getContentAsString());
        assertThat(invoice.path("invoiceNo").asText()).startsWith("INV-");
        assertThat(invoice.path("group").path("id").asLong()).isEqualTo(groupId);
        assertThat(invoice.path("host").path("displayName").asText()).isNotBlank();
        assertThat(invoice.path("payer").path("memberName").asText()).isNotBlank();
        assertThat(invoice.path("payment").path("amountMinor").asLong()).isEqualTo(amt);
        assertThat(invoice.path("totalAllocatedMinor").asLong()).isEqualTo(amt);
        assertThat(invoice.path("lines").isArray()).isTrue();
        assertThat(invoice.path("lines").size()).isGreaterThanOrEqualTo(1);

        // Cross-host forbidden/not-found
        mockMvc.perform(get("/api/v1/groups/" + groupId + "/payments/" + paymentId + "/invoice")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void step42_ledgerPaginationAndFiltering() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        // Open and close cycle
        MvcResult cycleRes = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(cycleRes.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerSelectionMode\":\"RANDOM_EQUAL_MAX\"}"))
                .andExpect(status().isOk());

        // 1. Unpaginated request (backward-compat)
        MvcResult unpaginatedRes = mockMvc.perform(get("/api/v1/groups/" + groupId + "/ledger")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode unpaginated = objectMapper.readTree(unpaginatedRes.getResponse().getContentAsString());
        assertThat(unpaginated.path("entries").size()).isGreaterThan(0);
        assertThat(unpaginated.path("page").isNull()).isTrue();

        // 2. Paginated request with page=0, size=1
        MvcResult paginatedRes = mockMvc.perform(get("/api/v1/groups/" + groupId + "/ledger?page=0&size=1")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paginated = objectMapper.readTree(paginatedRes.getResponse().getContentAsString());
        assertThat(paginated.path("entries").size()).isEqualTo(1);
        assertThat(paginated.path("page").path("number").asInt()).isEqualTo(0);
        assertThat(paginated.path("page").path("size").asInt()).isEqualTo(1);
        assertThat(paginated.path("page").path("totalElements").asLong()).isGreaterThan(1);
        assertThat(paginated.path("page").path("totalPages").asInt()).isGreaterThan(1);

        // 3. Filter by type=CONTRIBUTION
        MvcResult contribRes = mockMvc.perform(get("/api/v1/groups/" + groupId + "/ledger?page=0&size=20&type=CONTRIBUTION")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode contrib = objectMapper.readTree(contribRes.getResponse().getContentAsString());
        for (JsonNode entry : contrib.path("entries")) {
            assertThat(entry.path("type").asText()).isEqualTo("CONTRIBUTION");
        }

        // 4. Filter by status=UNPAID
        MvcResult unpaidRes = mockMvc.perform(get("/api/v1/groups/" + groupId + "/ledger?page=0&size=20&status=UNPAID")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode unpaid = objectMapper.readTree(unpaidRes.getResponse().getContentAsString());
        for (JsonNode entry : unpaid.path("entries")) {
            assertThat(entry.path("status").asText()).isEqualTo("UNPAID");
        }
    }
}
