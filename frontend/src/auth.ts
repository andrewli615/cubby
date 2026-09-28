import { Amplify } from "aws-amplify";
import { confirmSignIn, fetchAuthSession, getCurrentUser, signIn, signOut } from "aws-amplify/auth";
import { cognitoUserPoolsTokenProvider } from "aws-amplify/auth/cognito";
import { sessionStorage } from "aws-amplify/utils";

const userPoolId = import.meta.env.VITE_COGNITO_USER_POOL_ID;
const userPoolClientId = import.meta.env.VITE_COGNITO_CLIENT_ID;
export const authConfigured = Boolean(userPoolId && userPoolClientId);

if (authConfigured) {
  Amplify.configure({ Auth: { Cognito: { userPoolId, userPoolClientId } } });
  cognitoUserPoolsTokenProvider.setKeyValueStorage(sessionStorage);
}

export type AuthResult = "signedIn" | "totp" | "newPassword";

function requireConfig() {
  if (!authConfigured) throw new Error("Cognito configuration is missing.");
}

export async function currentSession(): Promise<string | null> {
  if (!authConfigured) return null;
  const session = await fetchAuthSession();
  if (!session.tokens?.accessToken) return null;
  const user = await getCurrentUser();
  return user.signInDetails?.loginId ?? user.username;
}

function nextStep(result: { isSignedIn: boolean; nextStep: { signInStep: string } }): AuthResult {
  if (result.isSignedIn) return "signedIn";
  if (result.nextStep.signInStep === "CONFIRM_SIGN_IN_WITH_TOTP_CODE") return "totp";
  if (result.nextStep.signInStep === "CONFIRM_SIGN_IN_WITH_NEW_PASSWORD_REQUIRED") return "newPassword";
  throw new Error("This sign-in step is not supported. Contact your administrator.");
}

export async function signInWithPassword(email: string, password: string): Promise<AuthResult> {
  requireConfig();
  return nextStep(await signIn({ username: email, password,
    options: { authFlowType: "USER_SRP_AUTH" } }));
}

export async function confirmTotp(code: string): Promise<AuthResult> {
  requireConfig();
  return nextStep(await confirmSignIn({ challengeResponse: code }));
}

export async function confirmNewPassword(password: string): Promise<AuthResult> {
  requireConfig();
  return nextStep(await confirmSignIn({ challengeResponse: password }));
}

export async function signOutOfSession(): Promise<void> {
  requireConfig();
  await signOut();
}
