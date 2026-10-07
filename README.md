# Tong Tin Management System (Hệ Thống Quản Lý Hụi / Hội / ROSCA)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16.3-black.svg)](https://nextjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-119%20passed-success.svg)](#testing--verification)
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
│   │   │   ├── identity/        # Xác thực JWT, Chủ Hụi & Hội viên, Sổ kiểm toán (Audit Trail)
│   │   │   ├── members/         # Quản lý danh bạ hội viên & phân quyền đăng nhập
│   │   │   ├── groups/          # Cấu hình dây hụi (BIDDING / FIXED_EQUAL) & gán chân hụi
│   │   │   ├── formulas/        # Động cơ tính toán tài chính thuần túy Java (Pure Zero-Dependency)
│   │   │   ├── cycles/          # Vòng quay kỳ hụi, chốt kỳ & tính toán sổ cái
│   │   │   ├── bidding/         # Bỏ thăm kín bảo mật (Sealed Bidding) & tự động phân xử
│   │   │   ├── payments/        # Ghi nhận đóng tiền, phân bổ nợ & Idempotency Key
│   │   │   ├── dashboard/       # Bảng điều khiển KPI Chủ Hụi & phân tích lợi nhuận
│   │   │   ├── me/              # Cổng thông tin Hội Viên (Member Portal)
│   │   │   ├── notify/          # Hệ thống thông báo nội bộ trong ứng dụng
│   │   │   ├── reports/         # Sổ cái cân bằng, báo cáo tiền thảo & sao kê hội viên
│   │   │   └── demo/            # Trình nạp dữ liệu mẫu (Demo Fixture Seeder)
│   │   └── src/main/resources/db/migration/  # 10 Flyway Migrations (V1 -> V10)
│   │
│   └── web/                     # Next.js 16 App Router + Tailwind CSS v4 Frontend
│       ├── app/                 # 11 routes: landing, login, register, host portal, member portal
│       ├── components/          # StatCard, StatusBadge, EmptyState, Navbar, v.v.
│       └── lib/                 # Client API wrapper, formatMoney (1.000.000 đ), định dạng ngày
│
├── docker-compose.yml           # PostgreSQL 16 Database
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
2. Áp dụng toàn bộ 10 migration Flyway và khởi động API Spring Boot trên cổng `8080`.
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
- [`/host/groups/[id]/ledger`](http://localhost:3000/host/groups/1/ledger): Báo cáo sổ cái kép chi tiết với chứng nhận cân bằng dòng tiền 100%.

### 2. Phân Hệ Dành Cho Hội Viên (Member Portal)
- [`/app`](http://localhost:3000/app): Cổng thông tin hội viên tổng hợp các dây hụi đã tham gia.
- [`/app/groups/[id]`](http://localhost:3000/app/groups/1): Giao diện bỏ thăm kín trực tiếp và bảng sao kê tài chính cá nhân (`contributed`, `received`, `netPosition`).
- [`/notifications`](http://localhost:3000/notifications): Trung tâm thông báo sự kiện (mở kỳ, kết quả bỏ thăm, nhận tiền, hoàn thành dây hụi).

---

## 🧪 Kiểm Thử & Đảm Bảo Chất Lượng (Testing & Verification)

### Kiểm Thử Backend (JUnit 5 + Spring Boot Test)
Toàn bộ hệ thống backend được bao phủ bởi **119 ca kiểm thử tự động**, bao gồm kiểm thử toán học biên, ma trận phân quyền chéo tenant và kiểm thử tương tranh:

```bash
cd apps/api
mvn test
```
```
[INFO] Results:
[INFO] Tests run: 119, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Kiểm Tra Frontend (ESLint & Next.js Build)
Mã nguồn frontend được kiểm tra nghiêm ngặt theo các tiêu chuẩn React 19 và biên dịch tối ưu hóa Turbopack:

```bash
cd apps/web
npm run lint     # 0 errors, 0 warnings
npm run build    # 11/11 routes tĩnh và động biên dịch thành công
```

---

## 📜 Tiến Độ Thực Hiện 18 Giai Đoạn (Build Roadmap)

Hệ thống đã hoàn tất trọn vẹn **18/18 giai đoạn** theo đúng lộ trình đặc tả:

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
| **17** | **Preview Polish** | Giao diện tiếng Việt chuẩn mực, định dạng `1.000.000 đ`, Dark Mode, 11 routes, `start.sh` |
| **18** | **Final Delivery** | Kiểm thử đầu cuối (E2E) trọn vẹn 10 quy trình, tài liệu hướng dẫn và hoàn tất chuyển giao |

---

## 📄 Bản Quyền & Giấy Phép (License)

Dự án thuộc quyền sở hữu trí tuệ của nhóm phát triển Tong Tin ROSCA Management System.
Mọi thắc mắc và đóng góp vui lòng tham khảo chi tiết tại thư mục [`docs/`](docs/).
