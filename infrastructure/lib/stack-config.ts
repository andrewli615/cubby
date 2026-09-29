export interface StackConfiguration {
  readonly region: string;
  readonly account?: string;
  readonly webOrigin?: string;
}

/** Local synthesis needs only a Region; a release must identify its account and browser origin. */
export function stackConfiguration(context: Record<string, unknown>): StackConfiguration {
  const region = context["cubby:region"];
  const account = context["cubby:account"];
  const webOrigin = context["cubby:webOrigin"];
  const release = context["cubby:release"];
  if (typeof region !== "string" || !/^[a-z]{2}(-[a-z]+)+-\d$/.test(region)) {
    throw new Error("Set cubby:region to the project's selected AWS Region in cdk.json.");
  }
  if (release !== undefined && release !== "true" && release !== true) {
    throw new Error("Set cubby:release=true only for the configured release synthesis.");
  }
  if (account !== undefined && (typeof account !== "string" || !/^\d{12}$/.test(account))) {
    throw new Error("Set cubby:account to the verified 12-digit AWS account ID.");
  }
  if (webOrigin !== undefined && typeof webOrigin !== "string") {
    throw new Error("Set cubby:webOrigin to the exact HTTPS origin of the hosted frontend.");
  }
  if (release && (account === undefined || webOrigin === undefined)) {
    throw new Error("Release synthesis requires cubby:account and cubby:webOrigin.");
  }
  if (release && typeof webOrigin === "string") {
    let hostname: string;
    try {
      hostname = new URL(webOrigin).hostname;
    } catch {
      throw new Error("Release synthesis requires a non-placeholder hosted origin.");
    }
    if (hostname === "localhost" || /\.(invalid|example|test)$/.test(hostname)) {
      throw new Error("Release synthesis requires a non-placeholder hosted origin.");
    }
  }
  return {
    region,
    ...(typeof account === "string" ? { account } : {}),
    ...(typeof webOrigin === "string" ? { webOrigin } : {}),
  };
}
