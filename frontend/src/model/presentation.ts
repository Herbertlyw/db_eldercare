import { values, statusLabels, columns } from "./registry";
import type { Row } from "./types";
export function pageHint(recordKind: string, role: string) {
  if (recordKind === "elders")
    return role === "ADMIN"
      ? "维护老人档案和家属关系，从档案发起预约或评估。"
      : role === "FAMILY"
        ? "查看已关联老人的档案、照护计划和服务记录。"
        : "查看服务对象档案，遇到紧急情况可上报求助。";
  if (recordKind === "orders")
    return role === "CAREGIVER"
      ? "按订单接单、签到和签退，并记录实际服务情况。"
      : role === "FAMILY"
        ? "服务结束后核对记录，再确认、评价或反馈问题。"
        : "安排护工班次，跟进接单、上门服务和家属确认。";
  const hints: Record<string, string> = {
    assessments: "能力等级由评估员录入；复评后请核对照护计划。",
    plans:
      role === "ADMIN"
        ? "计划须与最新评估一致，服务项目和时间均在计划内安排。"
        : "查看照护项目、责任护工和计划有效期。",
    credentials: "证书类型、等级和有效期用于核验护工的服务资格。",
    shifts: "同一护工的班次不能重叠，派单时还会核验资质和名额。",
    complaints: "评价与投诉分别记录；投诉可查看受理、回复和结案过程。",
    claims:
      role === "ADMIN"
        ? "选定政策和结算周期后，勾选符合条件的已完成订单。"
        : role === "STREET"
          ? "核对申报依据和材料，完成街道初审。"
          : role === "REGULATOR"
            ? "核对街道初审和申报材料，完成终审与拨付。"
            : "查看补贴审核进度和拨付记录。",
    devices: "先绑定老人，再进行采样；换绑后原有采样记录仍保留。",
    events: "按事件当前阶段核实、响应、登记到场和处置结果。",
    inspections: "抽查发现问题后交机构整改，再由监管人员复核。",
    recruitments: "录用时创建护工账号，随后安排岗前培训与资质登记。",
    trainings: "先安排培训和参训人员，培训结束后登记成绩。",
    policies: "政策包含适用项目、对象条件、社区范围和结算周期。",
  };
  return hints[recordKind] || "";
}
export function displayValue(key: string, value: any, kind = ""): string {
  if (value == null) return "—";
  if (key === "kind" && value === "REVIEW") return "服务评价";
  if (key === "method")
    return value === "VISIT"
      ? "上门抽查"
      : value === "PHONE"
        ? "电话抽查"
        : String(value);
  if (key === "status" && statusLabels[kind]?.[value])
    return statusLabels[kind][value];
  if (key === "tags" && typeof value === "string")
    return value
      .split(",")
      .map((t) => values[t.trim()] || t.trim())
      .join("、");
  if (
    [
      "abnormal",
      "fence_ok",
      "checkout_fence_ok",
      "subsidizable",
      "active",
      "simulation_enabled",
    ].includes(key)
  )
    return value ? "是" : "否";
  if (
    [
      "status",
      "ability_level",
      "channel",
      "source",
      "unit",
      "result",
      "decision",
      "kind",
      "settlement_cycle",
      "required_tag",
      "tag",
      "target_type",
    ].includes(key)
  )
    return values[value] || String(value);
  if (typeof value === "string" && /^\d{4}-\d\d-\d\dT/.test(value))
    return value.replace("T", " ").slice(0, 19);
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}
export function recordLabel(row: Row, kind = "") {
  const parts = [
    row.elder_name,
    row.caregiver_name,
    row.name,
    row.username,
    row.candidate_name,
    row.title,
    row.scale_name,
    row.metric,
    row.item_name,
    row.serial_no,
    row.period,
  ].filter(Boolean);
  for (const key of ["ability_level", "settlement_cycle", "status"])
    if (row[key]) parts.push(displayValue(key, row[key], kind));
  if (row.valid_until)
    parts.push("有效至 " + String(row.valid_until).slice(0, 10));
  if (row.starts_at)
    parts.push(displayValue("starts_at", row.starts_at).slice(0, 16));
  return [...new Set(parts)].join(" · ") + `（#${row.id}）`;
}
const leading: Record<string, string[]> = {
  elders: ["id", "name", "birth_date", "address", "tags", "status"],
  orders: ["id", "elder_name", "item_name", "status", "starts_at", "amount"],
  plans: ["id", "elder_name", "name", "status", "starts_at", "ends_at"],
  caregivers: ["id", "name", "status", "organization_id"],
  complaints: ["id", "kind", "status", "rating", "content", "created_at"],
  claims: ["id", "elder_name", "name", "status", "amount", "period"],
  events: [
    "id",
    "elder_name",
    "name",
    "status",
    "level",
    "source",
    "occurred_at",
  ],
  devices: ["id", "type_name", "status", "serial_no", "location_note"],
  inspections: ["id", "status", "method", "score", "issue", "rectification"],
  recruitments: ["id", "candidate_name", "status", "review_note", "created_at"],
  trainingRecords: ["id", "name", "title", "result", "score"],
  organizationReports: ["id", "period", "status", "summary", "review_note"],
  hours: [
    "id",
    "caregiver_name",
    "status",
    "effective_minutes",
    "check_in",
    "check_out",
  ],
  jurisdictionStats: [
    "name",
    "period",
    "active_elders",
    "served_elders",
    "coverage_rate",
    "events_total",
    "timely_responses",
    "response_rate",
    "paid_subsidy",
  ],
};
export const recordColumns = (kind: string, row: Row) =>
  (leading[kind] || Object.keys(row)).filter(
    (k) => k !== "current_worker_account" && k in row,
  );
export const columnTitle = (key: string, kind = "") =>
  kind === "elders" && key === "name" ? "姓名" : columns[key] || key;
export const pendingStates = [
  "PENDING",
  "CONFIRM",
  "REVIEW",
  "UNCONFIRMED",
  "SUBMITTED",
];
export const closedStates = [
  "CLOSED",
  "CANCELLED",
  "INACTIVE",
  "EXPIRED",
  "ENDED",
  "REJECTED",
];
export function flowSteps(kind: string, row: Row): [string, string][] {
  if (kind === "complaints" && row.kind === "REVIEW") return [];
  const flows: Record<string, [string, string][]> = {
    orders: [
      ["PENDING", "待派单"],
      ["ASSIGNED", "待接单"],
      ["ACCEPTED", "待上门"],
      ["SERVING", "服务中"],
      ["CONFIRM", "待确认"],
      ["COMPLETED", "已完成"],
    ],
    claims: [
      ["SUBMITTED", "社区申报"],
      ["STREET_APPROVED", "街道初审"],
      ["APPROVED", "民政终审"],
      ["PAID", "拨付"],
    ],
    complaints: [
      ["SUBMITTED", "提交"],
      ["ASSIGNED", "受理"],
      ["REPLIED", "回复"],
      ["CLOSED", "结案"],
    ],
    events: [
      ["UNCONFIRMED", "核实"],
      ["OPEN", "响应"],
      ["RESPONDED", "处置"],
      ["CLOSED", "结案"],
    ],
    inspections: row.requires_rectification
      ? [
          ["PENDING", "抽查"],
          ["OPEN", "整改"],
          ["REVIEW", "复核"],
          ["CLOSED", "结案"],
        ]
      : [
          ["PENDING", "抽查"],
          ["CLOSED", "结案"],
        ],
  };
  return flows[kind] || [];
}
