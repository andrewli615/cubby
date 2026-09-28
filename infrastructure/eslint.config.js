import awscdk from "eslint-plugin-awscdk";
import tseslint from "typescript-eslint";

export default [
  {
    ignores: ["cdk.out/**", "node_modules/**"],
  },
  ...tseslint.configs.recommended,
  {
    ...awscdk.configs.recommended,
    files: ["bin/**/*.ts", "lib/**/*.ts"],
  },
];
