export type Language = "km" | "en" | "zh" | "vi";

export const DEFAULT_LANGUAGE: Language = "km";

export interface LanguageOption {
  code: Language;
  label: string;
  nativeName: string;
  shortLabel: string;
  flag: string;
  htmlLang: string;
  dateLocale: string;
}

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  {
    code: "km",
    label: "Khmer",
    nativeName: "ភាសាខ្មែរ",
    shortLabel: "ខ្មែរ",
    flag: "🇰🇭",
    htmlLang: "km",
    dateLocale: "km-KH",
  },
  {
    code: "en",
    label: "English",
    nativeName: "English",
    shortLabel: "EN",
    flag: "🇬🇧",
    htmlLang: "en",
    dateLocale: "en-US",
  },
  {
    code: "zh",
    label: "Chinese",
    nativeName: "中文",
    shortLabel: "中文",
    flag: "🇨🇳",
    htmlLang: "zh-CN",
    dateLocale: "zh-CN",
  },
  {
    code: "vi",
    label: "Vietnamese",
    nativeName: "Tiếng Việt",
    shortLabel: "VI",
    flag: "🇻🇳",
    htmlLang: "vi",
    dateLocale: "vi-VN",
  },
];

export interface Translations {
  meta: {
    title: string;
    description: string;
  };
  navbar: {
    overview: string;
    members: string;
    myGroups: string;
    notifications: string;
    roleHost: string;
    roleMember: string;
    logout: string;
    login: string;
    registerHost: string;
    languageLabel: string;
  };
  status: {
    groupType: {
      BIDDING: string;
      FIXED_EQUAL: string;
    };
    cycleUnit: {
      DAY: string;
      WEEK: string;
      MONTH: string;
      DEFAULT: string;
    };
    groupStatus: {
      DRAFT: string;
      RECRUITING: string;
      READY: string;
      RUNNING: string;
      COMPLETED: string;
      CANCELLED: string;
    };
    cycleStatus: {
      DRAFT: string;
      OPEN: string;
      BIDDING: string;
      CLOSED_FOR_CALC: string;
      PAYOUT_PENDING: string;
      SETTLED: string;
      FAILED: string;
    };
    shareStatus: {
      ALIVE: string;
      DEAD: string;
      DEFAULTED: string;
    };
  };
  home: {
    badge: string;
    heroTitleLine1: string;
    heroTitleHighlight: string;
    heroSubtitle: string;
    enterSystem: string;
    registerAsHost: string;
    features: {
      sealedBiddingTitle: string;
      sealedBiddingDesc: string;
      exactMathTitle: string;
      exactMathDesc: string;
      immutableLedgerTitle: string;
      immutableLedgerDesc: string;
      memberPortalTitle: string;
      memberPortalDesc: string;
    };
    demoBar: {
      title: string;
      subtitle: string;
      button: string;
    };
  };
  login: {
    title: string;
    subtitle: string;
    demoFastLogin: string;
    demoHost: string;
    demoMember: string;
    phoneLabel: string;
    phonePlaceholder: string;
    passwordLabel: string;
    passwordPlaceholder: string;
    submitting: string;
    submit: string;
    newHostPrompt: string;
    registerHostLink: string;
    defaultError: string;
  };
  registerOwner: {
    title: string;
    subtitle: string;
    fullNameLabel: string;
    fullNamePlaceholder: string;
    phoneLabel: string;
    phonePlaceholder: string;
    passwordLabel: string;
    confirmPasswordLabel: string;
    termsPrefix: string;
    termsHighlight: string;
    termsSuffix: string;
    submitting: string;
    submit: string;
    alreadyHaveAccount: string;
    loginNow: string;
    errPasswordLength: string;
    errPasswordMismatch: string;
    errAcceptTerms: string;
    defaultError: string;
  };
  notifications: {
    title: string;
    newBadge: (count: number) => string;
    subtitle: string;
    markAllRead: string;
    emptyTitle: string;
    emptyDesc: string;
    markReadTitle: string;
  };
  hostDashboard: {
    loading: string;
    errorTitle: string;
    defaultError: string;
    retry: string;
    title: string;
    subtitle: string;
    memberListBtn: string;
    createGroupBtn: string;
    profitTitle: (currency: string) => string;
    profitTitleDefault: string;
    profitSubtitle: string;
    profitSubtitleEmpty: string;
    profitBadge: string;
    totalGroupsTitle: string;
    runningGroupsSubtitle: (count: number) => string;
    groupsBadge: (count: number) => string;
    unpaidTitle: string;
    unpaidSubtitle: string;
    unpaidBadgeNeed: string;
    unpaidBadgeDone: string;
    overdueTitle: string;
    overdueSubtitle: string;
    overdueBadgeAlert: string;
    overdueBadgeGood: string;
    groupsListTitle: string;
    openMoreGroup: string;
    emptyTitle: string;
    emptyDesc: string;
    emptyAction: string;
    sharesAndCycles: (shares: number, cycles: number) => string;
    cycleProgress: string;
    cycleOf: (current: number, total: number) => string;
    notOpenedYet: string;
    nextDue: string;
    unpaidOverdueLabel: string;
    unpaidCount: (count: number) => string;
    overdueCount: (count: number) => string;
    accumulatedFee: string;
    viewLedger: string;
    enterGroupRoom: string;
  };
  membersDirectory: {
    title: string;
    subtitle: string;
    backToDashboard: string;
    addMemberBtn: string;
    searchPlaceholder: string;
    totalCountPrefix: string;
    totalCountSuffix: string;
    defaultError: string;
    emptySearchTitle: string;
    emptyTitle: string;
    emptySearchDesc: (query: string) => string;
    emptyDesc: string;
    emptyAction: string;
    colFullName: string;
    colPhone: string;
    colIdCard: string;
    colZalo: string;
    colLoginStatus: string;
    colCreatedAt: string;
    colActions: string;
    statusActivated: string;
    statusNoPassword: string;
    changePasswordBtn: string;
    setPasswordBtn: string;
    createModalTitle: string;
    fullNameLabel: string;
    fullNamePlaceholder: string;
    phoneLabel: string;
    phonePlaceholder: string;
    idCardLabel: string;
    idCardPlaceholder: string;
    zaloLabel: string;
    zaloPlaceholder: string;
    cancelBtn: string;
    savingBtn: string;
    saveMemberBtn: string;
    createError: string;
    passwordModalTitle: string;
    passwordModalDesc: (name: string, phone: string) => string;
    newPasswordLabel: string;
    newPasswordPlaceholder: string;
    processingBtn: string;
    confirmPasswordBtn: string;
    errPasswordMin: string;
    passwordSuccess: (name: string) => string;
    passwordError: string;
  };
  createGroup: {
    defaultGroupName: string;
    title: string;
    subtitle: string;
    cancelBtn: string;
    defaultError: string;
    section1Title: string;
    groupNameLabel: string;
    groupNamePlaceholder: string;
    groupTypeLabel: string;
    biddingTitle: string;
    biddingDesc: string;
    fixedTitle: string;
    fixedDesc: string;
    baseAmountLabel: string;
    shareCountLabel: string;
    cycleUnitLabel: string;
    unitMonth: string;
    unitWeek: string;
    unitDay: string;
    bidCloseOffsetLabel: string;
    currencyLabel: string;
    currencyVND: string;
    currencyUSD: string;
    currencyKHR: string;
    tieBreakLabel: string;
    tieEarliest: string;
    tieLottery: string;
    section2Title: string;
    hostFeeTypeLabel: string;
    feeFixed: string;
    feePercent: string;
    feeNone: string;
    hostFeeAmountLabel: string;
    maxBidLabel: string;
    maxBidHint: (formattedAmount: string) => string;
    bidStepLabel: string;
    bidStepHint: string;
    summaryTitle: string;
    summarySharesLabel: string;
    summarySharesValue: (count: number) => string;
    summaryBaseAmountLabel: string;
    summaryFeePerCycleLabel: string;
    summaryFeePercentValue: string;
    summaryTotalFeeLabel: (count: number) => string;
    summaryEstFirstNetLabel: string;
    creatingBtn: string;
    submitBtn: string;
  };
  hostGroupDetail: {
    defaultError: string;
    notFoundTitle: string;
    notFoundDesc: string;
    backToDashboard: string;
    baseAmountLabel: string;
    scaleLabel: string;
    sharesProgress: (current: number, total: number) => string;
    durationLabel: string;
    hostFeeLabel: string;
    ledgerBtn: string;
    startGroupBtn: string;
    openCycleBtn: (nextCycleNo: number) => string;
    tabCycles: (current: number, total: number) => string;
    tabShares: (current: number, total: number) => string;
    tabPayments: (count: number) => string;
    emptyCyclesTitle: string;
    emptyCyclesReadyDesc: string;
    emptyCyclesNeedSharesDesc: (shareCount: number) => string;
    openFirstCycleBtn: string;
    cycleTitle: (cycleNo: number) => string;
    cycleDates: (openAt: string, dueAt: string) => string;
    enterBidForMemberBtn: string;
    closeAndCalcBtn: string;
    confirmPayoutBtn: string;
    winningBidLabel: string;
    grossPotLabel: string;
    hostFeeOutcomeLabel: string;
    netPayoutLabel: string;
    sharesHeader: (current: number, total: number) => string;
    assignMoreSharesBtn: string;
    colShareNo: string;
    colMemberName: string;
    colPhone: string;
    colShareStatus: string;
    colWonCycle: string;
    shareNoBadge: (shareNo: number) => string;
    cycleNoBadge: (cycleNo: number) => string;
    paymentsHeader: string;
    totalPending: (count: number) => string;
    emptyDebtsTitle: string;
    emptyDebtsDesc: string;
    colCycle: string;
    colShare: string;
    colAmountDue: string;
    colPaid: string;
    colRemaining: string;
    colDueDate: string;
    colActions: string;
    collectMoneyBtn: string;
    assignModalTitle: string;
    selectMemberLabel: string;
    selectMemberPlaceholder: string;
    shareCountToAssignLabel: string;
    cancelBtn: string;
    assignConfirmBtn: string;
    bidModalTitle: string;
    selectAliveShareLabel: string;
    bidShareOption: (shareNo: number, memberName: string) => string;
    bidAmountLabel: (currency: string) => string;
    saveBidBtn: string;
    paymentModalTitle: string;
    paymentCycleLabel: string;
    paymentShareLabel: string;
    paymentAmountLabel: string;
    paymentMethodLabel: string;
    methodCash: string;
    methodBank: string;
    methodOther: string;
    confirmPaymentBtn: string;
    msgStartSuccess: string;
    msgStartError: string;
    msgOpenCycleSuccess: (cycleNo: number) => string;
    msgOpenCycleError: string;
    msgAssignSuccess: string;
    msgAssignError: string;
    msgBidSuccess: string;
    msgBidError: string;
    msgCloseSuccess: string;
    msgCloseError: string;
    msgPayoutSuccess: string;
    msgPayoutError: string;
    msgPaymentSuccess: string;
    msgPaymentError: string;
  };
  groupLedger: {
    defaultError: string;
    errorTitle: string;
    emptyDesc: string;
    backToGroup: string;
    backToRoom: string;
    title: (groupName: string) => string;
    subtitle: (currency: string) => string;
    totalInTitle: string;
    totalOutTitle: string;
    balanceTitle: string;
    balanced100: string;
    processing: string;
    colEntryId: string;
    colCycle: string;
    colType: string;
    colDirection: string;
    colRecipient: string;
    colAmount: string;
    colPaid: string;
    colRemaining: string;
    colStatus: string;
    colDueDate: string;
    cycleNo: (cycleNo: number) => string;
    typeContribution: string;
    typePayout: string;
    typeHostFee: string;
    dirIn: string;
    dirOut: string;
    recipientMember: (memberName: string, shareNo: number | null) => string;
    recipientHost: string;
  };
  memberPortal: {
    defaultError: string;
    title: string;
    subtitle: string;
    joinedGroupsTitle: string;
    emptyTitle: string;
    emptyDesc: string;
    totalSharesAndCycles: (shares: number, cycles: number) => string;
    ownedSharesLabel: string;
    ownedSharesValue: (count: number) => string;
    cycleProgressLabel: string;
    cycleOf: (current: number, total: number) => string;
    notOpenedYet: string;
    viewDetailsAndStatement: string;
  };
  memberGroupDetail: {
    notFoundTitle: string;
    notFoundDesc: string;
    backToGroups: string;
    ownedSharesSummary: (count: number, details: string) => string;
    shareItem: (shareNo: number, status: string) => string;
    tabCycles: (current: number, total: number) => string;
    tabStatement: string;
    activeBiddingTitle: (cycleNo: number) => string;
    activeBiddingDesc: string;
    selectAliveShareLabel: string;
    aliveShareOption: (shareNo: number) => string;
    bidAmountLabel: (currency: string) => string;
    submittingBid: string;
    submitBidBtn: string;
    errCycleNotFound: string;
    bidSuccessMsg: (formattedAmount: string) => string;
    bidErrorMsg: string;
    colCycle: string;
    colStatus: string;
    colWinner: string;
    colWinningBid: string;
    colGrossPot: string;
    colNetPayout: string;
    colDueDate: string;
    cycleNo: (cycleNo: number) => string;
    winnerItem: (memberName: string, shareNo: number) => string;
    contributedLabel: string;
    receivedLabel: string;
    feesLabel: string;
    netPositionLabel: string;
    shareTitle: (shareNo: number) => string;
    statusLabel: string;
    colCycleNo: string;
    colDescription: string;
    colAmount: string;
    colRunningBalance: string;
    entryContribution: string;
    entryPayout: string;
    emptyStatementTitle: string;
    emptyStatementDesc: string;
  };
}
