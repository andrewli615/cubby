import { describe, expect, it } from "vitest";
import { checkReleaseConfig } from "../scripts/check-release-config.mjs";

const valid = {
  CUBBY_RELEASE_BUILD: "1",
  VITE_API_BASE_URL: "https://abc123.execute-api.us-west-2.amazonaws.com",
  VITE_COGNITO_USER_POOL_ID: "us-west-2_abc123",
  VITE_COGNITO_CLIENT_ID: "abcdefghijklmnopqrstuv1234",
};

describe("configured frontend build", () => {
  it("allows the initial origin-discovery build without backend outputs", () => {
    expect(() => checkReleaseConfig({})).not.toThrow();
  });

  it("accepts complete Oregon API and Cognito values", () => {
    expect(() => checkReleaseConfig(valid)).not.toThrow();
  });

  it.each(["VITE_API_BASE_URL", "VITE_COGNITO_USER_POOL_ID", "VITE_COGNITO_CLIENT_ID"])(
    "rejects a missing %s without printing configuration values", (name) => {
      const env = { ...valid, [name]: "" };
      expect(() => checkReleaseConfig(env)).toThrow(name);
      expect(() => checkReleaseConfig(env)).not.toThrow(valid.VITE_API_BASE_URL);
    },
  );

  it("rejects a partial configuration even without the release flag", () => {
    expect(() => checkReleaseConfig({ VITE_API_BASE_URL: valid.VITE_API_BASE_URL }))
      .toThrow("VITE_COGNITO_USER_POOL_ID");
  });

  it("rejects a mistyped release flag", () => {
    expect(() => checkReleaseConfig({ ...valid, CUBBY_RELEASE_BUILD: "true" }))
      .toThrow("CUBBY_RELEASE_BUILD");
  });

  it.each(["http://abc123.execute-api.us-west-2.amazonaws.com",
    "https://abc123.execute-api.us-west-2.amazonaws.com/path", "https://example.invalid"])(
    "rejects an invalid API URL", (value) => {
      expect(() => checkReleaseConfig({ ...valid, VITE_API_BASE_URL: value })).toThrow("VITE_API_BASE_URL");
    },
  );

  it("rejects an out-of-region user pool and malformed client ID", () => {
    expect(() => checkReleaseConfig({ ...valid, VITE_COGNITO_USER_POOL_ID: "us-east-1_abc123" }))
      .toThrow("VITE_COGNITO_USER_POOL_ID");
    expect(() => checkReleaseConfig({ ...valid, VITE_COGNITO_CLIENT_ID: "placeholder" }))
      .toThrow("VITE_COGNITO_CLIENT_ID");
  });
});
