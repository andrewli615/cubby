import assert from "node:assert/strict";
import { test } from "node:test";
import { parseDeployInput, runGuardedDeploy, type CommandRunner } from "../lib/deploy-guard.js";

const account = "123456789012";
const appId = "d123abc";
const origin = `https://main.${appId}.amplifyapp.com`;
const args = ["--context", "cubby:release=true", "--context", `cubby:account=${account}`,
  "--context", `cubby:webOrigin=${origin}`, "--profile", "cubby"];

function mockRunner(overrides: {
  identity?: unknown;
  app?: unknown;
  branch?: unknown;
} = {}) {
  const reads: Array<{ command: string; args: string[] }> = [];
  const deploys: Array<{ command: string; args: string[] }> = [];
  const runner: CommandRunner = {
    read(command, commandArgs) {
      reads.push({ command, args: commandArgs });
      if (commandArgs[0] === "sts") return JSON.stringify(overrides.identity ?? {
        Account: account, Arn: `arn:aws:sts::${account}:assumed-role/CubbyDeploy/test`,
      });
      if (commandArgs[1] === "get-app") return JSON.stringify({ app: overrides.app ?? {
        appId, defaultDomain: `${appId}.amplifyapp.com`,
        repository: "https://github.com/andrewli615/cubby",
      } });
      return JSON.stringify({ branch: overrides.branch ?? { branchName: "main" } });
    },
    deploy(command, commandArgs) {
      deploys.push({ command, args: commandArgs });
      return 0;
    },
  };
  return { runner, reads, deploys };
}

test("valid deployment inputs use verified AWS reads before one scoped CDK process", () => {
  const { runner, reads, deploys } = mockRunner();
  assert.deepEqual(parseDeployInput(args), { account, webOrigin: origin, appId });
  assert.equal(runGuardedDeploy(args, runner), 0);
  assert.deepEqual(reads.map(({ command, args: commandArgs }) => [command, ...commandArgs.slice(0, 2)]), [
    ["aws", "sts", "get-caller-identity"], ["aws", "amplify", "get-app"],
    ["aws", "amplify", "get-branch"],
  ]);
  for (const read of reads) {
    assert.deepEqual(read.args.slice(-7), ["--profile", "cubby", "--region", "us-west-2",
      "--output", "json", "--no-cli-pager"]);
  }
  assert.equal(deploys.length, 1);
  assert.equal(deploys[0]!.command, process.execPath);
  assert.match(deploys[0]!.args[0]!, /aws-cdk[\\/]bin[\\/]cdk$/);
  assert.deepEqual(deploys[0]!.args.slice(1), [
    "deploy", "Cubby", "--profile", "cubby", "--no-lookups",
      "--context", "cubby:release=true", "--context", `cubby:account=${account}`,
      "--context", `cubby:webOrigin=${origin}`]);
});

test("missing, malformed, or extra inputs stop before any process starts", () => {
  const cases = [
    args.filter((_, index) => index < 2 || index > 3), // missing account
    args.map((value) => value === `cubby:account=${account}` ? "cubby:account=123" : value),
    args.filter((_, index) => index > 1), // missing release
    args.map((value) => value === `cubby:webOrigin=${origin}` ?
      "cubby:webOrigin=https://example.invalid" : value),
    args.map((value) => value === `cubby:webOrigin=${origin}` ?
      "cubby:webOrigin=http://main.d123abc.amplifyapp.com" : value),
    args.map((value) => value === `cubby:webOrigin=${origin}` ?
      "cubby:webOrigin=https://dev.d123abc.amplifyapp.com" : value),
    args.map((value) => value === `cubby:webOrigin=${origin}` ?
      `cubby:webOrigin=${origin}/receipts` : value),
    args.map((value) => value === "cubby" ? "default" : value),
    args.slice(0, -2), // missing profile
    [...args, "--force", "true"],
    [...args, "--context", "cubby:release=true"],
  ];
  for (const invalid of cases) {
    const { runner, reads, deploys } = mockRunner();
    assert.throws(() => runGuardedDeploy(invalid, runner));
    assert.equal(reads.length, 0);
    assert.equal(deploys.length, 0);
  }
  const { runner, reads, deploys } = mockRunner();
  assert.throws(() => runGuardedDeploy(args, runner, { AWS_ACCESS_KEY_ID: "synthetic" }),
    /ambient AWS credentials/);
  assert.equal(reads.length, 0);
  assert.equal(deploys.length, 0);
});

test("failed identity lookup never starts CDK", () => {
  let deployed = false;
  const runner: CommandRunner = {
    read() { throw new Error("synthetic lookup failure"); },
    deploy() { deployed = true; return 0; },
  };
  assert.throws(() => runGuardedDeploy(args, runner), /Could not verify sts/);
  assert.equal(deployed, false);
});

test("root or wrong-account identity and unverified Amplify app or branch never start CDK", () => {
  const invalid = [
    { identity: { Account: account, Arn: `arn:aws:iam::${account}:root` } },
    { identity: { Account: "999999999999", Arn: "arn:aws:sts::999999999999:assumed-role/Other/test" } },
    { app: { appId, defaultDomain: "other.amplifyapp.com",
      repository: "https://github.com/andrewli615/cubby" } },
    { app: { appId, defaultDomain: `${appId}.amplifyapp.com`,
      repository: "https://github.com/other/repository" } },
    { branch: { branchName: "develop" } },
  ];
  for (const fixture of invalid) {
    const { runner, deploys } = mockRunner(fixture);
    assert.throws(() => runGuardedDeploy(args, runner));
    assert.equal(deploys.length, 0);
  }
});
