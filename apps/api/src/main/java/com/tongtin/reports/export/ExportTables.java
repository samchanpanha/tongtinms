package com.tongtin.reports.export;

import com.tongtin.common.money.Money;
import com.tongtin.reports.dto.LedgerReportResponse;
import com.tongtin.reports.dto.MemberStatementResponse;
import com.tongtin.reports.dto.ProfitReportResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds export Tables from the Step 15 JSON report DTOs. No new queries and
 * no new math: money becomes an exact major-unit decimal string via
 * BigDecimal.valueOf(amountMinor, exponent) — integer math only, VND (exponent
 * 0) yields the plain integer. Trailing TOTAL_* rows carry the report totals.
 */
public final class ExportTables {

    private ExportTables() {
    }

    public static Table ledger(LedgerReportResponse report) {
        List<String> header = List.of(
                "groupName", "currency", "entryId", "cycleNo", "shareNo", "type",
                "direction", "memberProfileId", "memberName", "amount", "status",
                "dueAt", "allocated", "remaining");
        List<List<String>> rows = new ArrayList<>();
        for (LedgerReportResponse.Entry entry : report.entries()) {
            rows.add(List.of(
                    str(report.groupName()), str(report.currency()),
                    str(entry.entryId()), str(entry.cycleNo()), str(entry.shareNo()),
                    str(entry.type()), str(entry.direction()), str(entry.memberProfileId()),
                    str(entry.memberName()), money(entry.amount()), str(entry.status()),
                    iso(entry.dueAt()), money(entry.allocated()), money(entry.remaining())));
        }
        rows.add(totalRow(report.groupName(), report.currency(), "TOTAL_IN", report.totalIn()));
        rows.add(totalRow(report.groupName(), report.currency(), "TOTAL_OUT", report.totalOut()));
        return new Table("ledger", report.groupId(), header, rows);
    }

    private static List<String> totalRow(String groupName, String currency, String label, Money amount) {
        return List.of(
                str(groupName), str(currency), "", "", "", label, "", "", "",
                money(amount), "", "", "", "");
    }

    public static Table profit(ProfitReportResponse report) {
        List<String> header = List.of(
                "groupName", "currency", "formulaVersion", "cycleNo", "status",
                "openedAt", "closedAt", "winnerShareNo", "winnerName", "winningBid",
                "grossPot", "hostFee", "netPayout", "settledHostFee");
        List<List<String>> rows = new ArrayList<>();
        for (ProfitReportResponse.CycleLine cycle : report.cycles()) {
            ProfitReportResponse.CycleLine.Winner winner = cycle.winner();
            rows.add(List.of(
                    str(report.groupName()), str(report.currency()),
                    str(report.formulaVersion()), str(cycle.cycleNo()), str(cycle.status()),
                    iso(cycle.openedAt()), iso(cycle.closedAt()),
                    winner == null ? "" : str(winner.shareNo()),
                    winner == null ? "" : str(winner.memberName()),
                    money(cycle.winningBid()), money(cycle.grossPot()), money(cycle.hostFee()),
                    money(cycle.netPayout()), ""));
        }
        ProfitReportResponse.Totals totals = report.totals();
        rows.add(List.of(
                str(report.groupName()), str(report.currency()),
                str(report.formulaVersion()), "TOTAL", "", "", "", "", "", "",
                money(totals.grossPot()), money(totals.hostFee()), money(totals.netPayout()),
                money(totals.settledHostFee())));
        return new Table("profit", report.groupId(), header, rows);
    }

    public static Table statement(MemberStatementResponse report) {
        List<String> header = List.of(
                "groupName", "currency", "shareNo", "shareStatus", "entryId", "cycleNo",
                "type", "direction", "amount", "status", "dueAt", "allocated", "remaining",
                "runningBalance");
        List<List<String>> rows = new ArrayList<>();
        for (MemberStatementResponse.Share share : report.shares()) {
            for (MemberStatementResponse.Entry entry : share.entries()) {
                rows.add(List.of(
                        str(report.groupName()), str(report.currency()),
                        str(share.shareNo()), str(share.status()), str(entry.entryId()),
                        str(entry.cycleNo()), str(entry.type()), str(entry.direction()),
                        money(entry.amount()), str(entry.status()), iso(entry.dueAt()),
                        money(entry.allocated()), money(entry.remaining()),
                        money(entry.runningBalance())));
            }
        }
        MemberStatementResponse.Total totals = report.totals();
        rows.add(statementTotal(report.groupName(), report.currency(), "TOTAL_CONTRIBUTED", totals.contributed()));
        rows.add(statementTotal(report.groupName(), report.currency(), "TOTAL_RECEIVED", totals.received()));
        rows.add(statementTotal(report.groupName(), report.currency(), "TOTAL_FEES_PAID", totals.feesPaid()));
        rows.add(statementTotal(report.groupName(), report.currency(), "TOTAL_NET_POSITION", totals.netPosition()));
        return new Table("statement", report.groupId(), header, rows);
    }

    private static List<String> statementTotal(String groupName, String currency, String label, Money amount) {
        return List.of(
                str(groupName), str(currency), "", "", "", "", label, "",
                money(amount), "", "", "", "", "");
    }

    public static String money(Money money) {
        return money == null ? ""
                : BigDecimal.valueOf(money.amountMinor(), money.exponent()).toPlainString();
    }

    public static String iso(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * True when the whole cell is a plain number (optional leading '-', digits,
     * optional single '.' fraction). ISO dates, names and anything else stay
     * false — those are the cells that must be text/escaped.
     */
    public static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int i = 0;
        if (value.charAt(0) == '-') {
            i = 1;
        }
        boolean digits = false;
        boolean dot = false;
        for (; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits = true;
            } else if (c == '.' && !dot) {
                dot = true;
            } else {
                return false;
            }
        }
        return digits;
    }
}
