import { describe, it, expect } from "vitest";
import {
  changedValues,
  fieldsFor,
  initialValues,
  parseSamples,
  passwordError,
  payload,
  periodRange,
  validate,
} from "../src/forms/schema";
import {
  appointmentSlot,
  displayValue,
  orderStatusLabel,
} from "../src/model/presentation";
import { choices } from "../src/model/rules";
import { actions, menus } from "../src/model/registry";
import type { User } from "../src/model/types";
const user = (role: User["role"], id = 8): User => ({
  role,
  id,
  name: "测试",
  community: 1,
});
describe("角色与状态操作", () => {
  it("保留六类角色和74种业务操作", () => {
    expect(Object.keys(menus)).toHaveLength(6);
    expect(Object.keys(actions)).toHaveLength(74);
  });
  it("过期的待派单可联系改约或取消", () => {
    const missed = { status: "PENDING", dispatch_window: 2 };
    const picks = (role: User["role"], row = missed) =>
      choices("orders", row, user(role)).map((c) => c.action);
    expect(picks("ADMIN")).toEqual(
      expect.arrayContaining(["order.cancel", "order.reschedule"]),
    );
    expect(picks("ADMIN")).not.toContain("order.assign");
    expect(picks("FAMILY")).not.toContain("order.reschedule");
    expect(picks("ADMIN", { ...missed, dispatch_window: 1 })).toContain(
      "order.assign",
    );
  });
  it("预约时段保留跨日结束日期，过期提示仅用于待派订单", () => {
    const start = { starts_at: "2026-11-18T23:30:00" };
    expect(
      appointmentSlot({ ...start, ends_at: "2026-11-19T00:30:00" }),
    ).toEqual({ date: "2026-11-18", time: "23:30 至 2026-11-19 00:30" });
    expect(
      appointmentSlot({ ...start, ends_at: "2026-11-18T23:55:00" }).time,
    ).toBe("23:30 至 23:55");
    expect(orderStatusLabel({ status: "PENDING", dispatch_window: 2 })).toBe(
      "已过预约时段",
    );
    expect(orderStatusLabel({ status: "COMPLETED", dispatch_window: 2 })).toBe(
      "已完成",
    );
  });
  it("待办列表内的订单已办结时不再显示确认或退回按钮", () => {
    expect(
      choices(
        "waitingConfirmation",
        { status: "COMPLETED" },
        user("FAMILY"),
      ).map((c) => c.action),
    ).not.toContain("order.confirm");
    expect(
      choices(
        "managerDispatchPool",
        { status: "COMPLETED" },
        user("MANAGER"),
      ).map((c) => c.action),
    ).not.toContain("order.release");
  });
  it("家属不能派单，护工不能替他人签到", () => {
    expect(
      choices("orders", { id: 1, status: "PENDING" }, user("FAMILY")).map(
        (c) => c.action,
      ),
    ).not.toContain("order.assign");
    expect(
      choices(
        "orders",
        { id: 1, status: "ACCEPTED", current_worker_account: 9 },
        user("CAREGIVER"),
      ).map((c) => c.action),
    ).not.toContain("order.checkin");
  });
  it("本人的订单可以签到，完成后家属可以评价", () => {
    expect(
      choices(
        "orders",
        { status: "ACCEPTED", current_worker_account: 8 },
        user("CAREGIVER"),
      ).map((c) => c.action),
    ).toContain("order.checkin");
    expect(
      choices("orders", { status: "COMPLETED" }, user("FAMILY")).map(
        (c) => c.action,
      ),
    ).toContain("review.create");
  });
  it("评价不进入投诉办理流程", () =>
    expect(
      choices(
        "complaints",
        { kind: "REVIEW", status: "SUBMITTED" },
        user("ADMIN"),
      ),
    ).toEqual([]));
  it("机构只能回复已交机构的投诉，社区可处理自办投诉", () => {
    expect(
      choices(
        "complaints",
        { kind: "COMPLAINT", status: "ASSIGNED", organization_id: 4 },
        user("ADMIN"),
      ).map((c) => c.action),
    ).not.toContain("complaint.reply");
    expect(
      choices(
        "complaints",
        { kind: "COMPLAINT", status: "ASSIGNED", organization_id: null },
        user("ADMIN"),
      ).map((c) => c.action),
    ).toContain("complaint.reply");
  });
  it("补贴初审、终审、拨付遵守角色与阶段", () => {
    expect(
      choices("claims", { status: "SUBMITTED" }, user("REGULATOR")).map(
        (c) => c.action,
      ),
    ).not.toContain("claim.review");
    expect(
      choices("claims", { status: "STREET_APPROVED" }, user("REGULATOR")).map(
        (c) => c.action,
      ),
    ).toContain("claim.review");
    expect(
      choices("claims", { status: "APPROVED" }, user("REGULATOR")).map(
        (c) => c.action,
      ),
    ).toContain("claim.pay");
  });
  it("应急处置需要本人响应并已到场", () => {
    expect(
      choices(
        "events",
        { status: "RESPONDED", responded_by: 8 },
        user("CAREGIVER"),
      ).map((c) => c.action),
    ).not.toContain("event.close");
    expect(
      choices(
        "events",
        { status: "RESPONDED", responded_by: 8, arrived_at: "2026-11-16" },
        user("CAREGIVER"),
      ).map((c) => c.action),
    ).toContain("event.close");
  });
});
describe("依赖和表单安全", () => {
  it("证书等级保持独立文本，不使用老人能力枚举", () =>
    expect(
      fieldsFor("credential.create", "MANAGER").find((f) => f.key === "level")
        ?.options,
    ).toBeUndefined());
  it("预约依赖从上到下排列", () =>
    expect(fieldsFor("order.create", "ADMIN").map((f) => f.key)).toEqual([
      "elderId",
      "startsAt",
      "itemId",
      "channel",
    ]));
  it("更换老人、时间和订单清空依赖选择", () => {
    expect(
      changedValues("order.create", { elderId: 1, itemId: 2 }, "elderId", 3)
        .itemId,
    ).toBeUndefined();
    expect(
      changedValues("order.create", { itemId: 2 }, "startsAt", "2026-11-16")
        .itemId,
    ).toBeUndefined();
    expect(
      changedValues("order.assign", { shiftId: 1 }, "orderId", 2).shiftId,
    ).toBeUndefined();
  });
  it("评估变化清空服务包和自定项目", () =>
    expect(
      changedValues(
        "plan.revise",
        { bundleId: 1, items: [{ itemId: 1, frequency: 2 }] },
        "assessmentId",
        2,
      ),
    ).toEqual({ assessmentId: 2 }));
  it("政策变化清空周期和订单", () =>
    expect(
      changedValues(
        "claim.submit",
        { period: "2026-11", orderIds: [1] },
        "policyId",
        2,
      ),
    ).toEqual({ policyId: 2 }));
  it("拒绝录用不提交隐藏账号和密码", () => {
    const v = {
      recruitmentId: 1,
      approve: "false",
      reason: "不符合",
      username: "old",
      password: "stale",
    };
    expect(payload("recruitment.review", "MANAGER", v)).toEqual({
      recruitmentId: 1,
      approve: "false",
      reason: "不符合",
    });
    expect(
      fieldsFor("recruitment.review", "MANAGER", v).find(
        (f) => f.key === "password",
      )?.hidden,
    ).toBe(true);
  });
  it("低评分强制整改，高评分无需整改时省略要求", () => {
    expect(
      changedValues(
        "inspection.record",
        { requiresRectification: "false" },
        "score",
        59,
      ).requiresRectification,
    ).toBe("true");
    expect(
      payload("inspection.record", "REGULATOR", {
        score: 90,
        requiresRectification: "false",
        rectification: "旧要求",
      }),
    ).not.toHaveProperty("rectification");
  });
  it("请求未就绪不允许提交关联选择", () =>
    expect(
      validate(
        "order.create",
        "ADMIN",
        {
          elderId: 1,
          startsAt: "2026-11-16T10:00",
          itemId: 1,
          channel: "COMMUNITY",
        },
        { itemId: false },
      ),
    ).toHaveProperty("itemId"));
  it("时间和数值范围校验", () => {
    expect(
      validate("shift.create", "MANAGER", {
        caregiverId: 1,
        startsAt: "2026-11-16T10:00",
        endsAt: "2026-11-16T09:00",
        capacity: 0,
      }),
    ).toMatchObject({
      endsAt: "结束时间必须晚于开始时间",
      capacity: "数值不在允许范围内",
    });
    expect(
      validate("metric.create", "ADMIN", {
        name: "示例",
        low: 100,
        high: 10,
        seconds: 30,
      }),
    ).toHaveProperty("high");
  });
  it("服务项目不可重复，次数为正整数", () => {
    expect(
      validate("bundle.create", "ADMIN", {
        name: "示例",
        level: "SELF",
        items: [
          { itemId: 1, frequency: 1 },
          { itemId: 1, frequency: 2 },
        ],
      }),
    ).toHaveProperty("items");
    expect(
      validate("bundle.create", "ADMIN", {
        name: "示例",
        level: "SELF",
        items: [{ itemId: 1, frequency: 0.5 }],
      }),
    ).toHaveProperty("items");
  });
  it("密码同时校验字符数、UTF8字节和确认值", () => {
    expect(passwordError("123456789")).not.toBe("");
    expect(passwordError("密".repeat(25))).not.toBe("");
    expect(passwordError("Abcdef123456")).toBe("");
    expect(
      validate("password.reset", "ADMIN", {
        username: "f",
        newPassword: "Abcdef123456",
        confirmPassword: "no",
        operatorPassword: "Current-password-example",
        reason: "已核实",
      }),
    ).toHaveProperty("confirmPassword");
  });
  it("季度和年度边界不混用月份格式", () => {
    const q = periodRange("QUARTER", "2026-Q4")!;
    expect(q[0].getMonth()).toBe(9);
    expect(q[1].getFullYear()).toBe(2027);
    expect(periodRange("QUARTER", "2026-11")).toBeNull();
    expect(periodRange("MONTH", "2026-13")).toBeNull();
    expect(periodRange("YEAR", "2026")![1].getFullYear()).toBe(2027);
  });
  it("模拟采样拒绝错误字段和非数组", () => {
    expect(() => parseSamples("{}")).toThrow();
    expect(() => parseSamples('[{"bindingId":1}]')).toThrow();
    expect(
      parseSamples(
        '[{"bindingId":1,"metricId":1,"sampleKey":"test","collectedAt":"2026-11-16T10:00:00","value":75}]',
      ),
    ).toHaveLength(1);
  });
  it("角色下单渠道不同，初始化不带其他业务字段", () => {
    expect(initialValues("order.create", "FAMILY").channel).toBe("FAMILY");
    expect(initialValues("order.create", "ADMIN").channel).toBe("COMMUNITY");
    expect(
      payload("order.accept", "CAREGIVER", {
        orderId: 3,
        elderId: 4,
        unused: "x",
      }),
    ).toEqual({ orderId: 3 });
  });
});

describe("业务中文显示", () => {
  it("机构采纳、上门抽查、驳回分别显示", () => {
    expect(displayValue("status", "ACCEPTED", "organizationReports")).toBe(
      "已采纳",
    );
    expect(displayValue("method", "VISIT")).toBe("上门抽查");
    expect(displayValue("decision", "REJECT")).toBe("驳回");
  });
});
