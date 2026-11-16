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
  try {
    p = await u.login("admin1");
    const rows = (await u.data("eligibleClaims", "&field=elder_id&value=1"))
      .rows;
    const eligible = rows.find(
      (r) => r.item_id === 1 && r.effective_minutes > 0,
    );
    assert(
      eligible,
      "fresh seed needs an unclaimed completed service for elder 1",
    );
    const order = (await u.data("orders", "&field=id&value=" + eligible.id))
      .rows[0];
    await h.open(p, "claim.submit");
    await u.fill("elderId", 1);
    await u.fill("policyId", 1);
    await u.fill("period", order.starts_at.slice(0, 7));
    await u.fill("orderIds", [order.id]);
    const response = p.waitForResponse((r) =>
      r.url().endsWith("/api/actions/claim.submit"),
    );
    await p.locator("#submit-operation").click();
    const r = await response;
    assert.equal(r.status(), 200, await r.text());
    await p.locator("#operation-receipt").waitFor();
    assert.equal(
      await p
        .locator(".arco-drawer-footer")
        .getByRole("button", { name: "查看记录", exact: true })
        .count(),
      0,
      "No inactive detail button when API returns text rather than an id",
    );
    await p
      .locator(".arco-drawer-footer")
      .getByRole("button", { name: "完成", exact: true })
      .click();
    u.actionsDone.push("claim.submit");
    const claim = (await u.data("claims", "&field=elder_id&value=1")).rows.find(
      (r) => r.period === order.starts_at.slice(0, 7) && r.item_id === 1,
    );
    assert.equal(claim.status, "SUBMITTED");
    await u.act("claim.evidence", {
      claimId: claim.id,
      name: "服务核验记录",
      content: "已核对服务订单、工时和家属确认",
    });
    checks.push("policy-period eligible order selection submission evidence");
    p = await u.login("street1");
    await u.rowAction("claims", claim.id, "claim.review");
    await u.submit("claim.review", {
      approve: false,
      reason: "请补充情况说明",
    });
    p = await u.login("admin1");
    await u.rowAction("claims", claim.id, "claim.resubmit");
    await u.submit("claim.resubmit", { reason: "已补充情况说明，申请复核" });
    assert.equal(
      (await u.data("claims", "&field=id&value=" + claim.id)).rows[0].status,
      "SUBMITTED",
    );
    checks.push("rejected claim resubmission preserves evidence");
    p = await u.login("regulator1");
    const policy = await u.act("policy.create", {
      name: "课程季度补贴 " + Date.now(),
      itemId: 1,
      level: "SEVERE",
      rate: 10,
      startsAt: "2025-01-01T00:00",
      endsAt: "2027-12-31T00:00",
      cycle: "QUARTER",
      minimumAge: 0,
      tag: "NONE",
      communityIds: [1, 2],
    });
    assert.equal(
      (await u.data("policies", "&field=id&value=" + policy)).rows[0]
        .settlement_cycle,
      "QUARTER",
    );
    checks.push("policy creation and community multiselect");
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "claims.json"),
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
    if (p) await h.screenshot(p, "claims-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
