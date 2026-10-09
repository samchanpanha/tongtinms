# Tong Tin Management System (Hệ Thống Quản Lý Hụi / Hội / ROSCA)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16.3-black.svg)](https://nextjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-230%20passed-success.svg)](#testing--verification)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)]()

> **Nền tảng quản lý Hụi / Hội (ROSCA — Rotating Savings and Credit Association) hiện đại, minh bạch và bảo mật**, thiết kế chuẩn xác theo tập quán tài chính truyền thống Việt Nam và thông lệ quốc tế.

---

## 🌟 Tổng Quan Kiến Trúc (Architecture Overview)

Hệ thống được tổ chức theo mô hình **Modular Monolith** kết hợp ứng dụng Web hiện đại:

```
tongtin/
├── apps/
│   ├── api/                     # Spring Boot 3 (Java 21) REST Backend
│   │   ├── src/main/java/com/tongtin/
│   │   │   ├── common/          # Money (long minor units), lỗi chuẩn, bảo mật JWT, healthcheck
│   │   │   ├── identity/        # Xác thực JWT, tự đăng ký Chủ Hụi, sổ kiểm toán (Audit Trail)
│   │   │   ├── members/         # Quản lý danh bạ hội viên & phân quyền đăng nhập
│   │   │   ├── groups/          # Cấu hình dây hụi (BIDDING / FIXED_EQUAL) & gán chân hụi
│   │   │   ├── cycles/          # Vòng quay kỳ hụi, bỏ thăm kín (cycles/bids) & chốt kỳ
│   │   │   ├── ledger/          # Công thức tài chính, nghĩa vụ nợ, đóng tiền & phí trễ hạn
│   │   │   ├── dashboard/       # Bảng điều khiển KPI Chủ Hụi & phân tích lợi nhuận
│   │   │   ├── reports/         # Sổ cái, báo cáo tiền thảo, sao kê hội viên & xuất CSV/XLSX
│   │   │   ├── notify/          # Hệ thống thông báo nội bộ trong ứng dụng
│   │   │   ├── memberportal/    # Cổng thông tin Hội Viên (Member Portal)
│   │   │   ├── subscription/    # Gói cước SaaS, tích hợp ABA PayWay & nhật ký thanh toán
│   │   │   ├── settings/        # Cấu hình hệ thống & gói tham số dây hụi mặc định
│   │   │   ├── attachments/     # Đính kèm biên nhận & hồ sơ hội viên (BYTEA, V15)
│   │   │   ├── khqr/            # Máy tạo mã QR KHQR (EMVCo, zxing)
│   │   │   ├── telegram/        # Thông báo Telegram (chat-id per host, digest job, V17)
│   │   │   └── demo/            # Trình nạp dữ liệu mẫu (Demo Fixture Seeder)
│   │   └── src/main/resources/db/migration/  # 17 Flyway Migrations (V1 -> V17)
│   │
│   └── web/                     # Next.js 16 App Router + Tailwind CSS v4 Frontend
│       ├── app/                 # 14 routes: landing, login, register, host portal, member portal
│       ├── components/          # StatCard, StatusBadge, EmptyState, Navbar, v.v.
│       └── lib/                 # Client API wrapper, formatMoney (1.000.000 đ), định dạng ngày
│
├── docker-compose.yml           # Triển khai Docker: PostgreSQL 16 + Backend API + Frontend Web
├── start.sh                     # Kịch bản khởi động toàn bộ một lệnh (All-in-one startup)
└── docs/                        # Tài liệu đặc tả kỹ thuật toàn diện
```

---

## 🔒 Các Nguyên Tắc Tài Chính & Bất Biến Cốt Lõi (Core Invariants)

1. **Tuyệt đối không sử dụng số thực (No Floating-Point Money Math)**:
   - Toàn bộ số tiền được lưu trữ và tính toán dưới dạng số nguyên `long` (đơn vị minor unit: VND không có số thập phân, USD/KHR hỗ trợ đúng exponent).
   - Tỷ lệ phần trăm tính bằng basis points (bps) với phép làm tròn chuẩn tài chính `RoundingMode.HALF_UP`.
2. **Cân Bằng Tuyệt Đối Sổ Cái Kép (Double-Sided Balanced Ledger)**:
   - Mọi kỳ hụi khi chốt đều bảo toàn định luật tài chính: `SUM(IN) == SUM(OUT) == Tổng gom (Gross Pot)`.
   - Các khoản đóng (CONTRIBUTION), tiền thảo (HOST_FEE) và tiền giao hụi (PAYOUT) được hạch toán minh bạch.
3. **Bỏ Thăm Kín Bảo Mật (Sealed Bidding Confidentiality)**:
   - Trong thời gian mở kỳ bỏ giá, các mức giá bỏ thăm của hội viên được giữ bí mật hoàn toàn.
   - Chỉ khi chốt kỳ hụi (`close-and-calculate`), hệ thống mới công bố giá trúng và danh sách chi tiết.
   - Báo cáo công khai của Hội Viên (`/me/groups/{id}/cycles`) tuyệt đối không làm lộ tiền thảo của Chủ Hụi.
4. **Chống Trùng Lặp Thanh Toán (Payment Idempotency Key)**:
   - API thanh toán hỗ trợ header `Idempotency-Key` với chỉ mục `UNIQUE (group_id, idempotency_key)`.
   - Việc gửi lại yêu cầu khi mạng chập chờn đảm bảo trả về kết quả 200 OK của giao dịch đã ghi nhận, không bao giờ trừ/cộng trùng tiền.
5. **Cách Ly Đa Khách Hàng Tuyệt Đối (Multi-Tenant Isolation)**:
   - Mọi dữ liệu dây hụi, hội viên, giao dịch đều phân lập chặt chẽ theo `owner_id`.
   - Hội viên chỉ truy cập được dữ liệu thuộc về chính các chân hụi mình sở hữu.

---

## 🚀 Hướng Dẫn Khởi Động Nhanh (Quickstart)

### Yêu Cầu Môi Trường
- **Docker** & **Docker Compose**
- **Java 21** & **Maven 3.9+**
- **Node.js 20+** & **npm**

### Khởi Động Bằng Một Lệnh (`start.sh`)

Chạy kịch bản khởi động tích hợp tại thư mục gốc của dự án:

```bash
# Cấp quyền thực thi (nếu cần)
chmod +x start.sh

# Khởi động kèm nạp sẵn dữ liệu mẫu thực nghiệm (N=10 kỳ hụi hoàn chỉnh)
./start.sh --seed
```

Kịch bản sẽ tự động:
1. Khởi động PostgreSQL 16 trên cổng `5432` và chờ kiểm tra trạng thái sẵn sàng.
2. Áp dụng toàn bộ 17 migration Flyway (V1 → V17) và khởi động API Spring Boot trên cổng `8080`.
3. Nạp bộ dữ liệu mẫu chuẩn (Dây hụi 10 kỳ với đầy đủ bỏ thăm, sổ cái cân bằng, tiền thảo và thanh toán).
4. Khởi động giao diện Next.js trên cổng `3000`.

### Các Tùy Chọn Khởi Động

```bash
./start.sh              # Khởi động PostgreSQL, Backend API và Frontend Web
./start.sh --seed       # Khởi động và nạp sẵn dữ liệu mẫu thực nghiệm
./start.sh --api-only   # Chỉ khởi động PostgreSQL và Backend API (cổng 8080)
./start.sh --web-only   # Chỉ khởi động Frontend Web (cổng 3000)
./start.sh --help       # Xem hướng dẫn tùy chọn
```

### Triển Khai Toàn Bộ Bằng Docker (Backend + Frontend + Database)

Không cần cài Java/Node trên máy — chỉ cần Docker. Toàn bộ hệ thống (PostgreSQL 16, API Spring Boot, giao diện Next.js) được đóng gói và chạy bằng một lệnh:

```bash
# (Tùy chọn) Sao chép cấu hình môi trường và chỉnh sửa
cp .env.example .env

# Build và khởi động toàn bộ stack (lần đầu mất vài phút để build image)
docker compose up --build -d

# Xem trạng thái / log
docker compose ps
docker compose logs -f api

# Dừng toàn bộ
docker compose down
```

- **API Spring Boot** chạy tại `http://localhost:8080` — tự động áp dụng Flyway migration khi khởi động, healthcheck tại `/api/v1/health`.
- **Giao diện Next.js** chạy tại `http://localhost:3000` — tự động proxy `/api/*` sang container API qua build arg `API_INTERNAL_URL` (mặc định `http://api:8080`).
- **Dữ liệu mẫu**: đặt `APP_SEED_DEMO=true` trong `.env` trước khi chạy để nạp bộ demo N=10 chuẩn (`./start.sh --seed` bản Docker).
- **Bảo mật**: đổi `JWT_SECRET` trong `.env` trước khi triển khai thật.
- `./start.sh` vẫn là quy trình phát triển (hot reload); Docker Compose là quy trình triển khai (image cố định, có healthcheck và tự khởi động lại).

> Lưu ý: `API_INTERNAL_URL` được Next.js đóng băng vào web image lúc build (rewrites). Nếu đổi URL API, cần chạy lại `docker compose build web`.

---

## 🌐 Các Điểm Truy Cập & Tài Khoản Thử Nghiệm

Sau khi khởi động với `--seed`, bạn có thể truy cập các dịch vụ:

- **Giao diện Web**: [http://localhost:3000](http://localhost:3000)
- **Cổng Dịch Vụ API**: [http://localhost:8080/api/v1](http://localhost:8080/api/v1)
- **Kiểm Tra Trạng Thái API**: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
- **Tài Liệu Swagger / OpenAPI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 🔑 Thông Tin Đăng Nhập Dùng Thử (Demo Credentials)

| Vai Trò | Số Điện Thoại | Mật Khẩu | Quyền Hạn |
|---|---|---|---|
| **Chủ Hụi (Host)** | `0900111001` | `demo1234` | Quản lý dây hụi, danh bạ hội viên, chốt kỳ, sổ cái, thu tiền |
| **Hội Viên (Member)** | `0900100001` | `demo1234` | Bỏ thăm kín, theo dõi dây hụi, xem sao kê số dư cá nhân |

*(Trên trang đăng nhập `/login` có sẵn các nút bấm 1 chạm để điền nhanh tài khoản dùng thử)*

---

## 📱 Bản Đồ Các Trang Giao Diện (Frontend Routes)

### 1. Phân Hệ Dành Cho Chủ Hụi (Host Portal)
- [`/login`](http://localhost:3000/login): Đăng nhập hệ thống thống nhất cho cả Chủ Hụi và Hội Viên.
- [`/register/owner`](http://localhost:3000/register/owner): Đăng ký tài khoản Chủ Hụi mới kèm thông tin ngân hàng.
- [`/host`](http://localhost:3000/host): Bảng điều khiển KPI, tổng kết lợi nhuận theo từng loại tiền tệ (VND, USD, KHR).
- [`/host/members`](http://localhost:3000/host/members): Danh bạ hội viên, tìm kiếm, tạo hồ sơ mới và cấp quyền đăng nhập.
- [`/host/groups/new`](http://localhost:3000/host/groups/new): Trình tạo dây hụi mới với công cụ ước tính tổng gom và tiền thảo theo thời gian thực.
- [`/host/groups/[id]`](http://localhost:3000/host/groups/1): Bàn điều khiển kỳ hụi — gán chân hụi, mở kỳ, nhập thăm, chốt sổ, giao hụi và ghi nhận đóng tiền.
- [`/host/groups/[id]/ledger`](http://localhost:3000/host/groups/1/ledger): Báo cáo sổ cái kép chi tiết với chứng nhận cân bằng dòng tiền 100% — có nút xuất **CSV / Excel (.xlsx)**.

### 2. Phân Hệ Dành Cho Hội Viên (Member Portal)
- [`/app`](http://localhost:3000/app): Cổng thông tin hội viên tổng hợp các dây hụi đã tham gia.
- [`/app/groups/[id]`](http://localhost:3000/app/groups/1): Giao diện bỏ thăm kín trực tiếp và bảng sao kê tài chính cá nhân (`contributed`, `received`, `netPosition`) — kèm nút xuất **CSV / Excel (.xlsx)**.
- [`/notifications`](http://localhost:3000/notifications): Trung tâm thông báo sự kiện (mở kỳ, kết quả bỏ thăm, nhận tiền, hoàn thành dây hụi).

---

## 🧪 Kiểm Thử & Đảm Bảo Chất Lượng (Testing & Verification)

### Kiểm Thử Backend (JUnit 5 + Spring Boot Test + Testcontainers)
Toàn bộ hệ thống backend được bao phủ bởi **230 ca kiểm thử tự động**, bao gồm kiểm thử toán học biên, ma trận phân quyền chéo tenant, kiểm thử tương tranh và kiểm thử xuất file CSV/XLSX.

Từ **Step 28 (2026-10-08)** bộ test chạy **hermetic** bằng Testcontainers: mỗi lần `mvn test`
tự khởi động một container `postgres:16` cô lập (Flyway migrate toàn bộ schema từ đầu) —
**không đụng vào database dev**, không cần `docker compose up` trước. Chỉ cần Docker đang chạy:

```bash
cd apps/api
mvn test
```
```
[INFO] Results:
[INFO] Tests run: 230, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Kiểm Tra Frontend (ESLint & Next.js Build)
Mã nguồn frontend được kiểm tra nghiêm ngặt theo các tiêu chuẩn React 19 và biên dịch tối ưu hóa Turbopack:

```bash
cd apps/web
npm run lint     # 0 errors, 0 warnings
npm run build    # 14/14 routes tĩnh và động biên dịch thành công
```

---

## 📜 Tiến Độ Thực Hiện 37 Giai Đoạn (Build Roadmap)

Hệ thống đã hoàn tất trọn vẹn **37/37 giai đoạn** theo đúng lộ trình đặc tả (`docs/monkey_ai/STATUS.md` + `plan.md`):

| Giai Đoạn | Tên Giai Đoạn | Nội Dung Hoàn Thành |
|:---:|---|---|
| **00** | **Planning Artifacts** | Đặc tả miền bài toán, công thức toán học, kiến trúc và quy tắc phân quyền |
| **01** | **Monorepo Skeleton** | Khởi tạo cấu trúc monorepo `apps/api` và `apps/web`, proxy API reverse rewrite |
| **02** | **Database & Flyway** | Thiết lập PostgreSQL 16, Flyway V1 (bảng `users`, `user_roles`) |
| **03** | **Identity & Owner Register** | Đăng ký tài khoản Chủ Hụi, cấp phát JWT và Flyway V2 (`owner_accounts`) |
| **04** | **Member Directory** | Quản lý danh bạ hội viên, phân tách tenant, Flyway V2.1 (`member_profiles`) |
| **05** | **Group Draft** | Tạo dây hụi (BIDDING / FIXED_EQUAL), Flyway V3 (`currencies`, `groups`) |
| **06** | **Shares & READY** | Gán chân hụi, kiểm soát đủ chân và chuyển trạng thái READY, Flyway V4 |
| **07** | **Formula Engine** | Động cơ toán tài chính thuần Java, vượt qua 100% các fixture mẫu A, B, C, D |
| **08** | **Cycle Open** | Quản lý trạng thái mở kỳ hụi tuần hoàn, Flyway V5 (`cycles`) |
| **09** | **Sealed Bidding** | Cơ chế bỏ thăm kín bảo mật, bảo toàn tính bí mật trước hạn, Flyway V6 (`bids`) |
| **10** | **Close & Calculate** | Chốt kỳ, xác định người hốt hụi, lập sổ cái cân bằng, Flyway V7 (`ledger_entries`) |
| **11** | **Payments** | Thu tiền hụi, phân bổ nghĩa vụ nợ, Flyway V8 (`payments`, `payment_allocations`) |
| **12** | **Host Dashboard** | Tổng hợp chỉ số KPI, kỳ đến hạn, công nợ và lợi nhuận tiền thảo theo loại tiền |
| **13** | **Member Portal** | Cấp mật khẩu hội viên, API Cổng Hội Viên xem dây hụi và bỏ thăm trực tuyến |
| **14** | **In-App Notifications** | Hệ thống thông báo nội bộ đa sự kiện theo thời gian thực, Flyway V9 (`notifications`) |
| **15** | **Reports** | Báo cáo tiền thảo Chủ Hụi, sao kê vị thế Hội Viên và tóm tắt kỳ hụi công khai |
| **16** | **Hardening** | Sổ kiểm toán (`audit_events`), Idempotency Key (Flyway V10), kiểm thử bảo mật & Demo Seeder |
| **17** | **Preview Polish** | Giao diện tiếng Việt chuẩn mực, định dạng `1.000.000 đ`, Dark Mode, 13 routes, `start.sh` |
| **18** | **Final Delivery** | Kiểm thử đầu cuối (E2E) trọn vẹn 10 quy trình, tài liệu hướng dẫn và hoàn tất chuyển giao |
| **19** | **SaaS Subscriptions & ABA PayWay** | Tặng 1 tháng dùng thử miễn phí khi đăng ký Chủ Hụi, tích hợp cổng thanh toán ABA PayWay (HMAC-SHA512 KHQR & Card theo [developer.payway.com.kh](https://developer.payway.com.kh/)), cấu hình gói cước và quản trị hệ thống qua Admin Panel (Flyway V11) |
| **20** | **Member Group Settings** | Cài đặt hệ thống (`system_settings`) và gói tham số công thức mặc định cho dây hụi mới (Flyway V?) |
| **21** | **Bid Rules & Tiebreaking** | Chạm trần lãi suất, đấu giá vòng 2 (tiebreak), san đều tiền thảo qua nhiều phiên bỏ thăm |
| **22** | **Late Fee Formula** | Tiền phạt rút hụi sớm / trễ hạn theo công thức, minh bạch trong sổ cái (Flyway V12 `payment_events`) |
| **23** | **Subscription Funnel** | Trang đăng ký, quyền truy cập theo gói (PLUS/PREMIUM), giới hạn dây hụi, gia hạn & nhắc hết hạn (Flyway V13) |
| **24** | **Cycle Public Summary** | Tóm tắt kỳ hụi công khai cho hội viên, minh bạch kết quả chốt kỳ |
| **25** | **Settings & Rule Presets** | Quản lý gói cước, xáo trộn thứ tự bỏ thăm, lịch sử thay đổi, kiểm thử chéo |
| **26** | **Multi-Currency Hardening** | Hỗ trợ USD/KHR theo exponent, làm tròn tài chính chuẩn, kiểm soát trạng thái tiền tệ |
| **27** | **Cycle Settings UI** | Giao diện cấu hình kỳ hụi nâng cao, combo tham số và nhập liệu tiện dụng |
| **28** | **Hermetic Tests & Release Gate** | Testcontainers tách biệt hoàn toàn khỏi DB dev, ReleaseGateTests chặn số thực, toàn bộ suite xanh |
| **29** | **Late Fees Delivery** | Thu phí trễ hạn, chốt sổ tự động, queuing thanh toán, hiển thị đủ trên sổ cái & sao kê (Flyway V14) |
| **30** | **CSV / Excel Exports** | Xuất sổ cái, báo cáo tiền thảo & sao kê hội viên ra **CSV** và **XLSX (Apache POI)** — cột đầy đủ, dòng TOTAL, phòng chống formula injection |
| **31** | **Wrap-up & Cleanup** | Dọn tài liệu lỗi thời (README, chỉ mục, kiến trúc), xác minh toàn bộ qua Docker compose, kiểm tra suite + build cuối cùng |
| **32** | **Attachments (Phase 2 #1)** | Đính kèm hình biên nhận trên khoản đóng & hồ sơ hội viên (Flyway V15 `attachments`, BYTEA), lịch sử `GET /groups/{id}/payments`, UI chủ hụi + hội viên, i18n ×4 |
| **33** | **Blacklist + Enforced Status (Phase 2 #2)** | Danh sách đen theo chủ hụi (Flyway V16 `member_blacklists`), BLOCKED → không đăng nhập, INACTIVE/BLOCKED → không bỏ thăm, UI quản lý blacklist, i18n ×4 |
| **34** | **KHQR per Obligation (Phase 2 #3)** | Mã KHQR EMVCo thật (CRC16, `TONGTIN <code>-O<entryId>`) trên nghĩa vụ chưa đóng (debts + statement), render PNG, i18n ×4 |
| **35** | **Telegram Notifications (Phase 2 #4)** | Thông báo Telegram theo chủ hụi (Flyway V17 `telegram_chat_id`), sự kiện vòng hụi + daily due-digest, tab `/host/telegram`, i18n ×4 |
| **36** | **Quick-pay Single-Step Settlement (Phase 2 #5)** | `POST /groups/{id}/quick-pay`: chủ hụi nhập tổng tiền, hệ thống tự phân bổ vào nghĩa vụ cũ nhất (kể cả late fee) qua core dùng chung của `PaymentService.record()` |
| **37** | **Wrap-up (Phase 2 #6)** | Rà soát toàn bộ tài liệu (README ×2, plan.md, roadmaps, STATUS), final suite `mvn package` (224), `npm run build` 14/14, Docker compose click-through (3 services healthy) |

---

## 💳 Tích Hợp Cổng Thanh Toán ABA PayWay & Gói Cước SaaS (Step 19)

Hệ thống hỗ trợ mô hình kinh doanh phần mềm dịch vụ (SaaS) toàn diện:
- **Tự Động Tặng 1 Tháng Dùng Thử**: Khi Chủ Hụi đăng ký mới, hệ thống tự động kích hoạt 30 ngày dùng thử miễn phí (`TRIAL`), theo dõi số ngày còn lại và hiển thị cảnh báo gia hạn khi hết hạn.
- **Tích Hợp Cổng Thanh Toán ABA PayWay**:
  - Tuân thủ đặc tả kỹ thuật chính thức tại [developer.payway.com.kh](https://developer.payway.com.kh/).
  - Mã hóa bảo mật chữ ký điện tử HMAC-SHA512.
  - Hỗ trợ thanh toán nhanh qua **ABA Pay KHQR** (Bakong) và thẻ quốc tế Visa/Mastercard/JCB.
  - Cơ chế Webhook Callback, tra cứu giao dịch và mô phỏng thanh toán Sandbox phục vụ kiểm thử.
- **Trung Tâm Quản Trị Hệ Thống (Administrator Control Panel)**:
  - Truy cập tại `/admin` dành riêng cho vai trò `ADMIN` (`0900999999` / `admin1234`).
  - Cấu hình thông số Merchant ID, API Hash Key, Purchase URL, Check URL, Sandbox Mode.
  - Quản trị danh mục gói cước đăng ký (Thêm, Sửa, Xóa, Bật/Tắt, Định giá theo USD/KHR/VND).
  - Quản lý danh sách Chủ Hụi và gia hạn gói dịch vụ thủ công (+30 ngày, +1 năm, VIP vĩnh viễn).
  - Sổ nhật ký giao dịch thanh toán PayWay thời gian thực.

---

## 📄 Bản Quyền & Giấy Phép (License)

Dự án thuộc quyền sở hữu trí tuệ của nhóm phát triển Tong Tin ROSCA Management System.
Mọi thắc mắc và đóng góp vui lòng tham khảo chi tiết tại thư mục [`docs/`](docs/).
