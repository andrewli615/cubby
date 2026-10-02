import { spawnSync } from "node:child_process";
import { runGuardedDeploy, type CommandRunner } from "../lib/deploy-guard.js";

const runner: CommandRunner = {
  read(command, args) {
    const result = spawnSync(command, args, { encoding: "utf8", windowsHide: true,
      env: { ...process.env, AWS_PROFILE: "cubby" } });
    if (result.error || result.status !== 0) throw new Error("Identity or hosting lookup failed.");
    return result.stdout;
  },
  deploy(command, args) {
    const result = spawnSync(command, args, { stdio: "inherit", windowsHide: true,
      env: { ...process.env, AWS_PROFILE: "cubby" } });
    if (result.error) throw new Error("Could not start the CDK deployment process.");
    return result.status ?? 1;
  },
};

try {
  process.exitCode = runGuardedDeploy(process.argv.slice(2), runner, process.env);
} catch (error) {
  console.error(error instanceof Error ? error.message : "Deployment preflight failed.");
  process.exitCode = 1;
}
