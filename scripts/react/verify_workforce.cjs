// Read-only UI checks against the explicitly selected test database.
const { start } = require("./browser.cjs"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path");
(async () => {
  const h = await start(),
    checks = [],
    widths = process.env.DESKTOP_ONLY ? [1280] : [1280, 768, 390, 320];
  try {
    for (const account of ["caregiver1", "manager1", "admin1"]) {
      const p = await h.login(account);
      await h.nav(p, "hours");
      const result = await h.request(p, "/data/hours?size=100");
      assert.equal(result.status, 200);
      const rows = [...result.data.rows];
      for (let page = 1; rows.length < result.data.total; page++) {
        const r = await h.request(p, "/data/hours?size=100&page=" + page);
        assert(r.data.rows.length);
        rows.push(...r.data.rows);
      }
      const minutes = rows
        .filter((r) => r.status === "VALID")
        .reduce((n, r) => n + Number(r.effective_minutes), 0);
      await p.waitForFunction(
        () =>
          document.querySelector('[data-metric="duration"]')?.textContent !==
          "—",
      );
      assert.equal(
        await p.locator(".hours-metric.primary small").innerText(),
        minutes + " 分钟",
      );
      assert.equal(
        Number(
          (await p.locator('[data-metric="valid"]').innerText()).replace(
            /\D/g,
            "",
          ),
        ),
        rows.filter((r) => r.status === "VALID").length,
      );
      await h.screenshot(p, "hours-" + account);
      // Hash navigation exercises status and date criteria independently of pagination.
      await p.evaluate(() => (location.hash = "#hours?status=INVALID"));
      await h.quiet(p);
      assert.equal(
        await p.locator(".hours-metric.primary small").innerText(),
        "0 分钟",
      );
      await p.evaluate(
        () => (location.hash = "#hours?from=2000-01-01&to=2000-01-02"),
      );
      await h.quiet(p);
      assert.equal(
        await p.locator(".hours-metric.primary small").innerText(),
        "0 分钟",
      );
      if (account === "admin1") {
        for (const width of widths) {
          await p.setViewportSize({ width, height: 900 });
          await p.evaluate(() => (location.hash = "#hours"));
          await h.quiet(p);
          assert(
            await p.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
          );
        }
        checks.push({ account, minutes, widths: [1600, ...widths] });
        await p.close();
        continue;
      }
      await h.nav(p, "credentials");
      const certificates = await h.request(p, "/data/credentials?size=10");
      assert.equal(
        await p.locator(".certificate-card").count(),
        certificates.data.rows.length,
      );
      assert(certificates.data.rows.length > 0);
      await p.locator(".certificate-image").first().click();
      await p.locator(".certificate-preview").waitFor();
      const url = await p
        .locator(".certificate-preview img")
        .getAttribute("src");
      assert(
        decodeURIComponent(url).includes(
          certificates.data.rows[0].certificate_no,
        ),
      );
      const downloadWait = p.waitForEvent("download");
      await p.locator(".certificate-preview").getByText("下载证书图片").click();
      const download = await downloadWait;
      assert(download.suggestedFilename().endsWith(".svg"));
      await p.locator(".certificate-preview .arco-modal-close-icon").click();
      await p.locator(".certificate-card .detail-link").first().click();
      await p.locator(".detail-dialog").waitFor();
      await h.quiet(p);
      assert.equal(
        await p.locator(".detail-dialog .arco-alert-error").count(),
        0,
      );
      await p.locator(".detail-dialog .arco-modal-close-icon").click();
      await h.screenshot(p, "certificates-" + account);
      await p
        .locator("#nav .arco-menu-inline-header")
        .filter({ hasText: "更多业务" })
        .click();
      // Every long group label must end before its separate disclosure arrow.
      const fit = await p
        .locator(".nav-group-label:visible")
        .evaluateAll((es) =>
          es.map((e) => {
            const a = e
              .closest(".arco-menu-inline-header")
              .querySelector(".arco-menu-icon-suffix");
            return {
              text: e.textContent,
              end: e.getBoundingClientRect().right,
              arrow: a.getBoundingClientRect().left,
            };
          }),
        );
      for (const item of fit)
        assert(item.end + 3 < item.arrow, JSON.stringify(item));
      await h.screenshot(p, "navigation-" + account);
      for (const width of widths) {
        await p.setViewportSize({ width, height: 900 });
        for (const kind of ["hours", "credentials"]) {
          await p.evaluate((k) => (location.hash = "#" + k), kind);
          await h.quiet(p);
          assert(
            await p.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
            `${account} ${kind} ${width} overflow`,
          );
          if (width === 390) await h.screenshot(p, kind + "-mobile-" + account);
        }
      }
      checks.push({
        account,
        minutes,
        credentials: certificates.data.total,
        download: true,
        widths: [1600, ...widths],
      });
      await p.close();
    }
    // Deterministic response fixture proves totals include pages beyond the first 100.
    const p = await h.login("caregiver1"),
      seen = [];
    let fail = true;
    const fixture = Array.from({ length: 105 }, (_, i) => ({
      id: i + 1,
      caregiver_name: "工时分页验证",
      status: "VALID",
      effective_minutes: 1,
      check_in: "2026-11-17T10:00:00",
      check_out: "2026-11-17T10:01:00",
    }));
    await p.route("**/api/data/hours?**", (r) => {
      const q = new URL(r.request().url()).searchParams,
        size = Number(q.get("size") || 10),
        page = Number(q.get("page") || 0);
      seen.push({ page, size });
      if (fail) return r.fulfill({ status: 500, json: { error: "验证重试" } });
      return r.fulfill({
        json: {
          rows: fixture.slice(page * size, (page + 1) * size),
          total: 105,
          summary: { minutes: 105, valid: 105, pending: 0, invalid: 0 },
          dateField: "check_in",
          states: ["VALID"],
        },
      });
    });
    await h.nav(p, "hours");
    assert(await p.locator("#page-error").getByText("验证重试").count());
    fail = false;
    await p
      .locator("#page-error")
      .getByRole("button", { name: "重试", exact: true })
      .click();
    await h.quiet(p);
    assert.equal(
      await p.locator(".hours-metric.primary small").innerText(),
      "105 分钟",
    );
    assert(seen.every((q) => q.page === 0 && q.size === 10));
    await p.locator(".arco-pagination-item").filter({ hasText: /^2$/ }).click();
    await h.quiet(p);
    assert.equal(
      await p.locator(".hours-metric.primary small").innerText(),
      "105 分钟",
    );
    assert.equal(
      await p.locator('[data-metric="duration"]').innerText(),
      "1 小时 45 分钟",
    );
    assert(seen.some((q) => q.page === 1 && q.size === 10));
    assert(seen.every((q) => q.size === 10));
    await p.close();
    assert.deepEqual(h.errors, []);
    assert.deepEqual(h.writes, []);
    fs.writeFileSync(
      path.join(h.env.output, "workforce.json"),
      JSON.stringify(
        {
          checks,
          paginationFixture: { records: 105, minutes: 105, retry: true },
          errors: h.errors,
          businessWrites: h.writes,
        },
        null,
        2,
      ),
    );
    console.log(
      "Three role hours, certificates, downloads, navigation and responsive checks passed; 105-row pagination and retry passed.",
    );
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
