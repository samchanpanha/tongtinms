"use client";

import React, { useEffect, useState, use } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { formatMoney, formatDateTime } from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
import { EmptyState } from "@/components/EmptyState";
import { FileSpreadsheet, ArrowLeft, Loader2, ArrowUpRight, ArrowDownLeft, Shield } from "lucide-react";

type LedgerData = Awaited<ReturnType<typeof api.getGroupLedger>>;

export default function GroupLedgerPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { language, t } = useLanguage();
  const [data, setData] = useState<LedgerData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    api.getGroupLedger(id)
      .then((res) => {
        if (!ignore) {
          setData(res);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : t.groupLedger.defaultError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id, t.groupLedger.defaultError]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-12">
        <EmptyState
          icon={FileSpreadsheet}
          title={t.groupLedger.errorTitle}
          description={error || t.groupLedger.emptyDesc}
          actionText={t.groupLedger.backToGroup}
          actionHref={`/host/groups/${id}`}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <div className="flex items-center gap-2">
            <Link
              href={`/host/groups/${id}`}
              className="inline-flex items-center gap-1 text-xs font-semibold text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t.groupLedger.backToRoom}
            </Link>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 mt-1">
            {t.groupLedger.title(data.groupName)}
          </h1>
          <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
            {t.groupLedger.subtitle(data.currency)}
          </p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
          <div className="flex items-center justify-between text-xs font-medium text-zinc-500">
            <span>{t.groupLedger.totalInTitle}</span>
            <div className="rounded-lg bg-emerald-50 p-2 text-emerald-600 dark:bg-emerald-950/60">
              <ArrowDownLeft className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-3 text-2xl font-bold text-emerald-600 dark:text-emerald-400">
            {formatMoney(data.totalIn.amountMinor, data.totalIn.currency)}
          </p>
        </div>

        <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
          <div className="flex items-center justify-between text-xs font-medium text-zinc-500">
            <span>{t.groupLedger.totalOutTitle}</span>
            <div className="rounded-lg bg-blue-50 p-2 text-blue-600 dark:bg-blue-950/60">
              <ArrowUpRight className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-3 text-2xl font-bold text-blue-600 dark:text-blue-400">
            {formatMoney(data.totalOut.amountMinor, data.totalOut.currency)}
          </p>
        </div>

        <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
          <div className="flex items-center justify-between text-xs font-medium text-zinc-500">
            <span>{t.groupLedger.balanceTitle}</span>
            <div className="rounded-lg bg-purple-50 p-2 text-purple-600 dark:bg-purple-950/60">
              <Shield className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-3 text-2xl font-bold text-purple-600 dark:text-purple-400">
            {data.totalIn.amountMinor === data.totalOut.amountMinor
              ? t.groupLedger.balanced100
              : t.groupLedger.processing}
          </p>
        </div>
      </div>

      {/* Main Table */}
      <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
            <tr>
              <th className="py-3.5 pl-6 pr-3">{t.groupLedger.colEntryId}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colCycle}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colType}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colDirection}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colRecipient}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colAmount}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colPaid}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colRemaining}</th>
              <th className="px-3 py-3.5">{t.groupLedger.colStatus}</th>
              <th className="py-3.5 pl-3 pr-6 text-right">{t.groupLedger.colDueDate}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300 text-xs">
            {data.entries.map((entry) => (
              <tr key={entry.entryId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                <td className="py-3.5 pl-6 pr-3 font-mono text-zinc-400">
                  #{entry.entryId}
                </td>
                <td className="px-3 py-3.5 font-bold">
                  {t.groupLedger.cycleNo(entry.cycleNo)}
                </td>
                <td className="px-3 py-3.5 font-medium">
                  {entry.type === "CONTRIBUTION" && (
                    <span className="text-zinc-800 dark:text-zinc-200">
                      {t.groupLedger.typeContribution}
                    </span>
                  )}
                  {entry.type === "PAYOUT" && (
                    <span className="font-bold text-emerald-600">
                      {t.groupLedger.typePayout}
                    </span>
                  )}
                  {entry.type === "HOST_FEE" && (
                    <span className="text-purple-600 font-semibold">
                      {t.groupLedger.typeHostFee}
                    </span>
                  )}
                </td>
                <td className="px-3 py-3.5 font-mono font-bold">
                  {entry.direction === "IN" ? (
                    <span className="text-emerald-600">{t.groupLedger.dirIn}</span>
                  ) : (
                    <span className="text-blue-600">{t.groupLedger.dirOut}</span>
                  )}
                </td>
                <td className="px-3 py-3.5 font-semibold">
                  {entry.memberName
                    ? t.groupLedger.recipientMember(entry.memberName, entry.shareNo)
                    : t.groupLedger.recipientHost}
                </td>
                <td className="px-3 py-3.5 font-bold">
                  {formatMoney(entry.amount.amountMinor, entry.amount.currency)}
                </td>
                <td className="px-3 py-3.5 text-emerald-600">
                  {formatMoney(entry.allocated.amountMinor, entry.allocated.currency)}
                </td>
                <td className="px-3 py-3.5 font-semibold text-amber-600">
                  {formatMoney(entry.remaining.amountMinor, entry.remaining.currency)}
                </td>
                <td className="px-3 py-3.5">
                  <span
                    className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold ${
                      entry.status === "PAID"
                        ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                        : entry.status === "PARTIAL"
                        ? "bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300"
                        : "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400"
                    }`}
                  >
                    {entry.status}
                  </span>
                </td>
                <td className="py-3.5 pl-3 pr-6 text-right text-zinc-500">
                  {formatDateTime(entry.dueAt, language)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
