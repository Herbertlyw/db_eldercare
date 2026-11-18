const { start } = require("./browser.cjs"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path");
(async () => {
  const h = await start(),
    checks = [];
  try {
    for (const [account, kinds] of [
      ["caregiver1", ["shifts", "trainingRecords", "notifications"]],
      ["manager1", ["shifts", "trainings", "trainingRecords", "hoursReview"]],
      ["family1", ["selfPay", "claimProgress", "notifications"]],
      ["street1", ["publicNotices", "notifications"]],
      ["regulator1", ["publicNotices", "notifications"]],
    ]) {
      const p = await h.login(account);
      for (const kind of kinds) {
        await h.nav(p, kind);
        assert.equal(await p.locator("#page-error").count(), 0);
        const response = await h.request(p, "/data/" + kind + "?size=10");
        if (
          [
            "shifts",
            "trainings",
            "trainingRecords",
            "notifications",
            "publicNotices",
          ].includes(kind) &&
          response.data.rows.length
        )
          assert.equal(
            await p.locator(".designed-records > article").count(),
            response.data.rows.length,
          );
        if (response.data.rows.length) {
          await p.locator(".records .detail-link").first().click();
          await p.locator(".detail-dialog").waitFor();
          await h.quiet(p);
          assert.equal(
            await p.locator(".detail-dialog .arco-alert-error").count(),
            0,
          );
          await p.locator(".detail-dialog .arco-modal-close-icon").click();
        }
        if (
          kind === "trainings" &&
          account === "manager1" &&
          response.data.rows.length
        ) {
          await p
            .locator('.designed-records [data-action="training.enroll"]')
            .first()
            .click();
          await p.locator("#operation-dialog").waitFor();
          assert.equal(
            (await p
              .locator('#operation-dialog [data-field="trainingId"] input')
              .count()) > 0,
            true,
          );
          await h.close(p);
        }
        if (
          kind === "notifications" &&
          (await p.getByText("查看关联事件").count())
        ) {
          const id = response.data.rows.find((r) => r.event_id).event_id;
          await p.getByText("查看关联事件").first().click();
          await h.quiet(p);
          assert.equal(
            new URLSearchParams(new URL(p.url()).hash.split("?")[1]).get(
              "value",
            ),
            String(id),
          );
          assert.equal(await p.locator("#page-error").count(), 0);
          await h.nav(p, kind);
        }
        await h.screenshot(p, account + "-" + kind);
        for (const width of [1280, 768, 390, 320]) {
          await p.setViewportSize({ width, height: 900 });
          await h.quiet(p);
          assert(
            await p.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
            `${account} ${kind} ${width}`,
          );
          if (width === 390)
            await h.screenshot(p, account + "-" + kind + "-mobile");
        }
        await p.setViewportSize({ width: 1600, height: 1000 });
        await p.locator("#record-search").fill("不存在的业务记录_视觉测试");
        await h.quiet(p);
        assert.equal(await p.locator("#total").innerText(), "0");
        assert(await p.locator(".records-empty").count());
        await p.locator(".records-empty").getByText("清除筛选").click();
        await h.quiet(p);
        assert.equal(
          await p.locator("#total").innerText(),
          String(response.data.total),
        );
        checks.push({
          account,
          kind,
          rows: response.data.total,
          widths: [1600, 1280, 768, 390, 320],
          emptyRecovery: true,
        });
      }
      await p.close();
    }
    assert.deepEqual(h.errors, []);
    assert.deepEqual(h.writes, []);
    fs.writeFileSync(
      path.join(h.env.output, "designed-records.json"),
      JSON.stringify(
        { checks, errors: h.errors, businessWrites: h.writes },
        null,
        2,
      ),
    );
    console.log(
      "Designed record views, actions, event links, filters and responsive checks passed.",
    );
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
