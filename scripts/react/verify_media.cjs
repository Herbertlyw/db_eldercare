const { start } = require("./browser.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path"),
  crypto = require("crypto");
(async () => {
  const h = await start(),
    checks = [];
  let p;
  try {
    const data = JSON.parse(
        fs.readFileSync("src/main/resources/static/demo-people.json"),
      ),
      people = [...data.elders, ...data.additionalElders],
      avatars = people.map((x) => x.avatar);
    assert.equal(new Set(avatars).size, people.length);
    assert.equal(
      new Set(
        avatars.map((a) =>
          crypto
            .createHash("sha256")
            .update(fs.readFileSync("src/main/resources/static/images/" + a))
            .digest("hex"),
        ),
      ).size,
      people.length,
    );
    checks.push(people.length + " unique portraits");
    p = await h.login("admin1");
    await p.locator("#record-search").fill("周惠琴");
    await h.quiet(p);
    const first = people.find((x) => x.id === 1);
    await p.locator("#record-search").fill(first.name);
    await h.quiet(p);
    const portrait = p.locator('tr[data-id="1"] img');
    assert.equal(await portrait.getAttribute("src"), "/images/" + first.avatar);
    await portrait.evaluate((i) => i.decode());
    const dots = p.locator(".photo-dots button");
    assert.equal(await dots.count(), 4);
    await dots.nth(2).click();
    assert.equal(await dots.nth(2).getAttribute("aria-current"), "true");
    await p.locator("#photo-pause").click();
    assert.equal(
      await p.locator("#photo-pause").getAttribute("aria-pressed"),
      "true",
    );
    await p.locator("#record-search").focus();
    await p.mouse.move(0, 0);
    await p.waitForTimeout(5700);
    assert.equal(await dots.nth(2).getAttribute("aria-current"), "true");
    await p.locator("#photo-pause").click();
    await p.locator("#record-search").focus();
    await p.mouse.move(0, 0);
    await p.waitForTimeout(5900);
    assert.equal(await dots.nth(3).getAttribute("aria-current"), "true");
    checks.push("dots pause resume and autoplay");
    await p.emulateMedia({ reducedMotion: "reduce" });
    await p.waitForFunction(
      () =>
        document.querySelector("#photo-pause").getAttribute("aria-pressed") ===
        "true",
    );
    assert.equal(
      await p.locator("#photo-pause").getAttribute("aria-pressed"),
      "true",
    );
    checks.push("reduced motion pauses carousel");
    await p.route("**/images/**", (route) => route.abort());
    await p.reload();
    await p.locator("#workspace").waitFor();
    await h.quiet(p);
    assert.equal(await p.locator(".photo-dots button:disabled").count(), 4);
    assert(await p.locator(".photo-empty").first().isVisible());
    await p.unroute("**/images/**");
    checks.push("image failure fallback");
    await p.reload();
    await p.locator("#workspace").waitFor();
    await h.quiet(p);
    await p.setViewportSize({ width: 390, height: 844 });
    assert(await p.locator(".record-card").count());
    const card = p.locator(".record-card").first();
    assert(
      (await card.locator("dl").innerText()).includes("住址") ||
        (await card.locator("dl").innerText()).includes("地址"),
    );
    await card.locator(".detail-link").click();
    await h.quiet(p);
    assert(await p.locator(".detail-dialog").isVisible());
    await p.locator(".detail-dialog .arco-modal-close-icon").click();
    await h.screenshot(p, "mobile-cards");
    checks.push("mobile record cards and details");
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "media.json"),
      JSON.stringify(
        { time: new Date().toISOString(), checks, pageErrors: h.errors },
        null,
        2,
      ),
    );
    console.log("PASS", checks);
  } catch (e) {
    if (p) await h.screenshot(p, "media-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
