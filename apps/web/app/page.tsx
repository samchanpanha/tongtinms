import Link from "next/link";
import { Users, Coins, ArrowRight, Lock, FileSpreadsheet, Sparkles } from "lucide-react";

export default function Home() {
  return (
    <div className="relative isolate overflow-hidden">
      {/* Background glow */}
      <div className="absolute inset-x-0 -top-40 -z-10 transform-gpu overflow-hidden blur-3xl sm:-top-80">
        <div
          className="relative left-[calc(50%-11rem)] aspect-[1155/678] w-[36.125rem] -translate-x-1/2 rotate-[30deg] bg-gradient-to-tr from-emerald-500 to-teal-300 opacity-20 sm:left-[calc(50%-30rem)] sm:w-[72.1875rem]"
          style={{
            clipPath:
              "polygon(74.1% 44.1%, 100% 61.6%, 97.5% 26.9%, 85.5% 0.1%, 80.7% 2%, 72.5% 32.5%, 60.2% 62.4%, 52.4% 68.1%, 47.5% 58.3%, 45.2% 34.5%, 27.5% 76.7%, 0.1% 64.9%, 17.9% 100%, 27.6% 76.8%, 76.1% 97.7%, 74.1% 44.1%)",
          }}
        />
      </div>

      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-24 lg:px-8">
        <div className="mx-auto max-w-3xl text-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/20 bg-emerald-50/50 px-3 py-1 text-xs font-semibold text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 mb-6">
            <Sparkles className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
            Nền tảng Quản lý Hụi / Hội Thông minh thế hệ mới
          </div>

          <h1 className="text-4xl font-extrabold tracking-tight text-zinc-900 dark:text-zinc-50 sm:text-6xl">
            Quản lý Dây Hụi <br className="hidden sm:inline" />
            <span className="bg-gradient-to-r from-emerald-600 via-teal-500 to-cyan-600 bg-clip-text text-transparent">
              Minh Bạch & An Toàn Tuyệt Đối
            </span>
          </h1>

          <p className="mt-6 text-lg leading-8 text-zinc-600 dark:text-zinc-300">
            Giải pháp chuyên nghiệp dành cho Chủ Hụi và Hội Viên. Tự động hóa tính toán tiền hụi,
            bỏ thăm kín bảo mật, theo dõi sổ cái bất biến theo thời gian thực chuẩn tiền tệ quốc tế.
          </p>

          <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/login"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-6 py-3.5 text-sm font-semibold text-white shadow-lg shadow-emerald-600/20 hover:bg-emerald-500 transition-all hover:scale-[1.02]"
            >
              Vào hệ thống quản lý
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              href="/register/owner"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl border border-zinc-200 bg-white px-6 py-3.5 text-sm font-semibold text-zinc-800 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800/80 transition-all"
            >
              Đăng ký làm Chủ Hụi
            </Link>
          </div>
        </div>

        {/* Feature Cards Grid */}
        <div className="mt-20 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-6 shadow-sm backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/50">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-950/80 dark:text-emerald-300">
              <Lock className="h-5 w-5" />
            </div>
            <h3 className="mt-4 text-base font-bold text-zinc-900 dark:text-zinc-100">Đấu Thăm Kín Bảo Mật</h3>
            <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
              Hội viên bỏ thăm trực tuyến bí mật. Chủ hụi và các hội viên khác hoàn toàn không thể xem giá trước giờ chốt.
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-6 shadow-sm backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/50">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 text-blue-700 dark:bg-blue-950/80 dark:text-blue-300">
              <Coins className="h-5 w-5" />
            </div>
            <h3 className="mt-4 text-base font-bold text-zinc-900 dark:text-zinc-100">Tính Tiền Chuẩn Xác</h3>
            <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
              Công thức hụi đấu và hụi thảo độc lập, không dùng số thực, đảm bảo không sai lệch dù chỉ 1 đồng.
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-6 shadow-sm backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/50">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-100 text-purple-700 dark:bg-purple-950/80 dark:text-purple-300">
              <FileSpreadsheet className="h-5 w-5" />
            </div>
            <h3 className="mt-4 text-base font-bold text-zinc-900 dark:text-zinc-100">Sổ Cái Bất Biến</h3>
            <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
              Ghi nhận từng khoản đóng và giao hụi minh bạch. Lịch sử không bị xóa, có khóa Idempotency chống đóng trùng.
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-6 shadow-sm backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/50">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100 text-amber-700 dark:bg-amber-950/80 dark:text-amber-300">
              <Users className="h-5 w-5" />
            </div>
            <h3 className="mt-4 text-base font-bold text-zinc-900 dark:text-zinc-100">Cổng Thông Tin Hội Viên</h3>
            <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
              Hội viên đăng nhập tra cứu sao kê tài chính cá nhân, vị thế ròng, và kết quả các kỳ hụi công khai rõ ràng.
            </p>
          </div>
        </div>

        {/* Quick Demo Access Bar */}
        <div className="mt-16 rounded-2xl border border-emerald-500/30 bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-cyan-500/10 p-6">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <div>
              <h4 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                Thử nghiệm ngay với tài khoản mẫu (Demo Fixture N=10)
              </h4>
              <p className="text-sm text-zinc-600 dark:text-zinc-400 mt-1">
                Tài khoản Chủ Hụi mẫu: <code className="font-mono bg-zinc-200/60 dark:bg-zinc-800 px-1.5 py-0.5 rounded text-xs">0900111001</code> / <code className="font-mono bg-zinc-200/60 dark:bg-zinc-800 px-1.5 py-0.5 rounded text-xs">demo1234</code>
              </p>
            </div>
            <Link
              href="/login"
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow hover:bg-emerald-500 transition-colors whitespace-nowrap"
            >
              Đăng nhập tài khoản mẫu
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
