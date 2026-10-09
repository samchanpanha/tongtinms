package com.tongtin.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static registry of every runtime knob in the system. This is the only place
 * a new setting key is declared: the admin UI, validation and default values
 * all derive from here. Rows are created in `system_settings` on first save,
 * so adding an entry never requires a Flyway migration.
 */
public final class SettingsCatalog {

    public record Category(String code, String label, List<SettingDefinition> settings) {
    }

    public static final String PAYMENT = "PAYMENT";
    public static final String SUBSCRIPTION = "SUBSCRIPTION";
    public static final String GROUPS = "GROUPS";
    public static final String SECURITY = "SECURITY";
    public static final String TELEGRAM = "TELEGRAM";

    private static final List<Category> CATEGORIES = List.of(
            new Category(PAYMENT, "Cổng thanh toán (ABA PayWay)", List.of(
                    SettingDefinition.of("payway_merchant_id", PAYMENT, "Merchant ID",
                            "Mã đơn vị thanh toán do ABA PayWay cấp", SettingType.STRING, "ec438992"),
                    SettingDefinition.of("payway_api_key", PAYMENT, "API Key (khóa bí mật)",
                            "Khóa dùng để ký giao dịch với ABA PayWay", SettingType.SECRET,
                            "4c05336bf1f621375d86242aebe3daee7fa5a1c3"),
                    SettingDefinition.of("payway_api_url", PAYMENT, "Purchase URL",
                            "Endpoint tạo giao dịch thanh toán", SettingType.URL,
                            "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase"),
                    SettingDefinition.of("payway_check_url", PAYMENT, "Check Transaction URL",
                            "Endpoint kiểm tra trạng thái giao dịch", SettingType.URL,
                            "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/check-transaction"),
                    SettingDefinition.of("payway_sandbox_mode", PAYMENT, "Chế độ Sandbox",
                            "Bật để dùng môi trường thử nghiệm, không trừ tiền thật", SettingType.BOOLEAN, "true"),
                    SettingDefinition.of("payway_enabled", PAYMENT, "Bật cổng PayWay",
                            "Tắt để tạm dừng thanh toán trực tuyến", SettingType.BOOLEAN, "true"),
                    SettingDefinition.of("payments_khqr_enabled", PAYMENT, "Bật mã QR nghĩa vụ",
                            "Bật để mỗi khoản còn nợ có mã KHQR thanh toán (debts + statement)", SettingType.BOOLEAN, "true"))),
            new Category(SUBSCRIPTION, "Gói sử dụng", List.of(
                    SettingDefinition.of("enforce_subscription", SUBSCRIPTION, "Bắt buộc gói",
                            "Bật để chặn tạo dây hụi mới khi gói hết hạn", SettingType.BOOLEAN, "true"),
                    SettingDefinition.numeric("free_trial_days", SUBSCRIPTION, "Số ngày dùng thử",
                            "Số ngày dùng thử miễn phí khi chủ hụi đăng ký mới", "30", 1, 365),
                    SettingDefinition.numeric("grace_period_days", SUBSCRIPTION, "Số ngày ân hạn",
                            "Số ngày ân hạn sau khi gói hết hạn trước khi bị khóa tạo dây mới", "3", 0, 30),
                    SettingDefinition.numericList("subscription_reminder_days", SUBSCRIPTION,
                            "Mốc nhắc gia hạn (ngày)",
                            "Các mốc nhắc trước khi gói hết hạn, cách nhau bởi dấu phẩy", "7,3,1", 1, 90),
                    SettingDefinition.numeric("checkout_pending_reuse_minutes", SUBSCRIPTION,
                            "Thời gian dùng lại đơn chờ",
                            "Phút giữ đơn thanh toán đang chờ để tránh tạo đơn trùng khi bấm lặp lại (0 = tắt)",
                            "10", 0, 60))),
            new Category(GROUPS, "Dây hụi", List.of(
                    SettingDefinition.numeric("default_bid_close_offset_days", GROUPS, "Ngày khóa ký mặc định",
                            "Số ngày từ khi mở ký đến khi khóa nhận ký, dùng khi tạo dây hụi không nhập",
                            "0", 0, 30))),
            new Category(SECURITY, "Bảo mật & phiên đăng nhập", List.of(
                    SettingDefinition.numeric("access_token_ttl_minutes", SECURITY, "Hạn access token (phút)",
                            "Thời gian sống của access token cấp cho người dùng", "30", 5, 1440),
                    SettingDefinition.numeric("refresh_token_ttl_days", SECURITY, "Hạn refresh token (ngày)",
                            "Thời gian sống của refresh token", "7", 1, 90),
                    SettingDefinition.numeric("rate_limit_register_per_hour", SECURITY, "Giới hạn đăng ký mỗi giờ",
                            "Số lần đăng ký tối đa từ một IP trong một giờ", "20", 1, 1000),
                    SettingDefinition.numeric("rate_limit_login_per_15min", SECURITY, "Giới hạn đăng nhập mỗi 15 phút",
                            "Số lần đăng nhập tối đa từ một IP trong 15 phút", "30", 1, 1000),
                    SettingDefinition.numeric("rate_limit_payway_callback_per_minute", SECURITY,
                            "Giới hạn callback PayWay mỗi phút",
                            "Số lần webhook callback PayWay tối đa từ một IP trong một phút", "60", 1, 1000),
                    SettingDefinition.numeric("storage_attachment_max_mb", SECURITY,
                            "Kích thước tệp đính kèm tối đa (MB)",
                            "Giới hạn kích thước mỗi tệp đính kèm (ảnh, PDF, bảng tính)", "10", 1, 50),
                    SettingDefinition.of("storage_attachment_allowed_types", SECURITY,
                            "Loại tệp đính kèm được phép",
                            "Danh sách Content-Type hợp lệ, cách nhau bởi dấu phẩy", SettingType.STRING,
                            "image/png,image/jpeg,image/gif,image/webp,application/pdf,"
                                    + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
                                    + "application/vnd.ms-excel,text/csv"))),
            new Category(TELEGRAM, "Thông báo Telegram", List.of(
                    SettingDefinition.of("telegram_bot_token", TELEGRAM, "Bot Token",
                            "Token bot Telegram do @BotFather cấp; để trống = tắt toàn bộ Telegram",
                            SettingType.SECRET, ""),
                    SettingDefinition.of("telegram_events_enabled", TELEGRAM, "Bật sự kiện Telegram",
                            "Bật để gửi thông báo sự kiện (mở kỳ, chốt kỳ, ghi nhận tiền…) cho chủ hụi",
                            SettingType.BOOLEAN, "true"),
                    SettingDefinition.of("telegram_daily_digest_enabled", TELEGRAM, "Bật báo cáo thu hàng ngày",
                            "Bật để mỗi sáng gửi tổng hợp khoản còn nợ cho chủ hụi qua Telegram",
                            SettingType.BOOLEAN, "false"),
                    SettingDefinition.of("telegram_daily_digest_time", TELEGRAM, "Giờ gửi báo cáo hàng ngày",
                            "Giờ gửi dạng HH:mm (múi giờ máy chủ)", SettingType.STRING, "08:00"))));

    private static final Map<String, SettingDefinition> BY_KEY = indexByKey();

    private SettingsCatalog() {
    }

    public static List<Category> categories() {
        return CATEGORIES;
    }

    public static Optional<SettingDefinition> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static String categoryLabel(String code) {
        return CATEGORIES.stream()
                .filter(category -> category.code().equals(code))
                .map(Category::label)
                .findFirst()
                .orElse(code);
    }

    private static Map<String, SettingDefinition> indexByKey() {
        Map<String, SettingDefinition> index = new LinkedHashMap<>();
        for (Category category : CATEGORIES) {
            for (SettingDefinition definition : category.settings()) {
                index.put(definition.key(), definition);
            }
        }
        return Map.copyOf(index);
    }
}
