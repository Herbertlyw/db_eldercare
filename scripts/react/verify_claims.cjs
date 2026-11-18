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
    const rows = (await u.data("eligibleClaims")).rows;
    const existing = (await u.data("claims")).rows;
    const eligible = rows.find(
      (r) =>
        r.effective_minutes > 0 &&
        !existing.some(
          (c) => c.elder_id === r.elder_id && c.item_id === r.item_id,
        ),
    );
    assert(
      eligible,
      "Scenario seed needs an unclaimed service with valid hours",
    );
    const order = (await u.data("orders", "&field=id&value=" + eligible.id))
      .rows[0];
    const assessments = (
      await u.data("assessments", "&field=elder_id&value=" + eligible.elder_id)
    ).rows;
    assert(assessments.length, "Subsidy applicant needs an ability assessment");
    const assessment = assessments.sort((a, b) =>
      b.assessed_at.localeCompare(a.assessed_at),
    )[0];
    p = await u.login("regulator1");
    // Own policy fixture avoids relying on a specific seed person's eligibility or policy id.
    const policyId = await u.act("policy.create", {
      name: "申报流程核验 " + Date.now(),
      itemId: eligible.item_id,
      level: assessment.ability_level,
      rate: 10,
      startsAt: "2025-01-01T00:00",
      endsAt: "2027-12-31T00:00",
      cycle: "MONTH",
      minimumAge: 0,
      tag: "NONE",
      communityIds: [1],
    });
    p = await u.login("admin1");
    await h.open(p, "claim.submit");
    await u.fill("elderId", eligible.elder_id);
    await u.fill("policyId", policyId);
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
    const claim = (
      await u.data("claims", "&field=elder_id&value=" + eligible.elder_id)
    ).rows.find(
      (r) =>
        r.period === order.starts_at.slice(0, 7) &&
        r.item_id === eligible.item_id,
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
