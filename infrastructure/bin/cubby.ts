import "source-map-support/register.js";
import * as cdk from "aws-cdk-lib";
import { CubbyStack } from "../lib/cubby-stack.js";

const app = new cdk.App();
const region: unknown = app.node.tryGetContext("cubby:region");
if (typeof region !== "string" || !/^[a-z]{2}(-[a-z]+)+-\d$/.test(region)) {
  throw new Error("Set cubby:region to the project's selected AWS Region in cdk.json.");
}
new CubbyStack(app, "Cubby", {
  env: { region },
  terminationProtection: true,
});
