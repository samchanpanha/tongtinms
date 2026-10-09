"use client";

import React, { useEffect, useState, useMemo, use } from "react";
import Link from "next/link";
import Image from "next/image";
import { api } from "@/lib/api";
import {
  formatMoney,
  formatDateTime,
  formatPhone,
  formatBytes,
  getCycleUnitLabel,
} from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
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
  Download,
  FileText,
  Upload,
  Trash2,
  Eye,
  QrCode,
  Zap,
} from "lucide-react";

type GroupDetails = Awaited<ReturnType<typeof api.getGroup>>;
type GroupShare = Awaited<ReturnType<typeof api.getGroupShares>>[number];
type GroupCycle = Awaited<ReturnType<typeof api.getGroupCycles>>[number];
type GroupDebt = Awaited<ReturnType<typeof api.getDebts>>[number];
type GroupPayment = Awaited<ReturnType<typeof api.getPayments>>[number];
type MemberItem = Awaited<ReturnType<typeof api.getMembers>>[number];

export default function GroupDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { language, t } = useLanguage();

  const [group, setGroup] = useState<GroupDetails | null>(null);
  const [shares, setShares] = useState<GroupShare[]>([]);
  const [cycles, setCycles] = useState<GroupCycle[]>([]);
  const [debts, setDebts] = useState<GroupDebt[]>([]);
  const [payments, setPayments] = useState<GroupPayment[]>([]);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"shares" | "cycles" | "payments">("cycles");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [uploadingPaymentId, setUploadingPaymentId] = useState<number | null>(null);

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

  const [showQuickPayModal, setShowQuickPayModal] = useState(false);
  const [quickPayMemberId, setQuickPayMemberId] = useState<number | null>(null);
  const [quickPayAmountMinor, setQuickPayAmountMinor] = useState<number>(0);
  const [quickPayMethod, setQuickPayMethod] = useState("CASH");
  const [quickPayNote, setQuickPayNote] = useState("");
  const [quickPayReceipt, setQuickPayReceipt] = useState<File | null>(null);
  const [quickPaying, setQuickPaying] = useState(false);

  const [qrDebt, setQrDebt] = useState<GroupDebt | null>(null);
  const [qrImageUrl, setQrImageUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [qrError, setQrError] = useState<string | null>(null);

  const reloadAll = () => setRefreshKey((k) => k + 1);

  // Step 36: members with outstanding debt, grouped for the quick-pay picker.
  const debtByMember = useMemo(() => {
    const map = new Map<
      number,
      { memberProfileId: number; obligationCount: number; totalRemaining: number; currency: string }
    >();
    for (const d of debts) {
      if (d.memberProfileId == null) continue;
      const entry = map.get(d.memberProfileId);
      if (entry) {
        entry.obligationCount += 1;
        entry.totalRemaining += d.remainingMinor;
      } else {
        map.set(d.memberProfileId, {
          memberProfileId: d.memberProfileId,
          obligationCount: 1,
          totalRemaining: d.remainingMinor,
          currency: d.currency,
        });
      }
    }
    return [...map.values()];
  }, [debts]);

  const assignQuickPayMember = (memberProfileId: number) => {
    setQuickPayMemberId(memberProfileId);
    const memberDebt = debtByMember.find((m) => m.memberProfileId === memberProfileId);
    setQuickPayAmountMinor(memberDebt?.totalRemaining ?? 0);
  };

  const openQr = async (debt: GroupDebt) => {
    if (qrImageUrl) URL.revokeObjectURL(qrImageUrl);
    setQrDebt(debt);
    setQrImageUrl(null);
    setQrError(null);
    setQrLoading(true);
    try {
      const url = await api.fetchObligationQr(debt.ledgerEntryId);
      setQrImageUrl(url);
    } catch (err) {
      setQrError(err instanceof Error ? err.message : String(err));
    } finally {
      setQrLoading(false);
    }
  };

  const closeQr = () => {
    if (qrImageUrl) URL.revokeObjectURL(qrImageUrl);
    setQrImageUrl(null);
    setQrError(null);
    setQrDebt(null);
  };

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.getGroup(id),
      api.getGroupShares(id),
      api.getGroupCycles(id),
      api.getDebts(id),
      api.getPayments(id),
      api.getMembers(),
    ])
      .then(([g, sh, cy, db, pm, mb]) => {
        if (!ignore) {
          setGroup(g);
          setShares(sh);
          setCycles(cy);
          setDebts(db);
          setPayments(pm);
          setMembers(mb);
          setActionError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setActionError(err instanceof Error ? err.message : t.hostGroupDetail.defaultError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id, refreshKey, t.hostGroupDetail.defaultError]);

  const handleStartGroup = async () => {
    setActionError(null);
    try {
      await api.startGroup(id);
      setActionSuccess(t.hostGroupDetail.msgStartSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgStartError);
    }
  };

  const handleOpenCycle = async () => {
    setActionError(null);
    try {
      const res = await api.openCycle(id);
      setActionSuccess(t.hostGroupDetail.msgOpenCycleSuccess(res.cycleNo));
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgOpenCycleError);
    }
  };

  const handleAssessLateFees = async () => {
    setActionError(null);
    setActionSuccess(null);
    try {
      const res = await api.assessLateFees(id);
      if (res.created > 0) {
        setActionSuccess(t.hostGroupDetail.msgLateFeeSuccess(res.created));
      } else {
        setActionSuccess(t.hostGroupDetail.msgLateFeeNothing);
      }
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgLateFeeError);
    }
  };

  const handleProfitExport = async (format: "csv" | "xlsx") => {
    setExportError(null);
    setExporting(true);
    try {
      await api.exportProfit(id, format);
    } catch (err: unknown) {
      setExportError(err instanceof Error ? err.message : t.hostGroupDetail.exportError);
    } finally {
      setExporting(false);
    }
  };

  const handleReceiptUpload = async (paymentId: number, file: File | null) => {
    if (!file) return;
    setActionError(null);
    setActionSuccess(null);
    setUploadingPaymentId(paymentId);
    try {
      await api.uploadPaymentAttachment(paymentId, file);
      setActionSuccess(t.hostGroupDetail.msgReceiptUploadSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgReceiptUploadError);
    } finally {
      setUploadingPaymentId(null);
    }
  };

  const handleReceiptDelete = async (attachmentId: number) => {
    setActionError(null);
    setActionSuccess(null);
    try {
      await api.deleteAttachment(attachmentId);
      setActionSuccess(t.hostGroupDetail.msgReceiptDeleteSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgReceiptUploadError);
    }
  };

  const handleReceiptDownload = async (attachmentId: number, name: string) => {
    setActionError(null);
    setActionSuccess(null);
    try {
      await api.downloadAttachment(attachmentId, name);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgReceiptDownloadError);
    }
  };

  const paymentMethodLabel = (method: string) => {
    if (method === "CASH") return t.hostGroupDetail.methodCash;
    if (method === "BANK_TRANSFER") return t.hostGroupDetail.methodBank;
    return t.hostGroupDetail.methodOther;
  };

  const handleAssignShare = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMemberId) return;
    setActionError(null);
    try {
      await api.assignShare(id, selectedMemberId, shareCountToAssign);
      setShowAssignModal(false);
      setActionSuccess(t.hostGroupDetail.msgAssignSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgAssignError);
    }
  };

  const handleSubmitBid = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bidCycleId || !bidShareId) return;
    setActionError(null);
    try {
      await api.submitHostBid(bidCycleId, bidShareId, bidAmountMinor);
      setShowBidModal(false);
      setActionSuccess(t.hostGroupDetail.msgBidSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgBidError);
    }
  };

  const handleCloseAndCalc = async (cycleId: number) => {
    setActionError(null);
    try {
      await api.closeAndCalculate(cycleId);
      setActionSuccess(t.hostGroupDetail.msgCloseSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgCloseError);
    }
  };

  const handleConfirmPayout = async (cycleId: number) => {
    setActionError(null);
    try {
      await api.confirmPayout(cycleId);
      setActionSuccess(t.hostGroupDetail.msgPayoutSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgPayoutError);
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
      setActionSuccess(t.hostGroupDetail.msgPaymentSuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgPaymentError);
    }
  };

  const handleQuickPay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!quickPayMemberId || quickPayAmountMinor <= 0) return;
    setActionError(null);
    setQuickPaying(true);
    try {
      const idempotencyKey = `QP-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
      const memberDebt = debtByMember.find((m) => m.memberProfileId === quickPayMemberId);
      const currency = memberDebt?.currency ?? group?.currency ?? "VND";
      const payment = await api.quickPay(
        id,
        {
          memberProfileId: quickPayMemberId,
          amountMinor: quickPayAmountMinor,
          currency,
          method: quickPayMethod,
          note: quickPayNote.trim() || undefined,
          allocateLateFees: true,
        },
        idempotencyKey
      );
      if (quickPayReceipt) {
        await api.uploadPaymentAttachment(payment.id, quickPayReceipt);
      }
      setShowQuickPayModal(false);
      setQuickPayReceipt(null);
      setQuickPayNote("");
      setActionSuccess(t.hostGroupDetail.msgQuickPaySuccess);
      reloadAll();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : t.hostGroupDetail.msgPaymentError);
    } finally {
      setQuickPaying(false);
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
          title={t.hostGroupDetail.notFoundTitle}
          description={t.hostGroupDetail.notFoundDesc}
          actionText={t.hostGroupDetail.backToDashboard}
          actionHref="/host"
        />
      </div>
    );
  }

  const activeCycle = cycles.find(
    (c) => c.status === "OPEN" || c.status === "BIDDING" || c.status === "PAYOUT_PENDING"
  );
  const canStartGroup =
    (group.status === "DRAFT" || group.status === "RECRUITING") &&
    shares.length === group.shareCount;
  const canOpenCycle =
    (group.status === "READY" || group.status === "RUNNING") &&
    !activeCycle &&
    cycles.length < group.cycleCount;

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
                {t.hostGroupDetail.baseAmountLabel}{" "}
                <strong className="text-zinc-900 dark:text-zinc-100">
                  {formatMoney(group.baseAmount, group.currency)}
                </strong>
              </span>
              <span>
                {t.hostGroupDetail.scaleLabel}{" "}
                <strong className="text-zinc-900 dark:text-zinc-100">
                  {t.hostGroupDetail.sharesProgress(shares.length, group.shareCount)}
                </strong>
              </span>
              <span>
                {t.hostGroupDetail.durationLabel}{" "}
                <strong className="text-zinc-900 dark:text-zinc-100">
                  {group.cycleCount} {getCycleUnitLabel(group.cycleUnit, language)}
                </strong>
              </span>
              <span>
                {t.hostGroupDetail.hostFeeLabel}{" "}
                <strong className="text-emerald-600 dark:text-emerald-400">
                  {group.hostFeeType === "FIXED_PER_CYCLE"
                    ? formatMoney(group.hostFeeMinor, group.currency)
                    : "1%"}
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
              {t.hostGroupDetail.ledgerBtn}
            </Link>

            {canStartGroup && (
              <button
                onClick={handleStartGroup}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow hover:bg-emerald-500 cursor-pointer"
              >
                <Play className="h-4 w-4" />
                {t.hostGroupDetail.startGroupBtn}
              </button>
            )}

            {canOpenCycle && (
              <button
                onClick={handleOpenCycle}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow hover:bg-emerald-500 cursor-pointer"
              >
                <PlusCircle className="h-4 w-4" />
                {t.hostGroupDetail.openCycleBtn(cycles.length + 1)}
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
          {t.hostGroupDetail.tabCycles(cycles.length, group.cycleCount)}
        </button>
        <button
          onClick={() => setActiveTab("shares")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "shares"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
        >
          {t.hostGroupDetail.tabShares(shares.length, group.shareCount)}
        </button>
        <button
          onClick={() => setActiveTab("payments")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "payments"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
        >
          {t.hostGroupDetail.tabPayments(debts.length)}
        </button>
      </div>

      {/* Tab 1: Cycles & Bidding */}
      {activeTab === "cycles" && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
              {t.hostGroupDetail.profitExportHeader}
            </h3>
            <div className="flex shrink-0 flex-col items-end gap-1.5">
              <div className="flex items-center gap-3">
                <button
                  onClick={() => handleProfitExport("csv")}
                  disabled={exporting}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 cursor-pointer disabled:opacity-50 dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70"
                >
                  <Download className="h-3.5 w-3.5" />
                  {t.hostGroupDetail.exportProfitCsv}
                </button>
                <button
                  onClick={() => handleProfitExport("xlsx")}
                  disabled={exporting}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 cursor-pointer disabled:opacity-50 dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70"
                >
                  <Download className="h-3.5 w-3.5" />
                  {t.hostGroupDetail.exportProfitXlsx}
                </button>
              </div>
              {exportError && (
                <span className="text-xs text-red-600 dark:text-red-400">{exportError}</span>
              )}
            </div>
          </div>
          {cycles.length === 0 ? (
            <EmptyState
              icon={Clock}
              title={t.hostGroupDetail.emptyCyclesTitle}
              description={
                group.status === "READY" || group.status === "RUNNING"
                  ? t.hostGroupDetail.emptyCyclesReadyDesc
                  : t.hostGroupDetail.emptyCyclesNeedSharesDesc(group.shareCount)
              }
              actionText={canOpenCycle ? t.hostGroupDetail.openFirstCycleBtn : undefined}
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
                          {t.hostGroupDetail.cycleTitle(c.cycleNo)}
                        </h3>
                        <p className="text-xs text-zinc-400">
                          {t.hostGroupDetail.cycleDates(
                            formatDateTime(c.openAt, language),
                            formatDateTime(c.dueAt, language)
                          )}
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
                            {t.hostGroupDetail.enterBidForMemberBtn}
                          </button>
                          <button
                            onClick={() => handleCloseAndCalc(c.id)}
                            className="rounded-xl bg-orange-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-orange-500 cursor-pointer"
                          >
                            {t.hostGroupDetail.closeAndCalcBtn}
                          </button>
                        </div>
                      )}

                      {c.status === "OPEN" && (
                        <button
                          onClick={() => handleCloseAndCalc(c.id)}
                          className="rounded-xl bg-orange-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-orange-500 cursor-pointer"
                        >
                          {t.hostGroupDetail.closeAndCalcBtn}
                        </button>
                      )}

                      {c.status === "PAYOUT_PENDING" && (
                        <button
                          onClick={() => handleConfirmPayout(c.id)}
                          className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
                        >
                          {t.hostGroupDetail.confirmPayoutBtn}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Financial outcome */}
                  <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">{t.hostGroupDetail.winningBidLabel}</span>
                      <p className="font-bold text-sm text-zinc-900 dark:text-zinc-50 mt-1">
                        {formatMoney(c.winningBid, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">{t.hostGroupDetail.grossPotLabel}</span>
                      <p className="font-bold text-sm text-zinc-900 dark:text-zinc-50 mt-1">
                        {formatMoney(c.grossPot, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/40">
                      <span className="text-zinc-500">{t.hostGroupDetail.hostFeeOutcomeLabel}</span>
                      <p className="font-bold text-sm text-emerald-600 dark:text-emerald-400 mt-1">
                        {formatMoney(c.hostFee, c.currency)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-emerald-50 p-3 dark:bg-emerald-950/30 border border-emerald-500/20">
                      <span className="text-emerald-800 dark:text-emerald-300 font-medium">
                        {t.hostGroupDetail.netPayoutLabel}
                      </span>
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
              {t.hostGroupDetail.sharesHeader(shares.length, group.shareCount)}
            </h3>
            {group.status === "DRAFT" || group.status === "RECRUITING" ? (
              <button
                onClick={() => setShowAssignModal(true)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
              >
                <PlusCircle className="h-4 w-4" />
                {t.hostGroupDetail.assignMoreSharesBtn}
              </button>
            ) : null}
          </div>

          <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                <tr>
                  <th className="py-3.5 pl-6 pr-3">{t.hostGroupDetail.colShareNo}</th>
                  <th className="px-3 py-3.5">{t.hostGroupDetail.colMemberName}</th>
                  <th className="px-3 py-3.5">{t.hostGroupDetail.colPhone}</th>
                  <th className="px-3 py-3.5">{t.hostGroupDetail.colShareStatus}</th>
                  <th className="py-3.5 pl-3 pr-6 text-right">{t.hostGroupDetail.colWonCycle}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                {shares.map((s) => (
                  <tr key={s.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                    <td className="py-3.5 pl-6 pr-3 font-mono font-bold text-xs text-zinc-500">
                      {t.hostGroupDetail.shareNoBadge(s.shareNo)}
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
                      {s.wonCycleId ? t.hostGroupDetail.cycleNoBadge(s.wonCycleId) : "—"}
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
              {t.hostGroupDetail.paymentsHeader}
            </h3>
            <div className="flex items-center gap-3">
              <span className="text-xs text-zinc-500">
                {t.hostGroupDetail.totalPending(debts.length)}
              </span>
              {debtByMember.length > 0 && (
                <button
                  onClick={() => {
                    assignQuickPayMember(debtByMember[0].memberProfileId);
                    setQuickPayMethod("CASH");
                    setQuickPayNote("");
                    setQuickPayReceipt(null);
                    setShowQuickPayModal(true);
                  }}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-600 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 cursor-pointer dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70"
                >
                  <Zap className="h-3.5 w-3.5" />
                  {t.hostGroupDetail.quickPayBtn}
                </button>
              )}
              {group.lateFeeType !== "NONE" && (
                <button
                  onClick={handleAssessLateFees}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-800 hover:bg-amber-100 cursor-pointer dark:border-amber-700/60 dark:bg-amber-950/40 dark:text-amber-300 dark:hover:bg-amber-950/70"
                >
                  <Clock className="h-3.5 w-3.5" />
                  {t.hostGroupDetail.assessLateFeesBtn}
                </button>
              )}
            </div>
          </div>

          {debts.length === 0 ? (
            <EmptyState
              icon={Coins}
              title={t.hostGroupDetail.emptyDebtsTitle}
              description={t.hostGroupDetail.emptyDebtsDesc}
            />
          ) : (
            <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                  <tr>
                    <th className="py-3.5 pl-6 pr-3">{t.hostGroupDetail.colCycle}</th>
                    <th className="px-3 py-3.5">{t.hostGroupDetail.colShare}</th>
                    <th className="px-3 py-3.5">{t.hostGroupDetail.colAmountDue}</th>
                    <th className="px-3 py-3.5">{t.hostGroupDetail.colPaid}</th>
                    <th className="px-3 py-3.5">{t.hostGroupDetail.colRemaining}</th>
                    <th className="px-3 py-3.5">{t.hostGroupDetail.colDueDate}</th>
                    <th className="py-3.5 pl-3 pr-6 text-right">{t.hostGroupDetail.colActions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                  {debts.map((d) => (
                    <tr key={d.ledgerEntryId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                      <td className="py-3.5 pl-6 pr-3 font-semibold">
                        {t.hostGroupDetail.cycleNoBadge(d.cycleNo)}
                      </td>
                      <td className="px-3 py-3.5 font-mono text-xs">
                        {t.hostGroupDetail.shareNoBadge(d.shareId)}
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
                        {formatDateTime(d.dueAt, language)}
                      </td>
                      <td className="py-3.5 pl-3 pr-6 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {d.khqr && (
                            <button
                              onClick={() => openQr(d)}
                              className="inline-flex items-center gap-1 rounded-lg border border-zinc-300 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100 cursor-pointer dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
                            >
                              <QrCode className="h-3.5 w-3.5" />
                              {t.hostGroupDetail.viewQrBtn}
                            </button>
                          )}
                          <button
                            onClick={() => {
                              setPaymentDebt(d);
                              setShowPaymentModal(true);
                            }}
                            className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
                          >
                            <CreditCard className="h-3.5 w-3.5" />
                            {t.hostGroupDetail.collectMoneyBtn}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Payment History & Receipts */}
          <div className="mt-8">
            <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
              {t.hostGroupDetail.paymentHistoryTitle}
            </h3>

            {payments.length === 0 ? (
              <p className="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
                {t.hostGroupDetail.paymentHistoryEmpty}
              </p>
            ) : (
              <div className="mt-4 space-y-3">
                {payments.map((p) => (
                  <div
                    key={p.id}
                    className="rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60"
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm sm:grid-cols-3">
                        <div>
                          <span className="text-xs text-zinc-400">{t.hostGroupDetail.historyColDate}</span>
                          <p className="font-medium text-zinc-900 dark:text-zinc-50">
                            {formatDateTime(p.paidAt, language)}
                          </p>
                        </div>
                        <div>
                          <span className="text-xs text-zinc-400">{t.hostGroupDetail.historyColAmount}</span>
                          <p className="font-bold text-emerald-600">
                            {formatMoney(p.amountMinor, p.currency)}
                          </p>
                        </div>
                        <div>
                          <span className="text-xs text-zinc-400">{t.hostGroupDetail.historyColMethod}</span>
                          <p className="font-medium text-zinc-900 dark:text-zinc-50">
                            {paymentMethodLabel(p.method)}
                          </p>
                        </div>
                        <div className="col-span-2 sm:col-span-1">
                          <span className="text-xs text-zinc-400">{t.hostGroupDetail.historyColObligations}</span>
                          <p className="font-medium text-zinc-900 dark:text-zinc-50">{p.allocations.length}</p>
                        </div>
                        <div className="col-span-2 sm:col-span-2">
                          <span className="text-xs text-zinc-400">{t.hostGroupDetail.historyColNote}</span>
                          <p className="font-medium text-zinc-900 dark:text-zinc-50">{p.note || "—"}</p>
                        </div>
                      </div>

                      <div className="flex shrink-0 items-center gap-2">
                        <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70">
                          {uploadingPaymentId === p.id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Upload className="h-3.5 w-3.5" />
                          )}
                          {uploadingPaymentId === p.id
                            ? t.hostGroupDetail.receiptsUploading
                            : t.hostGroupDetail.receiptsUpload}
                          <input
                            type="file"
                            className="hidden"
                            disabled={uploadingPaymentId !== null}
                            onChange={(e) => {
                              handleReceiptUpload(p.id, e.target.files?.[0] ?? null);
                              e.target.value = "";
                            }}
                          />
                        </label>
                      </div>
                    </div>

                    {p.attachments.length > 0 && (
                      <div className="mt-4 border-t border-zinc-100 pt-3 dark:border-zinc-800">
                        <div className="flex flex-wrap gap-2">
                          {p.attachments.map((att) => (
                            <div
                              key={att.id}
                              className="flex items-center gap-2 rounded-xl border border-zinc-200 bg-zinc-50/60 px-3 py-2 dark:border-zinc-700/60 dark:bg-zinc-800/40"
                            >
                              <FileText className="h-4 w-4 shrink-0 text-zinc-400" />
                              <span className="max-w-48 truncate text-xs font-medium text-zinc-700 dark:text-zinc-300">
                                {att.originalName}
                              </span>
                              <span className="text-[10px] text-zinc-400">{formatBytes(att.sizeBytes)}</span>
                              <button
                                onClick={() => handleReceiptDownload(att.id, att.originalName)}
                                className="rounded-md p-1 text-emerald-600 hover:bg-emerald-100 dark:hover:bg-emerald-950/40"
                                title={t.hostGroupDetail.receiptsView}
                              >
                                <Eye className="h-3.5 w-3.5" />
                              </button>
                              <button
                                onClick={() => handleReceiptDelete(att.id)}
                                className="rounded-md p-1 text-rose-500 hover:bg-rose-100 dark:hover:bg-rose-950/40"
                                title={t.hostGroupDetail.receiptsDelete}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modal: Assign Shares */}
      {showAssignModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {t.hostGroupDetail.assignModalTitle}
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
                  {t.hostGroupDetail.selectMemberLabel}
                </label>
                <select
                  required
                  value={selectedMemberId || ""}
                  onChange={(e) => setSelectedMemberId(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="">{t.hostGroupDetail.selectMemberPlaceholder}</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.fullName} ({formatPhone(m.phone)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.shareCountToAssignLabel}
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
                  {t.hostGroupDetail.cancelBtn}
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  {t.hostGroupDetail.assignConfirmBtn}
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
                {t.hostGroupDetail.bidModalTitle}
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
                  {t.hostGroupDetail.selectAliveShareLabel}
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
                        {t.hostGroupDetail.bidShareOption(s.shareNo, s.memberName)}
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.bidAmountLabel(group.currency)}
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
                  {t.hostGroupDetail.cancelBtn}
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  {t.hostGroupDetail.saveBidBtn}
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
                {t.hostGroupDetail.paymentModalTitle}
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
                  <span className="text-zinc-500">{t.hostGroupDetail.paymentCycleLabel}</span>
                  <span className="font-bold">
                    {t.hostGroupDetail.cycleNoBadge(paymentDebt.cycleNo)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-500">{t.hostGroupDetail.paymentShareLabel}</span>
                  <span className="font-bold">
                    {t.hostGroupDetail.shareNoBadge(paymentDebt.shareId)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-500">{t.hostGroupDetail.paymentAmountLabel}</span>
                  <span className="font-bold text-emerald-600">
                    {formatMoney(paymentDebt.remainingMinor, paymentDebt.currency)}
                  </span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.paymentMethodLabel}
                </label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="CASH">{t.hostGroupDetail.methodCash}</option>
                  <option value="BANK_TRANSFER">{t.hostGroupDetail.methodBank}</option>
                  <option value="OTHER">{t.hostGroupDetail.methodOther}</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowPaymentModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100"
                >
                  {t.hostGroupDetail.cancelBtn}
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                >
                  {t.hostGroupDetail.confirmPaymentBtn}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Quick-pay */}
      {showQuickPayModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {t.hostGroupDetail.quickPayModalTitle}
              </h3>
              <button
                onClick={() => setShowQuickPayModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleQuickPay} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.quickPayMemberLabel}
                </label>
                <select
                  value={quickPayMemberId ?? ""}
                  onChange={(e) => assignQuickPayMember(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  {debtByMember.map((m) => (
                    <option key={m.memberProfileId} value={m.memberProfileId}>
                      {shares.find((s) => s.memberProfileId === m.memberProfileId)?.memberName ??
                        `#${m.memberProfileId}`}
                      {" — "}
                      {t.hostGroupDetail.quickPayMemberTotal(
                        m.obligationCount,
                        formatMoney(m.totalRemaining, m.currency)
                      )}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.quickPayAmountLabel}
                </label>
                <input
                  type="number"
                  min={1}
                  value={quickPayAmountMinor}
                  onChange={(e) => setQuickPayAmountMinor(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.paymentMethodLabel}
                </label>
                <select
                  value={quickPayMethod}
                  onChange={(e) => setQuickPayMethod(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="CASH">{t.hostGroupDetail.methodCash}</option>
                  <option value="BANK_TRANSFER">{t.hostGroupDetail.methodBank}</option>
                  <option value="OTHER">{t.hostGroupDetail.methodOther}</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.quickPayNoteLabel}
                </label>
                <input
                  type="text"
                  value={quickPayNote}
                  onChange={(e) => setQuickPayNote(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.hostGroupDetail.quickPayReceiptLabel}
                </label>
                <input
                  type="file"
                  accept="image/*,application/pdf"
                  onChange={(e) => setQuickPayReceipt(e.target.files?.[0] ?? null)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2 px-3.5 text-sm text-zinc-700 file:mr-3 file:rounded-lg file:border-0 file:bg-emerald-600 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-white hover:file:bg-emerald-500 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-300"
                />
              </div>

              <p className="rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                {t.hostGroupDetail.quickPayAllocatesHint}
              </p>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowQuickPayModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
                >
                  {t.hostGroupDetail.cancelBtn}
                </button>
                <button
                  type="submit"
                  disabled={quickPaying || !quickPayMemberId || quickPayAmountMinor <= 0}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {quickPaying && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  {t.hostGroupDetail.quickPaySubmitBtn}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: KHQR */}
      {qrDebt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {t.hostGroupDetail.qrModalTitle}
              </h3>
              <button
                onClick={closeQr}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-5 flex flex-col items-center space-y-4">
              {qrLoading ? (
                <Loader2 className="h-14 w-14 animate-spin text-zinc-400" />
              ) : qrError ? (
                <p className="text-sm font-medium text-red-600 dark:text-red-400">{qrError}</p>
              ) : qrImageUrl ? (
                <Image
                  src={qrImageUrl}
                  alt={t.hostGroupDetail.qrModalTitle}
                  width={256}
                  height={256}
                  unoptimized
                  className="h-64 w-64 rounded-xl border border-zinc-200 bg-white p-2 dark:border-zinc-800"
                />
              ) : null}
              <p className="text-center text-xs text-zinc-500 dark:text-zinc-400">
                {t.hostGroupDetail.qrModalDesc}
              </p>
              <div className="w-full space-y-1.5 text-sm">
                <div className="flex justify-between">
                  <span className="text-zinc-500">{t.hostGroupDetail.qrModalCycle}</span>
                  <span className="font-bold">{t.hostGroupDetail.cycleNoBadge(qrDebt.cycleNo)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-500">{t.hostGroupDetail.qrModalAmount}</span>
                  <span className="font-bold text-emerald-600">
                    {formatMoney(qrDebt.remainingMinor, qrDebt.currency)}
                  </span>
                </div>
              </div>
              <button
                onClick={closeQr}
                className="w-full rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 cursor-pointer"
              >
                {t.hostGroupDetail.closeBtn}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
