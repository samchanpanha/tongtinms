"use client";

import React, { useEffect, useState, use } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import {
  formatMoney,
  formatDateTime,
  formatPhone,
  getCycleUnitLabel,
} from "@/lib/format";
import { GroupStatusBadge, GroupTypeBadge, CycleStatusBadge, ShareStatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import {
  Layers,
  Play,
  CheckCircle2,
  AlertCircle,
  FileSpreadsheet,
  Coins,
  PlusCircle,
  Loader2,
  Clock,
  X,
  CreditCard,
} from "lucide-react";

type GroupDetails = Awaited<ReturnType<typeof api.getGroup>>;
type GroupShare = Awaited<ReturnType<typeof api.getGroupShares>>[number];
type GroupCycle = Awaited<ReturnType<typeof api.getGroupCycles>>[number];
type GroupDebt = Awaited<ReturnType<typeof api.getDebts>>[number];
type MemberItem = Awaited<ReturnType<typeof api.getMembers>>[number];

export default function GroupDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);

  const [group, setGroup] = useState<GroupDetails | null>(null);
  const [shares, setShares] = useState<GroupShare[]>([]);
  const [cycles, setCycles] = useState<GroupCycle[]>([]);
  const [debts, setDebts] = useState<GroupDebt[]>([]);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"shares" | "cycles" | "payments">("cycles");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  // Modals & form state
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [shareCountToAssign, setShareCountToAssign] = useState<number>(1);

  const [showBidModal, setShowBidModal] = useState(false);
  const [bidCycleId, setBidCycleId] = useState<number | null>(null);
  const [bidShareId, setBidShareId] = useState<number | null>(null);
  const [bidAmountMinor, setBidAmountMinor] = useState<number>(100_000);

  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentDebt, setPaymentDebt] = useState<GroupDebt | null>(null);
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [refreshKey, setRefreshKey] = useState(0);

  const reloadAll = () => setRefreshKey((k) => k + 1);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.getGroup(id),
      api.getGroupShares(id),
      api.getGroupCycles(id),
      api.getDebts(id),
      api.getMembers(),
    ])
      .then(([g, sh, cy, db, mb]) => {
        if (!ignore) {
          setGroup(g);
          setShares(sh);
          setCycles(cy);
          setDebts(db);
          setMembers(mb);
          setActionError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setActionError(err instanceof Error ? err.message : "Lỗi khi tải thông tin dây hụi.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id, refreshKey]);

  const handleStartGroup = async () => {
    setActionError(null);
    try {
      await api.startGroup(id);
      setActionSuccess("Đã bắt đầu dây hụi thành công! Trạng thái sẵn sàng (READY).");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Không thể bắt đầu dây hụi.");
    }
  };

  const handleOpenCycle = async () => {
    setActionError(null);
    try {
      const res = await api.openCycle(id);
      setActionSuccess(`Đã mở thành công Kỳ số ${res.cycleNo}!`);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Mở kỳ hụi thất bại.");
    }
  };

  const handleAssignShare = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMemberId) return;
    setActionError(null);
    try {
      await api.assignShare(id, selectedMemberId, shareCountToAssign);
      setShowAssignModal(false);
      setActionSuccess("Đã gán chân hụi cho hội viên thành công!");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Gán chân hụi thất bại.");
    }
  };

  const handleSubmitBid = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bidCycleId || !bidShareId) return;
    setActionError(null);
    try {
      await api.submitHostBid(bidCycleId, bidShareId, bidAmountMinor);
      setShowBidModal(false);
      setActionSuccess("Đã ghi nhận thăm hụi thành công!");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Nhập thăm hụi thất bại.");
    }
  };

  const handleCloseAndCalc = async (cycleId: number) => {
    setActionError(null);
    try {
      await api.closeAndCalculate(cycleId);
      setActionSuccess("Đã chốt kỳ và tính toán sổ cái thành công! Chờ giao tiền hụi.");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Chốt kỳ thất bại.");
    }
  };

  const handleConfirmPayout = async (cycleId: number) => {
    setActionError(null);
    try {
      await api.confirmPayout(cycleId);
      setActionSuccess("Đã xác nhận giao hụi thành công! Kỳ hụi đã quyết toán (SETTLED).");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Xác nhận giao hụi thất bại.");
    }
  };

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!paymentDebt) return;
    setActionError(null);
    try {
      const idempotencyKey = `PAY-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
      await api.recordPayment(
        id,
        {
          amountMinor: paymentDebt.remainingMinor,
          currency: paymentDebt.currency,
          method: paymentMethod,
          allocations: [
            {
              ledgerEntryId: paymentDebt.ledgerEntryId,
              amountMinor: paymentDebt.remainingMinor,
            },
          ],
        },
        idempotencyKey
      );
      setShowPaymentModal(false);
      setActionSuccess("Ghi nhận đóng tiền hụi thành công!");
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Ghi nhận thanh toán thất bại.");
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  if (!group) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-12">
        <EmptyState
          icon={Layers}
          title="Không tìm thấy dây hụi"
          description="Dây hụi này không tồn tại hoặc bạn không có quyền quản lý."
          actionText="Về bảng điều khiển"
          actionHref="/host"
        />
      </div>
    );
  }

  const activeCycle = cycles.find((c) => c.status === "OPEN" || c.status === "BIDDING" || c.status === "PAYOUT_PENDING");
  const canStartGroup = (group.status === "DRAFT" || group.status === "RECRUITING") && shares.length === group.shareCount;
  const canOpenCycle = (group.status === "READY" || group.status === "RUNNING") && !activeCycle && cycles.length < group.cycleCount;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Top Banner */}
      <div className="rounded-3xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
          <div>
            <div className="flex items-center gap-3">
              <span className="font-mono text-xs font-semibold text-zinc-400">
                {group.code}
              </span>
              <GroupStatusBadge status={group.status} />
              <GroupTypeBadge type={group.type} />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 mt-1">
              {group.name}
            </h1>
            <div className="mt-2 flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-zinc-500 dark:text-zinc-400">
              <span>
                Tiền chân: <strong className="text-zinc-900 dark:text-zinc-100">{formatMoney(group.baseAmount, group.currency)}</strong>
              </span>
              <span>
                Quy mô: <strong className="text-zinc-900 dark:text-zinc-100">{shares.length}/{group.shareCount} chân</strong>
              </span>
              <span>
                Kỳ hạn: <strong className="text-zinc-900 dark:text-zinc-100">{group.cycleCount} {getCycleUnitLabel(group.cycleUnit)}</strong>
              </span>
              <span>
                Tiền thảo: <strong className="text-emerald-600 dark:text-emerald-400">
                  {group.hostFeeType === "FIXED_PER_CYCLE" ? formatMoney(group.hostFeeMinor, group.currency) : "1%"}
                </strong>
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Link
              href={`/host/groups/${id}/ledger`}
              className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800"
            >
              <FileSpreadsheet className="h-4 w-4 text-purple-600" />
              Sổ cái Dây Hụi
            </Link>

            {canStartGroup && (
              <button
                onClick={handleStartGroup}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow hover:bg-emerald-500 cursor-pointer"
              >
                <Play className="h-4 w-4" />
                Kích hoạt Dây Hụi (Khóa chân)
              </button>
            )}

            {canOpenCycle && (
              <button
                onClick={handleOpenCycle}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow hover:bg-emerald-500 cursor-pointer"
              >
                <PlusCircle className="h-4 w-4" />
                Mở Kỳ Hụi Số {cycles.length + 1}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Notifications / Alerts */}
      {actionError && (
        <div className="flex items-center gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{actionError}</span>
        </div>
      )}
      {actionSuccess && (
        <div className="flex items-center gap-2.5 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-xs text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{actionSuccess}</span>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="flex border-b border-zinc-200 dark:border-zinc-800 text-sm font-semibold">
        <button
          onClick={() => setActiveTab("cycles")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "cycles"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
        >
          Kỳ Hụi & Tiến Độ ({cycles.length}/{group.cycleCount})
        </button>
        <button
          onClick={() => setActiveTab("shares")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "shares"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
        >
          Chân Hụi & Hội Viên ({shares.length}/{group.shareCount})
        </button>
        <button
          onClick={() => setActiveTab("payments")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "payments"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
        >
          Khoản Cần Thu ({debts.length})
        </button>
      </div>

      {/* Tab 1: Cycles & Bidding */}
      {activeTab === "cycles" && (
        <div className="space-y-6">
          {cycles.length === 0 ? (
            <EmptyState
              icon={Clock}
              title="Chưa có kỳ hụi nào được mở"
              description={
                group.status === "READY" || group.status === "RUNNING"
                  ? "Dây hụi đã sẵn sàng! Bấm nút bên dưới để mở kỳ hụi đầu tiên."
                  : `Cần đủ ${group.shareCount} chân hụi và bấm Kích hoạt trước khi mở kỳ.`
              }
              actionText={canOpenCycle ? "Mở Kỳ Hụi Đầu Tiên" : undefined}
              onAction={canOpenCycle ? handleOpenCycle : undefined}
            />
          ) : (
            <div className="grid grid-cols-1 gap-6">
              {cycles.map((c) => (
                <div
                  key={c.id}
                  className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-zinc-100 dark:border-zinc-800">
                    <div className="flex items-center gap-3">
                      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-zinc-100 font-bold text-xs text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                        #{c.cycleNo}
                      </span>
                      <div>
                        <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
                          Kỳ Hụi Số {c.cycleNo}
                        </h3>
                        <p className="text-xs text-zinc-400">
                          Mở ngày: {formatDateTime(c.openAt)} • Hạn đóng: {formatDateTime(c.dueAt)}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <CycleStatusBadge status={c.status} />
                      
                      {/* Workflow Actions */}
                      {c.status === "BIDDING" && (
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => {
                              setBidCycleId(c.id);
                              setBidShareId(shares[0]?.id || null);
                              setShowBidModal(true);
                            }}
                            className="rounded-xl border border-zinc-200 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800 cursor-pointer"
                          >
                            + Nhập thăm hộ
                          </button>
                          <button
                            onClick={() => handleCloseAndCalc(c.id)}
                            className="rounded-xl bg-orange-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-orange-500 cursor-pointer"
                          >
                            Chốt kỳ & Tính tiền
                          </button>
                        </div>
                      )}

                      {c.status === "OPEN" && (
                        <button
                          onClick={() => handleCloseAndCalc(c.id)}
                          className="rounded-xl bg-orange-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-orange-500 cursor-pointer"
                        >
                          Chốt kỳ & Tính tiền
                        </button>
                      )}

                      {c.status === "PAYOUT_PENDING" && (
                        <button
                          onClick={() => handleConfirmPayout(c.id)}
                          className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
                        >
                          Xác nhận Đã Giao Tiền
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Financial outcome */}
                  <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">Thăm trúng (Bỏ hụi):</span>
                      <p className="font-bold text-sm text-zinc-900 dark:text-zinc-50 mt-1">
                        {formatMoney(c.winningBid, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">Tổng tiền gom:</span>
                      <p className="font-bold text-sm text-zinc-900 dark:text-zinc-50 mt-1">
                        {formatMoney(c.grossPot, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">Tiền thảo Chủ Hụi:</span>
                      <p className="font-bold text-sm text-emerald-600 dark:text-emerald-400 mt-1">
                        {formatMoney(c.hostFee, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-emerald-50 p-3 dark:bg-emerald-950/30 border border-emerald-500/20">
                      <span className="text-emerald-800 dark:text-emerald-300 font-medium">Thực nhận (Giao hụi):</span>
                      <p className="font-bold text-sm text-emerald-700 dark:text-emerald-200 mt-1">
                        {formatMoney(c.netPayout, c.currency)}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Shares & Members */}
      {activeTab === "shares" && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
              Danh sách {shares.length}/{group.shareCount} Chân Hụi
            </h3>
            {group.status === "DRAFT" || group.status === "RECRUITING" ? (
              <button
                onClick={() => setShowAssignModal(true)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
              >
                <PlusCircle className="h-4 w-4" />
                Gán thêm chân hụi
              </button>
            ) : null}
          </div>

          <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                <tr>
                  <th className="py-3.5 pl-6 pr-3">Số chân</th>
                  <th className="px-3 py-3.5">Tên Hội Viên</th>
                  <th className="px-3 py-3.5">Số điện thoại</th>
                  <th className="px-3 py-3.5">Trạng thái chân</th>
                  <th className="py-3.5 pl-3 pr-6 text-right">Kỳ đã hốt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                {shares.map((s) => (
                  <tr key={s.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                    <td className="py-3.5 pl-6 pr-3 font-mono font-bold text-xs text-zinc-500">
                      Chân #{s.shareNo}
                    </td>
                    <td className="px-3 py-3.5 font-semibold text-zinc-900 dark:text-zinc-50">
                      {s.memberName}
                    </td>
                    <td className="px-3 py-3.5 font-mono text-xs">
                      {formatPhone(s.memberPhone)}
                    </td>
                    <td className="px-3 py-3.5">
                      <ShareStatusBadge status={s.status} />
                    </td>
                    <td className="py-3.5 pl-3 pr-6 text-right font-medium text-xs">
                      {s.wonCycleId ? `Kỳ #${s.wonCycleId}` : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 3: Payments & Debts */}
      {activeTab === "payments" && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
              Khoản Hụi Cần Thu Theo Kỳ
            </h3>
            <span className="text-xs text-zinc-500">
              Tổng số: <strong>{debts.length}</strong> suất chưa hoàn tất
            </span>
          </div>

          {debts.length === 0 ? (
            <EmptyState
              icon={Coins}
              title="Không có khoản đóng nào tồn đọng"
              description="Tất cả hội viên đã thanh toán đầy đủ các kỳ hiện tại."
            />
          ) : (
            <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                  <tr>
                    <th className="py-3.5 pl-6 pr-3">Kỳ hụi</th>
                    <th className="px-3 py-3.5">Chân hụi</th>
                    <th className="px-3 py-3.5">Số tiền cần đóng</th>
                    <th className="px-3 py-3.5">Đã đóng</th>
                    <th className="px-3 py-3.5">Còn nợ</th>
                    <th className="px-3 py-3.5">Hạn đóng</th>
                    <th className="py-3.5 pl-3 pr-6 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                  {debts.map((d) => (
                    <tr key={d.ledgerEntryId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                      <td className="py-3.5 pl-6 pr-3 font-semibold">
                        Kỳ #{d.cycleNo}
                      </td>
                      <td className="px-3 py-3.5 font-mono text-xs">
                        Chân #{d.shareId}
                      </td>
                      <td className="px-3 py-3.5 font-medium">
                        {formatMoney(d.amountMinor, d.currency)}
                      </td>
                      <td className="px-3 py-3.5 text-emerald-600 font-medium">
                        {formatMoney(d.allocatedMinor, d.currency)}
                      </td>
                      <td className="px-3 py-3.5 font-bold text-amber-600">
                        {formatMoney(d.remainingMinor, d.currency)}
                      </td>
                      <td className="px-3 py-3.5 text-xs text-zinc-500">
                        {formatDateTime(d.dueAt)}
                      </td>
                      <td className="py-3.5 pl-3 pr-6 text-right">
                        <button
                          onClick={() => {
                            setPaymentDebt(d);
                            setShowPaymentModal(true);
                          }}
                          className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
                        >
                          <CreditCard className="h-3.5 w-3.5" />
                          Thu tiền
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Modal: Assign Shares */}
      {showAssignModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                Gán Chân Hụi Cho Hội Viên
              </h3>
              <button
                onClick={() => setShowAssignModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleAssignShare} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Chọn Hội Viên *
                </label>
                <select
                  required
                  value={selectedMemberId || ""}
                  onChange={(e) => setSelectedMemberId(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="">-- Chọn hội viên từ danh bạ --</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.fullName} ({formatPhone(m.phone)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Số lượng chân gán
                </label>
                <input
                  type="number"
                  min={1}
                  max={group.shareCount - shares.length}
                  required
                  value={shareCountToAssign}
                  onChange={(e) => setShareCountToAssign(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowAssignModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  Gán chân hụi
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Enter Bid on behalf of member */}
      {showBidModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                Nhập Thăm Hụi (Chủ Hụi Nhập Hộ)
              </h3>
              <button
                onClick={() => setShowBidModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitBid} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Chọn chân hụi bỏ giá *
                </label>
                <select
                  required
                  value={bidShareId || ""}
                  onChange={(e) => setBidShareId(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  {shares
                    .filter((s) => s.status === "ALIVE")
                    .map((s) => (
                      <option key={s.id} value={s.id}>
                        Chân #{s.shareNo} — {s.memberName}
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Mức thăm bỏ (VND) *
                </label>
                <input
                  type="number"
                  min={0}
                  step={group.bidStep || 1000}
                  max={group.maxBid || group.baseAmount - 1}
                  required
                  value={bidAmountMinor}
                  onChange={(e) => setBidAmountMinor(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowBidModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  Lưu thăm hụi
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Record Payment */}
      {showPaymentModal && paymentDebt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                Ghi Nhận Đóng Tiền Hụi
              </h3>
              <button
                onClick={() => setShowPaymentModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleRecordPayment} className="mt-4 space-y-4">
              <div className="rounded-xl bg-zinc-50 p-3.5 text-xs dark:bg-zinc-800/40 space-y-1">
                <div className="flex justify-between">
                  <span className="text-zinc-500">Kỳ hụi:</span>
                  <span className="font-bold">Kỳ #{paymentDebt.cycleNo}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-500">Chân hụi:</span>
                  <span className="font-bold">Chân #{paymentDebt.shareId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-500">Số tiền thu:</span>
                  <span className="font-bold text-emerald-600">
                    {formatMoney(paymentDebt.remainingMinor, paymentDebt.currency)}
                  </span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Phương thức thanh toán
                </label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="CASH">Tiền mặt (CASH)</option>
                  <option value="BANK_TRANSFER">Chuyển khoản ngân hàng (BANK_TRANSFER)</option>
                  <option value="OTHER">Khác</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowPaymentModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  Xác nhận đã thu tiền
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
