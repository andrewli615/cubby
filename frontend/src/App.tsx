import { BrowserRouter, Route, Routes } from "react-router-dom";

function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center px-6 py-16">
      <section className="max-w-xl text-center">
        <p className="mb-5 text-sm font-semibold uppercase tracking-[0.24em] text-emerald-800">
          Your receipts, in one place
        </p>
        <h1 className="text-6xl font-semibold tracking-tight text-stone-900 sm:text-7xl">
          Cubby
        </h1>
        <p className="mt-5 text-lg text-stone-600 sm:text-xl">
          Receipt management made simple.
        </p>
      </section>
    </main>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="*" element={<HomePage />} />
      </Routes>
    </BrowserRouter>
  );
}
