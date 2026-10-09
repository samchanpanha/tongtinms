package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Step 32 (V15): attachments — receipts on payments + documents on members.
 * Storage is BYTEA in Postgres; size + type limits come from SECURITY settings.
 * Cross-owner identifiers are 404, anonymous is 401, disallowed types / oversize
 * / empty uploads are 400 and never partially persist.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttachmentTests {

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
        hostAPhone = "0989" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostBPhone = "0978" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostAToken = registerHost(hostAPhone);
        hostBToken = registerHost(hostBPhone);
    }

    @AfterEach
    void cleanUp() {
        String filter = "phone IN ('" + hostAPhone + "','" + hostBPhone + "','+84"
                + hostAPhone.substring(1) + "','+84" + hostBPhone.substring(1) + "')";
        String ownerIds = "(SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))";
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN " + ownerIds + ")";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
        jdbcTemplate.update("DELETE FROM attachments WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM payment_allocations WHERE ledger_entry_id IN " + ledgerIds);
        jdbcTemplate.update("DELETE FROM payments WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("DELETE FROM bids WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("UPDATE group_shares SET won_cycle_id = NULL WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM cycles WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM group_shares WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM groups WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM member_profiles WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
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

    private String uniqueIp() {
        return "10.17." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        return "0965" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private long createGroup(String token, int shareCount) throws Exception {
        String groupBody = """
                {"name":"Hoi Attachments","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
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
            MvcResult m = mockMvc.perform(post("/api/v1/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"TV %d\",\"phone\":\"%s\"}".formatted(i, randomPhone())))
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

    private long createMember(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Member Doc\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long recordPayment(String token, long groupId) throws Exception {
        JsonNode cycle = openCycle(token, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(token, groupId);
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shares.path(0).path("id").asLong())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Long contribution = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION' ORDER BY share_id LIMIT 1",
                Long.class, cycleId);
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800000,\"currency\":\"VND\",\"method\":\"CASH\","
                                + "\"allocations\":[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}".formatted(contribution)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private JsonNode openCycle(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode sharesOf(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockMultipartFile png(String name, String content) {
        return new MockMultipartFile("file", name, "image/png", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pdf(String name, String content) {
        return new MockMultipartFile("file", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile bytesOf(String contentType, long size) {
        byte[] payload = new byte[(int) size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        return new MockMultipartFile("file", "big.bin", contentType, payload);
    }

    private void putSetting(String key, String value) {
        jdbcTemplate.update(
                "INSERT INTO system_settings (key, value) VALUES (?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                key, value);
    }

    private void clearSetting(String key) {
        jdbcTemplate.update("DELETE FROM system_settings WHERE key = ?", key);
    }

    private long attachmentCountForPhone(String phone) {
        String normalized = phone.startsWith("0") ? "+84" + phone.substring(1) : phone;
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attachments WHERE owner_id IN "
                        + "(SELECT id FROM owner_accounts WHERE user_id IN "
                        + "(SELECT id FROM users WHERE phone IN (?, ?)))",
                Long.class, phone, normalized);
    }

    @Test
    void uploadReceiptOnPaymentStreamsBackAndAppearsInHistory() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        long paymentId = recordPayment(hostAToken, groupId);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(png("receipt.png", "PNG-PAYLOAD-123"))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode meta = objectMapper.readTree(upload.getResponse().getContentAsString());
        long attachmentId = meta.path("id").asLong();
        assertThat(meta.path("entityType").asText()).isEqualTo("PAYMENT");
        assertThat(meta.path("entityId").asLong()).isEqualTo(paymentId);
        assertThat(meta.path("originalName").asText()).isEqualTo("receipt.png");
        assertThat(meta.path("contentType").asText()).isEqualTo("image/png");
        assertThat(meta.path("sizeBytes").asLong()).isEqualTo("PNG-PAYLOAD-123".length());

        MvcResult download = mockMvc.perform(get("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray())
                .isEqualTo("PNG-PAYLOAD-123".getBytes(StandardCharsets.UTF_8));
        assertThat(download.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("attachment");

        JsonNode history = objectMapper.readTree(mockMvc.perform(get("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(1);
        JsonNode datum = history.path(0);
        assertThat(datum.path("amountMinor").asLong()).isEqualTo(800_000);
        assertThat(datum.path("allocations")).hasSize(1);
        assertThat(datum.path("attachments")).hasSize(1);
        assertThat(datum.path("attachments").path(0).path("id").asLong()).isEqualTo(attachmentId);
    }

    @Test
    void memberDocumentUploadAppearsInDirectoryThenDeleteRemovesIt() throws Exception {
        long memberId = createMember(hostAToken);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                        .file(pdf("contract.pdf", "PDF-CONTRACT"))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        long attachmentId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("id").asLong();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode member = list.path(0);
        assertThat(member.path("attachments")).hasSize(1);
        assertThat(member.path("attachments").path(0).path("originalName").asText()).isEqualTo("contract.pdf");

        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isNoContent());

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(after.path(0).path("attachments")).isEmpty();

        mockMvc.perform(get("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void disallowedTypeAndEmptyUploadRejected() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        long paymentId = recordPayment(hostAToken, groupId);

        MockMultipartFile notAllowed = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(notAllowed)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isBadRequest());

        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(empty)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isBadRequest());

        assertThat(attachmentCountForPhone(hostAPhone)).isZero();
    }

    @Test
    void oversizeUploadRejectedAgainstConfiguredLimit() throws Exception {
        long memberId = createMember(hostAToken);
        try {
            mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                            .file(bytesOf("image/png", 11L * 1024 * 1024))
                            .header("Authorization", "Bearer " + hostAToken))
                    .andExpect(status().isBadRequest());

            putSetting("storage_attachment_max_mb", "1");

            mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                            .file(bytesOf("image/png", 2L * 1024 * 1024))
                            .header("Authorization", "Bearer " + hostAToken))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                            .file(png("ok.png", "small"))
                            .header("Authorization", "Bearer " + hostAToken))
                    .andExpect(status().isOk());
        } finally {
            clearSetting("storage_attachment_max_mb");
        }

        assertThat(attachmentCountForPhone(hostAPhone)).isEqualTo(1);
    }

    @Test
    void settingNarrowedAllowedTypesRejectsOthers() throws Exception {
        long memberId = createMember(hostAToken);
        try {
            putSetting("storage_attachment_allowed_types", "application/pdf");

            mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                            .file(png("not-allowed.png", "x"))
                            .header("Authorization", "Bearer " + hostAToken))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(multipart("/api/v1/members/{id}/attachments", memberId)
                            .file(pdf("allowed.pdf", "x"))
                            .header("Authorization", "Bearer " + hostAToken))
                    .andExpect(status().isOk());
        } finally {
            clearSetting("storage_attachment_allowed_types");
        }
    }

    @Test
    void crossOwnerUploadDownloadDeleteAreAll404() throws Exception {
        long groupId = createGroup(hostAToken, 3);
        long paymentId = recordPayment(hostAToken, groupId);
        MvcResult upload = mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(png("receipt.png", "SECRET"))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        long attachmentId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("id").asLong();

        long foreignPayment = recordPayment(hostBToken, createGroup(hostBToken, 3));
        mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", foreignPayment)
                        .file(png("x.png", "x"))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(png("y.png", "y"))
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        assertThat(attachmentCountForPhone(hostAPhone)).isEqualTo(1);
    }

    @Test
    void anonymousRequestsAre401() throws Exception {
        mockMvc.perform(multipart("/api/v1/payments/1/attachments").file(png("a.png", "a")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/members/1/attachments").file(png("b.png", "b")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attachments/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/attachments/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/groups/1/payments"))
                .andExpect(status().isUnauthorized());
    }
}