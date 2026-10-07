#!/usr/bin/env bash
# ==============================================================================
# Tong Tin Management System (ROSCA / Hụi / Hội)
# All-in-One Startup Script
# ==============================================================================
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$ROOT_DIR/apps/api"
WEB_DIR="$ROOT_DIR/apps/web"

API_PID=""
WEB_PID=""

cleanup() {
  echo ""
  echo "🛑 Shutting down Tong Tin services..."
  if [ -n "$API_PID" ] && kill -0 "$API_PID" 2>/dev/null; then
    echo "  - Stopping Backend API (PID $API_PID)..."
    kill "$API_PID" 2>/dev/null || true
  fi
  if [ -n "$WEB_PID" ] && kill -0 "$WEB_PID" 2>/dev/null; then
    echo "  - Stopping Frontend Web (PID $WEB_PID)..."
    kill "$WEB_PID" 2>/dev/null || true
  fi
  echo "✓ Services stopped cleanly."
  exit 0
}

trap cleanup SIGINT SIGTERM

MODE="all"
SEED_FLAG="false"

for arg in "$@"; do
  case "$arg" in
    --api-only)
      MODE="api"
      ;;
    --web-only)
      MODE="web"
      ;;
    --seed)
      SEED_FLAG="true"
      ;;
    --help|-h)
      echo "Tong Tin Startup Script"
      echo "Usage: ./start.sh [options]"
      echo ""
      echo "Options:"
      echo "  --api-only    Start only PostgreSQL and Backend API (port 8080)"
      echo "  --web-only    Start only Next.js Frontend (port 3000)"
      echo "  --seed        Seed demo data (Host 0900111001, Member 0900100001, 10 shares)"
      echo "  --help, -h    Show this help message"
      exit 0
      ;;
  esac
done

echo "=============================================================================="
echo "           🚀 KHỞI ĐỘNG HỆ THỐNG QUẢN LÝ HỤI / TONG TIN / ROSCA               "
echo "=============================================================================="
echo "Mode: $MODE | Seed Demo Data: $SEED_FLAG"
echo ""

# 1. Start PostgreSQL via Docker Compose
if [ "$MODE" != "web" ]; then
  echo "📦 [1/3] Kiểm tra & khởi động cơ sở dữ liệu PostgreSQL..."
  if command -v docker &> /dev/null; then
    docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres
    echo "⏳ Chờ PostgreSQL sẵn sàng trên cổng 5432..."
    for i in {1..20}; do
      if docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T postgres pg_isready -U tongtin -d tongtin &> /dev/null; then
        echo "✓ PostgreSQL đã sẵn sàng!"
        break
      fi
      sleep 1
    done
  else
    echo "⚠️ Docker không tìm thấy. Giả định PostgreSQL đang chạy trên cổng 5432..."
  fi
fi

# 2. Start Backend API
if [ "$MODE" == "all" ] || [ "$MODE" == "api" ]; then
  echo ""
  echo "⚙️  [2/3] Khởi động Backend API (Spring Boot Java 21)..."
  SPRING_OPTS="-Dspring.profiles.active=default"
  if [ "$SEED_FLAG" == "true" ]; then
    SPRING_OPTS="$SPRING_OPTS -Dapp.seed-demo=true"
    echo "  🌱 Demo Data Seeder được bật (app.seed-demo=true)"
  fi

  (
    cd "$API_DIR"
    mvn spring-boot:run -Dspring-boot.run.jvmArguments="$SPRING_OPTS"
  ) &
  API_PID=$!
  echo "Backend API đã khởi chạy với PID $API_PID"

  echo "⏳ Đang đợi Backend API khởi động tại http://localhost:8080/api/v1/health..."
  API_READY=false
  for i in {1..40}; do
    if curl -s -f http://localhost:8080/api/v1/health > /dev/null 2>&1; then
      API_READY=true
      echo "✓ Backend API đã sẵn sàng!"
      break
    fi
    sleep 2
  done

  if [ "$API_READY" = false ]; then
    echo "⚠️ Backend API mất nhiều thời gian hơn dự kiến, đang tiếp tục..."
  fi
fi

# 3. Start Frontend Web
if [ "$MODE" == "all" ] || [ "$MODE" == "web" ]; then
  echo ""
  echo "🌐 [3/3] Khởi động Frontend Web (Next.js 16 + Tailwind CSS v4)..."
  (
    cd "$WEB_DIR"
    npm run dev
  ) &
  WEB_PID=$!
  echo "Frontend Web đã khởi chạy với PID $WEB_PID"
fi

echo ""
echo "=============================================================================="
echo "                       🎉 HỆ THỐNG ĐÃ SẴN SÀNG!                               "
echo "=============================================================================="
if [ "$MODE" != "api" ]; then
  echo "🌐 Cổng giao diện Web : http://localhost:3000"
fi
if [ "$MODE" != "web" ]; then
  echo "⚙️  Cổng dịch vụ API   : http://localhost:8080/api/v1"
  echo "📖 Tài liệu OpenAPI    : http://localhost:8080/swagger-ui.html"
fi
echo ""
echo "🔑 Tài khoản mẫu thử nghiệm (sau khi chạy với --seed):"
echo "  • Chủ Hụi (Host)     : SĐT 0900111001 | Mật khẩu: demo1234"
echo "  • Hội Viên (Member)  : SĐT 0900100001 | Mật khẩu: demo1234"
echo ""
echo "Nhấn Ctrl+C để dừng toàn bộ hệ thống."
echo "=============================================================================="

# Wait for children
wait
