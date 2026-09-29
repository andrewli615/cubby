import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const auth = vi.hoisted(() => ({
  currentSession: vi.fn(), signInWithPassword: vi.fn(), confirmTotp: vi.fn(),
  confirmNewPassword: vi.fn(), signOutOfSession: vi.fn(),
}));
const receiptApi = vi.hoisted(() => ({ list: vi.fn(), spending: vi.fn() }));
vi.mock("../src/auth", () => ({ authConfigured: true, ...auth }));
vi.mock("../src/receipts-api", () => ({ receiptApi }));
import { App } from "../src/App";

beforeEach(() => {
  vi.resetAllMocks();
  auth.currentSession.mockResolvedValue(null);
  auth.signOutOfSession.mockResolvedValue(undefined);
  receiptApi.list.mockResolvedValue([]);
  receiptApi.spending.mockResolvedValue({ currencies: [] });
});
afterEach(cleanup);

describe("Cognito sign-in state", () => {
  it("restores an existing session without asking for credentials", async () => {
    auth.currentSession.mockResolvedValue("owner@example.com");
    render(<App />);
    expect(await screen.findByRole("heading", { name: "Company expenses" })).toBeInTheDocument();
    expect(screen.getByText("owner@example.com")).toBeInTheDocument();
    expect(auth.signInWithPassword).not.toHaveBeenCalled();
  });

  it("signs in with SRP credentials and signs out", async () => {
    auth.signInWithPassword.mockResolvedValue("signedIn");
    auth.currentSession.mockResolvedValueOnce(null).mockResolvedValueOnce("owner@example.com");
    render(<App />);
    fireEvent.change(await screen.findByLabelText("Email"), { target: { value: "owner@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("heading", { name: "Company expenses" })).toBeInTheDocument();
    expect(auth.signInWithPassword).toHaveBeenCalledWith("owner@example.com", "password");
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument());
    expect(auth.signOutOfSession).toHaveBeenCalledOnce();
  });

  it("handles an authenticator challenge without creating a session early", async () => {
    auth.signInWithPassword.mockResolvedValue("totp");
    auth.confirmTotp.mockResolvedValue("signedIn");
    auth.currentSession.mockResolvedValueOnce(null).mockResolvedValueOnce("owner@example.com");
    render(<App />);
    fireEvent.change(await screen.findByLabelText("Email"), { target: { value: "owner@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    fireEvent.change(await screen.findByLabelText("Authenticator code"), { target: { value: "123456" } });
    expect(auth.currentSession).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Confirm code" }));
    expect(await screen.findByRole("heading", { name: "Company expenses" })).toBeInTheDocument();
    expect(auth.confirmTotp).toHaveBeenCalledWith("123456");
  });

  it("lets an administrator-created account set its required first password", async () => {
    auth.signInWithPassword.mockResolvedValue("newPassword");
    auth.confirmNewPassword.mockResolvedValue("signedIn");
    auth.currentSession.mockResolvedValueOnce(null).mockResolvedValueOnce("owner@example.com");
    render(<App />);
    fireEvent.change(await screen.findByLabelText("Email"), { target: { value: "owner@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "temporary" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    fireEvent.change(await screen.findByLabelText("New password"), { target: { value: "NewSecurePassword!123" } });
    fireEvent.click(screen.getByRole("button", { name: "Set password" }));
    expect(await screen.findByRole("heading", { name: "Company expenses" })).toBeInTheDocument();
    expect(auth.confirmNewPassword).toHaveBeenCalledWith("NewSecurePassword!123");
  });

  it("keeps the user signed out after invalid credentials", async () => {
    auth.signInWithPassword.mockRejectedValue(new Error("credential details"));
    render(<App />);
    fireEvent.change(await screen.findByLabelText("Email"), { target: { value: "owner@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Sign-in failed.");
    expect(screen.queryByText("credential details")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Company expenses" })).not.toBeInTheDocument();
  });
});
