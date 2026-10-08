package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

/**
 * Step 30 exports: CSV (UTF-8 BOM + RFC 4180 quoting + formula-injection
 * guard) and real XLSX via Apache POI for ledger, profit, and member
 * statement. Same services and permissions as the Step 15 JSON reports —
 * export adds no new exposure. Money renders as exact major-unit decimals
 * (VND exponent 0 = plain integer).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdPhones = new ArrayList<>();
    private String hostAToken;
    private String hostBToken;

    @BeforeEach
    void registerTwoHosts() throws Exception {
        createdPhones.add("0988" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        createdPhones.add("0977" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        hostAToken = register(createdPhones.get(createdPhones.size() - 2));
        hostBToken = register(createdPhones.get(createdPhones.size() - 1));
    }

    @AfterEach
    void cleanUp() {
        String filter = phonesFilter();
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
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
    }

    @Test
    void hostLedgerCsvHasBomHeaderGuardAndTotals() throws Exception {
        Fixture f = fixture();
        JsonNode totals = getJson("/api/v1/groups/" + f.groupId() + "/ledger", hostAToken);
        String totalIn = totals.path("totalIn").path("amountMinor").asText();

        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk()).andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("tongtin-ledger-g" + f.groupId() + ".csv");
        assertThat(new String(bytes, 0, 3, StandardCharsets.UTF_8)).isEqualTo("\uFEFF");

        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("groupName,currency,entryId,cycleNo,shareNo,type");
        assertThat(csv).contains("CONTRIBUTION");
        assertThat(csv).contains("PAYOUT");
        // formula-injection guard on the member named `=SUM(A1), "B"`
        assertThat(csv).contains("\"'=SUM(A1), \"\"B\"\"\"");
        // TOTAL_IN row carries the JSON report total in the amount column
        String totalInLine = csv.lines().filter(l -> l.contains("TOTAL_IN")).findFirst().orElseThrow();
        assertThat(totalInLine.split(",")[9]).isEqualTo(totalIn);
    }

    @Test
    void hostLedgerXlsxRoundTripsThroughPoi() throws Exception {
        Fixture f = fixture();

        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger")
                        .param("format", "xlsx")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk()).andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getContentType())
                .startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("tongtin-ledger-g" + f.groupId() + ".xlsx");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("ledger");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("groupName");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("amount");
            Row last = sheet.getRow(sheet.getLastRowNum());
            assertThat(last.getCell(5).getStringCellValue()).isEqualTo("TOTAL_OUT");
            assertThat(last.getCell(9).getNumericCellValue()).isGreaterThan(0);
        }
    }

    @Test
    void hostProfitCsvCarriesSettledTotalRow() throws Exception {
        Fixture f = fixture();
        JsonNode profit = getJson("/api/v1/groups/" + f.groupId() + "/profit", hostAToken);
        String settled = profit.path("totals").path("settledHostFee").path("amountMinor").asText();
        assertThat(settled).isNotBlank();

        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/profit")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk()).andReturn();
        String csv = result.getResponse().getContentAsString();

        assertThat(csv).startsWith("\uFEFF" + "groupName,currency,formulaVersion,cycleNo");
        String totalLine = csv.lines().filter(l -> l.contains(",TOTAL,")).findFirst().orElseThrow();
        assertThat(totalLine.split(",")[13]).isEqualTo(settled);
        assertThat(totalLine.split(",")[11]).isEqualTo(
                profit.path("totals").path("hostFee").path("amountMinor").asText());
    }

    @Test
    void memberStatementExportIsScopedToOwnGroup() throws Exception {
        Fixture f = fixture();
        String memberToken = login(hostAToken, f.memberIds.get(0), f.memberPhones.get(0));

        MvcResult own = mockMvc.perform(get("/api/v1/me/groups/" + f.groupId() + "/export/statement")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(own.getResponse().getContentType()).startsWith("text/csv");
        String csv = own.getResponse().getContentAsString();
        assertThat(csv).contains("TOTAL_NET_POSITION");
        assertThat(csv).contains("runningBalance");
        assertThat(csv).doesNotContain("hostFee");

        // a member of another host's group gets 404 (not a member here)
        String foreignToken = createForeignMember();
        mockMvc.perform(get("/api/v1/me/groups/" + f.groupId() + "/export/statement")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
        // host role cannot call the member export
        mockMvc.perform(get("/api/v1/me/groups/" + f.groupId() + "/export/statement")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isForbidden());
        // cross-tenant host gets 404 on host exports
        mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void formatDefaultsToCsvAndUnknownFormatIs400() throws Exception {
        Fixture f = fixture();

        MvcResult plain = mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(plain.getResponse().getContentType()).startsWith("text/csv");

        mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/export/ledger")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ fixture

    /** Group with 3 members (first name is formula/comma/quote poison), cycle 1 settled. */
    private Fixture fixture() throws Exception {
        long groupId = createGroup(hostAToken, "FIXED_PER_CYCLE", 60000);
        List<Long> memberIds = new ArrayList<>();
        List<String> memberPhones = new ArrayList<>();
        String[] names = {"=SUM(A1), \"B\"", "Hoi Vien Hai", "Hoi Vien Ba"};
        for (String name : names) {
            JsonNode member = createMember(hostAToken, name);
            memberIds.add(member.path("id").asLong());
            memberPhones.add(member.path("phone").asText());
        }
        for (long memberId : memberIds) {
            assignShare(hostAToken, groupId, memberId);
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        long cycleId = openCycle(hostAToken, groupId);
        List<Long> shareIds = new ArrayList<>();
        for (JsonNode share : readArray("/api/v1/groups/" + groupId + "/shares", hostAToken)) {
            shareIds.add(share.path("id").asLong());
        }
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareIds.get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        return new Fixture(groupId, memberIds, memberPhones);
    }

    private record Fixture(long groupId, List<Long> memberIds, List<String> memberPhones) {
    }

    private String createForeignMember() throws Exception {
        JsonNode member = createMember(hostBToken, "Foreign Member");
        return login(hostBToken, member.path("id").asLong(), member.path("phone").asText());
    }

    private String phonesFilter() {
        return "phone IN (" + createdPhones.stream()
                .flatMap(p -> List.of(p, "+84" + p.substring(1)).stream())
                .map(p -> "'" + p + "'")
                .collect(Collectors.joining(",")) + ")";
    }

    private String uniqueIp() {
        return "10.18." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        return "0965" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String register(String phone) throws Exception {
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

    private JsonNode createMember(String hostToken, String fullName) throws Exception {
        String phone = randomPhone();
        createdPhones.add(phone);
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":%s,\"phone\":\"%s\"}"
                                .formatted(objectMapper.writeValueAsString(fullName), phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(m.getResponse().getContentAsString());
    }

    private long createGroup(String token, String hostFeeType, long hostFeeMinor) throws Exception {
        String body = """
                {"name":"Hoi Export","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "hostFeeType":"%s","hostFeeMinor":%d}
                """.formatted(hostFeeType, hostFeeMinor);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
    }

    private void assignShare(String token, long groupId, long memberId) throws Exception {
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                .andExpect(status().isCreated());
    }

    private long openCycle(String token, long groupId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private String login(String hostToken, long memberId, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult r = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private List<JsonNode> readArray(String url, String token) throws Exception {
        JsonNode node = getJson(url, token);
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }
}
