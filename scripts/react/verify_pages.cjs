// Opens every role route and command against the isolated app without business writes.
const { start } = require("./browser.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path");
(async () => {
  const h = await start(),
    roles = [],
    forms = [],
    responses = [];
  try {
    for (const [account, role] of [
      ["admin1", "ADMIN"],
      ["admin2", "ADMIN"],
      ["family1", "FAMILY"],
      ["caregiver1", "CAREGIVER"],
      ["manager1", "MANAGER"],
      ["street1", "STREET"],
      ["regulator1", "REGULATOR"],
    ]) {
      const p = await h.login(account);
      p.on("response", (r) => {
        if (r.url().includes("/api/") && r.status() >= 400)
          responses.push({
            account,
            url: new URL(r.url()).pathname,
            status: r.status(),
          });
      });
      await p.route("**/api/actions/**", (route) =>
        route.request().url().endsWith("/order.recommend")
          ? route.continue()
          : route.abort(),
      );
      assert.equal(await p.locator(".role-home").getAttribute("data-role"), role);
      assert.equal(await p.locator("#nav [data-kind=home]").count(), 1);
      await h.screenshot(p, "workspace-" + account);
      let detailCount = 0;
      for (const kind of h.registry.menus[role]) {
        await h.nav(p, kind);
        assert.equal(
          await p.locator("#page-error").count(),
          0,
          account + " " + kind,
        );
        assert(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
          account + " " + kind + " overflow",
        );
        const detail = p.locator(".business-table .detail-link, .certificate-card .detail-link, .designed-records .detail-link").first();
        if (await detail.count()) {
          await detail.click();
          await p.locator(".detail-dialog").waitFor();
          await h.quiet(p);
          assert.equal(
            await p.locator(".detail-dialog .arco-alert-error").count(),
            0,
            "Detail " + account + " " + kind,
          );
          await p.locator(".detail-dialog .arco-modal-close-icon").click();
          detailCount++;
        }
      }
      await h.nav(p, h.registry.menus[role][0]);
      await p.locator(".task-card").first().click();
      await h.quiet(p);
      assert.equal(
        await p.locator("#title").innerText(),
        h.registry.names[h.registry.tasks[role][0][1]],
      );
      await h.nav(p, h.registry.menus[role][0]);
      if (account !== "admin2")
        for (const [action, def] of Object.entries(h.registry.actions).filter(
          ([, def]) => def[1].includes(role),
        )) {
          await h.open(p, action);
          assert.equal(
            await p.locator("#result").count(),
            0,
            action + " initial error",
          );
          assert.equal(await p.locator("#operation-title").innerText(), def[0]);
          assert.equal(await p.getByLabel("业务类型", { exact: true }).count(), 0);
          const expected = def[2]
            .map((s) => s.split(":")[0])
            .filter(
              (key) =>
                !(role === "FAMILY" && action === "order.create" && key === "channel"),
            );
          const present = await p
            .locator("#fields [data-field]")
            .evaluateAll((es) => es.map((e) => e.dataset.field));
          assert.deepEqual(
            present.slice().sort(),
            expected.slice().sort(),
            action + " fields missing",
          );
          await p.setViewportSize({ width: 390, height: 844 });
          assert(
            await p.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
            action + " mobile page overflow",
          );
          assert(
            await p
              .locator(".operation-drawer")
              .evaluate((e) => e.scrollWidth <= e.clientWidth + 1),
            action + " mobile drawer overflow",
          );
          await p.setViewportSize({ width: 1600, height: 1000 });
          forms.push({ account, action, fields: present });
        }
      if (await p.locator("#operation-dialog").count()) await h.close(p);
      for (const width of [1280, 1024, 768, 760, 390, 320]) {
        await p.setViewportSize({ width, height: 844 });
        assert(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
          account + " " + width + " overflow",
        );
      }
      await p.setViewportSize({ width: 390, height: 844 });
      await h.screenshot(p, "mobile-" + account);
      await p.locator("#toggle-nav").click();
      const next = h.registry.menus[role][1];
      await h.nav(p, next);
      assert(
        !(await p
          .locator(".sidebar")
          .evaluate((e) => e.classList.contains("nav-open"))),
      );
      roles.push({
        account,
        role,
        routes: h.registry.menus[role].length,
        details: detailCount,
      });
      await p.close();
      console.log(account, "routes/details/forms checked");
    }
    assert.deepEqual(h.errors, []);
    assert.deepEqual(responses, []);
    assert(h.writes.every((p) => p.endsWith("/order.recommend")));
    const result = {
      time: new Date().toISOString(),
      database: h.env.database,
      roles,
      forms,
      pageErrors: h.errors,
      failedResponses: responses,
      businessWrites: false,
    };
    fs.writeFileSync(
      path.join(h.env.output, "pages.json"),
      JSON.stringify(result, null, 2),
    );
    console.log(
      JSON.stringify({
        roles: roles.length,
        routes: roles.reduce((s, r) => s + r.routes, 0),
        forms: forms.length,
        errors: 0,
      }),
    );
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
