import * as cdk from "aws-cdk-lib";
import { Construct } from "constructs";

/** Empty stack reserved for the infrastructure phases; creates no AWS resources. */
export class CubbyStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);
  }
}
