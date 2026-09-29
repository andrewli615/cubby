import { pathToFileURL } from "node:url";

/** The discovery build has no backend outputs yet. Final builds must opt in explicitly. */
export function checkReleaseConfig(env) {
  const names = ["VITE_API_BASE_URL", "VITE_COGNITO_USER_POOL_ID", "VITE_COGNITO_CLIENT_ID"];
  if (env.CUBBY_RELEASE_BUILD && env.CUBBY_RELEASE_BUILD !== "1") {
    throw new Error("CUBBY_RELEASE_BUILD must be 1 for a configured build.");
  }
  if (env.CUBBY_RELEASE_BUILD !== "1" && names.every((name) => !env[name])) return;
  for (const name of names) {
    if (!env[name]?.trim()) throw new Error(`${name} is required for a configured build.`);
  }
  let api;
  try {
    api = new URL(env.VITE_API_BASE_URL);
  } catch {
    throw new Error("VITE_API_BASE_URL must be an HTTPS API Gateway origin.");
  }
  if (api.protocol !== "https:" || api.origin !== env.VITE_API_BASE_URL ||
      !api.hostname.endsWith(".execute-api.us-west-2.amazonaws.com")) {
    throw new Error("VITE_API_BASE_URL must be an HTTPS API Gateway origin.");
  }
  if (!/^us-west-2_[A-Za-z0-9]+$/.test(env.VITE_COGNITO_USER_POOL_ID)) {
    throw new Error("VITE_COGNITO_USER_POOL_ID must identify the Oregon user pool.");
  }
  if (!/^[a-z0-9]{20,128}$/.test(env.VITE_COGNITO_CLIENT_ID)) {
    throw new Error("VITE_COGNITO_CLIENT_ID must identify the web app client.");
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    checkReleaseConfig(process.env);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
