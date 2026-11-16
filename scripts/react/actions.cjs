const assert = require("assert/strict");
function interactions(h) {
  let p;
  const actionsDone = [];
  async function login(name, password, forced = false) {
    if (p) await p.close();
    p = await h.login(name, password, forced);
    return p;
  }
  const field = (name) => p.locator('[data-field="' + name + '"]');
  async function fill(name, value) {
    const box = field(name);
    await box.waitFor();
    if (name === "items") {
      for (const item of value) {
        await box.getByRole("button", { name: "添加服务项目" }).click();
        const row = box.locator(".service-item").last();
        await row.locator(".arco-select").click();
        await p
          .getByRole("option")
          .filter({ hasText: new RegExp("#" + item.itemId + "[）)]") })
          .click();
        await row
          .locator(".arco-input-number input")
          .fill(String(item.frequency));
        await h.quiet(p);
      }
      return;
    }
    if (["orderIds", "communityIds"].includes(name)) {
      for (const id of value) {
        let label = box
          .locator(".arco-checkbox")
          .filter({ hasText: new RegExp("#" + id + "[）)]") });
        if (name === "communityIds") {
          const r = await h.request(
            p,
            "/data/communities?field=id&value=" + id,
          );
          label = box.getByText(r.data.rows[0].name, { exact: true });
        }
        await label.click();
      }
      return;
    }
    const select = box.locator(".arco-select");
    if (await select.count()) {
      await select.click();
      const searchable = await select.evaluate((e) =>
        e.classList.contains("arco-select-show-search"),
      );
      if (searchable) {
        await select.locator("input").fill(String(value));
        await h.quiet(p);
      }
      const action = await p
        .locator("#operation-dialog")
        .getAttribute("data-action");
      let label;
      if (value === true || value === "true")
        label =
          name === "approve"
            ? action === "recruitment.review"
              ? "录用"
              : "通过"
            : "是";
      else if (value === false || value === "false")
        label =
          name === "approve"
            ? action === "recruitment.review"
              ? "不录用"
              : "驳回"
            : "否";
      else if (name === "rating") label = new RegExp("^" + value + " 分");
      else if (name === "level" && action === "event.create")
        label = value + " 级";
      else if (name === "status")
        label = (
          action === "plan.state"
            ? { ACTIVE: "恢复生效", PAUSED: "暂停", ENDED: "终止" }
            : action === "device.status"
              ? { ACTIVE: "正常", OFFLINE: "离线", RETIRED: "停用" }
              : { ACTIVE: "在岗", LEAVE: "请假", LEFT: "离职" }
        )[value];
      else if (name === "method")
        label = value === "PHONE" ? "电话抽查" : "上门抽查";
      else if (name === "level")
        label = { SELF: "自理", PARTIAL: "部分失能", SEVERE: "重度失能" }[
          value
        ];
      else if (name === "unit") label = value === "VISIT" ? "每次" : "每小时";
      else if (name === "cycle")
        label = { MONTH: "月度", QUARTER: "季度", YEAR: "年度" }[value];
      else if (name === "target")
        label = value === "ORDER" ? "已完成订单" : "有已完成服务的护工";
      else if (name === "channel")
        label = {
          COMMUNITY: "社区代下单",
          PHONE: "电话预约",
          FAMILY: "家属下单",
        }[value];
      else if (name === "tag")
        label = {
          ALONE: "独居",
          DISABLED: "失能",
          LOW_INCOME: "低保",
          NONE: "无附加标签",
        }[value];
      else if (name === "username")
        label = new RegExp(
          String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
        );
      else label = new RegExp("#" + value + "(?:[）)]|\\s|$)");
      const option =
        typeof label === "string"
          ? p.getByRole("option", { name: label, exact: true })
          : p.getByRole("option").filter({ hasText: label });
      await option.first().click();
      await h.quiet(p);
      return;
    }
    const picker = box.locator(".arco-picker");
    if (await picker.count()) {
      const input = picker.locator("input");
      let date = String(value).replace("T", " ");
      if (/ \d\d:\d\d$/.test(date)) date += ":00";
      await input.click();
      await input.fill(date);
      const confirm = p
        .locator(".arco-picker-container:visible")
        .getByRole("button", { name: "确定", exact: true });
      if (await confirm.count()) await confirm.last().click();
      else await input.press("Enter");
      await p.locator("#operation-title").click();
      await input.blur();
      await h.quiet(p);
      return;
    }
    const input = box.locator("textarea,input").first();
    await input.fill(
      typeof value === "object" ? JSON.stringify(value) : String(value),
    );
    await input.blur();
    await h.quiet(p);
  }
  async function submit(action, values = {}) {
    for (const [k, v] of Object.entries(values)) await fill(k, v);
    const response = p.waitForResponse(
      (r) => r.url().endsWith("/api/actions/" + action),
      { timeout: 15000 },
    );
    await p.locator("#submit-operation").click();
    if (
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
      ].includes(action)
    )
      await p
        .locator(".arco-modal-simple")
        .getByRole("button", { name: "确认", exact: true })
        .click();
    const r = await response;
    const data = await r.json();
    assert.equal(r.status(), 200, action + " " + JSON.stringify(data));
    await p.locator("#operation-receipt").waitFor();
    assert.equal(
      await p.locator("#submit-operation").count(),
      0,
      "duplicate submit",
    );
    actionsDone.push(action);
    await p
      .locator(".arco-drawer-footer")
      .getByRole("button", { name: "完成", exact: true })
      .click();
    await p.locator("#operation-dialog").waitFor({ state: "hidden" });
    return data.result;
  }
  async function act(action, values = {}) {
    await h.open(p, action);
    return submit(action, values);
  }
  async function data(kind, query = "") {
    const r = await h.request(p, "/data/" + kind + "?size=100" + query);
    assert.equal(r.status, 200, kind);
    return r.data;
  }
  async function rowAction(kind, id, action) {
    await h.nav(p, kind);
    await p.locator("#record-search").fill(String(id));
    await h.quiet(p);
    const row = p.locator('.business-table tr[data-id="' + id + '"]');
    for (let n = 0; n < 30 && !(await row.count()); n++) {
      const next = p.locator(".arco-pagination-item-next");
      if (
        await next.evaluate((e) =>
          e.classList.contains("arco-pagination-item-disabled"),
        )
      )
        break;
      await next.click();
      await h.quiet(p);
    }
    let button = row.locator('[data-action="' + action + '"]');
    if (!(await button.count())) {
      await row.getByRole("button", { name: "详情", exact: true }).click();
      await p.locator(".detail-dialog").waitFor();
      await h.quiet(p);
      button = p.locator('.detail-actions [data-action="' + action + '"]');
    }
    await button.click();
    await h.quiet(p);
  }
  return {
    login,
    fill,
    submit,
    act,
    data,
    rowAction,
    actionsDone,
    get page() {
      return p;
    },
  };
}
module.exports = { interactions };
