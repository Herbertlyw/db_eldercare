const fs = require("fs"),
  path = require("path"),
  { chromium } = require("../../frontend/node_modules/playwright");
const registry = require("../../frontend/src/model/registry.json");
function environment() {
  const port = Number(process.env.TEST_PORT || 18087),
    database = process.env.TEST_DATABASE || "";
  if (
    port < 18081 ||
    port > 18089 ||
    !/^eldercare_[a-z0-9_]+_test$/.test(database)
  )
    throw Error("Explicit isolated MySQL test environment required");
  return {
    port,
    database,
    base: "http://127.0.0.1:" + port,
    output: process.env.EVIDENCE_DIR || "evidence/react",
  };
}
async function start() {
  const env = environment();
  fs.mkdirSync(env.output, { recursive: true });
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const errors = [],
    writes = [];
  const password =
    process.env.DEMO_PASSWORD ||
    JSON.parse(fs.readFileSync(".local/config.json")).demo;
  async function page() {
    const p = await browser.newPage({
      viewport: { width: 1600, height: 1000 },
    });
    p.setDefaultTimeout(15000);
    p.on("pageerror", (e) => errors.push(e.message));
    p.on("request", (r) => {
      if (r.method() === "POST" && r.url().includes("/api/actions/"))
        writes.push(new URL(r.url()).pathname);
    });
    return p;
  }
  async function quiet(p) {
    await p.waitForTimeout(350);
    await p.waitForLoadState("networkidle");
    await p
      .locator(".arco-spin-loading")
      .first()
      .waitFor({ state: "hidden", timeout: 10000 })
      .catch(() => {});
  }
  async function login(account, customPassword = password, forced = false) {
    const p = await page();
    await p.goto(env.base);
    await p.locator("#username").fill(account);
    await p.locator("#password").fill(customPassword);
    await p.locator(".login-submit").click();
    await p.locator(forced ? ".password-dialog" : "#workspace").waitFor();
    await quiet(p);
    return p;
  }
  async function nav(p, kind) {
    const node = p.locator('[data-kind="' + kind + '"]');
    if (!(await node.isVisible())) {
      const group = registry.groups.find(([, items]) =>
        items.includes(kind),
      )[0];
      const header = p
        .locator("#nav .arco-menu-inline-header")
        .filter({ hasText: group });
      if (!(await header.isVisible())) {
        await p
          .locator("#nav .arco-menu-inline-header")
          .filter({ hasText: "更多业务" })
          .click();
      }
      await header.click();
    }
    await node.click();
    await quiet(p);
    if ((await p.locator("#title").innerText()) !== registry.names[kind])
      throw Error("Navigation failed " + kind);
  }
  async function open(p, action) {
    if (!(await p.locator("#operation-dialog").count())) {
      if (!(await p.locator("#open-operation").count())) {
        const me = await request(p, "/me");
        await nav(
          p,
          {
            ADMIN: "elders",
            FAMILY: "orders",
            CAREGIVER: "orders",
            MANAGER: "caregivers",
            STREET: "claims",
            REGULATOR: "claims",
          }[me.data.role],
        );
      }
      await p.locator("#open-operation").click();
    }
    const dialog = p.locator("#operation-dialog");
    if ((await dialog.getAttribute("data-action")) === action) {
      await quiet(p);
      return;
    }
    if (!(await p.locator("#action").count())) {
      // 家属的预约抽屉专办预约，其他事项从对应页面进入。
      await close(p);
      const kind =
        action.startsWith("complaint.") || action === "review.create"
          ? "complaints"
          : action === "event.create"
            ? "events"
            : "waitingConfirmation";
      await nav(p, kind);
      await p.locator("#open-operation").click();
    }
    await p.locator("#action").click();
    await p.locator("#action input").fill(registry.actions[action][0]);
    await p
      .getByRole("option", { name: registry.actions[action][0], exact: true })
      .click();
    await p
      .locator('#operation-dialog[data-action="' + action + '"]')
      .waitFor();
    await quiet(p);
  }
  async function close(p) {
    await p.locator(".operation-drawer .arco-drawer-close-icon").click();
    const modal = p.locator(".arco-modal-simple");
    if (
      await modal.waitFor({ state: "visible", timeout: 500 }).then(
        () => true,
        () => false,
      )
    )
      await modal.getByRole("button", { name: "确认", exact: true }).click();
    await p.locator("#operation-dialog").waitFor({ state: "hidden" });
  }
  async function request(p, url, body) {
    return p.evaluate(
      async ({ url, body }) => {
        const r = await fetch("/api" + url, {
          method: body ? "POST" : "GET",
          headers: {
            "Content-Type": "application/json",
            Authorization: "Bearer " + sessionStorage.getItem("elder_token"),
          },
          body: body ? JSON.stringify(body) : undefined,
        });
        return { status: r.status, data: await r.json() };
      },
      { url, body },
    );
  }
  async function screenshot(p, name) {
    await p.waitForTimeout(300);
    await p.screenshot({
      path: path.join(env.output, name + ".png"),
      fullPage: true,
    });
  }
  return {
    env,
    browser,
    errors,
    writes,
    password,
    page,
    login,
    nav,
    open,
    close,
    quiet,
    request,
    screenshot,
    registry,
  };
}
module.exports = { start, registry };
