const { start } = require("./browser.cjs"),
  { interactions } = require("./actions.cjs"),
  assert = require("assert/strict"),
  fs = require("fs"),
  path = require("path");
(async () => {
  const h = await start(),
    u = interactions(h),
    account = "reactpwd_" + Date.now(),
    initial = "Course-initial-123",
    temporary = "Course-temporary-456",
    changed = "Course-changed-789",
    final = "Course-final-12345";
  let p, old;
  const checks = [];
  async function enter(a, b, c) {
    await p.locator("#old-password").fill(a);
    await p.locator("#new-password").fill(b);
    await p.locator("#confirm-password").fill(c);
    await p.locator("#save-password").click();
  }
  try {
    p = await h.page();
    await p.goto(h.env.base);
    await p.locator("#forgot-password").click();
    assert(
      (await p.locator(".recovery-list").innerText()).includes("机构负责人"),
    );
    await p.close();
    p = await u.login("admin1");
    await u.act("account.create", { username: account, password: initial });
    old = await h.login(account, initial);
    const oldToken = await old.evaluate(() =>
      sessionStorage.getItem("elder_token"),
    );
    await u.act("password.reset", {
      username: account,
      newPassword: temporary,
      confirmPassword: temporary,
      operatorPassword: h.password,
      reason: "课程测试：已核实家属身份",
    });
    const expired = await old.evaluate(
      async (t) =>
        (await fetch("/api/me", { headers: { Authorization: "Bearer " + t } }))
          .status,
      oldToken,
    );
    assert.equal(expired, 401);
    await old.close();
    checks.push("admin reset and old token revoked");
    p = await u.login(account, temporary, true);
    await p.keyboard.press("Escape");
    assert(await p.locator(".password-dialog").isVisible());
    assert.equal((await h.request(p, "/data/elders")).status, 403);
    await p.reload();
    await p.locator(".password-dialog").waitFor();
    await p.setViewportSize({ width: 390, height: 844 });
    assert(
      await p.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await h.screenshot(p, "forced-password-mobile");
    await p.setViewportSize({ width: 1600, height: 1000 });
    await enter(temporary, changed, "mismatch");
    assert((await p.locator("#password-error").innerText()).includes("不一致"));
    await enter(temporary, changed, changed);
    await p.locator(".password-dialog").waitFor({ state: "hidden" });
    assert.equal(await p.locator("#password").inputValue(), "");
    checks.push(
      "forced password change persists across reload and cannot dismiss",
    );
    p = await u.login(account, changed);
    await p.locator("#change-password").click();
    await enter("wrong-current", final, final);
    await p.locator("#password-error").waitFor();
    assert(await p.locator(".password-dialog").isVisible());
    await enter(changed, final, final);
    await p.locator(".password-dialog").waitFor({ state: "hidden" });
    p = await u.login(account, final);
    assert(await p.locator("#workspace").isVisible());
    checks.push("own password change validates current password and relogin");
    assert.deepEqual(h.errors, []);
    fs.writeFileSync(
      path.join(h.env.output, "passwords.json"),
      JSON.stringify(
        {
          time: new Date().toISOString(),
          database: h.env.database,
          checks,
          pageErrors: h.errors,
        },
        null,
        2,
      ),
    );
    console.log("PASS password flows");
  } catch (e) {
    if (p) await h.screenshot(p, "password-failed");
    throw e;
  } finally {
    await h.browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
