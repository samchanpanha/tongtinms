import {
  getStoredLanguage,
  getTranslations,
  Language,
  LANGUAGE_OPTIONS,
} from "@/lib/i18n";

export function formatMoney(
  amountMinor: number | bigint | string | null | undefined,
  currency: string = "VND",
  exponent: number = 0
): string {
  if (amountMinor === null || amountMinor === undefined) return "—";
  const num = typeof amountMinor === "bigint" ? Number(amountMinor) : Number(amountMinor);
  if (isNaN(num)) return "—";

  const exp = currency === "USD" || currency === "THB" || currency === "SGD" ? 2 : exponent;
  const major = exp > 0 ? num / Math.pow(10, exp) : num;

  if (currency === "VND") {
    const formatted = Math.round(major).toLocaleString("vi-VN");
    return `${formatted} đ`;
  } else if (currency === "USD") {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(major);
  } else if (currency === "KHR") {
    return `${Math.round(major).toLocaleString("vi-VN")} ៛`;
  } else if (currency === "THB") {
    return `${major.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ฿`;
  }

  return `${major.toLocaleString("en-US")} ${currency}`;
}

function resolveDateLocale(lang?: Language): string {
  const activeLang = lang || getStoredLanguage();
  const opt = LANGUAGE_OPTIONS.find((o) => o.code === activeLang);
  return opt ? opt.dateLocale : "km-KH";
}

export function formatDate(isoString: string | null | undefined, lang?: Language): string {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    return d.toLocaleDateString(resolveDateLocale(lang), {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  } catch {
    return isoString;
  }
}

export function formatDateTime(isoString: string | null | undefined, lang?: Language): string {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    const locale = resolveDateLocale(lang);
    return `${d.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })} • ${d.toLocaleDateString(locale, {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    })}`;
  } catch {
    return isoString;
  }
}

export function formatPhone(phone: string | null | undefined): string {
  if (!phone) return "—";
  const p = phone.startsWith("+84") ? "0" + phone.slice(3) : phone;
  if (p.length === 10) {
    return `${p.slice(0, 4)} ${p.slice(4, 7)} ${p.slice(7)}`;
  }
  return p;
}

export function formatBytes(bytes: number): string {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function getGroupTypeLabel(type: string | null | undefined, lang?: Language): string {
  const t = getTranslations(lang || getStoredLanguage());
  switch (type) {
    case "BIDDING":
      return t.status.groupType.BIDDING;
    case "FIXED_EQUAL":
      return t.status.groupType.FIXED_EQUAL;
    default:
      return type || "—";
  }
}

export function getCycleUnitLabel(unit: string | null | undefined, lang?: Language): string {
  const t = getTranslations(lang || getStoredLanguage());
  switch (unit) {
    case "DAY":
      return t.status.cycleUnit.DAY;
    case "WEEK":
      return t.status.cycleUnit.WEEK;
    case "MONTH":
      return t.status.cycleUnit.MONTH;
    default:
      return unit || t.status.cycleUnit.DEFAULT;
  }
}

export function getGroupStatusBadge(
  status: string | null | undefined,
  lang?: Language
): { label: string; className: string } {
  const t = getTranslations(lang || getStoredLanguage());
  switch (status) {
    case "DRAFT":
      return {
        label: t.status.groupStatus.DRAFT,
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
    case "RECRUITING":
      return {
        label: t.status.groupStatus.RECRUITING,
        className: "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200 dark:border-blue-800",
      };
    case "READY":
      return {
        label: t.status.groupStatus.READY,
        className: "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200 dark:border-amber-800",
      };
    case "RUNNING":
      return {
        label: t.status.groupStatus.RUNNING,
        className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800",
      };
    case "COMPLETED":
      return {
        label: t.status.groupStatus.COMPLETED,
        className: "bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border-purple-200 dark:border-purple-800",
      };
    case "CANCELLED":
      return {
        label: t.status.groupStatus.CANCELLED,
        className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800",
      };
    default:
      return {
        label: status || "—",
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
  }
}

export function getCycleStatusBadge(
  status: string | null | undefined,
  lang?: Language
): { label: string; className: string } {
  const t = getTranslations(lang || getStoredLanguage());
  switch (status) {
    case "DRAFT":
      return {
        label: t.status.cycleStatus.DRAFT,
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
    case "OPEN":
      return {
        label: t.status.cycleStatus.OPEN,
        className: "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200 dark:border-blue-800",
      };
    case "BIDDING":
      return {
        label: t.status.cycleStatus.BIDDING,
        className: "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200 dark:border-amber-800 animate-pulse",
      };
    case "CLOSED_FOR_CALC":
      return {
        label: t.status.cycleStatus.CLOSED_FOR_CALC,
        className: "bg-orange-50 text-orange-700 dark:bg-orange-950/60 dark:text-orange-300 border-orange-200 dark:border-orange-800",
      };
    case "PAYOUT_PENDING":
      return {
        label: t.status.cycleStatus.PAYOUT_PENDING,
        className: "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border-indigo-200 dark:border-indigo-800",
      };
    case "SETTLED":
      return {
        label: t.status.cycleStatus.SETTLED,
        className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800",
      };
    case "FAILED":
      return {
        label: t.status.cycleStatus.FAILED,
        className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800",
      };
    default:
      return {
        label: status || "—",
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
  }
}

export function getShareStatusBadge(
  status: string | null | undefined,
  lang?: Language
): { label: string; className: string } {
  const t = getTranslations(lang || getStoredLanguage());
  switch (status) {
    case "ALIVE":
      return {
        label: t.status.shareStatus.ALIVE,
        className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800",
      };
    case "DEAD":
      return {
        label: t.status.shareStatus.DEAD,
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
    case "DEFAULTED":
      return {
        label: t.status.shareStatus.DEFAULTED,
        className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800",
      };
    default:
      return {
        label: status || "—",
        className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
      };
  }
}
