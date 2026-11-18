const { spawnSync } = require("node:child_process");
const path = require("node:path");
function run(names) {
  for (const name of names) {
    const result = spawnSync(
      process.execPath,
      [path.join(__dirname, name + ".cjs")],
      {
        cwd: path.resolve(__dirname, "../.."),
        env: process.env,
        stdio: "inherit",
      },
    );
    if (result.error) throw result.error;
    if (result.status !== 0) {
      process.exitCode = result.status || 1;
      return;
    }
  }
}
module.exports = { run };
if (require.main === module)
  run([
    "verify_scenarios",
    "verify_workspaces",
    "verify_pages",
    "verify_layout",
    "verify_workforce",
    "verify_designed_records",
    "verify_forms",
    "verify_media",
    "verify_passwords",
    "verify_records",
    "verify_workflows",
    "verify_claims",
  ]);
