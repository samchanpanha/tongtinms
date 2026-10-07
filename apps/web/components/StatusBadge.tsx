"use client";

import {
  getGroupStatusBadge,
  getCycleStatusBadge,
  getShareStatusBadge,
  getGroupTypeLabel,
} from "@/lib/format";
import { useLanguage } from "@/lib/i18n";

export function GroupStatusBadge({ status }: { status: string | null | undefined }) {
  const { language } = useLanguage();
  const { label, className } = getGroupStatusBadge(status, language);
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${className}`}>
      {label}
    </span>
  );
}

export function CycleStatusBadge({ status }: { status: string | null | undefined }) {
  const { language } = useLanguage();
  const { label, className } = getCycleStatusBadge(status, language);
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${className}`}>
      {label}
    </span>
  );
}

export function ShareStatusBadge({ status }: { status: string | null | undefined }) {
  const { language } = useLanguage();
  const { label, className } = getShareStatusBadge(status, language);
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${className}`}>
      {label}
    </span>
  );
}

export function GroupTypeBadge({ type }: { type: string | null | undefined }) {
  const { language } = useLanguage();
  const label = getGroupTypeLabel(type, language);
  const isBidding = type === "BIDDING";
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${
        isBidding
          ? "bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300"
          : "bg-blue-100 text-blue-800 dark:bg-blue-950/80 dark:text-blue-300"
      }`}
    >
      {label}
    </span>
  );
}
