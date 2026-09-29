import type { Receipt, ReceiptFields, ReceiptListFilters, SpendingSummary } from "./receipts-api";

const demoUser = "local-demo-finance-user";
const createdAt = "2026-09-28T12:00:00Z";
const records: Receipt[] = [
  ["920a995d-693c-4634-9fcf-985b1ddc0101", "Paper & Ink", "Office supplies", "2026-09-02", 184.5, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0102", "Desk Depot", "Office supplies", "2026-09-11", 92.3, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0103", "Air North", "Travel", "2026-09-15", 560, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0104", "Cedar Hotel", "Travel", "2026-09-18", 420, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0105", "Northstar SaaS", "Software", "2026-09-20", 129.99, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0106", "Corner Cafe", null, "2026-09-25", 38.5, "CAD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0107", "Cloud Tools", "Software", "2026-09-08", 249, "USD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0108", "Metro Cab", "Travel", "2026-09-21", 49.5, "USD"],
  ["920a995d-693c-4634-9fcf-985b1ddc0109", "Team Lunch", null, "2026-09-27", 86.25, "USD"],
].map(([receiptId, merchant, category, purchaseDate, total, currency]) => ({
  receiptId: receiptId as string,
  userId: demoUser,
  merchant: merchant as string,
  category: category as string | null,
  purchaseDate: purchaseDate as string,
  total: total as number,
  currency: currency as string,
  imageKey: `${demoUser}/originals/demo-only`,
  status: "READY",
  createdAt,
  updatedAt: createdAt,
})) as Receipt[];

export function demoList(filters: ReceiptListFilters = {}): Receipt[] {
  const filtered = records.filter((record) =>
    (!filters.merchant || record.merchant.toLowerCase().includes(filters.merchant.toLowerCase().trim())) &&
    (!filters.category || record.category?.toLowerCase() === filters.category.toLowerCase().trim()) &&
    (!filters.dateFrom || record.purchaseDate >= filters.dateFrom) &&
    (!filters.dateTo || record.purchaseDate <= filters.dateTo));
  const sort = filters.sort ?? "date_desc";
  return [...filtered].sort((a, b) => {
    switch (sort) {
      case "date_asc": return a.purchaseDate.localeCompare(b.purchaseDate);
      case "merchant_asc": return a.merchant.localeCompare(b.merchant);
      case "merchant_desc": return b.merchant.localeCompare(a.merchant);
      case "total_asc": return a.total - b.total;
      case "total_desc": return b.total - a.total;
      default: return b.purchaseDate.localeCompare(a.purchaseDate);
    }
  });
}

export function demoSpending(filters: Pick<ReceiptListFilters, "dateFrom" | "dateTo"> = {}): SpendingSummary {
  const currencies = new Map<string, Map<string, Map<string, number>>>();
  for (const record of records) {
    if ((filters.dateFrom && record.purchaseDate < filters.dateFrom) ||
        (filters.dateTo && record.purchaseDate > filters.dateTo)) continue;
    const category = record.category || "Uncategorized";
    const categories = currencies.get(record.currency) ?? new Map<string, Map<string, number>>();
    const merchants = categories.get(category) ?? new Map<string, number>();
    merchants.set(record.merchant, (merchants.get(record.merchant) ?? 0) + record.total);
    categories.set(category, merchants);
    currencies.set(record.currency, categories);
  }
  return { currencies: [...currencies].sort(([a], [b]) => a.localeCompare(b)).map(([currency, categories]) => {
    const nodes = [{ id: "spending", label: "Company spending" }];
    const links: SpendingSummary["currencies"][number]["links"] = [];
    let total = 0;
    for (const [category, merchants] of [...categories].sort(([a], [b]) => a.localeCompare(b))) {
      const categoryId = `category:${category}`;
      nodes.push({ id: categoryId, label: category });
      const categoryTotal = [...merchants.values()].reduce((sum, amount) => sum + amount, 0);
      total += categoryTotal;
      links.push({ source: "spending", target: categoryId, value: categoryTotal });
      for (const [merchant, amount] of [...merchants].sort(([a], [b]) => a.localeCompare(b))) {
        const merchantId = `merchant:${category}:${merchant}`;
        nodes.push({ id: merchantId, label: merchant });
        links.push({ source: categoryId, target: merchantId, value: amount });
      }
    }
    return { currency, total, nodes, links };
  }) };
}

export function demoGet(receiptId: string): Receipt {
  const record = records.find((receipt) => receipt.receiptId === receiptId);
  if (!record) throw new Error("Expense not found.");
  return { ...record };
}

export function demoCreate(fields: ReceiptFields, imageKey: string): Receipt {
  const now = new Date().toISOString();
  const receipt: Receipt = { ...fields, receiptId: crypto.randomUUID(), userId: demoUser, imageKey,
    status: "READY", createdAt: now, updatedAt: now };
  records.push(receipt);
  return { ...receipt };
}

export function demoUpdate(receiptId: string, fields: ReceiptFields): Receipt {
  const index = records.findIndex((receipt) => receipt.receiptId === receiptId);
  if (index < 0) throw new Error("Expense not found.");
  const updated = { ...records[index]!, ...fields, updatedAt: new Date().toISOString() };
  records[index] = updated;
  return { ...updated };
}

export function demoDelete(receiptId: string): void {
  const index = records.findIndex((receipt) => receipt.receiptId === receiptId);
  if (index < 0) throw new Error("Expense not found.");
  records.splice(index, 1);
}

export function demoUpload(): string {
  return `${demoUser}/originals/${crypto.randomUUID()}`;
}
