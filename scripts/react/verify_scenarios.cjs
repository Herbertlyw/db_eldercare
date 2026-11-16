// Run before state-changing workflow checks on the seeded scenario database.
const { start } = require("./browser.cjs"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path");
(async () => {
  const h = await start(),
    totals = {},
    checks = [];
  try {
    for (const username of [
      "admin1",
      "admin2",
      "family1",
      "family2",
      "caregiver1",
      "scene_caregiver1",
      "scene_caregiver2",
      "manager1",
      "street1",
      "regulator1",
    ]) {
      const p = await h.login(username),
        me = await h.request(p, "/me");
      totals[username] = {};
      for (const kind of h.registry.menus[me.data.role]) {
        const r = await h.request(p, "/data/" + kind + "?size=1");
        assert.equal(r.status, 200, username + "/" + kind);
        totals[username][kind] = r.data.total;
        checks.push(username + "/" + kind);
      }
      if (username === "admin1") {
        for (const [kind, states] of [
          [
            "orders",
            [
              "PENDING",
              "ASSIGNED",
              "ACCEPTED",
              "SERVING",
              "CONFIRM",
              "COMPLETED",
              "CANCELLED",
            ],
          ],
          [
            "claims",
            [
              "DRAFT",
              "SUBMITTED",
              "STREET_APPROVED",
              "APPROVED",
              "REJECTED",
              "PAID",
            ],
          ],
        ]) {
          const r = await h.request(p, "/data/" + kind + "?size=100");
          for (const state of states)
            assert(
              r.data.rows.some((row) => row.status === state),
              kind + " lacks " + state,
            );
        }
        for (const [kind, status, label] of [
          ["orders", "PENDING", "待派单"],
          ["complaints", "SUBMITTED", "待受理"],
          ["events", "OPEN", "待响应"],
          ["inspections", "OPEN", "待整改"],
        ]) {
          await h.nav(p, kind);
          await p.locator("#record-state").click();
          await p.getByRole("option", { name: label, exact: true }).click();
          await h.quiet(p);
          assert(
            (await p.locator(".business-table").innerText()).includes(label),
          );
          const r = await h.request(
            p,
            "/data/" + kind + "?size=100&status=" + status,
          );
          assert.equal(r.status, 200);
          await p.locator(".detail-link").first().click();
          await h.quiet(p);
          assert((await p.locator(".detail-content").innerText()).length > 50);
          await p.locator(".detail-dialog .arco-modal-close-icon").click();
        }
      }
      if (username === "family1")
        assert.equal((await h.request(p, "/details/elders/21005")).status, 403);
      if (username === "regulator1")
        assert(
          (await h.request(p, "/data/claims?size=100")).data.rows.some(
            (row) => row.elder_id === 21005,
          ),
        );
      await p.close();
    }
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "scenarios.json"),
      JSON.stringify(
        {
          time: new Date().toISOString(),
          database: h.env.database,
          readOnly: true,
          accounts: Object.keys(totals).length,
          checkedLists: checks.length,
          totals,
          pageErrors: h.errors,
        },
        null,
        2,
      ),
    );
    console.log("PASS scenarios", checks.length);
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
