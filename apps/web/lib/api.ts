export interface UserProfile {
  id: number;
  phone: string;
  fullName: string;
  email: string | null;
  status: string;
}

export interface OwnerProfile {
  id: number;
  userId: number;
  displayName: string;
  bankName: string | null;
  bankAccount: string | null;
  accountHolder: string | null;
  zalo: string | null;
  city: string | null;
  cccd: string | null;
  status: string;
}

export interface AuthTokens {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface LoginResponse {
  user: UserProfile;
  owner: OwnerProfile | null;
  roles: string[];
  tokens: AuthTokens;
}

export interface AdminSetting {
  key: string;
  label: string;
  description: string;
  type: "STRING" | "SECRET" | "URL" | "INT" | "BOOLEAN" | "INT_LIST";
  value: string | null;
  configured: boolean;
  defaultValue: string | null;
  min: number | null;
  max: number | null;
  updatedAt: string | null;
}

export interface AdminSettingCategory {
  code: string;
  label: string;
  settings: AdminSetting[];
}

export interface AdminSettingsView {
  categories: AdminSettingCategory[];
}

export interface AdminInsights {
  revenueByPlan: Array<{
    planId: number | null;
    planName: string;
    currency: string;
    paidOrders: number;
    revenueMinor: number;
  }>;
  totalsByCurrency: Array<{
    currency: string;
    paidOrders: number;
    revenueMinor: number;
  }>;
  cohorts: Array<{
    month: string;
    registered: number;
    activeNow: number;
    churned: number;
  }>;
}

export interface AttachmentMeta {
  id: number;
  entityType: "PAYMENT" | "MEMBER";
  entityId: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface PaymentRecord {
  id: number;
  groupId: number;
  amountMinor: number;
  currency: string;
  method: string;
  paidAt: string;
  note: string | null;
  createdAt: string;
  allocations: Array<{ ledgerEntryId: number; amountMinor: number }>;
  attachments: AttachmentMeta[];
}

const TOKEN_KEY = "tongtin_access_token";
const USER_KEY = "tongtin_auth_data";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredAuth(): LoginResponse | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function saveAuth(data: LoginResponse) {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY, data.tokens.accessToken);
  localStorage.setItem(USER_KEY, JSON.stringify(data));
}

export function clearAuth() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getStoredToken();
  const headers = new Headers(options.headers || {});
  
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const res = await fetch(`/api/v1${endpoint}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    let errorMsg = `Request failed (HTTP ${res.status})`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.error || errorMsg;
    } catch {
      // ignore
    }
    const error = new Error(errorMsg) as Error & { status: number };
    error.status = res.status;
    throw error;
  }

  if (res.status === 204) {
    return {} as T;
  }

  return res.json();
}

export type ExportFormat = "csv" | "xlsx";

async function download(endpoint: string, fallbackFilename: string): Promise<void> {
  const token = getStoredToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const res = await fetch(`/api/v1${endpoint}`, { headers });
  if (!res.ok) {
    let errorMsg = `Request failed (HTTP ${res.status})`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.error || errorMsg;
    } catch {
      // ignore
    }
    const error = new Error(errorMsg) as Error & { status: number };
    error.status = res.status;
    throw error;
  }
  const blob = await res.blob();
  const disposition = res.headers.get("Content-Disposition") || "";
  const starMatch = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  const nameMatch = /filename="([^"]+)"/.exec(disposition);
  const filename = starMatch
    ? decodeURIComponent(starMatch[1])
    : nameMatch
      ? nameMatch[1]
      : fallbackFilename;
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}

/** Fetches an obligation's KHQR PNG (Bearer-authed; the image itself cannot hold the header) */
async function fetchObligationQr(entryId: number | string): Promise<string> {
  const token = getStoredToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const res = await fetch(`/api/v1/obligations/${entryId}/khqr`, { headers });
  if (!res.ok) {
    let errorMsg = `Request failed (HTTP ${res.status})`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.error || errorMsg;
    } catch {
      // ignore
    }
    const error = new Error(errorMsg) as Error & { status: number };
    error.status = res.status;
    throw error;
  }
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

export const api = {
  // Auth
  async login(phone: string, password: string): Promise<LoginResponse> {
    const data = await request<LoginResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ phone, password }),
    });
    saveAuth(data);
    return data;
  },

  async registerOwner(body: {
    fullName: string;
    phone: string;
    password: string;
    confirmPassword: string;
    acceptTerms: boolean;
  }): Promise<LoginResponse> {
    const data = await request<LoginResponse>("/auth/register-owner", {
      method: "POST",
      body: JSON.stringify(body),
    });
    saveAuth(data);
    return data;
  },

  async getMe(): Promise<{ user: UserProfile; roles: string[]; owner?: OwnerProfile }> {
    return request("/me");
  },

  // Host Dashboard
  async getHostDashboard(): Promise<{
    currencies: Array<{ currency: string; amountMinor: number; exponent: number; symbol: string }>;
    groups: Array<{
      id: number;
      code: string;
      name: string;
      type: string;
      status: string;
      currency: string;
      shareCount: number;
      cycleCount: number;
      currentCycleNo: number | null;
      currentCycleStatus: string | null;
      nextDueAt: string | null;
      unpaidCount: number;
      overdueCount: number;
      hostProfitMinor: number;
    }>;
  }> {
    return request("/host/dashboard");
  },

  // Members
  async getMembers(q?: string): Promise<Array<{
    id: number;
    fullName: string;
    phone: string;
    zalo: string | null;
    cccd: string | null;
    status: string;
    loginEnabled: boolean;
    createdAt: string;
    attachments: AttachmentMeta[];
  }>> {
    const query = q ? `?q=${encodeURIComponent(q)}` : "";
    return request(`/members${query}`);
  },

  async createMember(body: { fullName: string; phone: string; zalo?: string; cccd?: string }) {
    return request<{ id: number; fullName: string; phone: string }>("/members", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async setMemberLogin(memberId: number, password: string) {
    return request<{ memberProfileId: number; loginEnabled: boolean; phone: string }>(`/members/${memberId}/set-login`, {
      method: "POST",
      body: JSON.stringify({ password }),
    });
  },

  async updateMember(
    memberId: number,
    body: { fullName?: string; phone?: string; note?: string; status?: string },
  ) {
    return request<{
      id: number;
      fullName: string;
      phone: string;
      status: string;
      createdAt: string;
      attachments: AttachmentMeta[];
    }>(`/members/${memberId}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    });
  },

  async getBlacklist(): Promise<Array<{
    id: number;
    phone: string;
    reason: string | null;
    active: boolean;
    createdAt: string;
    updatedAt: string | null;
  }>> {
    return request(`/members/blacklist`);
  },

  async addBlacklist(body: { phone: string; reason?: string }) {
    return request<{
      id: number;
      phone: string;
      reason: string | null;
      active: boolean;
      createdAt: string;
      updatedAt: string | null;
    }>(`/members/blacklist`, {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async unlistBlacklist(id: number) {
    return request<{ id: number; phone: string; active: boolean }>(`/members/blacklist/${id}`, {
      method: "DELETE",
    });
  },

  async uploadMemberAttachment(memberId: number, file: File): Promise<AttachmentMeta> {
    const form = new FormData();
    form.append("file", file);
    return request(`/members/${memberId}/attachments`, { method: "POST", body: form });
  },

  // Groups
  async getGroups(): Promise<Array<{
    id: number;
    code: string;
    name: string;
    type: string;
    status: string;
    currency: string;
    baseAmount: number;
    shareCount: number;
    cycleCount: number;
    cycleUnit: string;
    hostFeeType: string;
    hostFeeMinor: number;
  }>> {
    return request("/groups");
  },

  async getGroup(id: number | string): Promise<{
    id: number;
    code: string;
    name: string;
    type: string;
    status: string;
    currency: string;
    baseAmount: number;
    shareCount: number;
    cycleCount: number;
    cycleUnit: string;
    hostFeeType: string;
    hostFeeMinor: number;
    maxBid: number | null;
    bidStep: number | null;
    bidCloseOffset: number;
    tieBreak: string;
    lateFeeType: string;
    lateFeeValue: number;
    rulesFrozen: boolean;
  }> {
    return request(`/groups/${id}`);
  },

  async createGroup(body: Record<string, unknown>) {
    return request<{ id: number; code: string; name: string }>("/groups", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async getGroupShares(groupId: number | string): Promise<Array<{
    id: number;
    shareNo: number;
    memberProfileId: number;
    memberName: string;
    memberPhone: string;
    status: string;
    wonCycleId: number | null;
  }>> {
    return request(`/groups/${groupId}/shares`);
  },

  async assignShare(groupId: number | string, memberProfileId: number, count: number = 1) {
    return request(`/groups/${groupId}/shares`, {
      method: "POST",
      body: JSON.stringify({ memberProfileId, count }),
    });
  },

  async startGroup(groupId: number | string) {
    return request(`/groups/${groupId}/start`, {
      method: "POST",
    });
  },

  // Cycles
  async getGroupCycles(groupId: number | string): Promise<Array<{
    id: number;
    cycleNo: number;
    status: string;
    currency: string;
    openAt: string;
    bidCloseAt: string;
    dueAt: string;
    winnerShareId: number | null;
    winningBid: number | null;
    grossPot: number | null;
    hostFee: number | null;
    netPayout: number | null;
  }>> {
    return request(`/groups/${groupId}/cycles`);
  },

  async openCycle(groupId: number | string) {
    return request<{ id: number; cycleNo: number; status: string }>(`/groups/${groupId}/cycles/open`, {
      method: "POST",
    });
  },

  async submitHostBid(cycleId: number | string, shareId: number, amountMinor: number) {
    return request(`/cycles/${cycleId}/bids`, {
      method: "POST",
      body: JSON.stringify({ shareId, amountMinor }),
    });
  },

  async closeAndCalculate(cycleId: number | string, winnerShareId?: number) {
    return request(`/cycles/${cycleId}/close-and-calculate`, {
      method: "POST",
      body: winnerShareId ? JSON.stringify({ winnerShareId }) : undefined,
    });
  },

  async confirmPayout(cycleId: number | string) {
    return request(`/cycles/${cycleId}/confirm-payout`, {
      method: "POST",
    });
  },

  // Payments & Debts
  async recordPayment(groupId: number | string, body: Record<string, unknown>, idempotencyKey?: string) {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers["Idempotency-Key"] = idempotencyKey;
    }
    return request(`/groups/${groupId}/payments`, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    });
  },

  /** Step 36: host types one total; the backend auto-allocates oldest-first. */
  async quickPay(
    groupId: number | string,
    body: Record<string, unknown>,
    idempotencyKey?: string
  ): Promise<{ id: number }> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers["Idempotency-Key"] = idempotencyKey;
    }
    return request<{ id: number }>(`/groups/${groupId}/quick-pay`, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    });
  },

  async getPayments(groupId: number | string): Promise<PaymentRecord[]> {
    return request(`/groups/${groupId}/payments`);
  },

  async uploadPaymentAttachment(paymentId: number, file: File): Promise<AttachmentMeta> {
    const form = new FormData();
    form.append("file", file);
    return request(`/payments/${paymentId}/attachments`, { method: "POST", body: form });
  },

  async deleteAttachment(attachmentId: number): Promise<void> {
    return request(`/attachments/${attachmentId}`, { method: "DELETE" });
  },

  async downloadAttachment(attachmentId: number, fallbackName: string): Promise<void> {
    return download(`/attachments/${attachmentId}`, fallbackName);
  },

  async getDebts(groupId: number | string): Promise<Array<{
    ledgerEntryId: number;
    cycleId: number;
    cycleNo: number;
    shareId: number;
    memberProfileId: number;
    type: string;
    direction: string;
    amountMinor: number;
    allocatedMinor: number;
    remainingMinor: number;
    currency: string;
    status: string;
    dueAt: string;
    overdueDays: number;
    khqr?: string | null;
  }>> {
    return request(`/groups/${groupId}/debts`);
  },

  /** Obligation KHQR as a blob URL for an <img> (Bearer token travels via header) */
  fetchObligationQr(entryId: number | string): Promise<string> {
    return fetchObligationQr(entryId);
  },

  async assessLateFees(groupId: number | string): Promise<{
    assessed: number;
    created: number;
    entries: Array<{
      ledgerEntryId: number;
      cycleId: number;
      shareId: number;
      amountMinor: number;
      currency: string;
      dueAt: string;
    }>;
  }> {
    return request(`/groups/${groupId}/late-fees/assess`, { method: "POST" });
  },

  // Reports
  async getGroupLedger(groupId: number | string): Promise<{
    groupId: number;
    groupName: string;
    currency: string;
    entries: Array<{
      entryId: number;
      cycleNo: number;
      type: string;
      direction: string;
      shareNo: number | null;
      memberProfileId: number | null;
      memberName: string | null;
      amount: { currency: string; amountMinor: number; exponent: number; symbol: string };
      status: string;
      dueAt: string;
      allocated: { currency: string; amountMinor: number; exponent: number; symbol: string };
      remaining: { currency: string; amountMinor: number; exponent: number; symbol: string };
    }>;
    totalIn: { currency: string; amountMinor: number; exponent: number; symbol: string };
    totalOut: { currency: string; amountMinor: number; exponent: number; symbol: string };
  }> {
    return request(`/groups/${groupId}/ledger`);
  },

  async getGroupProfit(groupId: number | string): Promise<{
    groupId: number;
    groupName: string;
    currency: string;
    formulaVersion: number;
    cycles: Array<{
      cycleNo: number;
      status: string;
      winner: { shareNo: number; memberName: string } | null;
      winningBid: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
      grossPot: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
      hostFee: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
      netPayout: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
    }>;
    totals: {
      grossPot: { currency: string; amountMinor: number; exponent: number; symbol: string };
      hostFee: { currency: string; amountMinor: number; exponent: number; symbol: string };
      netPayout: { currency: string; amountMinor: number; exponent: number; symbol: string };
      settledHostFee: { currency: string; amountMinor: number; exponent: number; symbol: string };
    };
  }> {
    return request(`/groups/${groupId}/profit`);
  },

  async exportLedger(groupId: number | string, format: ExportFormat): Promise<void> {
    await download(
      `/groups/${groupId}/export/ledger?format=${format}`,
      `tongtin-ledger-g${groupId}.${format}`
    );
  },

  async exportProfit(groupId: number | string, format: ExportFormat): Promise<void> {
    await download(
      `/groups/${groupId}/export/profit?format=${format}`,
      `tongtin-profit-g${groupId}.${format}`
    );
  },

  async exportStatement(groupId: number | string, format: ExportFormat): Promise<void> {
    await download(
      `/me/groups/${groupId}/export/statement?format=${format}`,
      `tongtin-statement-g${groupId}.${format}`
    );
  },

  // Member Portal
  async getMyGroups(): Promise<Array<{
    id: number;
    code: string;
    name: string;
    type: string;
    status: string;
    currency: string;
    shareCount: number;
    cycleCount: number;
    currentCycleNo: number | null;
    currentCycleStatus: string | null;
    myShareCount: number;
    myShares: Array<{ id: number; shareNo: number; status: string }>;
  }>> {
    return request("/me/groups");
  },

  async getMyGroupStatement(groupId: number | string): Promise<{
    groupId: number;
    groupName: string;
    currency: string;
    shares: Array<{
      shareId: number;
      shareNo: number;
      status?: string;
      shareStatus?: string;
      entries: Array<{
        entryId: number;
        cycleNo: number;
        type: string;
        direction: string;
        amount: { currency: string; amountMinor: number; exponent: number; symbol: string };
        status: string;
        dueAt?: string;
        allocated?: { currency: string; amountMinor: number; exponent: number; symbol: string };
        remaining?: { currency: string; amountMinor: number; exponent: number; symbol: string };
        runningBalance?: { currency: string; amountMinor: number; exponent: number; symbol: string };
        runningPosition?: { currency: string; amountMinor: number; exponent: number; symbol: string };
        khqr?: string | null;
      }>;
      totals?: {
        contributed: { currency: string; amountMinor: number; exponent: number; symbol: string };
        received: { currency: string; amountMinor: number; exponent: number; symbol: string };
        feesPaid: { currency: string; amountMinor: number; exponent: number; symbol: string };
        netPosition: { currency: string; amountMinor: number; exponent: number; symbol: string };
      };
    }>;
    totals: {
      contributed: { currency: string; amountMinor: number; exponent: number; symbol: string };
      received: { currency: string; amountMinor: number; exponent: number; symbol: string };
      feesPaid: { currency: string; amountMinor: number; exponent: number; symbol: string };
      netPosition: { currency: string; amountMinor: number; exponent: number; symbol: string };
    };
  }> {
    return request(`/me/groups/${groupId}/statement`);
  },

  async getMyGroupCycles(groupId: number | string): Promise<Array<{
    cycleNo: number;
    status: string;
    openAt: string;
    bidCloseAt: string;
    dueAt: string;
    winner: { shareNo: number; memberName: string } | null;
    winningBid: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
    grossPot: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
    netPayout: { currency: string; amountMinor: number; exponent: number; symbol: string } | null;
  }>> {
    return request(`/me/groups/${groupId}/cycles`);
  },

  async submitMyBid(cycleId: number | string, shareId: number, amountMinor: number) {
    return request(`/me/cycles/${cycleId}/bids`, {
      method: "POST",
      body: JSON.stringify({ shareId, amountMinor }),
    });
  },

  // Notifications
  async getNotifications(): Promise<{
    notifications: Array<{
      id: number;
      type: string;
      title: string;
      body: string;
      readAt: string | null;
      createdAt: string;
    }>;
    unreadCount: number;
  }> {
    return request("/notifications");
  },

  async getUnreadCount(): Promise<{ unreadCount: number }> {
    return request("/notifications/unread-count");
  },

  async markNotificationRead(id: number) {
    return request(`/notifications/${id}/read`, { method: "POST" });
  },

  async markAllNotificationsRead() {
    return request<{ updatedCount: number }>("/notifications/read-all", { method: "POST" });
  },

  // Subscription & ABA PayWay
  async getSubscriptionStatus(): Promise<{
    ownerId: number;
    displayName: string;
    subscriptionStatus: string;
    trialEndsAt: string;
    subscriptionEndsAt: string;
    daysRemaining: number;
    isTrial: boolean;
    isGracePeriod: boolean;
    isExpired: boolean;
    canCreateGroup: boolean;
    currentPlan: {
      id: number;
      code: string;
      name: string;
      description: string;
      priceMinor: number;
      currency: string;
      durationMonths: number;
      maxGroups: number;
      maxMembers: number;
      featuresJson: string;
      badge: string;
      sortOrder: number;
      isActive: boolean;
    } | null;
    groupsCount: number;
    maxGroups: number;
    membersCount: number;
    maxMembers: number;
  }> {
    return request("/subscription/my-status");
  },

  async getSubscriptionPlans(): Promise<Array<{
    id: number;
    code: string;
    name: string;
    description: string;
    priceMinor: number;
    currency: string;
    durationMonths: number;
    maxGroups: number;
    maxMembers: number;
    featuresJson: string;
    badge: string;
    sortOrder: number;
    isActive: boolean;
  }>> {
    return request("/subscription/plans");
  },

  async checkoutPayWay(planId: number, paymentOption?: string, returnUrl?: string) {
    return request<{
      tranId: string;
      reqTime: string;
      merchantId: string;
      amount: string;
      currency: string;
      itemsBase64: string;
      hash: string;
      checkoutUrl: string;
      paymentOption: string;
      returnUrl: string;
      continueSuccessUrl: string;
      cancelUrl: string;
      firstName: string;
      lastName: string;
      email: string;
      phone: string;
      qrString: string;
      formFields: Record<string, string>;
    }>("/subscription/checkout/payway", {
      method: "POST",
      body: JSON.stringify({ planId, paymentOption, returnUrl }),
    });
  },

  async verifySubscriptionOrder(tranId: string) {
    return request<{
      id: number;
      tranId: string;
      status: string;
      amountMinor: number;
      currency: string;
      paidAt: string;
    }>(`/subscription/verify/${tranId}`, {
      method: "POST",
    });
  },

  async simulatePayWayPayment(tranId: string) {
    return request<{ status: number; message: string; tranId: string; orderStatus: string }>(
      "/payments/payway/simulate-complete",
      {
        method: "POST",
        body: JSON.stringify({ tranId }),
      }
    );
  },

  async getSubscriptionInvoices(): Promise<Array<{
    id: number;
    tranId: string;
    planId: number;
    amountMinor: number;
    currency: string;
    status: string;
    paymentGateway: string;
    paidAt: string | null;
    createdAt: string;
  }>> {
    return request("/subscription/invoices");
  },

  // Admin APIs
  async getAdminPlans(): Promise<Array<{
    id: number;
    code: string;
    name: string;
    description: string;
    priceMinor: number;
    currency: string;
    durationMonths: number;
    maxGroups: number;
    maxMembers: number;
    featuresJson: string;
    badge: string;
    sortOrder: number;
    isActive: boolean;
  }>> {
    return request("/admin/plans");
  },

  async createAdminPlan(body: Record<string, unknown>) {
    return request("/admin/plans", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async updateAdminPlan(id: number, body: Record<string, unknown>) {
    return request(`/admin/plans/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  async deleteAdminPlan(id: number) {
    return request(`/admin/plans/${id}`, {
      method: "DELETE",
    });
  },

  async getAdminHosts(): Promise<Array<{
    ownerId: number;
    userId: number;
    fullName: string;
    phone: string;
    email: string | null;
    displayName: string;
    subscriptionStatus: string;
    trialEndsAt: string;
    subscriptionEndsAt: string;
    daysRemaining: number;
    currentPlanId: number | null;
    currentPlanName: string;
    groupsCount: number;
    membersCount: number;
    registeredAt: string;
  }>> {
    return request("/admin/hosts");
  },

  async extendHostSubscription(ownerId: number, body: { extendDays: number; planId?: number; reason?: string; setLifetime?: boolean }) {
    return request(`/admin/hosts/${ownerId}/extend`, {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async getAdminOrders(): Promise<Array<{
    id: number;
    ownerId: number;
    planId: number;
    tranId: string;
    amountMinor: number;
    currency: string;
    status: string;
    paymentGateway: string;
    gatewayTranId: string | null;
    reqTime: string;
    paidAt: string | null;
    createdAt: string;
  }>> {
    return request("/admin/orders");
  },

  async getAdminInsights(): Promise<AdminInsights> {
    return request("/admin/insights");
  },

  async getAdminSettings(): Promise<AdminSettingsView> {
    return request("/admin/settings");
  },

  async updateAdminSettings(values: Record<string, string>): Promise<AdminSettingsView> {
    return request("/admin/settings", {
      method: "PUT",
      body: JSON.stringify({ values }),
    });
  },

  // Telegram (host channel)
  async getTelegramStatus(): Promise<{
    chatId: number | null;
    linked: boolean;
    eventsEnabled: boolean;
    digestEnabled: boolean;
    digestTime: string;
    eventsConfigured: boolean;
  }> {
    return request("/host/telegram");
  },

  async setTelegramChatId(chatId: number | null): Promise<{
    chatId: number | null;
    linked: boolean;
    eventsEnabled: boolean;
    digestEnabled: boolean;
    digestTime: string;
    eventsConfigured: boolean;
  }> {
    return request("/host/telegram/chat-id", {
      method: "PUT",
      body: JSON.stringify({ chatId }),
    });
  },

  async testTelegram(): Promise<{
    chatId: number | null;
    linked: boolean;
    eventsEnabled: boolean;
    digestEnabled: boolean;
    digestTime: string;
    eventsConfigured: boolean;
  }> {
    return request("/host/telegram/test", { method: "POST" });
  },
};
