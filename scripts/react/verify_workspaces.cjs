// Role homes use real scoped queries; all writes stay on the explicitly isolated test app.
const { start } = require("./browser.cjs"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path");
(async () => {
  const h = await start(),
    checks = [];
  try {
    for (const [account, role, title] of [
      ["admin1", "ADMIN", "社区工作台"],
      ["family1", "FAMILY", "老人照护"],
      ["caregiver1", "CAREGIVER", "今日服务"],
      ["manager1", "MANAGER", "机构工作台"],
      ["street1", "STREET", "补贴审核工作台"],
      ["regulator1", "REGULATOR", "辖区监管"],
    ]) {
      const p = await h.login(account);
      await h.quiet(p);
      assert.equal(
        await p.locator(".role-home").getAttribute("data-role"),
        role,
      );
      assert.equal(await p.locator("#title").innerText(), title);
      assert.equal(
        await p.locator(".role-home .arco-alert-error").count(),
        0,
        account + " query error",
      );
      assert.equal(
        await p.locator(".role-home .home-loading").count(),
        0,
        account + " still loading",
      );
      const profile = await h.request(p, "/data/workspaceProfile?size=1");
      assert.equal(profile.status, 200);
      if (role === "FAMILY") {
        const care = await h.request(p, "/data/workspaceFamily?size=1");
        assert.equal(care.status, 200);
        assert(
          (await p.locator(".elder-overview").innerText()).includes(
            care.data.rows[0].name,
          ),
        );
        await p
          .getByRole("button", { name: "预约上门服务", exact: true })
          .click();
        await h.quiet(p);
        assert.equal(
          await p.locator("#operation-dialog").getAttribute("data-action"),
          "order.create",
        );
        assert(
          (await p.locator("#field-elderId").innerText()).includes(
            care.data.rows[0].name,
          ),
        );
        await h.close(p);
        await p
          .getByRole("button", { name: "评价与服务反馈", exact: true })
          .click();
        await h.quiet(p);
        assert.equal(
          await p.locator("#page-error").count(),
          0,
          "feedback link lost elder scope",
        );
        await p.locator("[data-kind=home]").click();
        await h.quiet(p);
        const elders = await h.request(p, "/data/workspaceFamily?size=20");
        if (elders.data.rows.length > 1) {
          const second = elders.data.rows[1];
          await p.locator(".elder-picker .arco-select").click();
          await p
            .getByRole("option", { name: second.name, exact: true })
            .click();
          await h.quiet(p);
          assert(
            (await p.locator(".elder-overview").innerText()).includes(
              second.name,
            ),
          );
          await p
            .getByRole("button", { name: "费用明细", exact: true })
            .click();
          await h.quiet(p);
          assert(p.url().includes("field=elder_id"));
          await p.locator("[data-kind=home]").click();
          await h.quiet(p);
          assert(
            (await p.locator(".elder-overview").innerText()).includes(
              second.name,
            ),
          );
          await p.reload();
          await h.quiet(p);
          assert(
            (await p.locator(".elder-overview").innerText()).includes(
              second.name,
            ),
          );
          await p.locator(".elder-picker .arco-select").click();
          await p
            .getByRole("option", {
              name: elders.data.rows[0].name,
              exact: true,
            })
            .click();
          await h.quiet(p);
        }
      }
      if (role === "CAREGIVER") {
        await p.getByText("待接单", { exact: true }).last().click();
        await h.quiet(p);
        const tasks = await h.request(
          p,
          "/data/workspaceSchedule?status=ASSIGNED&size=5",
        );
        assert.equal(
          await p.locator(".visit-row").count(),
          tasks.data.rows.length,
        );
        await p.getByRole("button", { name: "回到今天", exact: true }).click();
        await h.quiet(p);
        assert(await p.getByRole("button", { name: "回到今天", exact: true }).isDisabled());
        const date = String(profile.data.rows[0].today).slice(0, 10);
        const todayTasks = await h.request(p, `/data/workspaceSchedule?from=${date}&to=${date}&size=5`);
        assert.equal(await p.locator(".visit-row").count(), todayTasks.data.rows.length);
        const dateInput = p.locator(".caregiver-schedule .arco-picker input");
        await dateInput.fill("2026-11-02");
        await dateInput.press("Enter");
        await h.quiet(p);
        assert.equal(await dateInput.inputValue(), "2026-11-02");
        const selectedTasks = await h.request(p, "/data/workspaceSchedule?from=2026-11-02&to=2026-11-02&size=5");
        assert.equal(await p.locator(".visit-row").count(), selectedTasks.data.rows.length);
        await p.getByRole("button", { name: "回到今天", exact: true }).click();
        await h.quiet(p);
        assert.equal(await dateInput.inputValue(), date);
        const certificates = await h.request(p, "/data/credentials?size=2");
        if (certificates.data.rows.length) {
          await p.locator(".credential-thumb").first().click();
          await h.quiet(p);
          assert(p.url().includes("field=id"));
          assert(p.url().includes("value=" + certificates.data.rows[0].id));
          assert.equal(await p.locator("#page-error").count(), 0);
          await p.locator("[data-kind=home]").click();
          await h.quiet(p);
        }
      }
      if (role === "MANAGER") {
        const date = String(profile.data.rows[0].today).slice(0, 10);
        const roster = await h.request(p, `/data/workspaceShifts?from=${date}&to=${date}&size=5`);
        assert.equal((await p.locator(".roster-heading strong").innerText()).replace(/\s/g, ""), `${roster.data.total}个班次`);
        const recruitmentPanel = p.locator(".home-panel").filter({ hasText: "招聘与培训" });
        const rosterBounds = await p.locator(".manager-roster").boundingBox();
        const recruitmentBounds = await recruitmentPanel.boundingBox();
        assert(recruitmentBounds.y >= rosterBounds.y + rosterBounds.height);
        assert(recruitmentBounds.y - rosterBounds.y - rosterBounds.height < 40, "staff development separated by empty space");
        await p.getByRole("button", { name: "安排排班", exact: true }).click();
        await h.quiet(p);
        assert.equal(await p.locator("#operation-dialog").getAttribute("data-action"), "shift.create");
        await h.close(p);
      }
      if (role === "REGULATOR") {
        const all = await h.request(p, "/data/jurisdictionSummary?size=1");
        async function checkRates(row) {
          const cards = p.locator(".rate-copy");
          for (const [index, key] of ["coverage_rate", "response_rate"].entries()) {
            assert.equal(await cards.nth(index).locator("strong").innerText(), row[key] == null ? "—" : row[key] + "%");
          }
        }
        await checkRates(all.data.rows[0]);
        const communities = await h.request(p, "/data/communities?size=30");
        assert(communities.data.rows.length > 0);
        const community = communities.data.rows[0];
        await p.locator(".regulator-scope .arco-select").click();
        await p.getByRole("option", { name: community.name, exact: true }).click();
        await h.quiet(p);
        const selected = await h.request(p, `/data/jurisdictionStats?field=id&value=${community.id}&size=1`);
        await checkRates(selected.data.rows[0]);
        assert.equal(await p.locator(".scope-stat strong").innerText(), "1");
        await p.locator(".regulator-coverage").getByRole("button", { name: /查看全部/ }).click();
        await h.quiet(p);
        assert(p.url().includes("field=id") && p.url().includes("value=" + community.id));
        await p.locator("[data-kind=home]").click();
        await h.quiet(p);
        await checkRates(all.data.rows[0]);
      }
      if (role === "STREET") {
        const claims = await h.request(
          p,
          "/data/claims?status=SUBMITTED&size=5",
        );
        assert.equal(
          await p.locator(".claim-selection button").count(),
          claims.data.rows.length,
        );
        if (claims.data.rows.length) {
          await p.locator(".claim-evidence summary").click();
          assert(await p.locator(".claim-preview").innerText());
          await p.locator("#refresh").click();
          await h.quiet(p);
          assert(
            await p.locator(".claim-evidence").evaluate((e) => e.open),
            "refresh collapsed evidence",
          );
          await p
            .getByRole("button", { name: "填写审核意见", exact: true })
            .click();
          await h.quiet(p);
          assert.equal(
            await p.locator("#operation-dialog").getAttribute("data-action"),
            "claim.review",
          );
          await h.close(p);
          await p.locator(".claim-evidence summary").click();
        }
      }
      await p.evaluate(() => window.scrollTo(0, 0));
      await h.screenshot(p, "home-" + account);
      for (const width of [1600, 1280, 768, 390]) {
        await p.setViewportSize({ width, height: 1000 });
        assert(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
          account + " overflow " + width,
        );
      }
      await h.screenshot(p, "home-mobile-" + account);
      await p.setViewportSize({ width: 1600, height: 1000 });
      await h.nav(p, h.registry.menus[role][0]);
      await p.locator("[data-kind=home]").click();
      await h.quiet(p);
      assert.equal(await p.locator("#title").innerText(), title);
      await p.reload();
      await h.quiet(p);
      assert.equal(await p.locator("#title").innerText(), title);
      // Failure must have an explicit retry state, never a fabricated zero.
      const endpoint =
        role === "FAMILY"
          ? "workspaceFamily"
          : role === "CAREGIVER"
            ? "workspaceSchedule"
            : role === "STREET"
              ? "claims"
              : role === "REGULATOR"
                ? "jurisdictionSummary"
                : role === "MANAGER"
                  ? "workspaceShifts"
                  : "workspaceSchedule";
      await p.route("**/api/data/" + endpoint + "?**", (route) =>
        route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({ error: "课程验收网络故障" }),
        }),
      );
      await p.locator("#refresh").click();
      await h.quiet(p);
      assert(
        (await p.locator(".role-home .arco-alert-error").count()) > 0,
        account + " missing retry",
      );
      await p.unroute("**/api/data/" + endpoint + "?**");
      await p
        .locator(".role-home .arco-alert-error")
        .first()
        .getByRole("button", { name: "重试" })
        .click();
      await h.quiet(p);
      assert.equal(await p.locator(".role-home .arco-alert-error").count(), 0);
      checks.push({
        account,
        role,
        title,
        widths: [1600, 1280, 768, 390],
        reload: true,
        retry: true,
      });
      await p.close();
      console.log(account, "home checked");
    }
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "workspaces.json"),
      JSON.stringify({ checks, pageErrors: h.errors }, null, 2),
    );
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
