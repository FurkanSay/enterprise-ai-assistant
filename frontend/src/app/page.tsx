export default function HomePage() {
  return (
    <main className="mx-auto max-w-2xl px-6 py-20">
      <h1 className="text-4xl font-semibold tracking-tight">Enterprise AI Assistant</h1>
      <p className="mt-4 text-muted-foreground">
        Multi-tenant retrieval-augmented assistant for company documents.
      </p>

      <section className="prose-chat mt-10 rounded-xl border bg-card p-8 text-card-foreground">
        <p>
          The chat interface, document upload, and live token streaming will arrive in
          Phase H of the roadmap. This landing page exists so the visual foundation
          (typography, color tokens, spacing) is set before component work starts.
        </p>
        <p className="mt-4">
          The design follows the visual decisions recorded in ADR-005.
        </p>
      </section>

      <div className="mt-8 flex items-center gap-3">
        <button
          type="button"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:opacity-90"
        >
          Primary action
        </button>
        <button
          type="button"
          className="rounded-md border bg-card px-4 py-2 text-sm font-medium text-foreground transition hover:bg-accent"
        >
          Secondary action
        </button>
      </div>
    </main>
  );
}
