"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { formatMoney, formatDateTime } from "@/lib/format";
import { StatCard } from "@/components/StatCard";
import { EmptyState } from "@/components/EmptyState";
import { GroupStatusBadge, GroupTypeBadge, CycleStatusBadge } from "@/components/StatusBadge";
import {
  Coins,
  Layers,
  AlertTriangle,
  PlusCircle,
  ArrowRight,
  TrendingUp,
  ShieldAlert,
  Loader2,
} from "lucide-react";

export default function HostDashboardPage() {
  const router = useRouter();
  const [data, setData] = useState<Awaited<ReturnType<typeof api.getHostDashboard>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const auth = getStoredAuth();
    if (!auth || !auth.roles.includes("HOST")) {
      router.push("/login");
      return;
    }

    let ignore = false;
    api.getHostDashboard()
      .then((res) => {
        if (!ignore) {
          setData(res);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : "Không thể tải dữ liệu bảng điều khiển.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [router, refreshKey]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-zinc-500">
          <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
          <p className="text-sm">Đang tải bảng điều khiển Chủ Hụi...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <div className="flex items-center gap-3">
            <ShieldAlert className="h-6 w-6 shrink-0" />
            <div>
              <h3 className="text-base font-bold">Lỗi truy xuất dữ liệu</h3>
              <p className="text-sm mt-1">{error}</p>
            </div>
          </div>
          <button
            onClick={() => {
              setLoading(true);
              setRefreshKey((k) => k + 1);
            }}
            className="mt-4 rounded-xl bg-rose-700 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-600 transition-colors"
          >
            Thử lại
          </button>
        </div>
      </div>
    );
  }

  const groups = data?.groups || [];
  const currencies = data?.currencies || [];
  const totalUnpaid = groups.reduce((acc, g) => acc + g.unpaidCount, 0);
  const totalOverdue = groups.reduce((acc, g) => acc + g.overdueCount, 0);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-8 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            Bảng Điều Khiển Chủ Hụi
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Theo dõi dòng tiền, tiến độ các dây hụi và quản lý các kỳ thanh toán
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            href="/host/members"
            className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
          >
            Danh sách Hội Viên
          </Link>
          <Link
            href="/host/groups/new"
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 transition-colors"
          >
            <PlusCircle className="h-4 w-4" />
            Tạo dây hụi mới
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {currencies.length > 0 ? (
          currencies.map((c) => (
            <StatCard
              key={c.currency}
              title={`Lợi nhuận tiền thảo (${c.currency})`}
              value={formatMoney(c.amountMinor, c.currency, c.exponent)}
              subtitle="Tổng từ các kỳ đã quyết toán"
              icon={TrendingUp}
              badge="Tiền thảo thực thu"
              badgeColor="emerald"
            />
          ))
        ) : (
          <StatCard
            title="Lợi nhuận tiền thảo"
            value="0 đ"
            subtitle="Chưa có kỳ hụi quyết toán"
            icon={TrendingUp}
            badge="VND"
            badgeColor="emerald"
          />
        )}

        <StatCard
          title="Tổng dây hụi"
          value={groups.length}
          subtitle={`${groups.filter((g) => g.status === "RUNNING").length} dây đang chạy`}
          icon={Layers}
          badge={`${groups.length} dây`}
          badgeColor="blue"
        />

        <StatCard
          title="Khoản hụi chưa thu"
          value={totalUnpaid}
          subtitle="Suất đóng cần thu trong các kỳ"
          icon={Coins}
          badge={totalUnpaid > 0 ? "Cần thu" : "Đã thu hết"}
          badgeColor={totalUnpaid > 0 ? "amber" : "emerald"}
        />

        <StatCard
          title="Khoản đóng quá hạn"
          value={totalOverdue}
          subtitle="Số suất hụi trễ hạn thanh toán"
          icon={AlertTriangle}
          badge={totalOverdue > 0 ? "Cần nhắc nợ" : "Tốt"}
          badgeColor={totalOverdue > 0 ? "rose" : "emerald"}
        />
      </div>

      {/* Groups Section */}
      <div className="mt-12">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            Danh sách Dây Hụi
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {groups.length}
            </span>
          </h2>
          <Link
            href="/host/groups/new"
            className="text-xs font-semibold text-emerald-600 hover:text-emerald-500 inline-flex items-center gap-1"
          >
            + Mở thêm dây hụi
          </Link>
        </div>

        {groups.length === 0 ? (
          <EmptyState
            icon={Layers}
            title="Chưa có dây hụi nào"
            description="Bắt đầu tạo dây hụi mới (hụi đấu hoặc hụi thảo) để gom chân và mở các kỳ hụi."
            actionText="Tạo dây hụi đầu tiên"
            actionHref="/host/groups/new"
          />
        ) : (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
            {groups.map((group) => (
              <div
                key={group.id}
                className="group flex flex-col justify-between rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm transition-all hover:border-emerald-500/50 hover:shadow-md dark:border-zinc-800 dark:bg-zinc-900/60"
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <span className="font-mono text-xs font-medium text-zinc-400">
                        {group.code}
                      </span>
                      <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors mt-0.5">
                        {group.name}
                      </h3>
                    </div>
                    <GroupStatusBadge status={group.status} />
                  </div>

                  <div className="mt-3 flex items-center gap-2">
                    <GroupTypeBadge type={group.type} />
                    <span className="text-xs text-zinc-500 dark:text-zinc-400">
                      {group.shareCount} chân • {group.cycleCount} kỳ
                    </span>
                  </div>

                  <div className="mt-6 space-y-2.5 rounded-xl bg-zinc-50 p-3.5 text-xs dark:bg-zinc-800/40">
                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500 dark:text-zinc-400">Tiến độ kỳ:</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                          {group.currentCycleNo ? `Kỳ ${group.currentCycleNo}/${group.cycleCount}` : "Chưa mở kỳ"}
                        </span>
                        {group.currentCycleStatus && (
                          <CycleStatusBadge status={group.currentCycleStatus} />
                        )}
                      </div>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500 dark:text-zinc-400">Hạn đóng tiếp theo:</span>
                      <span className="font-medium text-zinc-700 dark:text-zinc-300">
                        {formatDateTime(group.nextDueAt)}
                      </span>
                    </div>

                    <div className="flex justify-between items-center pt-1 border-t border-zinc-200/60 dark:border-zinc-700/60">
                      <span className="text-zinc-500 dark:text-zinc-400">Chưa thu / Quá hạn:</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-amber-600 dark:text-amber-400">
                          {group.unpaidCount} chưa thu
                        </span>
                        {group.overdueCount > 0 && (
                          <span className="font-semibold text-rose-600 dark:text-rose-400">
                            • {group.overdueCount} trễ
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex justify-between items-center pt-1 border-t border-zinc-200/60 dark:border-zinc-700/60">
                      <span className="text-zinc-500 dark:text-zinc-400">Tiền thảo tích lũy:</span>
                      <span className="font-bold text-emerald-600 dark:text-emerald-400">
                        {formatMoney(group.hostProfitMinor, group.currency)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-6 pt-4 border-t border-zinc-100 dark:border-zinc-800/80 flex items-center justify-between">
                  <Link
                    href={`/host/groups/${group.id}/ledger`}
                    className="text-xs font-semibold text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors"
                  >
                    Xem Sổ cái
                  </Link>
                  <Link
                    href={`/host/groups/${group.id}`}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-600 hover:text-emerald-500 group-hover:translate-x-0.5 transition-all"
                  >
                    Vào phòng hụi
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
