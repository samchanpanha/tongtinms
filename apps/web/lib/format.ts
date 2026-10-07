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

export function formatDate(isoString: string | null | undefined): string {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    return d.toLocaleDateString("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  } catch {
    return isoString;
  }
}

export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    return `${d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} • ${d.toLocaleDateString("vi-VN", {
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

export function getGroupTypeLabel(type: string | null | undefined): string {
  switch (type) {
    case "BIDDING":
      return "Hụi Đấu (Kín)";
    case "FIXED_EQUAL":
      return "Hụi Thảo (Cố định)";
    default:
      return type || "—";
  }
}

export function getCycleUnitLabel(unit: string | null | undefined): string {
  switch (unit) {
    case "DAY":
      return "Ngày";
    case "WEEK":
      return "Tuần";
    case "MONTH":
      return "Tháng";
    default:
      return unit || "Kỳ";
  }
}

export function getGroupStatusBadge(status: string | null | undefined): { label: string; className: string } {
  switch (status) {
    case "DRAFT":
      return { label: "Bản nháp", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
    case "RECRUITING":
      return { label: "Đang gom chân", className: "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200 dark:border-blue-800" };
    case "READY":
      return { label: "Sẵn sàng", className: "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200 dark:border-amber-800" };
    case "RUNNING":
      return { label: "Đang chạy", className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800" };
    case "COMPLETED":
      return { label: "Hoàn tất", className: "bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border-purple-200 dark:border-purple-800" };
    case "CANCELLED":
      return { label: "Đã hủy", className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800" };
    default:
      return { label: status || "—", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
  }
}

export function getCycleStatusBadge(status: string | null | undefined): { label: string; className: string } {
  switch (status) {
    case "DRAFT":
      return { label: "Chưa mở", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
    case "OPEN":
      return { label: "Đang mở", className: "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border-blue-200 dark:border-blue-800" };
    case "BIDDING":
      return { label: "Đang bỏ thăm", className: "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300 border-amber-200 dark:border-amber-800 animate-pulse" };
    case "CLOSED_FOR_CALC":
      return { label: "Đang tính tiền", className: "bg-orange-50 text-orange-700 dark:bg-orange-950/60 dark:text-orange-300 border-orange-200 dark:border-orange-800" };
    case "PAYOUT_PENDING":
      return { label: "Chờ giao tiền", className: "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border-indigo-200 dark:border-indigo-800" };
    case "SETTLED":
      return { label: "Đã quyết toán", className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800" };
    case "FAILED":
      return { label: "Thất bại", className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800" };
    default:
      return { label: status || "—", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
  }
}

export function getShareStatusBadge(status: string | null | undefined): { label: string; className: string } {
  switch (status) {
    case "ALIVE":
      return { label: "Hụi Sống (Chưa hốt)", className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800" };
    case "DEAD":
      return { label: "Hụi Chết (Đã hốt)", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
    case "DEFAULTED":
      return { label: "Bể hụi (Mất khả năng)", className: "bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border-rose-200 dark:border-rose-800" };
    default:
      return { label: status || "—", className: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700" };
  }
}
