"use client";

import React, { useEffect, useState, use } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { formatMoney, formatDateTime } from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
import { GroupStatusBadge, CycleStatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import {
  ArrowLeft,
  Lock,
  Coins,
  FileText,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Send,
  Download,
} from "lucide-react";

type MyGroup = Awaited<ReturnType<typeof api.getMyGroups>>[number];
type PublicCycle = Awaited<ReturnType<typeof api.getMyGroupCycles>>[number];
type MemberStatement = Awaited<ReturnType<typeof api.getMyGroupStatement>>;

export default function MemberGroupDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { language, t } = useLanguage();

  const [group, setGroup] = useState<MyGroup | null>(null);
  const [cycles, setCycles] = useState<PublicCycle[]>([]);
  const [statement, setStatement] = useState<MemberStatement | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"cycles" | "statement">("cycles");

  // Bidding form state
  const [bidShareId, setBidShareId] = useState<number | null>(null);
  const [bidAmountMinor, setBidAmountMinor] = useState<number>(100_000);
  const [bidLoading, setBidLoading] = useState(false);
  const [bidError, setBidError] = useState<string | null>(null);
  const [bidSuccess, setBidSuccess] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const handleStatementExport = async (format: "csv" | "xlsx") => {
    setExportError(null);
    setExporting(true);
    try {
      await api.exportStatement(id, format);
    } catch (err: unknown) {
      setExportError(
        err instanceof Error ? err.message : t.memberGroupDetail.exportError
      );
    } finally {
      setExporting(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.getMyGroups(),
      api.getMyGroupCycles(id),
      api.getMyGroupStatement(id).catch(() => null),
    ])
      .then(([allGroups, cy, st]) => {
        if (!ignore) {
          const current = allGroups.find((g) => String(g.id) === String(id)) || null;
          setGroup(current);
          setCycles(cy);
          setStatement(st);

          if (current?.myShares && current.myShares.length > 0) {
            const aliveShare = current.myShares.find((s) => s.status === "ALIVE");
            if (aliveShare) setBidShareId(aliveShare.id);
          }
          setLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id]);

  const activeBiddingCycle = cycles.find((c) => c.status === "BIDDING");

  const handlePlaceBid = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeBiddingCycle || !bidShareId) return;

    setBidError(null);
    setBidSuccess(null);
    setBidLoading(true);

    try {
      // Find actual cycle ID from group cycles
      const allGroupCycles = await api.getGroupCycles(id);
      const activeObj = allGroupCycles.find((c) => c.cycleNo === activeBiddingCycle.cycleNo);
      if (!activeObj) throw new Error(t.memberGroupDetail.errCycleNotFound);

      await api.submitMyBid(activeObj.id, bidShareId, bidAmountMinor);
      setBidSuccess(
        t.memberGroupDetail.bidSuccessMsg(formatMoney(bidAmountMinor, group?.currency))
      );
    } catch (err: unknown) {
      setBidError(err instanceof Error ? err.message : t.memberGroupDetail.bidErrorMsg);
    } finally {
      setBidLoading(false);
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
          icon={Coins}
          title={t.memberGroupDetail.notFoundTitle}
          description={t.memberGroupDetail.notFoundDesc}
          actionText={t.memberGroupDetail.backToGroups}
          actionHref="/app"
        />
      </div>
    );
  }

  const shareDetailsString = group.myShares
    .map((s) => t.memberGroupDetail.shareItem(s.shareNo, s.status))
    .join(", ");

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Top Banner */}
      <div className="rounded-3xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <Link
              href="/app"
              className="inline-flex items-center gap-1 text-xs font-semibold text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 mb-2"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t.memberGroupDetail.backToGroups}
            </Link>
            <div className="flex items-center gap-3">
              <span className="font-mono text-xs font-semibold text-zinc-400">
                {group.code}
              </span>
              <GroupStatusBadge status={group.status} />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 mt-1">
              {group.name}
            </h1>
            <p className="mt-1 text-xs text-zinc-500">
              {t.memberGroupDetail.ownedSharesSummary(group.myShareCount, shareDetailsString)}
            </p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-zinc-200 dark:border-zinc-800 text-sm font-semibold">
        <button
          onClick={() => setActiveTab("cycles")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "cycles"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400"
          }`}
        >
          {t.memberGroupDetail.tabCycles(cycles.length, group.cycleCount)}
        </button>
        <button
          onClick={() => setActiveTab("statement")}
          className={`pb-3 px-4 border-b-2 transition-colors cursor-pointer ${
            activeTab === "statement"
              ? "border-emerald-600 text-emerald-600 dark:text-emerald-400"
              : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400"
          }`}
        >
          {t.memberGroupDetail.tabStatement}
        </button>
      </div>

      {/* Tab 1: Cycles & Sealed Bidding */}
      {activeTab === "cycles" && (
        <div className="space-y-6">
          {/* Active Bidding Box */}
          {activeBiddingCycle && (
            <div className="rounded-2xl border border-amber-500/30 bg-amber-50/50 p-6 dark:bg-amber-950/20">
              <div className="flex items-center gap-2 text-amber-900 dark:text-amber-200 text-sm font-bold">
                <Lock className="h-4 w-4 text-amber-600" />
                {t.memberGroupDetail.activeBiddingTitle(activeBiddingCycle.cycleNo)}
              </div>
              <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
                {t.memberGroupDetail.activeBiddingDesc}
              </p>

              {bidError && (
                <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{bidError}</span>
                </div>
              )}
              {bidSuccess && (
                <div className="mt-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800">
                  <CheckCircle2 className="h-4 w-4 shrink-0" />
                  <span>{bidSuccess}</span>
                </div>
              )}

              <form onSubmit={handlePlaceBid} className="mt-4 flex flex-col sm:flex-row items-end gap-4">
                <div className="w-full sm:w-auto flex-1">
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    {t.memberGroupDetail.selectAliveShareLabel}
                  </label>
                  <select
                    value={bidShareId || ""}
                    onChange={(e) => setBidShareId(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-white py-2.5 px-3.5 text-sm text-zinc-900 outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                  >
                    {group.myShares
                      .filter((s) => s.status === "ALIVE")
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {t.memberGroupDetail.aliveShareOption(s.shareNo)}
                        </option>
                      ))}
                  </select>
                </div>

                <div className="w-full sm:w-auto flex-1">
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    {t.memberGroupDetail.bidAmountLabel(group.currency)}
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={1000}
                    required
                    value={bidAmountMinor}
                    onChange={(e) => setBidAmountMinor(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-white py-2.5 px-3.5 text-sm text-zinc-900 outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                  />
                </div>

                <button
                  type="submit"
                  disabled={bidLoading || !bidShareId}
                  className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-amber-600 px-5 py-2.5 text-sm font-semibold text-white shadow hover:bg-amber-500 disabled:opacity-50 cursor-pointer"
                >
                  <Send className="h-4 w-4" />
                  {bidLoading ? t.memberGroupDetail.submittingBid : t.memberGroupDetail.submitBidBtn}
                </button>
              </form>
            </div>
          )}

          {/* Public Cycles Table */}
          <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                <tr>
                  <th className="py-3.5 pl-6 pr-3">{t.memberGroupDetail.colCycle}</th>
                  <th className="px-3 py-3.5">{t.memberGroupDetail.colStatus}</th>
                  <th className="px-3 py-3.5">{t.memberGroupDetail.colWinner}</th>
                  <th className="px-3 py-3.5">{t.memberGroupDetail.colWinningBid}</th>
                  <th className="px-3 py-3.5">{t.memberGroupDetail.colGrossPot}</th>
                  <th className="px-3 py-3.5">{t.memberGroupDetail.colNetPayout}</th>
                  <th className="py-3.5 pl-3 pr-6 text-right">{t.memberGroupDetail.colDueDate}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300 text-xs">
                {cycles.map((c) => (
                  <tr key={c.cycleNo} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                    <td className="py-3.5 pl-6 pr-3 font-bold">
                      {t.memberGroupDetail.cycleNo(c.cycleNo)}
                    </td>
                    <td className="px-3 py-3.5">
                      <CycleStatusBadge status={c.status} />
                    </td>
                    <td className="px-3 py-3.5 font-semibold">
                      {c.winner
                        ? t.memberGroupDetail.winnerItem(c.winner.memberName, c.winner.shareNo)
                        : "—"}
                    </td>
                    <td className="px-3 py-3.5">
                      {c.winningBid
                        ? formatMoney(c.winningBid.amountMinor, c.winningBid.currency)
                        : "—"}
                    </td>
                    <td className="px-3 py-3.5">
                      {c.grossPot
                        ? formatMoney(c.grossPot.amountMinor, c.grossPot.currency)
                        : "—"}
                    </td>
                    <td className="px-3 py-3.5 font-bold text-emerald-600 dark:text-emerald-400">
                      {c.netPayout
                        ? formatMoney(c.netPayout.amountMinor, c.netPayout.currency)
                        : "—"}
                    </td>
                    <td className="py-3.5 pl-3 pr-6 text-right text-zinc-500">
                      {formatDateTime(c.dueAt, language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Personal Statement */}
      {activeTab === "statement" && (
<div className="space-y-6">
          {statement ? (
            <>
              {/* Export actions */}
              <div className="flex items-center justify-between">
                <h3 className="font-bold text-base text-zinc-900 dark:text-zinc-50">
                  {t.memberGroupDetail.statementExportHeader}
                </h3>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => handleStatementExport("csv")}
                      disabled={exporting}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 cursor-pointer disabled:opacity-50 dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70"
                    >
                      <Download className="h-3.5 w-3.5" />
                      {t.memberGroupDetail.exportStatementCsv}
                    </button>
                    <button
                      onClick={() => handleStatementExport("xlsx")}
                      disabled={exporting}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 cursor-pointer disabled:opacity-50 dark:border-emerald-700/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70"
                    >
                      <Download className="h-3.5 w-3.5" />
                      {t.memberGroupDetail.exportStatementXlsx}
                    </button>
                  </div>
                  {exportError && (
                    <span className="text-xs text-red-600 dark:text-red-400">{exportError}</span>
                  )}
                </div>
              </div>
              {statement.totals && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
                  <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
                    <span className="text-xs font-medium text-zinc-500">
                      {t.memberGroupDetail.contributedLabel}
                    </span>
                    <p className="mt-2 text-2xl font-bold text-zinc-900 dark:text-zinc-50">
                      {formatMoney(
                        statement.totals.contributed?.amountMinor ?? 0,
                        statement.totals.contributed?.currency || statement.currency
                      )}
                    </p>
                  </div>
                  <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
                    <span className="text-xs font-medium text-zinc-500">
                      {t.memberGroupDetail.receivedLabel}
                    </span>
                    <p className="mt-2 text-2xl font-bold text-emerald-600 dark:text-emerald-400">
                      {formatMoney(
                        statement.totals.received?.amountMinor ?? 0,
                        statement.totals.received?.currency || statement.currency
                      )}
                    </p>
                  </div>
                  <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
                    <span className="text-xs font-medium text-zinc-500">
                      {t.memberGroupDetail.feesLabel}
                    </span>
                    <p className="mt-2 text-2xl font-bold text-zinc-500">
                      {formatMoney(
                        statement.totals.feesPaid?.amountMinor ?? 0,
                        statement.totals.feesPaid?.currency || statement.currency
                      )}
                    </p>
                  </div>
                  <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
                    <span className="text-xs font-medium text-zinc-500">
                      {t.memberGroupDetail.netPositionLabel}
                    </span>
                    <p
                      className={`mt-2 text-2xl font-bold ${
                        (statement.totals.netPosition?.amountMinor ?? 0) >= 0
                          ? "text-emerald-600 dark:text-emerald-400"
                          : "text-amber-600 dark:text-amber-400"
                      }`}
                    >
                      {formatMoney(
                        statement.totals.netPosition?.amountMinor ?? 0,
                        statement.totals.netPosition?.currency || statement.currency
                      )}
                    </p>
                  </div>
                </div>
              )}

              {/* Per-share breakdown */}
              <div className="space-y-6">
                {(statement.shares || []).map((share) => (
                  <div
                    key={share.shareId}
                    className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60"
                  >
                    <div className="bg-zinc-50 px-6 py-3 border-b border-zinc-200 dark:bg-zinc-800/40 dark:border-zinc-800 flex items-center justify-between">
                      <span className="font-bold text-sm text-zinc-900 dark:text-zinc-100">
                        {t.memberGroupDetail.shareTitle(share.shareNo)}
                      </span>
                      <span className="text-xs text-zinc-500">
                        {t.memberGroupDetail.statusLabel}{" "}
                        <strong>{share.status || share.shareStatus || "-"}</strong>
                      </span>
                    </div>

                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-zinc-200 bg-zinc-50/50 text-zinc-500">
                        <tr>
                          <th className="py-2.5 pl-6 pr-3">{t.memberGroupDetail.colCycleNo}</th>
                          <th className="px-3 py-2.5">{t.memberGroupDetail.colDescription}</th>
                          <th className="px-3 py-2.5">{t.memberGroupDetail.colAmount}</th>
                          <th className="px-3 py-2.5">{t.memberGroupDetail.colStatus}</th>
                          <th className="py-2.5 pl-3 pr-6 text-right">
                            {t.memberGroupDetail.colRunningBalance}
                          </th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/40">
                        {(share.entries || []).map((e) => {
                          const balance = e.runningBalance || e.runningPosition;
                          return (
                            <tr key={e.entryId}>
                              <td className="py-3 pl-6 pr-3 font-semibold">
                                {t.memberGroupDetail.cycleNo(e.cycleNo)}
                              </td>
                              <td className="px-3 py-3">
                                {e.type === "CONTRIBUTION"
                                  ? t.memberGroupDetail.entryContribution
                                  : t.memberGroupDetail.entryPayout}
                              </td>
                              <td className="px-3 py-3 font-bold">
                                {e.amount
                                  ? formatMoney(e.amount.amountMinor, e.amount.currency)
                                  : "-"}
                              </td>
                              <td className="px-3 py-3">
                                <span className="font-medium text-emerald-600">{e.status}</span>
                              </td>
                              <td className="py-3 pl-3 pr-6 text-right font-bold font-mono">
                                {balance
                                  ? formatMoney(balance.amountMinor, balance.currency)
                                  : "-"}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <EmptyState
              icon={FileText}
              title={t.memberGroupDetail.emptyStatementTitle}
              description={t.memberGroupDetail.emptyStatementDesc}
            />
          )}
        </div>
      )}
    </div>
  );
}
