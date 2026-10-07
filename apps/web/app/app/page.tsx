"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { GroupStatusBadge, GroupTypeBadge, CycleStatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Wallet, ArrowRight, Loader2, AlertCircle } from "lucide-react";

type MyGroup = Awaited<ReturnType<typeof api.getMyGroups>>[number];

export default function MemberPortalPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const [groups, setGroups] = useState<MyGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const auth = getStoredAuth();
    if (!auth) {
      router.push("/login");
      return;
    }

    let ignore = false;
    api.getMyGroups()
      .then((data) => {
        if (!ignore) {
          setGroups(data);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : t.memberPortal.defaultError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [router, t.memberPortal.defaultError]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Header */}
      <div className="rounded-3xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-300">
            <Wallet className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
              {t.memberPortal.title}
            </h1>
            <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
              {t.memberPortal.subtitle}
            </p>
          </div>
        </div>
      </div>

      {error && (
        <div className="rounded-2xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400 flex items-center gap-2">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Group List */}
      <div>
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            {t.memberPortal.joinedGroupsTitle}
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {groups.length}
            </span>
          </h2>
        </div>

        {groups.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title={t.memberPortal.emptyTitle}
            description={t.memberPortal.emptyDesc}
          />
        ) : (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {groups.map((g) => (
              <div
                key={g.id}
                className="group flex flex-col justify-between rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm transition-all hover:border-emerald-500/50 hover:shadow-md dark:border-zinc-800 dark:bg-zinc-900/60"
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <span className="font-mono text-xs font-medium text-zinc-400">
                        {g.code}
                      </span>
                      <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors mt-0.5">
                        {g.name}
                      </h3>
                    </div>
                    <GroupStatusBadge status={g.status} />
                  </div>

                  <div className="mt-3 flex items-center gap-2">
                    <GroupTypeBadge type={g.type} />
                    <span className="text-xs text-zinc-500 dark:text-zinc-400">
                      {t.memberPortal.totalSharesAndCycles(g.shareCount, g.cycleCount)}
                    </span>
                  </div>

                  <div className="mt-6 space-y-2 rounded-xl bg-zinc-50 p-3.5 text-xs dark:bg-zinc-800/40">
                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500">{t.memberPortal.ownedSharesLabel}</span>
                      <span className="font-bold text-emerald-600 dark:text-emerald-400">
                        {t.memberPortal.ownedSharesValue(g.myShareCount)}
                      </span>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-zinc-500">{t.memberPortal.cycleProgressLabel}</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                          {g.currentCycleNo
                            ? t.memberPortal.cycleOf(g.currentCycleNo, g.cycleCount)
                            : t.memberPortal.notOpenedYet}
                        </span>
                        {g.currentCycleStatus && (
                          <CycleStatusBadge status={g.currentCycleStatus} />
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-6 pt-4 border-t border-zinc-100 dark:border-zinc-800 flex items-center justify-between">
                  <span className="text-xs text-zinc-500">{g.currency}</span>
                  <Link
                    href={`/app/groups/${g.id}`}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-600 hover:text-emerald-500 group-hover:translate-x-0.5 transition-all"
                  >
                    {t.memberPortal.viewDetailsAndStatement}
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
