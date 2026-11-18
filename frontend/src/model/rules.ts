import { actions } from "./registry";
import type { Row, User, Choice, Values } from "./types";
const aliases: Record<string, string> = {
  dispatchPool: "orders",
  waitingConfirmation: "orders",
  managerDispatchPool: "orders",
  activePlans: "plans",
  claimProgress: "claims",
  pendingEvents: "events",
  currentDevices: "devices",
  rectifications: "inspections",
  hoursReview: "hours",
};
export const entity = (k: string) => aliases[k] || k;
export function choices(k: string, r: Row, user: User) {
  const result: Choice[] = [],
    role = user.role,
    state = r.status;
  const add = (
    action: string,
    label: string,
    when = true,
    preset: Values = {},
  ) => {
    if (when && actions[action]?.[1].includes(role))
      result.push({ action, label: label || actions[action][0], preset });
  };
  switch (entity(k)) {
    case "elders":
      add("order.create", "预约服务", state === "ACTIVE");
      add("assessment.create", "登记评估", state === "ACTIVE");
      add("event.create", "上报求助", state === "ACTIVE");
      add("elder.update", "编辑住址", state === "ACTIVE");
      add("guardian.create", "关联家属", state === "ACTIVE");
      add("guardian.update", "家属联系方式", state === "ACTIVE");
      break;
    case "assessments":
      add("plan.create", "制定计划", new Date(r.valid_until) > new Date());
      break;
    case "plans":
      add("plan.revise", "调整计划");
      add(
        "plan.state",
        state === "ACTIVE" ? "暂停计划" : "恢复计划",
        ["ACTIVE", "PAUSED"].includes(state),
        { status: state === "ACTIVE" ? "PAUSED" : "ACTIVE" },
      );
      break;
    case "orders": {
      const owns =
        role !== "CAREGIVER" ||
        Number(r.current_worker_account) === Number(user.id);
      const missed = Number(r.dispatch_window) === 2;
      add("order.reschedule", "联系后改约", missed && state === "PENDING");
      add(
        "order.assign",
        state === "PENDING" ? "派单" : "改派",
        !missed && ["PENDING", "ASSIGNED", "ACCEPTED"].includes(state),
      );
      add("order.accept", "接单", state === "ASSIGNED" && owns);
      add("order.checkin", "签到", state === "ACCEPTED" && owns);
      add("order.finish", "签退并记录服务", state === "SERVING" && owns);
      add(
        "order.confirm",
        "确认服务",
        state === "CONFIRM" || (k === "waitingConfirmation" && state == null),
      );
      add("review.create", "评价", state === "COMPLETED");
      add("complaint.create", "投诉");
      add(
        "order.reject",
        "退回调度",
        ["ASSIGNED", "ACCEPTED"].includes(state) && owns,
      );
      add(
        "order.release",
        "退回调度",
        k === "managerDispatchPool" && (state == null || state === "ASSIGNED"),
      );
      add(
        "order.cancel",
        "取消预约",
        ["PENDING", "ASSIGNED", "ACCEPTED"].includes(state),
      );
      break;
    }
    case "caregivers":
      add("shift.create", "安排班次", state === "ACTIVE");
      add("credential.create", "登记证书", state !== "LEFT");
      add("caregiver.status", "在岗状态");
      add("password.reset", "重置密码", !!r.username);
      break;
    case "accounts":
      add("password.reset", "重置密码", r.active === true || r.active === 1);
      break;
    case "credentials":
      add("credential.type", "证书类型");
      break;
    case "shifts":
      add(
        "shift.delete",
        "删除班次",
        Number(r.used_count) === 0 && new Date(r.starts_at) > new Date(),
      );
      break;
    case "hours":
      add("hours.review", "复核工时", state === "REVIEW");
      break;
    case "complaints":
      if (r.kind !== "COMPLAINT") break;
      add("complaint.assign", "受理", state === "SUBMITTED");
      add(
        "complaint.reply",
        "回复处理",
        state === "ASSIGNED" &&
          (role === "MANAGER" || r.organization_id == null),
      );
      add("complaint.close", "确认结案", state === "REPLIED");
      break;
    case "recruitments":
      add("recruitment.review", "审核应聘", state === "APPLIED");
      break;
    case "trainings":
      add("training.enroll", "登记参训");
      break;
    case "trainingRecords":
      add("training.result", "登记成绩", r.result === "PENDING");
      break;
    case "devices":
      add(
        "device.bind",
        r.binding_id ? "更换绑定" : "绑定老人",
        state === "ACTIVE",
      );
      add("device.unbind", "解除绑定", !!r.binding_id);
      add("device.status", "设备状态");
      break;
    case "events": {
      const responder =
        role === "ADMIN" || Number(r.responded_by) === Number(user.id);
      add("event.confirm", "核实告警", state === "UNCONFIRMED");
      add("event.dismiss", "标记误报", state === "UNCONFIRMED");
      add("event.respond", "响应", state === "OPEN");
      add(
        "event.arrive",
        "登记到场",
        state === "RESPONDED" && !r.arrived_at && responder,
      );
      add(
        "event.close",
        "完成处置",
        state === "RESPONDED" && !!r.arrived_at && responder,
      );
      break;
    }
    case "claims":
      add(
        "claim.review",
        role === "STREET" ? "初审" : "终审",
        (role === "STREET" && state === "SUBMITTED") ||
          (role === "REGULATOR" && state === "STREET_APPROVED"),
      );
      add("claim.pay", "登记拨付", state === "APPROVED");
      add(
        "claim.evidence",
        "补充材料",
        ["SUBMITTED", "REJECTED", "DRAFT"].includes(state),
      );
      add("claim.resubmit", "重新申报", state === "REJECTED");
      break;
    case "inspections":
      add("inspection.record", "登记抽查", state === "PENDING");
      add("rectification.submit", "提交整改", state === "OPEN");
      add("inspection.close", "复核整改", state === "REVIEW");
      add("notice.publish", "发布公示", state === "CLOSED" && r.score != null);
      break;
    case "organizationReports":
      add("organization.review", "审核材料", state === "SUBMITTED");
      break;
    case "publicNotices":
      add("notice.withdraw", "撤回公示");
      break;
  }
  return result;
}
const ids: Record<string, string> = {
  elders: "elderId",
  assessments: "assessmentId",
  plans: "planId",
  orders: "orderId",
  caregivers: "caregiverId",
  credentials: "credentialId",
  shifts: "shiftId",
  hours: "attendanceId",
  complaints: "complaintId",
  recruitments: "recruitmentId",
  trainings: "trainingId",
  trainingRecords: "recordId",
  devices: "deviceId",
  events: "eventId",
  claims: "claimId",
  inspections: "inspectionId",
  organizationReports: "reportId",
  publicNotices: "noticeId",
  accounts: "accountId",
  bundles: "bundleId",
  policies: "policyId",
  catalog: "itemId",
};
export function preset(k: string, r: Row) {
  const p: Values = {};
  for (const [a, b] of Object.entries({
    elder_id: "elderId",
    caregiver_id: "caregiverId",
    assessment_id: "assessmentId",
    bundle_id: "bundleId",
    order_id: "orderId",
    item_id: "itemId",
    training_id: "trainingId",
  }))
    if (r[a] != null) p[b] = r[a];
  const key = ids[entity(k)];
  if (key) p[key] = r.id;
  if (r.username) p.username = r.username;
  return p;
}

export const primaryKey = (k: string) => ids[entity(k)];
