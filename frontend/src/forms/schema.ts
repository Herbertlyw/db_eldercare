import { actions } from "../model/registry";
import type { Role, Values } from "../model/types";
export interface Field {
  key: string;
  label: string;
  type: string;
  required: boolean;
  hidden: boolean;
  options?: { label: string; value: string | number; disabled?: boolean }[];
}
export const sources: Record<string, string> = {
  elderId: "elders",
  assessmentId: "assessments",
  planId: "plans",
  bundleId: "bundles",
  caregiverId: "caregivers",
  orderId: "orders",
  itemId: "catalog",
  shiftId: "shifts",
  accountId: "accounts",
  credentialId: "credentials",
  attendanceId: "hours",
  deviceId: "devices",
  eventId: "events",
  claimId: "claims",
  complaintId: "complaints",
  policyId: "policies",
  inspectionId: "inspections",
  recruitmentId: "recruitments",
  trainingId: "trainings",
  recordId: "trainingRecords",
  reportId: "organizationReports",
  noticeId: "publicNotices",
  communityId: "communities",
  organizationId: "organizationQuality",
  metricId: "metrics",
};
export const sourceFor = (key: string, action: string, role: Role) =>
  key === "username" && action === "password.reset"
    ? role === "ADMIN"
      ? "accounts"
      : "caregivers"
    : sources[key];
const options = (pairs: (string | number)[][]) =>
  pairs.map(([value, label]) => ({ label: String(label), value }));
const yes = options([
  ["true", "是"],
  ["false", "否"],
]);
const levels = options([
  ["SELF", "自理"],
  ["PARTIAL", "部分失能"],
  ["SEVERE", "重度失能"],
]);
const areas = new Set([
  "notes",
  "reason",
  "content",
  "result",
  "rectification",
  "description",
  "summary",
  "evidence",
  "measures",
  "issue",
  "confirmation",
  "note",
]);
export function fieldsFor(action: string, role: Role, v: Values = {}): Field[] {
  let fields = (actions[action]?.[2] || []).map((s) => {
    const [key, caption, type = "text", optional] = s.split(":");
    let label = caption.replace(
      /（SELF\/PARTIAL\/SEVERE）|（ACTIVE\/LEAVE\/LEFT）|（PHONE\/VISIT）|（true\/false）/g,
      "",
    );
    if (sourceFor(key, action, role) || key === "guardianId")
      label = label.replace(/编号/g, "");
    const field: Field = {
      key,
      label,
      type: areas.has(key) ? "textarea" : type,
      required: optional !== "optional",
      hidden: false,
    };
    if (
      [
        "active",
        "enabled",
        "subsidizable",
        "requiresRectification",
        "fenceOk",
      ].includes(key)
    )
      field.options = yes;
    if (key === "approve")
      field.options = options([
        ["true", action === "recruitment.review" ? "录用" : "通过"],
        ["false", action === "recruitment.review" ? "不录用" : "驳回"],
      ]);
    if (
      key === "level" &&
      [
        "event.create",
        "assessment.create",
        "bundle.create",
        "policy.create",
      ].includes(action)
    )
      field.options =
        action === "event.create"
          ? options([
              [1, "1 级"],
              [2, "2 级"],
              [3, "3 级"],
            ])
          : levels;
    if (key === "tag")
      field.options = options([
        ["ALONE", "独居"],
        ["DISABLED", "失能"],
        ["LOW_INCOME", "低保"],
        ...(action === "policy.create" ? [["NONE", "无附加标签"]] : []),
      ]);
    if (key === "unit")
      field.options = options([
        ["VISIT", "每次"],
        ["HOUR", "每小时"],
      ]);
    if (key === "cycle")
      field.options = options([
        ["MONTH", "月度"],
        ["QUARTER", "季度"],
        ["YEAR", "年度"],
      ]);
    if (key === "channel" && role === "FAMILY") field.hidden = true;
    if (key === "channel")
      field.options =
        role === "FAMILY"
          ? options([["FAMILY", "家属下单"]])
          : options([
              ["COMMUNITY", "社区代下单"],
              ["PHONE", "电话预约"],
            ]);
    if (key === "target")
      field.options = options([
        ["ORDER", "已完成订单"],
        ["CAREGIVER", "有已完成服务的护工"],
      ]);
    if (key === "method")
      field.options = options([
        ["PHONE", "电话抽查"],
        ["VISIT", "上门抽查"],
      ]);
    if (key === "status")
      field.options = options(
        action === "plan.state"
          ? [
              ["PAUSED", "暂停"],
              ["ACTIVE", "恢复生效"],
              ["ENDED", "终止"],
            ]
          : action === "device.status"
            ? [
                ["ACTIVE", "正常"],
                ["OFFLINE", "离线"],
                ["RETIRED", "停用"],
              ]
            : [
                ["ACTIVE", "在岗"],
                ["LEAVE", "请假"],
                ["LEFT", "离职"],
              ],
      );
    if (key === "rating") {
      field.label = "服务评分";
      field.options = options([
        [1, "1 分 · 很不满意"],
        [2, "2 分 · 不满意"],
        [3, "3 分 · 一般"],
        [4, "4 分 · 满意"],
        [5, "5 分 · 很满意"],
      ]);
    }
    if (
      action === "recruitment.review" &&
      ["username", "password"].includes(key)
    ) {
      field.hidden = v.approve === "false";
      field.required = !field.hidden;
    }
    if (key === "rectification") {
      field.hidden =
        v.requiresRectification === "false" &&
        !(v.score !== "" && v.score != null && Number(v.score) < 60);
      field.required = !field.hidden;
      field.label = "整改要求";
    }
    if (key === "password") field.label = "初始密码（至少 10 个字符）";
    if (key === "items") field.label = "服务项目与每周次数";
    if (key === "orderIds") field.label = "申报订单";
    if (key === "communityIds") field.label = "适用社区";
    if (key === "period" && action === "organization.submit") {
      field.label = "考核月份";
      field.type = "month";
    }
    return field;
  });
  const order =
    action === "plan.revise"
      ? [
          "elderId",
          "planId",
          "assessmentId",
          "bundleId",
          "caregiverId",
          "startsAt",
          "endsAt",
        ]
      : [];
  if (order.length)
    fields = [
      ...order.map((k) => fields.find((f) => f.key === k)!),
      ...fields.filter((f) => !order.includes(f.key)),
    ];
  if (fields.some((f) => f.key === "requiresRectification")) {
    const rect = fields.find((f) => f.key === "rectification")!;
    fields = fields.filter((f) => f !== rect);
    fields.splice(
      fields.findIndex((f) => f.key === "requiresRectification") + 1,
      0,
      rect,
    );
  }
  return fields;
}
export function initialValues(
  action: string,
  role: Role,
  preset: Values = {},
): Values {
  const result: Values = {};
  for (const f of fieldsFor(action, role)) {
    if (
      f.options &&
      !["rating"].includes(f.key) &&
      !(action === "event.create" && f.key === "level")
    )
      result[f.key] = f.options[0].value;
  }
  return { ...result, ...preset };
}
export function changedValues(
  action: string,
  previous: Values,
  key: string,
  value: any,
): Values {
  const next = { ...previous, [key]: value };
  const clear = (...keys: string[]) => keys.forEach((k) => delete next[k]);
  if (key === "elderId") {
    clear(
      "assessmentId",
      "planId",
      "bundleId",
      "items",
      "guardianId",
      "orderIds",
    );
    if (action === "order.create") clear("itemId");
    if (action === "guardian.update") clear("relation", "phone", "active");
  }
  if (key === "assessmentId") clear("bundleId", "items");
  if (key === "bundleId") clear("items");
  if (key === "orderId") clear("shiftId");
  if (key === "startsAt" && action === "order.create") clear("itemId");
  if (key === "policyId") clear("period", "orderIds");
  if (key === "period") clear("orderIds");
  if (key === "approve" && value === "false" && action === "recruitment.review")
    clear("username", "password");
  if (
    key === "score" &&
    value != null &&
    value !== "" &&
    Number(value) < 60 &&
    actions[action][2].some((s) => s.startsWith("requiresRectification:"))
  )
    next.requiresRectification = "true";
  if (key === "requiresRectification" && value === "false")
    clear("rectification");
  return next;
}
export const passwordError = (s: string) =>
  [...s].length < 10 || new TextEncoder().encode(s).length > 72
    ? "密码至少 10 个字符，且不超过 72 字节"
    : "";
export function parseSamples(value: string) {
  const rows = JSON.parse(value);
  if (!Array.isArray(rows) || !rows.length || rows.length > 500)
    throw Error("采样记录应为 1 至 500 条");
  for (const r of rows)
    if (
      !r ||
      !Number.isInteger(r.bindingId) ||
      r.bindingId <= 0 ||
      !Number.isInteger(r.metricId) ||
      r.metricId <= 0 ||
      typeof r.sampleKey !== "string" ||
      !r.sampleKey.trim() ||
      typeof r.collectedAt !== "string" ||
      Number.isNaN(new Date(r.collectedAt).getTime()) ||
      typeof r.value !== "number" ||
      !Number.isFinite(r.value)
    )
      throw Error("请检查绑定编号、指标、采样编号、时间和数值");
  return rows;
}
export const numericBounds = (
  key: string,
  action: string,
): [number | undefined, number | undefined, number] =>
  (
    ({
      capacity: [1, undefined, 1],
      duration: [1, undefined, 1],
      price: [0, undefined, 0.01],
      rate: [0, undefined, 0.01],
      minimumAge: [0, 120, 1],
      score: [0, 100, 1],
      seconds:
        action === "device.simulation" ? [30, 3600, 1] : [1, undefined, 1],
      ratio: [0, 1, 0.01],
    }) as Record<string, [number | undefined, number | undefined, number]>
  )[key] || [undefined, undefined, 1];
export function periodRange(cycle: string, value: string): [Date, Date] | null {
  let month = 1;
  let year = 0;
  if (cycle === "MONTH" && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)) {
    year = +value.slice(0, 4);
    month = +value.slice(5);
  } else if (cycle === "QUARTER" && /^\d{4}-Q[1-4]$/.test(value)) {
    year = +value.slice(0, 4);
    month = (+value.slice(-1) - 1) * 3 + 1;
  } else if (cycle === "YEAR" && /^\d{4}$/.test(value)) year = +value;
  else return null;
  const start = new Date(year, month - 1, 1),
    end = new Date(start);
  end.setMonth(
    end.getMonth() + ({ MONTH: 1, QUARTER: 3, YEAR: 12 }[cycle] || 0),
  );
  return [start, end];
}
export function validate(
  action: string,
  role: Role,
  v: Values,
  ready: Record<string, boolean> = {},
  cycle?: string,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const f of fieldsFor(action, role, v)) {
    if (f.hidden) continue;
    const value = v[f.key],
      empty =
        value == null ||
        value === "" ||
        (Array.isArray(value) && !value.length);
    if (f.required && empty) {
      errors[f.key] = "请填写" + f.label;
      continue;
    }
    if (empty) continue;
    if (ready[f.key] === false) {
      errors[f.key] = "选项尚未就绪，请完成前置选择或重试";
      continue;
    }
    if (f.options && !f.options.some((o) => String(o.value) === String(value)))
      errors[f.key] = "请选择有效选项";
    if (f.type === "number") {
      const number = Number(value),
        [min, max, step] = numericBounds(f.key, action);
      if (
        !Number.isFinite(number) ||
        (min != null && number < min) ||
        (max != null && number > max)
      )
        errors[f.key] = "数值不在允许范围内";
      if (
        [
          "capacity",
          "duration",
          "minimumAge",
          "score",
          "seconds",
          "rating",
          "level",
        ].includes(f.key) &&
        step === 1 &&
        !Number.isInteger(number)
      )
        errors[f.key] = "请输入整数";
      if (f.key === "ratio" && number <= 0)
        errors[f.key] = "抽查比例必须大于 0";
      if (/Id$/.test(f.key) && (!Number.isInteger(number) || number <= 0))
        errors[f.key] = "请选择有效记录";
    }
    if (f.key === "password" || f.key === "newPassword") {
      const error = passwordError(String(value));
      if (error) errors[f.key] = error;
    }
    if (f.key === "items") {
      if (
        !Array.isArray(value) ||
        value.some(
          (r: any) =>
            !Number.isInteger(r.itemId) ||
            r.itemId <= 0 ||
            !Number.isInteger(r.frequency) ||
            r.frequency < 1,
        )
      )
        errors.items = "请选择项目并填写正整数次数";
      else if (new Set(value.map((r: any) => r.itemId)).size !== value.length)
        errors.items = "不能重复添加服务项目";
    }
    if (f.key === "samples") {
      try {
        parseSamples(value);
      } catch (e) {
        errors.samples =
          e instanceof SyntaxError ? "JSON 格式不正确" : (e as Error).message;
      }
    }
    if (
      ["orderIds", "communityIds"].includes(f.key) &&
      (!Array.isArray(value) ||
        !value.length ||
        value.some((id: any) => !Number.isInteger(id) || id <= 0))
    )
      errors[f.key] = "请至少选择一项";
    if (
      ["date", "datetime-local", "month"].includes(f.type) &&
      Number.isNaN(new Date(value).getTime())
    )
      errors[f.key] = "日期格式不正确";
  }
  for (const [start, end] of [
    ["startsAt", "endsAt"],
    ["validFrom", "validUntil"],
    ["low", "high"],
  ])
    if (
      v[start] != null &&
      v[end] != null &&
      v[start] !== "" &&
      v[end] !== "" &&
      (start === "low"
        ? Number(v[end]) <= Number(v[start])
        : new Date(v[end]) <= new Date(v[start]))
    )
      errors[end] =
        start === "low" ? "上限必须大于下限" : "结束时间必须晚于开始时间";
  if (action === "claim.submit" && !periodRange(cycle || "", v.period || ""))
    errors.period = "请按政策填写有效结算周期";
  if (action === "password.reset" && v.newPassword !== v.confirmPassword)
    errors.confirmPassword = "两次输入的临时密码不一致";
  return errors;
}
export function payload(action: string, role: Role, v: Values): Values {
  const body: Values = {};
  for (const f of fieldsFor(action, role, v)) {
    if (f.hidden) continue;
    const value = v[f.key];
    if (
      value == null ||
      value === "" ||
      (!f.required && Array.isArray(value) && !value.length)
    )
      continue;
    body[f.key] =
      f.key === "samples"
        ? parseSamples(value)
        : f.type === "number"
          ? Number(value)
          : value;
  }
  return body;
}
export const consequences: Record<string, string> = {
  "assessment.create":
    "复评登记后，旧评估对应的照护计划会暂停。请先处理未完成订单，再按最新评估调整计划。",
  "elder.transfer":
    "迁移前请处理未完成订单，并解绑原社区设备。迁移后重新安排照护计划和设备，历史记录保留。",
};
export const needsConfirmation = (action: string) =>
  [
    "order.cancel",
    "order.reject",
    "order.release",
    "elder.deactivate",
    "elder.transfer",
    "shift.delete",
    "claim.pay",
    "notice.withdraw",
    "device.unbind",
  ].includes(action);
export const createdKinds: Record<string, string> = {
  "elder.create": "elders",
  "order.create": "orders",
  "assessment.create": "assessments",
  "plan.create": "plans",
  "event.create": "events",
  "complaint.create": "complaints",
  "review.create": "complaints",
  "recruitment.create": "recruitments",
  "training.create": "trainings",
  "shift.create": "shifts",
  "device.create": "devices",
};
