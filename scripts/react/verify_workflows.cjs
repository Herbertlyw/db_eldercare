// Real browser writes only against the explicitly isolated MySQL test app.
const { start } = require("./browser.cjs"),
  { interactions } = require("./actions.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path");
(async () => {
  const h = await start(),
    u = interactions(h),
    stamp = Date.now();
  let p;
  const checks = [];
  const login = async (name) => {
      p = await u.login(name);
      console.log("role", name);
    },
    act = async (a, v) => {
      const r = await u.act(a, v);
      console.log(a, r);
      return r;
    },
    rowAct = async (k, id, a, v) => {
      await u.rowAction(k, id, a);
      const r = await u.submit(a, v);
      console.log(a, r);
      return r;
    };
  const row = async (k, id) =>
    (await u.data(k, "&field=id&value=" + id)).rows[0];
  try {
    await login("manager1");
    const cert = await act("credential.create", {
      caregiverId: 1,
      itemId: 1,
      certificateNo: "REACT-" + stamp,
      certificateType: "养老护理员",
      level: "初级",
      validFrom: "2026-11-01T00:00",
      validUntil: "2027-11-01T00:00",
    });
    await act("credential.type", {
      credentialId: cert,
      certificateType: "健康照护师",
    });
    assert.equal(
      (await row("credentials", cert)).certificate_type,
      "健康照护师",
    );
    const recruitment = await act("recruitment.create", {
      name: "陈秋宁",
      phone: "COURSE-PHONE",
    });
    await act("recruitment.review", {
      recruitmentId: recruitment,
      approve: false,
      reason: "课程测试：不予录用",
    });
    const training = await act("training.create", {
      title: "上门照护操作培训 " + stamp,
      startsAt: "2026-11-01T09:00",
      endsAt: "2026-11-01T10:00",
    });
    const record = await act("training.enroll", {
      trainingId: training,
      caregiverId: 1,
    });
    await act("training.result", { recordId: record, score: 85 });
    const report = await act("organization.submit", {
      period: "2026-11",
      summary: "课程测试机构月报",
      evidence: "关联服务与回访记录",
    });
    checks.push("qualification recruitment training report");
    await login("family1");
    const complaint = await act("complaint.create", {
      orderId: 1,
      rating: 2,
      content: "React测试：核实回访记录 " + stamp,
    });
    await login("admin1");
    await act("complaint.assign", {
      complaintId: complaint,
      organizationId: 1,
      reason: "已登记并转机构核实",
    });
    await login("manager1");
    await act("complaint.reply", {
      complaintId: complaint,
      result: "回访记录已补充",
    });
    await login("family1");
    await act("complaint.close", {
      complaintId: complaint,
      reason: "已收到解释并确认处理",
    });
    assert.equal((await row("complaints", complaint)).status, "CLOSED");
    await h.nav(p, "complaints");
    await p.locator("#record-search").fill(String(complaint));
    await h.quiet(p);
    await p.locator('tr[data-id="' + complaint + '"] .detail-link').click();
    await h.quiet(p);
    assert(
      (await p.locator(".detail-dialog").innerText()).includes(
        "已登记并转机构核实",
      ),
    );
    await h.screenshot(p, "complaint-history");
    checks.push("complaint full history");
    await login("regulator1");
    await act("organization.review", {
      reportId: report,
      approve: true,
      reason: "课程模拟资料齐备",
    });
    const inspection = await act("inspection.order", {
      orderId: 1,
      method: "PHONE",
      score: 50,
      issue: "记录不完整",
      rectification: "补齐回访说明",
      requiresRectification: true,
    });
    await login("manager1");
    await act("rectification.submit", {
      inspectionId: inspection,
      measures: "补齐回访说明",
      evidence: "课程模拟核验记录",
    });
    await login("regulator1");
    await act("inspection.close", {
      inspectionId: inspection,
      approve: true,
      result: "整改复核通过",
    });
    await act("notice.publish", { inspectionId: inspection });
    await h.nav(p, "jurisdictionStats");
    await h.screenshot(p, "regulation");
    checks.push("inspection rectification review public notice");
    await login("admin1");
    const metric = await act("metric.create", {
      name: "模拟监测指标 " + stamp,
      low: 10,
      high: 90,
      seconds: 300,
    });
    const device = await act("device.create", {
      serial: "REACT-" + stamp,
      type: "课程模拟采集器",
      location: "演示住址",
    });
    await act("device.bind", { deviceId: device, elderId: 1 });
    await act("device.simulation", {
      deviceId: device,
      enabled: true,
      metricId: metric,
      seconds: 60,
    });
    await act("simulator.run", { metricId: metric });
    await act("device.simulation", {
      deviceId: device,
      enabled: false,
      metricId: metric,
      seconds: 60,
    });
    const event = await act("event.create", {
      elderId: 1,
      level: 1,
      description: "课程测试：老人请求协助",
    });
    await rowAct("events", event, "event.respond");
    await rowAct("events", event, "event.arrive", { note: "已到老人家中" });
    await rowAct("events", event, "event.close", {
      result: "已联系家属，完成处置",
    });
    assert.equal((await row("events", event)).status, "CLOSED");
    checks.push("device sampling emergency response arrival close");
    const old = await row("orders", 2);
    await u.rowAction("elders", 1, "order.create");
    assert(
      (
        await p
          .locator('[data-field="elderId"] .arco-select-view')
          .getAttribute("title")
      ).match(/#1[)）]/),
    );
    const order = await u.submit("order.create", {
      startsAt: old.starts_at.slice(0, 16),
      itemId: 1,
    });
    await u.rowAction("orders", order, "order.assign");
    const rec = await h.request(p, "/actions/order.recommend", {
      orderId: order,
    });
    assert.equal(rec.status, 200);
    assert(rec.data.result.length);
    const shift =
      rec.data.result.find((r) => Number(r.caregiver_id) === 1) ||
      rec.data.result[0];
    await u.submit("order.assign", {
      shiftId: shift.shift_id,
      reason: "课程演示调度",
    });
    const assigned = await row("orders", order);
    assert.equal(Number(assigned.current_worker_account), 3);
    const detail = await h.request(p, "/details/orders/" + order),
      coord = detail.data["订单"];
    await login("caregiver1");
    await rowAct("orders", order, "order.accept");
    await rowAct("orders", order, "order.checkin", {
      location: "老人住址",
      x: coord.position_x,
      y: coord.position_y,
    });
    await rowAct("orders", order, "order.finish", {
      notes: "完成上门照护，已记录服务情况",
      x: coord.position_x,
      y: coord.position_y,
    });
    await login("family1");
    await rowAct("waitingConfirmation", order, "order.confirm", {
      confirmation: "已确认护工完成本次服务",
    });
    await rowAct("orders", order, "review.create", {
      rating: 5,
      content: "按约完成服务",
    });
    assert.equal((await row("orders", order)).status, "COMPLETED");
    checks.push(
      "appointment dispatch accept checkin finish family confirmation review",
    );
    await login("manager1");
    const hours = (await u.data("hoursReview")).rows[0];
    assert(hours);
    await rowAct("hoursReview", hours.id, "hours.review", {
      approve: false,
      reason: "课程秒级演示不计入有效工时",
    });
    await login("street1");
    const claim = (await u.data("claims", "&status=SUBMITTED")).rows[0];
    assert(claim);
    await rowAct("claims", claim.id, "claim.review", {
      approve: true,
      reason: "核对订单与材料，初审通过",
    });
    assert.equal((await row("claims", claim.id)).status, "STREET_APPROVED");
    await login("regulator1");
    await rowAct("claims", claim.id, "claim.review", {
      approve: true,
      reason: "核验初审记录，终审通过",
    });
    await rowAct("claims", claim.id, "claim.pay", {
      reference: "REACT-PAY-" + stamp,
    });
    assert.equal((await row("claims", claim.id)).status, "PAID");
    await h.nav(p, "claims");
    await p.locator("#record-search").fill(String(claim.id));
    await h.quiet(p);
    assert.equal(
      await p
        .locator('tr[data-id="' + claim.id + '"] [data-action="claim.pay"]')
        .count(),
      0,
    );
    await p.locator('tr[data-id="' + claim.id + '"] .detail-link').click();
    await h.quiet(p);
    assert(
      (await p.locator(".detail-dialog").innerText()).includes("核验初审记录"),
    );
    await h.screenshot(p, "claim-history");
    checks.push("hours review two-stage subsidy approval payment no-repeat");
    await login("admin1");
    await h.nav(p, "elders");
    await p.locator("#record-search").fill("查询无匹配记录");
    await h.quiet(p);
    const emptyResults = p.locator(".records-empty");
    await emptyResults.getByText("没有符合条件的记录", { exact: true }).waitFor();
    assert.equal(await p.locator("tr[data-id]").count(), 0);
    await p.locator("#refresh").click();
    await h.quiet(p);
    assert.equal(
      await p.locator("#record-search").inputValue(),
      "查询无匹配记录",
    );
    await emptyResults.getByRole("button", { name: "清除筛选", exact: true }).click();
    await h.quiet(p);
    assert(await p.locator(".detail-link").count());
    const forbidden = await h.request(p, "/actions/claim.pay", {
      claimId: claim.id,
      reference: "NOT-ALLOWED",
    });
    assert.equal(forbidden.status, 403);
    checks.push("search refresh and role boundary");
    await p.locator("#logout").click();
    await p.locator(".public-entry").click();
    await h.quiet(p);
    assert(
      (await p.locator(".public-notices-dialog").innerText()).includes(
        "养老服务质量抽查结果",
      ),
    );
    checks.push("anonymous public notice");
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "workflows.json"),
      JSON.stringify(
        {
          time: new Date().toISOString(),
          database: h.env.database,
          actions: u.actionsDone,
          checks,
          pageErrors: h.errors,
        },
        null,
        2,
      ),
    );
    console.log("PASS", u.actionsDone.length, checks);
  } catch (e) {
    if (p) await h.screenshot(p, "workflow-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
