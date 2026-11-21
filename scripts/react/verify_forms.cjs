const { start } = require("./browser.cjs"),
  { interactions } = require("./actions.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path");
(async () => {
  const h = await start(),
    u = interactions(h),
    checks = [];
  let p;
  const box = (k) => p.locator('[data-field="' + k + '"]');
  const disabled = async (k) =>
    box(k)
      .locator(".arco-select")
      .evaluate((e) => e.classList.contains("arco-select-disabled"));
  async function discard() {
    await h.close(p);
  }
  try {
    p = await u.login("admin1");
    const [initialOrder, selectedOrder] = (
      await u.data("orders", "&status=PENDING")
    ).rows;
    await u.rowAction("orders", initialOrder.id, "order.cancel");
    await u.fill("orderId", selectedOrder.id);
    await u.fill("reason", "已联系家属核对");
    await p.locator("#submit-operation").click();
    const dialog = p.locator(".arco-modal-simple");
    const warning = await dialog.innerText();
    assert(
      warning.includes(`#${selectedOrder.id}）`) &&
        !warning.includes(`#${initialOrder.id}）`),
    );
    assert(warning.includes("名额"));
    await dialog.getByRole("button", { name: "返回", exact: true }).click();
    assert.equal(
      await p.locator("#field-reason").inputValue(),
      "已联系家属核对",
    );
    assert(!h.writes.some((url) => url.endsWith("/order.cancel")));
    await discard();
    checks.push(
      "confirmation names the current order and preserves input on return",
    );

    await h.open(p, "order.create");
    assert.deepEqual(
      await p
        .locator("#fields [data-field]")
        .evaluateAll((e) => e.map((x) => x.dataset.field)),
      ["elderId", "startsAt", "itemId", "channel"],
    );
    assert(await disabled("itemId"));
    await u.fill("elderId", 1);
    assert(await disabled("itemId"));
    const order = (await u.data("orders", "&field=id&value=2")).rows[0];
    await u.fill("startsAt", order.starts_at.slice(0, 16));
    await u.fill("itemId", 1);
    await u.fill("elderId", 1001);
    assert(!(await box("itemId").innerText()).includes("（#1）"));
    await u.fill("startsAt", "2099-01-01T10:00");
    assert(await disabled("itemId"));
    assert((await box("itemId").innerText()).includes("该时段没有可预约"));
    checks.push("appointment top-down dependencies and no-availability");
    await discard();
    await h.open(p, "order.create");
    await u.fill("elderId", 1);
    let fail = true;
    await p.route("**/api/data/plans?**", (route) =>
      fail
        ? route.fulfill({
            status: 503,
            contentType: "application/json",
            body: JSON.stringify({ error: "测试网络故障" }),
          })
        : route.continue(),
    );
    await u.fill("startsAt", order.starts_at.slice(0, 16));
    assert((await box("itemId").innerText()).includes("测试网络故障"));
    assert(await disabled("itemId"));
    fail = false;
    await box("itemId")
      .getByRole("button", { name: "重试", exact: true })
      .click();
    await h.quiet(p);
    assert(!(await disabled("itemId")));
    await p.unroute("**/api/data/plans?**");
    checks.push("linked data error visible and retry recovers");
    await discard();
    await h.open(p, "plan.create");
    assert(await disabled("assessmentId"));
    assert(await disabled("bundleId"));
    await u.fill("elderId", 1);
    await u.fill("assessmentId", 1);
    await u.fill("bundleId", 1);
    await u.fill("elderId", 1001);
    assert(await disabled("bundleId"));
    assert(!(await box("assessmentId").innerText()).includes("（#1）"));
    checks.push("assessment and bundle scoped to selected elder");
    await discard();
    await h.open(p, "guardian.update");
    assert(await disabled("guardianId"));
    await u.fill("elderId", 1);
    await box("guardianId").locator(".arco-select").click();
    await p.getByRole("option").first().click();
    await h.quiet(p);
    assert((await box("relation").locator("input").inputValue()).length > 0);
    await u.fill("elderId", 1001);
    assert.equal(await box("relation").locator("input").inputValue(), "");
    checks.push("guardian details prefill and clear");
    await discard();
    await h.open(p, "bundle.create");
    await u.fill("items", [{ itemId: 1, frequency: 3 }]);
    await box("items").getByRole("button", { name: "添加服务项目" }).click();
    await box("items")
      .locator(".service-item")
      .last()
      .locator(".arco-select")
      .click();
    assert(
      await p
        .getByRole("option")
        .filter({ hasText: /#1[）)]/ })
        .last()
        .evaluate(
          (e) =>
            e.getAttribute("aria-disabled") === "true" ||
            e.className.includes("disabled"),
        ),
    );
    await p.locator("#operation-title").click();
    checks.push("service items prevent duplicates");
    await discard();
    await h.open(p, "vital.upload");
    const samples =
      '[{"bindingId":1,"metricId":1,"sampleKey":"react-form-only","collectedAt":"2026-11-16T10:00:00","value":75}]';
    await box("samples")
      .locator("input[type=file]")
      .setInputFiles({
        name: "samples.json",
        mimeType: "application/json",
        buffer: Buffer.from(samples),
      });
    await box("samples").getByText("1 条采样记录", { exact: true }).waitFor();
    assert.equal(
      JSON.parse(await box("samples").locator("textarea").inputValue()).length,
      1,
    );
    await box("samples")
      .locator("input[type=file]")
      .setInputFiles({
        name: "invalid.json",
        mimeType: "application/json",
        buffer: Buffer.from("{bad"),
      });
    await box("samples")
      .getByText(/已保留原内容/)
      .waitFor();
    assert.equal(
      JSON.parse(await box("samples").locator("textarea").inputValue()).length,
      1,
    );
    checks.push("JSON import validates without erasing existing input");
    await discard();
    await h.open(p, "account.create");
    await u.fill("username", "react_draft");
    await p.locator(".operation-drawer .arco-drawer-close-icon").click();
    await p
      .locator(".arco-modal-simple")
      .getByRole("button", { name: "返回", exact: true })
      .click();
    assert.equal(
      await box("username").locator("input").inputValue(),
      "react_draft",
    );
    assert.equal(await p.getByLabel("业务类型", { exact: true }).count(), 0);
    assert.equal(
      await p.locator("#operation-dialog").getAttribute("data-action"),
      "account.create",
    );
    await u.fill("password", "Course-form-123");
    await p.route("**/api/actions/account.create", (route) =>
      route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ error: "测试提交失败" }),
      }),
    );
    await p.locator("#submit-operation").click();
    await p.locator("#result").waitFor();
    assert.equal(
      await box("username").locator("input").inputValue(),
      "react_draft",
    );
    assert.equal(
      await box("password").locator("input").inputValue(),
      "Course-form-123",
    );
    await p.unroute("**/api/actions/account.create");
    checks.push(
      "focused form keeps its draft after cancelling close or failing submission",
    );
    await discard();
    await h.open(p, "maintenance.run");
    assert.equal(await p.locator("#title").innerText(), "评估到期提醒");
    assert.match(await p.locator("#action-form").innerText(), /超时仍未响应/);
    assert.equal(await p.locator("#submit-operation").innerText(), "开始检查");
    checks.push("manual checks are available on the assessment reminders page");
    await discard();
    p = await u.login("manager1");
    await h.open(p, "recruitment.review");
    await u.fill("username", "unused_user");
    await u.fill("password", "Course-unused-123");
    await u.fill("approve", false);
    assert.equal(await box("password").count(), 0);
    await u.fill("approve", true);
    assert.equal(await box("password").locator("input").inputValue(), "");
    checks.push("conditional recruitment secrets cleared");
    await discard();
    p = await u.login("regulator1");
    await h.open(p, "inspection.order");
    await u.fill("score", 50);
    assert((await box("requiresRectification").innerText()).includes("是"));
    await box("requiresRectification").locator(".arco-select").click();
    assert(
      await p
        .getByRole("option", { name: "否", exact: true })
        .evaluate((e) => e.className.includes("disabled")),
    );
    await p.locator("#operation-title").click();
    await u.fill("score", 90);
    await u.fill("requiresRectification", false);
    assert.equal(await box("rectification").count(), 0);
    checks.push("low inspection score requires rectification");
    await discard();
    await h.open(p, "policy.create");
    const communities = box("communityIds");
    await communities.getByRole("button", { name: "选择当前结果" }).click();
    const text = await communities.locator(".selection-toolbar").innerText();
    await communities.getByRole("textbox").fill("没有此社区");
    assert.equal(
      await communities.locator(".selection-toolbar").innerText(),
      text,
    );
    await communities.getByRole("textbox").fill("");
    assert((await communities.locator(".arco-checkbox-checked").count()) > 1);
    checks.push("community selection survives search");
    await discard();
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "forms.json"),
      JSON.stringify(
        {
          time: new Date().toISOString(),
          database: h.env.database,
          checks,
          pageErrors: h.errors,
        },
        null,
        2,
      ),
    );
    console.log("PASS", checks);
  } catch (e) {
    if (p) await h.screenshot(p, "forms-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
