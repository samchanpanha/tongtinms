package com.tongtin.telegram;

import com.tongtin.groups.entity.Group;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Step 35: pure Vietnamese message composition for the host Telegram channel.
 * No Spring, no DB — formatting uses long-only decimal math (07-MULTI-CURRENCY
 * §10 exponents) and never touches floating-point types.
 */
public final class TelegramMessages {

    private static final DecimalFormat GROUPED = new DecimalFormat(
            "#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** One group row shown in the daily digest. */
    public record GroupSummary(String name, String code, int unpaidCount,
                               long totalRemainingMinor, String currency) {
    }

    private TelegramMessages() {
    }

    public static String cycleOpened(Group group, int cycleNo, int cycleCount, Instant bidCloseAt) {
        return header(group)
                + ": đã mở KỲ " + cycleNo + "/" + cycleCount
                + ".\n⏰ Hạn chốt ký: " + DATE_TIME.format(bidCloseAt) + ".";
    }

    public static String winnerPublished(Group group, int cycleNo, int winnerShareNo,
                                         long winningBid, long netPayout) {
        return header(group)
                + ": KỲ " + cycleNo + " đã chốt — phần " + winnerShareNo + " thắng ký "
                + fmt(winningBid, group.getCurrency()) + ", nhận "
                + fmt(netPayout, group.getCurrency()) + ".";
    }

    public static String payoutConfirmed(Group group, int cycleNo, long netPayout) {
        return header(group) + ": đã thanh toán KỲ " + cycleNo + " — "
                + fmt(netPayout, group.getCurrency()) + " cho người thắng.";
    }

    public static String groupCompleted(Group group, int cycleCount) {
        return header(group) + ": đã HOÀN TẤT " + cycleCount + " kỳ. 🎉";
    }

    public static String paymentRecorded(Group group, long amountMinor, int payerCount) {
        return header(group) + ": đã ghi nhận " + fmt(amountMinor, group.getCurrency())
                + " từ " + payerCount + " thành viên. 💰";
    }

    public static String lateFeesAssessed(Group group, long totalFeeMinor, int affectedCount) {
        return header(group) + ": đã tính phí trễ tổng "
                + fmt(totalFeeMinor, group.getCurrency())
                + " cho " + affectedCount + " thành viên. ⏰";
    }

    public static String testPing(String displayName) {
        return "🔔 Kiểm tra Telegram — xin chào " + displayName
                + "!\nHệ thống Tong Tin đã gửi thành công tin nhắn này đến Telegram của bạn.";
    }

    public static String dailyDigest(List<GroupSummary> groups) {
        long totalMinor = groups.stream().mapToLong(GroupSummary::totalRemainingMinor).sum();
        int totalCount = groups.stream().mapToInt(GroupSummary::unpaidCount).sum();
        String currency = groups.get(0).currency();

        StringBuilder sb = new StringBuilder("📋 Báo cáo thu nhập của các dây hụi\n");
        sb.append("Tổng: ").append(totalCount).append(" khoản còn nợ, tổng ")
                .append(fmt(totalMinor, currency)).append(".\n");
        for (GroupSummary g : groups) {
            sb.append("\n• ").append(g.name()).append(" (").append(g.code()).append("): ")
                    .append(g.unpaidCount()).append(" khoản — ")
                    .append(fmt(g.totalRemainingMinor(), g.currency()));
        }
        return sb.toString();
    }

    private static String header(Group group) {
        return "📣 Hội \"" + group.getName() + "\" (" + group.getCode() + ")";
    }

    private static String fmt(long minor, String currency) {
        int exponent = switch (currency.toUpperCase(Locale.ROOT)) {
            case "VND", "KHR", "LAK" -> 0;
            default -> 2;
        };
        String digits = GROUPED.format(BigDecimal.valueOf(minor, exponent));
        String grouped = digits.replace(',', '.');
        if ("VND".equalsIgnoreCase(currency)) {
            return grouped + " đ";
        }
        return grouped + " " + currency.toUpperCase(Locale.ROOT);
    }
}