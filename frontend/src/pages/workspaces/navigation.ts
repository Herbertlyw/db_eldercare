import { menus } from "../../model/registry";
import type { Criteria, Role } from "../../model/types";
export function readLocation(role: Role, hash = location.hash) {
  const [target, params] = hash.replace(/^#/, "").split("?"),
    q = new URLSearchParams(params);
  const kind =
    target === "home" || menus[role].includes(target) ? target : "home";
  const criteria: Criteria = {};
  for (const key of [
    "search",
    "status",
    "from",
    "to",
    "field",
    "value",
  ] as const) {
    const v = q.get(key);
    if (v) criteria[key] = v;
  }
  const value = Number(q.get("page") || 1);
  const page =
    Number.isSafeInteger(value) && value > 0 && value <= 100001 ? value : 1;
  return { kind, criteria, page };
}
export function writeLocation(kind: string, criteria: Criteria, page: number) {
  if (kind === "home") return "#home";
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(criteria))
    if (v !== undefined && v !== "") params.set(k, String(v));
  if (page > 1) params.set("page", String(page));
  return "#" + kind + (params.size ? "?" + params : "");
}

// 常用入口直接可见；剩余查询仍在原业务分组中保留。
export const primaryNavigation: Record<Role, [string, string][]> = {
  FAMILY: [
    ["elders", "老人档案"],
    ["orders", "服务预约"],
    ["selfPay", "费用明细"],
    ["claimProgress", "补贴进度"],
    ["complaints", "评价与反馈"],
    ["notifications", "消息通知"],
  ],
  CAREGIVER: [
    ["orders", "我的订单"],
    ["shifts", "我的排班"],
    ["hours", "服务工时"],
    ["credentials", "职业证书"],
    ["trainingRecords", "培训记录"],
    ["events", "应急处置"],
    ["notifications", "消息通知"],
  ],
  ADMIN: [
    ["elders", "老人档案"],
    ["orders", "服务调度"],
    ["assessments", "能力评估"],
    ["plans", "照护计划"],
    ["events", "应急事件"],
    ["claims", "补贴申报"],
    ["complaints", "评价与投诉"],
  ],
  MANAGER: [
    ["caregivers", "护工档案"],
    ["shifts", "排班管理"],
    ["hoursReview", "工时复核"],
    ["recruitments", "招聘管理"],
    ["trainings", "岗前培训"],
    ["inspections", "质量与整改"],
    ["organizationReports", "机构考核"],
  ],
  STREET: [
    ["claims", "补贴初审"],
    ["policies", "补贴政策"],
    ["jurisdictionStats", "辖区服务"],
    ["publicNotices", "质量公示"],
    ["notifications", "消息通知"],
  ],
  REGULATOR: [
    ["claims", "补贴终审与拨付"],
    ["inspections", "质量抽查与整改"],
    ["inspectionBatches", "抽查批次"],
    ["organizationReports", "机构考核"],
    ["jurisdictionStats", "辖区统计"],
    ["publicNotices", "质量公示"],
    ["notifications", "消息通知"],
  ],
};
