import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  list: vi.fn(), spending: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(), upload: vi.fn(),
}));
vi.mock("../src/receipts-api", async (loadOriginal) => {
  const actual = await loadOriginal<typeof import("../src/receipts-api")>();
  return { ...actual, receiptApi: api };
});
import { ReceiptPages } from "../src/receipt-pages";

const receipt = {
  receiptId: "920a995d-693c-4634-9fcf-985b1ddc0199", userId: "verified-subject",
  merchant: "Office Store", purchaseDate: "2026-09-28", total: 19.99, currency: "CAD",
  category: "Office", imageKey: "verified-subject/originals/uuid", status: "UPLOADED",
  createdAt: "2026-09-28T12:00:00Z", updatedAt: "2026-09-28T12:00:00Z",
} as const;

function show(path = "/") {
  return render(<MemoryRouter initialEntries={[path]}><ReceiptPages account="owner@example.com" onSignOut={vi.fn()} /></MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  api.list.mockResolvedValue([]);
  api.spending.mockResolvedValue({ currencies: [] });
  api.get.mockResolvedValue(receipt);
  api.create.mockResolvedValue(receipt);
  api.update.mockResolvedValue(receipt);
  api.delete.mockResolvedValue(undefined);
  api.upload.mockResolvedValue(receipt.imageKey);
});
afterEach(cleanup);

describe("receipt screens", () => {
  it("shows a dedicated spending-summary loading state", async () => {
    let finish!: (value: { currencies: [] }) => void;
    api.spending.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    show();
    expect(screen.getByText("Loading spending summary…")).toHaveAttribute("role", "status");
    finish({ currencies: [] });
    expect(await screen.findByText(/No expense data yet/)).toBeInTheDocument();
  });

  it("renders a currency-specific Sankey and accessible flow table using date filters", async () => {
    api.spending.mockResolvedValue({ currencies: [{ currency: "CAD", total: 30, nodes: [
      { id: "spending", label: "Company spending" }, { id: "category:Office", label: "Office" },
      { id: "merchant:Office:Office Store", label: "Office Store" },
    ], links: [{ source: "spending", target: "category:Office", value: 30 },
      { source: "category:Office", target: "merchant:Office:Office Store", value: 30 }] }] });
    show();
    expect(await screen.findByRole("img", { name: /Sankey diagram of CAD/ })).toBeInTheDocument();
    expect(screen.getByLabelText("Spending flow summary")).toHaveTextContent("Company spending");
    expect(screen.getByLabelText("Spending flow summary")).toHaveTextContent("Office Store");
    fireEvent.change(screen.getByLabelText("Date from"), { target: { value: "2026-09-01" } });
    fireEvent.change(screen.getByLabelText("Date to"), { target: { value: "2026-09-28" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
    await waitFor(() => expect(api.spending).toHaveBeenLastCalledWith({ dateFrom: "2026-09-01", dateTo: "2026-09-28" }));
  });

  it("requires a currency choice when there are several currencies", async () => {
    api.spending.mockResolvedValue({ currencies: [
      { currency: "CAD", total: 10, nodes: [], links: [] },
      { currency: "USD", total: 20, nodes: [], links: [] },
    ] });
    show();
    expect(await screen.findByText("Choose a currency to view its spending flow.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Currency"), { target: { value: "USD" } });
    expect(await screen.findByText(/Total USD/)).toBeInTheDocument();
  });

  it("shows retryable analytics errors and empty analytics state", async () => {
    api.spending.mockRejectedValueOnce(new Error("Analytics unavailable"));
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent("Analytics unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Retry spending summary" }));
    expect(await screen.findByText(/No expense data yet/)).toBeInTheDocument();
  });

  it("shows loading, empty, and populated dashboard states", async () => {
    let finish!: (value: typeof receipt[]) => void;
    api.list.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const view = show();
    expect(screen.getByText("Loading receipts…")).toHaveAttribute("role", "status");
    finish([]);
    expect(await screen.findByText("No expenses yet")).toBeInTheDocument();
    view.unmount();
    api.list.mockResolvedValue([receipt]);
    show();
    expect(await screen.findByRole("link", { name: /Office Store/ })).toBeInTheDocument();
    expect(screen.getByText("1 expense")).toBeInTheDocument();
  });

  it("shows a retryable API error", async () => {
    api.list.mockRejectedValueOnce(new Error("Connection unavailable"));
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent("Connection unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("No expenses yet")).toBeInTheDocument();
  });

  it("applies combined filters, sort, empty state, and reset", async () => {
    api.list.mockResolvedValueOnce([receipt]).mockResolvedValueOnce([]).mockResolvedValueOnce([receipt]);
    show();
    expect(await screen.findByRole("link", { name: /Office Store/ })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Merchant"), { target: { value: " Office " } });
    fireEvent.change(screen.getByLabelText("Category"), { target: { value: "Travel" } });
    fireEvent.change(screen.getByLabelText("Date from"), { target: { value: "2026-09-01" } });
    fireEvent.change(screen.getByLabelText("Date to"), { target: { value: "2026-09-28" } });
    fireEvent.change(screen.getByLabelText("Sort by"), { target: { value: "merchant_asc" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ merchant: "Office", category: "Travel",
      dateFrom: "2026-09-01", dateTo: "2026-09-28", sort: "merchant_asc" }));
    expect(await screen.findByText("No expense documents match these filters")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({}));
    expect(await screen.findByRole("link", { name: /Office Store/ })).toBeInTheDocument();
    expect(screen.getByLabelText("Merchant")).toHaveValue("");
    expect(screen.getByLabelText("Sort by")).toHaveValue("date_desc");
  });

  it("validates the date range and applies another sort without changing the owner", async () => {
    api.list.mockResolvedValue([receipt]);
    show();
    expect(await screen.findByRole("link", { name: /Office Store/ })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Date from"), { target: { value: "2026-09-29" } });
    fireEvent.change(screen.getByLabelText("Date to"), { target: { value: "2026-09-28" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(screen.getByRole("alert")).toHaveTextContent("start date");
    expect(api.list).toHaveBeenCalledTimes(1);
    fireEvent.change(screen.getByLabelText("Date to"), { target: { value: "2026-09-30" } });
    fireEvent.change(screen.getByLabelText("Sort by"), { target: { value: "total_desc" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ merchant: "", category: "",
      dateFrom: "2026-09-29", dateTo: "2026-09-30", sort: "total_desc" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("validates a create form and uploads before creating metadata", async () => {
    show("/receipts/new");
    fireEvent.change(screen.getByLabelText("Merchant"), { target: { value: "Office Store" } });
    fireEvent.change(screen.getByLabelText("Purchase date"), { target: { value: "2026-09-28" } });
    fireEvent.change(screen.getByLabelText("Total"), { target: { value: "-1" } });
    const file = new File(["bytes"], "receipt.png", { type: "image/png" });
    fireEvent.change(screen.getByLabelText(/Original receipt/), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "Create expense" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("non-negative amount");
    expect(api.upload).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Total"), { target: { value: "19.99" } });
    fireEvent.click(screen.getByRole("button", { name: "Create expense" }));
    expect(await screen.findByText("Expense created.")).toBeInTheDocument();
    expect(api.upload).toHaveBeenCalledWith(file);
    expect(api.create).toHaveBeenCalledWith({ merchant: "Office Store", purchaseDate: "2026-09-28",
      total: 19.99, currency: "CAD", category: null }, receipt.imageKey);
    expect(api.upload.mock.invocationCallOrder[0]).toBeLessThan(api.create.mock.invocationCallOrder[0]);
  });

  it("retains the uploaded key when metadata creation fails, then retries without reuploading", async () => {
    api.create.mockRejectedValueOnce(new Error("Save failed"));
    show("/receipts/new");
    fireEvent.change(screen.getByLabelText("Merchant"), { target: { value: "Store" } });
    fireEvent.change(screen.getByLabelText("Purchase date"), { target: { value: "2026-09-28" } });
    fireEvent.change(screen.getByLabelText("Total"), { target: { value: "1.00" } });
    fireEvent.change(screen.getByLabelText(/Original receipt/),
      { target: { files: [new File(["x"], "receipt.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: "Create expense" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Save failed");
    fireEvent.click(screen.getByRole("button", { name: "Create expense" }));
    expect(await screen.findByText("Expense created.")).toBeInTheDocument();
    expect(api.upload).toHaveBeenCalledTimes(1);
    expect(api.create).toHaveBeenCalledTimes(2);
  });

  it("shows an upload error and never creates metadata for a failed original", async () => {
    api.upload.mockRejectedValueOnce(new Error("Upload failed"));
    show("/receipts/new");
    fireEvent.change(screen.getByLabelText("Merchant"), { target: { value: "Store" } });
    fireEvent.change(screen.getByLabelText("Purchase date"), { target: { value: "2026-09-28" } });
    fireEvent.change(screen.getByLabelText("Total"), { target: { value: "1.00" } });
    fireEvent.change(screen.getByLabelText(/Original receipt/),
      { target: { files: [new File(["x"], "receipt.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: "Create expense" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Upload failed");
    expect(api.create).not.toHaveBeenCalled();
  });

  it("shows detail and saves only editable metadata", async () => {
    show(`/receipts/${receipt.receiptId}`);
    expect(await screen.findByRole("heading", { name: "Office Store" })).toBeInTheDocument();
    expect(screen.getByText("Stored privately")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: "Edit" }));
    expect(await screen.findByRole("heading", { name: "Edit expense" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Merchant"), { target: { value: "Updated Store" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(receipt.receiptId, {
      merchant: "Updated Store", purchaseDate: receipt.purchaseDate, total: receipt.total,
      currency: receipt.currency, category: receipt.category,
    }));
    expect(await screen.findByText("Expense updated.")).toBeInTheDocument();
  });

  it("shows ambiguous OCR values separately for review", async () => {
    api.get.mockResolvedValueOnce({ ...receipt, status: "REVIEW_NEEDED", ocr: {
      merchant: "OCR Market", purchaseDate: null, total: 12.4, currency: "CAD", reviewRequired: true,
    } });
    show(`/receipts/${receipt.receiptId}`);
    expect(await screen.findByRole("heading", { name: "Office Store" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("OCR could not verify every detail");
    expect(screen.getByText("OCR Market")).toBeInTheDocument();
    expect(screen.getByText("Not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit" })).toBeInTheDocument();
  });

  it("requires confirmation before deleting and shows success", async () => {
    show(`/receipts/${receipt.receiptId}`);
    fireEvent.click(await screen.findByRole("button", { name: "Delete" }));
    expect(api.delete).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Confirm delete" }));
    expect(await screen.findByText("Receipt deleted.")).toBeInTheDocument();
    expect(api.delete).toHaveBeenCalledWith(receipt.receiptId);
  });

  it("keeps detail visible when deletion fails", async () => {
    api.delete.mockRejectedValueOnce(new Error("Delete failed"));
    show(`/receipts/${receipt.receiptId}`);
    fireEvent.click(await screen.findByRole("button", { name: "Delete" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm delete" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Delete failed");
    expect(screen.getByRole("heading", { name: "Office Store" })).toBeInTheDocument();
  });
});
