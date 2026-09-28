import { useEffect, useState, type FormEvent } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { authConfigured, confirmNewPassword, confirmTotp, currentSession, signInWithPassword, signOutOfSession } from "./auth";
import { ReceiptPages } from "./receipt-pages";

function HomePage({ account, onSignOut }: { account: string; onSignOut: () => void }) {
  return <ReceiptPages account={account} onSignOut={onSignOut} />;
}

function AuthPage() {
  const [account, setAccount] = useState<string | null>(null);
  const [stage, setStage] = useState<"loading" | "password" | "newPassword" | "totp" | "signedIn">("loading");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    void currentSession().then((user) => {
      if (!active) return;
      setAccount(user);
      setStage(user ? "signedIn" : "password");
    }).catch(() => { if (active) setStage("password"); });
    return () => { active = false; };
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const result = stage === "totp" ? await confirmTotp(code)
        : stage === "newPassword" ? await confirmNewPassword(newPassword)
          : await signInWithPassword(email.trim(), password);
      setPassword("");
      setNewPassword("");
      if (result === "totp" || result === "newPassword") {
        setStage(result);
      } else {
        const user = await currentSession();
        if (!user) throw new Error("Sign-in session was not established.");
        setAccount(user);
        setStage("signedIn");
      }
    } catch {
      setPassword("");
      setNewPassword("");
      setError("Sign-in failed. Check your details and try again.");
    } finally {
      setBusy(false);
    }
  }

  async function logOut() {
    setError("");
    setBusy(true);
    try {
      await signOutOfSession();
      setAccount(null);
      setCode("");
      setNewPassword("");
      setStage("password");
    } catch {
      setError("Sign-out failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  if (!authConfigured) return <main className="p-8">Cognito configuration is missing.</main>;
  if (stage === "loading") return <main className="p-8">Loading session…</main>;
  if (stage === "signedIn" && account) return <><HomePage account={account} onSignOut={() => void logOut()} />
    {error && <p role="alert">{error}</p>}</>;

  return <main className="flex min-h-screen items-center justify-center px-6">
    <form className="w-full max-w-sm space-y-4" onSubmit={(event) => void submit(event)}>
      <h1 className="text-4xl font-semibold">Cubby sign in</h1>
      {stage === "password" ? <>
        <label className="block">Email<input className="mt-1 w-full rounded border p-2" type="email" required
          autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
        <label className="block">Password<input className="mt-1 w-full rounded border p-2" type="password" required
          autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      </> : stage === "newPassword" ? <label className="block">New password<input
        className="mt-1 w-full rounded border p-2" type="password" autoComplete="new-password" required
        value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
      : <label className="block">Authenticator code<input className="mt-1 w-full rounded border p-2"
        inputMode="numeric" autoComplete="one-time-code" required value={code}
        onChange={(event) => setCode(event.target.value)} /></label>}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      <button className="w-full rounded bg-emerald-800 px-4 py-2 text-white disabled:opacity-50" disabled={busy}>
        {stage === "totp" ? "Confirm code" : stage === "newPassword" ? "Set password" : "Sign in"}
      </button>
    </form>
  </main>;
}

export function App() {
  return <BrowserRouter><Routes><Route path="*" element={<AuthPage />} /></Routes></BrowserRouter>;
}
