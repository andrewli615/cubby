import "source-map-support/register.js";
import * as cdk from "aws-cdk-lib";
import { CubbyStack } from "../lib/cubby-stack.js";

const app = new cdk.App();
new CubbyStack(app, "Cubby");
