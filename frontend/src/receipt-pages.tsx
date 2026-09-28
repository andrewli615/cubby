import { useEffect, useState, type FormEvent } from "react";
import { Link, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { receiptApi, validateUpload, type Receipt, type ReceiptFields } from "./receipts-api";

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Something went wrong. Try again.";
}

function money(receipt: Receipt): string {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: receipt.currency }).format(receipt.total);
  } catch {
    return `${receipt.currency} ${receipt.total}`;
  }
}

function Status({ status }: { status: Receipt["status"] }) {
  return <span className="rounded-full bg-stone-200 px-3 py-1 text-xs font-medium text-stone-700">
    {status.replaceAll("_", " ").toLowerCase()}
  </span>;
}

function Dashboard() {
  const location = useLocation();
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    void receiptApi.list().then((result) => {
      if (active) { setReceipts(result); setError(""); setLoading(false); }
    }).catch((failure: unknown) => {
      if (active) { setError(errorMessage(failure)); setLoading(false); }
    });
    return () => { active = false; };
  }, [reload]);

  return <section className="space-y-6">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="text-sm font-semibold uppercase tracking-widest text-emerald-800">Dashboard</p>
        <h1 className="mt-2 text-3xl font-semibold">Your receipts</h1></div>
      <Link className="rounded bg-emerald-800 px-4 py-2 font-medium text-white" to="/receipts/new">Add receipt</Link>
    </div>
    {new URLSearchParams(location.search).has("deleted") &&
      <p role="status" className="rounded bg-emerald-50 p-3 text-emerald-900">Receipt deleted.</p>}
    {loading ? <p role="status">Loading receipts…</p> : error ?
      <div role="alert" className="rounded border border-red-200 bg-red-50 p-4">
        <p>{error}</p><button className="mt-2 underline" onClick={() => { setLoading(true); setReload((value) => value + 1); }}>Retry</button>
      </div> : receipts.length === 0 ?
      <div className="rounded border border-dashed border-stone-300 bg-white p-8 text-center">
        <h2 className="text-xl font-medium">No receipts yet</h2>
        <p className="mt-2 text-stone-600">Upload your first receipt to keep its details together.</p>
      </div> : <>
        <p className="text-sm text-stone-600">{receipts.length} {receipts.length === 1 ? "receipt" : "receipts"}</p>
        <ul className="space-y-3" aria-label="Receipts">
          {receipts.map((receipt) => <li key={receipt.receiptId}>
            <Link to={`/receipts/${receipt.receiptId}`} className="flex flex-wrap items-center justify-between gap-3 rounded border border-stone-200 bg-white p-4 hover:border-emerald-700 focus-visible:outline-2 focus-visible:outline-emerald-700">
              <span><span className="block font-semibold">{receipt.merchant}</span>
                <span className="text-sm text-stone-600">{receipt.purchaseDate}</span></span>
              <span className="flex items-center gap-4"><Status status={receipt.status} />
                <span className="font-semibold">{money(receipt)}</span></span>
            </Link>
          </li>)}
        </ul>
      </>}
  </section>;
}

interface FormValues {
  merchant: string;
  purchaseDate: string;
  total: string;
  currency: string;
  category: string;
}

function ReceiptForm({ receipt, saving, progress, error, onSave, onFileChange }: {
  receipt?: Receipt;
  saving: boolean;
  progress: string;
  error: string;
  onSave: (fields: ReceiptFields, file?: File) => Promise<void>;
  onFileChange?: () => void;
}) {
  const [values, setValues] = useState<FormValues>({
    merchant: receipt?.merchant ?? "", purchaseDate: receipt?.purchaseDate ?? "",
    total: receipt ? String(receipt.total) : "", currency: receipt?.currency ?? "CAD",
    category: receipt?.category ?? "",
  });
  const [file, setFile] = useState<File | undefined>();
  const [validation, setValidation] = useState("");

  function change(field: keyof FormValues, value: string) {
    setValues((previous) => ({ ...previous, [field]: value }));
    setValidation("");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const merchant = values.merchant.trim();
    const total = values.total.trim();
    const currency = values.currency.trim().toUpperCase();
    if (!merchant || !values.purchaseDate || !/^(0|[1-9]\d*)(\.\d{1,2})?$/.test(total) ||
        !Number.isSafeInteger(Math.round(Number(total) * 100)) || !/^[A-Z]{3}$/.test(currency)) {
      setValidation("Enter a merchant, date, non-negative amount (up to two decimals), and three-letter currency.");
      return;
    }
    if (!receipt && !file) { setValidation("Choose an original receipt file."); return; }
    try {
      if (file) validateUpload(file);
    } catch (failure) {
      setValidation(errorMessage(failure));
      return;
    }
    setValidation("");
    await onSave({ merchant, purchaseDate: values.purchaseDate, total: Number(total), currency,
      category: values.category.trim() || null }, file);
  }

  return <form className="max-w-xl space-y-4" onSubmit={(event) => void submit(event)}>
    <label className="block font-medium">Merchant<input required value={values.merchant}
      onChange={(event) => change("merchant", event.target.value)} className="mt-1 w-full rounded border p-2" /></label>
    <label className="block font-medium">Purchase date<input type="date" required value={values.purchaseDate}
      onChange={(event) => change("purchaseDate", event.target.value)} className="mt-1 w-full rounded border p-2" /></label>
    <div className="grid gap-4 sm:grid-cols-2">
      <label className="block font-medium">Total<input required inputMode="decimal" value={values.total}
        onChange={(event) => change("total", event.target.value)} className="mt-1 w-full rounded border p-2" /></label>
      <label className="block font-medium">Currency<input required maxLength={3} value={values.currency}
        onChange={(event) => change("currency", event.target.value)} className="mt-1 w-full rounded border p-2" /></label>
    </div>
    <label className="block font-medium">Category (optional)<input value={values.category}
      onChange={(event) => change("category", event.target.value)} className="mt-1 w-full rounded border p-2" /></label>
    {!receipt && <label className="block font-medium">Original receipt
      <input type="file" accept="image/jpeg,image/png,application/pdf"
        onChange={(event) => { setFile(event.target.files?.[0]); setValidation(""); onFileChange?.(); }}
        className="mt-1 block w-full rounded border bg-white p-2" />
      <span className="mt-1 block text-sm font-normal text-stone-600">JPEG, PNG, or PDF; 10 MB maximum.</span>
    </label>}
    {validation && <p role="alert" className="text-red-700">{validation}</p>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {progress && <p role="status">{progress}</p>}
    <div className="flex gap-3"><button disabled={saving} className="rounded bg-emerald-800 px-4 py-2 text-white disabled:opacity-50">
      {receipt ? "Save changes" : "Create receipt"}</button>
      <Link to={receipt ? `/receipts/${receipt.receiptId}` : "/"} className="rounded border px-4 py-2">Cancel</Link></div>
  </form>;
}

function CreateReceipt() {
  const navigate = useNavigate();
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState("");
  const [error, setError] = useState("");
  const [imageKey, setImageKey] = useState<string | null>(null);

  async function save(fields: ReceiptFields, file?: File) {
    if (!file) return;
    setSaving(true);
    setError("");
    try {
      let key = imageKey;
      if (!key) {
        setProgress("Uploading original…");
        key = await receiptApi.upload(file);
        setImageKey(key);
      }
      setProgress("Saving receipt…");
      const created = await receiptApi.create(fields, key);
      navigate(`/receipts/${created.receiptId}?created=1`, { replace: true });
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSaving(false);
      setProgress("");
    }
  }

  return <section className="space-y-6"><Link className="text-emerald-800 underline" to="/">← All receipts</Link>
    <h1 className="text-3xl font-semibold">Add receipt</h1>
    <ReceiptForm saving={saving} progress={progress} error={error} onSave={save}
      onFileChange={() => setImageKey(null)} /></section>;
}

function Detail() {
  const { receiptId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!receiptId) return;
    let active = true;
    void receiptApi.get(receiptId).then((result) => {
      if (active) { setReceipt(result); setError(""); setLoading(false); }
    }).catch((failure: unknown) => {
      if (active) { setError(errorMessage(failure)); setLoading(false); }
    });
    return () => { active = false; };
  }, [receiptId, reload]);

  async function remove() {
    if (!receiptId) return;
    setDeleting(true);
    setError("");
    try {
      await receiptApi.delete(receiptId);
      navigate("/?deleted=1", { replace: true });
    } catch (failure) {
      setError(errorMessage(failure));
      setDeleting(false);
    }
  }

  return <section className="space-y-6"><Link className="text-emerald-800 underline" to="/">← All receipts</Link>
    {loading ? <p role="status">Loading receipt…</p> : !receipt ?
      <div role="alert"><p>{error || "Receipt not found."}</p>
        <button className="mt-2 underline" onClick={() => { setLoading(true); setReload((value) => value + 1); }}>Retry</button></div> : <>
        {new URLSearchParams(location.search).has("created") &&
          <p role="status" className="rounded bg-emerald-50 p-3 text-emerald-900">Receipt created.</p>}
        {new URLSearchParams(location.search).has("updated") &&
          <p role="status" className="rounded bg-emerald-50 p-3 text-emerald-900">Receipt updated.</p>}
        <div className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-semibold">{receipt.merchant}</h1>
          <p className="mt-2 text-stone-600">{receipt.purchaseDate}</p></div><Status status={receipt.status} /></div>
        <dl className="grid gap-4 rounded border border-stone-200 bg-white p-6 sm:grid-cols-2">
          <div><dt className="text-sm text-stone-600">Total</dt><dd className="text-xl font-semibold">{money(receipt)}</dd></div>
          <div><dt className="text-sm text-stone-600">Category</dt><dd>{receipt.category || "Uncategorized"}</dd></div>
          <div><dt className="text-sm text-stone-600">Created</dt><dd>{new Date(receipt.createdAt).toLocaleString()}</dd></div>
          <div><dt className="text-sm text-stone-600">Original</dt><dd>Stored privately</dd></div>
        </dl>
        <div className="flex gap-3"><Link className="rounded bg-emerald-800 px-4 py-2 text-white" to={`/receipts/${receipt.receiptId}/edit`}>Edit</Link>
          <button className="rounded border border-red-700 px-4 py-2 text-red-800" onClick={() => setConfirming(true)}>Delete</button></div>
        {confirming && <div className="rounded border border-red-200 bg-red-50 p-4">
          <p>Delete this receipt record? Its original file remains privately stored.</p>
          <div className="mt-3 flex gap-3"><button disabled={deleting} className="rounded bg-red-800 px-4 py-2 text-white disabled:opacity-50"
            onClick={() => void remove()}>Confirm delete</button>
            <button className="rounded border px-4 py-2" onClick={() => setConfirming(false)}>Cancel</button></div>
        </div>}
        {error && <p role="alert" className="text-red-700">{error}</p>}
      </>}
  </section>;
}

function EditReceipt() {
  const { receiptId } = useParams();
  const navigate = useNavigate();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!receiptId) return;
    let active = true;
    void receiptApi.get(receiptId).then((result) => {
      if (active) { setReceipt(result); setLoading(false); }
    }).catch((failure: unknown) => {
      if (active) { setError(errorMessage(failure)); setLoading(false); }
    });
    return () => { active = false; };
  }, [receiptId]);

  async function save(fields: ReceiptFields) {
    if (!receiptId) return;
    setSaving(true);
    setError("");
    try {
      await receiptApi.update(receiptId, fields);
      navigate(`/receipts/${receiptId}?updated=1`, { replace: true });
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSaving(false);
    }
  }

  return <section className="space-y-6"><Link className="text-emerald-800 underline" to={`/receipts/${receiptId}`}>← Receipt detail</Link>
    <h1 className="text-3xl font-semibold">Edit receipt</h1>
    {loading ? <p role="status">Loading receipt…</p> : receipt ?
      <ReceiptForm receipt={receipt} saving={saving} progress="" error={error} onSave={save} /> :
      <p role="alert">{error || "Receipt not found."}</p>}
  </section>;
}

export function ReceiptPages({ account, onSignOut }: { account: string; onSignOut: () => void }) {
  return <div className="min-h-screen bg-stone-50 text-stone-900">
    <header className="border-b border-stone-200 bg-white"><div className="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-3 px-5 py-4">
      <Link to="/" className="text-xl font-bold text-emerald-900">Cubby</Link>
      <div className="flex items-center gap-4"><span className="text-sm text-stone-600">{account}</span>
        <button className="text-sm font-medium text-emerald-800 underline" onClick={onSignOut}>Sign out</button></div>
    </div></header>
    <main className="mx-auto max-w-4xl px-5 py-8"><Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/receipts/new" element={<CreateReceipt />} />
      <Route path="/receipts/:receiptId" element={<Detail />} />
      <Route path="/receipts/:receiptId/edit" element={<EditReceipt />} />
      <Route path="*" element={<p role="alert">Page not found. <Link to="/" className="underline">View receipts</Link></p>} />
    </Routes></main>
  </div>;
}
