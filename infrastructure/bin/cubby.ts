import "source-map-support/register.js";
import * as cdk from "aws-cdk-lib";
import { CubbyStack } from "../lib/cubby-stack.js";
import { stackConfiguration } from "../lib/stack-config.js";

const app = new cdk.App();
const config = stackConfiguration({
  "cubby:region": app.node.tryGetContext("cubby:region"),
  "cubby:account": app.node.tryGetContext("cubby:account"),
  "cubby:webOrigin": app.node.tryGetContext("cubby:webOrigin"),
  "cubby:release": app.node.tryGetContext("cubby:release"),
});
new CubbyStack(app, "Cubby", {
  env: { region: config.region, ...(config.account ? { account: config.account } : {}) },
  terminationProtection: true,
  ...(config.webOrigin ? { webOrigin: config.webOrigin } : {}),
});
