const { start } = require("./browser.cjs");
const { interactions } = require("./actions.cjs");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
  const h = await start(),
    u = interactions(h),
    checks = [];
  try {
    for (const [account, kind] of [
      ["admin1", "elders"],
      ["family1", "orders"],
      ["caregiver1", "orders"],
      ["manager1", "caregivers"],
      ["street1", "claims"],
      ["regulator1", "claims"],
    ]) {
      const p = await u.login(account);
      assert.equal(
        await p.locator("#nav .arco-menu-inline-header:visible").count(),
        1,
        "Only More businesses is expanded as a top-level group",
      );
      if (account === "admin1") {
        await p.goto(h.env.base + "/#devices");
        await h.quiet(p);
        assert(
          await p.locator('[data-kind="devices"]').isVisible(),
          "Deep links expand More businesses and the owning group",
        );
      }
      await h.nav(p, kind);
      assert.equal(
        await p.locator("#nav .arco-menu-inline-header:visible").count(),
        1,
        "Primary routes must not open unrelated groups",
      );
      assert(
        (await p.locator(".business-summary").boundingBox()).height < 70,
        "Desktop todo summary stays compact",
      );
      await p.locator("#record-search").fill("不存在的查询对象-页面验证");
      await h.quiet(p);
      assert(
        await p.getByText("没有符合条件的记录", { exact: true }).isVisible(),
      );
      await p
        .locator(".records-empty")
        .getByRole("button", { name: "清除筛选", exact: true })
        .click();
      await h.quiet(p);
      assert.equal(await p.locator("#record-search").inputValue(), "");
      assert(await p.locator(".business-table tbody tr").count());
      if (account === "admin1") {
        await p
          .locator('.business-table [data-action="order.create"]')
          .first()
          .click();
        await h.quiet(p);
        assert(await p.locator(".operation-context").isVisible());
        const origin = await p.locator(".operation-context").innerText();
        const elders = await h.request(p, "/data/elders?size=100");
        const next = elders.data.rows.find(
          (r) => r.status === "ACTIVE" && !origin.includes("#" + r.id + "）"),
        );
        assert(next);
        await u.fill("elderId", next.id);
        await h.quiet(p);
        assert.equal(
          await p.locator(".operation-context").count(),
          0,
          "Changed elder must not retain original context label",
        );
        await h.close(p);
      }
      for (const width of [1600, 1280, 768, 390]) {
        await p.setViewportSize({ width, height: 1000 });
        await h.quiet(p);
        assert(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
          account + " list overflow at " + width,
        );
      }
      const card = p.locator(".record-card").first();
      assert(await card.isVisible());
      assert(
        (await card.locator("[data-action]").count()) <= 2,
        "Only common actions occupy the mobile card",
      );
      if (await p.locator(".record-more").count()) {
        await p.locator(".record-more").first().click();
        await h.quiet(p);
        assert(
          (await p.locator(".detail-actions [data-action]").count()) > 2,
          "Additional actions stay reachable",
        );
      } else {
        await card.locator(".detail-link").click();
        await h.quiet(p);
      }
      assert(await p.locator(".detail-overview").isVisible());
      const tag = p.locator(".detail-overview > .arco-tag");
      if (await tag.count())
        assert(
          (await tag.boundingBox()).width < 140,
          "Status stays a compact label",
        );
      const descriptions = p
        .locator(".detail-section .arco-descriptions")
        .first();
      if (await descriptions.count())
        assert(
          (await descriptions
            .locator("tbody tr")
            .first()
            .locator("th,td")
            .count()) <= 1,
          "Mobile descriptions use one column",
        );
      await p.screenshot({
        path: path.join(h.env.output, "detail-mobile-" + account + ".png"),
        fullPage: false,
      });
      await p.locator(".detail-dialog .arco-modal-close-icon").click();
      await h.screenshot(p, "list-mobile-" + account);
      await p.setViewportSize({ width: 1600, height: 1000 });
      await h.screenshot(p, "list-" + account);
      checks.push({
        account,
        kind,
        widths: [1600, 1280, 768, 390],
        emptyRecovery: true,
        mobileDetail: true,
      });
      console.log(account, "layout passed");
    }
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "layout.json"),
      JSON.stringify(
        { time: new Date().toISOString(), checks, pageErrors: h.errors },
        null,
        2,
      ),
    );
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
