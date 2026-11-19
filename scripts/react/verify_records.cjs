const { start } = require("./browser.cjs"),
  { interactions } = require("./actions.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path");
(async () => {
  const h = await start(),
    u = interactions(h),
    stamp = Date.now(),
    checks = [];
  let p;
  const date = (days) => {
    const d = new Date(Date.now() + days * 86400000);
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
  };
  const act = async (a, v) => {
    const r = await u.act(a, v);
    console.log(a, r);
    return r;
  };
  try {
    p = await u.login("admin2");
    const target = (await h.request(p, "/details/elders/2")).data["档案"];
    p = await u.login("admin1");
    const details = (await h.request(p, "/details/elders/1")).data;
    const home = Object.values(details).find((v) => v && v.position_x != null);
    assert(home);
    const elder = await act("elder.create", {
      name: "沈惠芳",
      identity: "COURSE-REACT-" + stamp,
      birthDate: "1952-04-18",
      address: "邻里小区一号楼",
      x: home.position_x,
      y: home.position_y,
    });
    const account = await act("account.create", {
      username: "reactfamily_" + stamp,
      password: "Course-family-123",
    });
    const guardian = await act("guardian.create", {
      elderId: elder,
      accountId: account,
      relation: "女儿",
      phone: "COURSE-PHONE",
    });
    await act("guardian.update", {
      elderId: elder,
      guardianId: guardian,
      relation: "女儿",
      phone: "COURSE-PHONE-UPDATED",
      active: true,
    });
    await act("elder.tags", { elderId: elder, tag: "ALONE", enabled: true });
    await u.rowAction("elders", elder, "elder.update");
    assert.equal(await p.locator("#operation-title").innerText(), "更新老人住址");
    assert.equal(await p.getByLabel("业务类型", { exact: true }).count(), 0);
    assert.match(await p.locator(".operation-context").innerText(), /沈惠芳/);
    await u.submit("elder.update", {
      address: "邻里小区一号楼202室",
      x: home.position_x,
      y: home.position_y,
    });
    assert.equal(
      (await u.data("elders", "&field=id&value=" + elder)).rows[0].address,
      "邻里小区一号楼202室",
    );
    const assessment = await act("assessment.create", {
      elderId: elder,
      scale: "课程能力评估",
      score: 95,
      level: "SELF",
      validUntil: date(180),
    });
    const bundle = await act("bundle.create", {
      name: "日常生活照护 " + stamp,
      level: "SELF",
      items: [{ itemId: 1, frequency: 2 }],
    });
    let plan = await act("plan.create", {
      elderId: elder,
      assessmentId: assessment,
      bundleId: bundle,
      caregiverId: 1,
      startsAt: date(0.01),
      endsAt: date(3),
    });
    plan = await act("plan.revise", {
      elderId: elder,
      planId: plan,
      assessmentId: assessment,
      bundleId: bundle,
      caregiverId: 1,
      startsAt: date(0.01),
      endsAt: date(4),
      items: [{ itemId: 1, frequency: 3 }],
      reason: "调整每周服务次数",
    });
    await act("plan.state", {
      planId: plan,
      status: "PAUSED",
      reason: "家属暂时陪护",
    });
    await act("plan.state", {
      planId: plan,
      status: "ACTIVE",
      reason: "恢复上门照护",
    });
    const order = await act("order.create", {
      elderId: elder,
      startsAt: date(1),
      itemId: 1,
    });
    await act("order.cancel", { orderId: order, reason: "家属调整安排" });
    assert.equal(
      (await u.data("orders", "&field=id&value=" + order)).rows[0].status,
      "CANCELLED",
    );
    checks.push(
      "elder guardian assessment plan revise pause resume appointment cancel",
    );
    const forbidden = await h.request(p, "/details/elders/2");
    assert.equal(forbidden.status, 403);
    p = await u.login("reactfamily_" + stamp, "Course-family-123");
    const related = (await u.data("elders")).rows;
    assert.deepEqual(
      related.map((r) => r.id),
      [elder],
    );
    assert.equal((await h.request(p, "/details/elders/1")).status, 403);
    checks.push("family linked-elder scope and cross-community access");
    p = await u.login("admin2");
    const details2 = (await h.request(p, "/details/elders/2")).data;
    const home2 = Object.values(details2).find(
      (v) => v && v.position_x != null,
    );
    p = await u.login("admin1");
    await act("elder.transfer", {
      elderId: elder,
      communityId: 2,
      address: "二社区邻里服务站旁",
      x: home2.position_x,
      y: home2.position_y,
    });
    assert.equal(
      (await u.data("elders", "&field=id&value=" + elder)).rows.length,
      0,
    );
    p = await u.login("admin2");
    assert.equal(
      (await u.data("elders", "&field=id&value=" + elder)).rows.length,
      1,
    );
    await act("elder.deactivate", { elderId: elder });
    checks.push("transfer updates visible community and deactivate");
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "records.json"),
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
    console.log("PASS", checks);
  } catch (e) {
    if (p) await h.screenshot(p, "records-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
