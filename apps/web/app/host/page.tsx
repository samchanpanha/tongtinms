"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { formatMoney, formatDateTime } from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
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
  const { language, t } = useLanguage();
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
          setError(err instanceof Error ? err.message : t.hostDashboard.defaultError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [router, refreshKey, t.hostDashboard.defaultError]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-zinc-500">
          <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
          <p className="text-sm">{t.hostDashboard.loading}</p>
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
              <h3 className="text-base font-bold">{t.hostDashboard.errorTitle}</h3>
              <p className="text-sm mt-1">{error}</p>
            </div>
          </div>
          <button
            onClick={() => {
              setLoading(true);
              setRefreshKey((k) => k + 1);
            }}
            className="mt-4 rounded-xl bg-rose-700 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-600 transition-colors cursor-pointer"
          >
            {t.hostDashboard.retry}
          </button>
        </div>
      </div>
    );
  }

  const groups = data?.groups || [];
  const currencies = data?.currencies || [];
  const totalUnpaid = groups.reduce((acc, g) => acc + g.unpaidCount, 0);
  const totalOverdue = groups.reduce((acc, g) => acc + g.overdueCount, 0);
  const runningCount = groups.filter((g) => g.status === "RUNNING").length;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-8 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.hostDashboard.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.hostDashboard.subtitle}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            href="/host/members"
            className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
          >
            {t.hostDashboard.memberListBtn}
          </Link>
          <Link
            href="/host/groups/new"
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 transition-colors"
          >
            <PlusCircle className="h-4 w-4" />
            {t.hostDashboard.createGroupBtn}
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {currencies.length > 0 ? (
          currencies.map((c) => (
            <StatCard
              key={c.currency}
              title={t.hostDashboard.profitTitle(c.currency)}
              value={formatMoney(c.amountMinor, c.currency, c.exponent)}
              subtitle={t.hostDashboard.profitSubtitle}
              icon={TrendingUp}
              badge={t.hostDashboard.profitBadge}
              badgeColor="emerald"
            />
          ))
        ) : (
          <StatCard
            title={t.hostDashboard.profitTitleDefault}
            value="0 đ"
            subtitle={t.hostDashboard.profitSubtitleEmpty}
            icon={TrendingUp}
            badge="VND"
            badgeColor="emerald"
          />
        )}

        <StatCard
          title={t.hostDashboard.totalGroupsTitle}
          value={groups.length}
          subtitle={t.hostDashboard.runningGroupsSubtitle(runningCount)}
          icon={Layers}
          badge={t.hostDashboard.groupsBadge(groups.length)}
          badgeColor="blue"
        />

        <StatCard
          title={t.hostDashboard.unpaidTitle}
          value={totalUnpaid}
          subtitle={t.hostDashboard.unpaidSubtitle}
          icon={Coins}
          badge={totalUnpaid > 0 ? t.hostDashboard.unpaidBadgeNeed : t.hostDashboard.unpaidBadgeDone}
          badgeColor={totalUnpaid > 0 ? "amber" : "emerald"}
        />

        <StatCard
          title={t.hostDashboard.overdueTitle}
          value={totalOverdue}
          subtitle={t.hostDashboard.overdueSubtitle}
          icon={AlertTriangle}
          badge={totalOverdue > 0 ? t.hostDashboard.overdueBadgeAlert : t.hostDashboard.overdueBadgeGood}
          badgeColor={totalOverdue > 0 ? "rose" : "emerald"}
        />
      </div>

      {/* Groups Section */}
      <div className="mt-12">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            {t.hostDashboard.groupsListTitle}
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {groups.length}
            </span>
          </h2>
          <Link
            href="/host/groups/new"
            className="text-xs font-semibold text-emerald-600 hover:text-emerald-500 inline-flex items-center gap-1"
          >
            {t.hostDashboard.openMoreGroup}
          </Link>
        </div>

        {groups.length === 0 ? (
          <EmptyState
            icon={Layers}
            title={t.hostDashboard.emptyTitle}
            description={t.hostDashboard.emptyDesc}
            actionText={t.hostDashboard.emptyAction}
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
                      {t.hostDashboard.sharesAndCycles(group.shareCount, group.cycleCount)}
                    </span>
                  </div>

                  <div className="mt-6 space-y-2.5 rounded-xl bg-zinc-50 p-3.5 text-xs dark:bg-zinc-800/40">
                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500 dark:text-zinc-400">{t.hostDashboard.cycleProgress}</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                          {group.currentCycleNo
                            ? t.hostDashboard.cycleOf(group.currentCycleNo, group.cycleCount)
                            : t.hostDashboard.notOpenedYet}
                        </span>
                        {group.currentCycleStatus && (
                          <CycleStatusBadge status={group.currentCycleStatus} />
                        )}
                      </div>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500 dark:text-zinc-400">{t.hostDashboard.nextDue}</span>
                      <span className="font-medium text-zinc-700 dark:text-zinc-300">
                        {formatDateTime(group.nextDueAt, language)}
                      </span>
                    </div>

                    <div className="flex justify-between items-center pt-1 border-t border-zinc-200/60 dark:border-zinc-700/60">
                      <span className="text-zinc-500 dark:text-zinc-400">{t.hostDashboard.unpaidOverdueLabel}</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-amber-600 dark:text-amber-400">
                          {t.hostDashboard.unpaidCount(group.unpaidCount)}
                        </span>
                        {group.overdueCount > 0 && (
                          <span className="font-semibold text-rose-600 dark:text-rose-400">
                            {t.hostDashboard.overdueCount(group.overdueCount)}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex justify-between items-center pt-1 border-t border-zinc-200/60 dark:border-zinc-700/60">
                      <span className="text-zinc-500 dark:text-zinc-400">{t.hostDashboard.accumulatedFee}</span>
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
                    {t.hostDashboard.viewLedger}
                  </Link>
                  <Link
                    href={`/host/groups/${group.id}`}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-600 hover:text-emerald-500 group-hover:translate-x-0.5 transition-all"
                  >
                    {t.hostDashboard.enterGroupRoom}
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
