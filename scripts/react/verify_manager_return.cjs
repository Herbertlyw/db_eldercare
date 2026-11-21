const assert = require("node:assert/strict");
const { start } = require("./browser.cjs");
const { interactions } = require("./actions.cjs");

(async () => {
  const h = await start();
  const ui = interactions(h);
  try {
    const manager = await ui.login("manager1");
    const [order] = (await ui.data("managerDispatchPool")).rows;
    assert.ok(order, "需要本机构已超时未接的种子订单");
    const card = manager
      .locator(".manager-attention .home-row")
      .filter({ hasText: "订单 #" + order.id });
    await card.getByRole("button", { name: "退回调度" }).click();
    await h.quiet(manager);
    assert.ok(
      (await manager.locator("#field-orderId").innerText()).includes(
        order.caregiver_name,
      ),
      "退回表单应选中首页的本机构待办",
    );

    const reason = "已联系护工，本次服务由社区另行安排";
    await ui.fill("reason", reason);
    // 模拟代理返回成功状态但没有业务回执。请求未到达业务服务。
    const endpoint = "**/api/actions/order.release";
    for (const [body, contentType] of [
      ["<html></html>", "text/html"],
      ["", "application/json"],
      ["{}", "application/json"],
    ]) {
      await manager.route(endpoint, (route) =>
        route.fulfill({ status: 200, contentType, body }),
      );
      await manager.locator("#submit-operation").click();
      await manager
        .locator(".arco-modal-simple")
        .getByRole("button", { name: "确认", exact: true })
        .click();
      await manager
        .locator("#result")
        .getByText(/核对记录/)
        .waitFor();
      assert.equal(await manager.locator("#operation-receipt").count(), 0);
      assert.equal(await manager.locator("#field-reason").inputValue(), reason);
      await manager.unroute(endpoint);
    }
    await h.screenshot(manager, "manager-return-unconfirmed");
    assert.equal(
      (await ui.data("managerDispatchPool", "&field=id&value=" + order.id))
        .total,
      1,
    );
    await ui.submit("order.release");
    assert.equal(
      (await ui.data("managerDispatchPool", "&field=id&value=" + order.id))
        .total,
      0,
    );

    const admin = await ui.login("admin1");
    const detail = async () => {
      const result = await h.request(admin, "/details/orders/" + order.id);
      assert.equal(result.status, 200);
      return result.data;
    };
    let result = await detail();
    assert.equal(result["订单"].status, "PENDING");
    const previous = result["派单与服务过程"].find(
      (row) => row.status === "REJECTED" && row.reason === reason,
    );
    assert.ok(previous, "退回原因和原派单应保留");
    await ui.rowAction("orders", order.id, "order.assign");
    const candidates = await h.request(admin, "/actions/order.recommend", {
      orderId: order.id,
    });
    assert.equal(candidates.status, 200);
    const restored = candidates.data.result.find(
      (row) => row.name === order.caregiver_name,
    );
    assert.ok(restored?.available > 0, "释放的班次应可再次选择");
    await ui.submit("order.assign", {
      shiftId: restored.shift_id,
      reason: "已确认服务时间，重新安排",
    });
    result = await detail();
    assert.equal(result["订单"].status, "ASSIGNED");
    assert.ok(
      result["派单与服务过程"].some(
        (row) =>
          row.dispatch_id === previous.dispatch_id && row.status === "REJECTED",
      ),
    );
    assert.ok(
      result["派单与服务过程"].some(
        (row) =>
          row.dispatch_id !== previous.dispatch_id && row.status === "WAITING",
      ),
    );
    assert.deepEqual(h.errors, []);
    console.log(
      "PASS 机构从待办退回订单、异常回执保留输入、社区重新派单及历史保留",
    );
  } finally {
    await h.browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
