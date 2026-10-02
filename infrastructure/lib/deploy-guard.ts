import { stackConfiguration } from "./stack-config.js";
import { fileURLToPath } from "node:url";

const region = "us-west-2";
const profile = "cubby";
const repository = "https://github.com/andrewli615/cubby";

export interface CommandRunner {
  read(command: string, args: string[]): string;
  deploy(command: string, args: string[]): number;
}

interface DeployInput {
  account: string;
  webOrigin: string;
  appId: string;
}

/** Parse only the reviewed inputs. No arbitrary CDK options can be forwarded. */
export function parseDeployInput(args: string[], env: NodeJS.ProcessEnv = {}): DeployInput {
  const context = new Map<string, string>();
  let selectedProfile: string | undefined;
  for (let index = 0; index < args.length; index += 2) {
    const flag = args[index];
    const value = args[index + 1];
    if (!value || (flag !== "--context" && flag !== "--profile")) {
      throw new Error("Use only --context key=value and --profile cubby.");
    }
    if (flag === "--profile") {
      if (selectedProfile !== undefined) throw new Error("Specify --profile only once.");
      selectedProfile = value;
      continue;
    }
    const separator = value.indexOf("=");
    const key = value.slice(0, separator);
    if (separator < 1 || !["cubby:release", "cubby:account", "cubby:webOrigin"].includes(key) ||
        context.has(key)) throw new Error("Unknown or repeated deployment context.");
    context.set(key, value.slice(separator + 1));
  }
  if (selectedProfile !== profile) throw new Error("Deployment requires --profile cubby.");
  if (context.get("cubby:release") !== "true") {
    throw new Error("Deployment requires --context cubby:release=true.");
  }
  for (const name of ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN",
    "AWS_SECURITY_TOKEN", "AWS_WEB_IDENTITY_TOKEN_FILE"]) {
    if (env[name]) throw new Error("Remove ambient AWS credentials before using the cubby profile.");
  }
  const config = stackConfiguration({
    "cubby:region": region,
    "cubby:release": context.get("cubby:release"),
    "cubby:account": context.get("cubby:account"),
    "cubby:webOrigin": context.get("cubby:webOrigin"),
  });
  const webOrigin = config.webOrigin!;
  let url: URL;
  try {
    url = new URL(webOrigin);
  } catch {
    throw new Error("Use the actual HTTPS main-branch Amplify origin.");
  }
  const match = /^main\.(d[a-z0-9]{1,19})\.amplifyapp\.com$/.exec(url.hostname);
  if (url.protocol !== "https:" || url.origin !== webOrigin || !match) {
    throw new Error("Use the actual HTTPS main-branch Amplify origin.");
  }
  return { account: config.account!, webOrigin, appId: match[1]! };
}

function awsJson(runner: CommandRunner, operation: string, args: string[]): Record<string, unknown> {
  let output: unknown;
  try {
    output = JSON.parse(runner.read("aws", [operation, ...args, "--profile", profile,
      "--region", region, "--output", "json", "--no-cli-pager"])) as unknown;
  } catch {
    throw new Error(`Could not verify ${operation} with the cubby profile.`);
  }
  if (!output || typeof output !== "object" || Array.isArray(output)) {
    throw new Error(`Invalid ${operation} response.`);
  }
  return output as Record<string, unknown>;
}

/** AWS reads and CDK are reached only after local input validation; tests inject a fake runner. */
export function runGuardedDeploy(args: string[], runner: CommandRunner,
  env: NodeJS.ProcessEnv = {}): number {
  const input = parseDeployInput(args, env);
  const identity = awsJson(runner, "sts", ["get-caller-identity"]);
  if (identity.Account !== input.account || typeof identity.Arn !== "string" ||
      identity.Arn === `arn:aws:iam::${input.account}:root` ||
      !identity.Arn.includes(`::${input.account}:`)) {
    throw new Error("The cubby profile must be a non-root identity in the requested account.");
  }
  const app = awsJson(runner, "amplify", ["get-app", "--app-id", input.appId]).app as
    Record<string, unknown> | undefined;
  if (!app || app.appId !== input.appId ||
      app.defaultDomain !== `${input.appId}.amplifyapp.com` ||
      ![repository, `${repository}.git`].includes(String(app.repository))) {
    throw new Error("The origin does not match the Cubby Amplify app.");
  }
  const branch = awsJson(runner, "amplify", ["get-branch", "--app-id", input.appId,
    "--branch-name", "main"]).branch as Record<string, unknown> | undefined;
  if (!branch || branch.branchName !== "main") {
    throw new Error("The Cubby Amplify main branch is unavailable.");
  }
  const cdkCli = fileURLToPath(new URL("../node_modules/aws-cdk/bin/cdk", import.meta.url));
  return runner.deploy(process.execPath, [cdkCli, "deploy", "Cubby", "--profile", profile, "--no-lookups",
    "--context", "cubby:release=true",
    "--context", `cubby:account=${input.account}`,
    "--context", `cubby:webOrigin=${input.webOrigin}`,
  ]);
}
