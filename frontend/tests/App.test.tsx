import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "../src/App";

describe("Cubby home page", () => {
  it("shows the product name and tagline", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "Cubby" })).toBeInTheDocument();
    expect(screen.getByText("Receipt management made simple.")).toBeInTheDocument();
  });
});
