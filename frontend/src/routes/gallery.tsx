import { useMemo, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ScanLine, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardTile } from "@/components/CardTile";
import { ReviewCard } from "@/components/ReviewCard";
import { deleteCard, listCards, updateCard, type BusinessCard } from "@/lib/api";

export const Route = createFileRoute("/gallery")({
  component: Gallery,
  head: () => ({
    meta: [
      { title: "Saved cards — Cardfile" },
      {
        name: "description",
        content: "Search your filed business cards by name or company, then edit or delete any record.",
      },
      { property: "og:title", content: "Saved cards — Cardfile" },
      {
        property: "og:description",
        content: "Search your filed business cards by name or company, then edit or delete any record.",
      },
    ],
  }),
});

function Gallery() {
  const qc = useQueryClient();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<BusinessCard | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["cards"],
    queryFn: listCards,
  });

  const save = useMutation({
    mutationFn: updateCard,
    onSuccess: (card) => {
      toast.success("Record updated");
      setSelected(card);
      void qc.invalidateQueries({ queryKey: ["cards"] });
    },
    onError: () => toast.error("Couldn't reach the card service. Check your connection and retry."),
  });

  const remove = useMutation({
    mutationFn: deleteCard,
    onSuccess: () => {
      toast.success("Card removed from your collection");
      setSelected(null);
      void qc.invalidateQueries({ queryKey: ["cards"] });
    },
    onError: () => toast.error("The card couldn't be deleted. Try again in a moment."),
  });

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return data ?? [];
    return (data ?? []).filter((c) =>
      `${c.name ?? ""} ${c.company ?? ""}`.toLowerCase().includes(q),
    );
  }, [data, query]);

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:py-12">
      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-end gap-4 sm:flex sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold sm:text-3xl">Saved cards</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {data ? `${data.length} record${data.length === 1 ? "" : "s"} in the file` : "Loading the file…"}
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to="/">
            <ScanLine className="size-4" aria-hidden="true" /> Scan a card
          </Link>
        </Button>
      </div>

      <div className="relative mt-6">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name or company"
          aria-label="Search saved cards by name or company"
          className="bg-card pl-9"
        />
      </div>

      {selected && (
        <div className="mt-8">
          <ReviewCard
            card={selected}
            saving={save.isPending || remove.isPending}
            discardLabel="Close"
            onSave={(c) => save.mutate(c)}
            onDiscard={() => setSelected(null)}
            onDelete={() => remove.mutate(selected.id)}
          />
        </div>
      )}

      <div className="mt-8">
        {isLoading && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="aspect-[1.75/1] animate-pulse rounded-lg border border-border bg-card" />
            ))}
          </div>
        )}

        {isError && (
          <div className="rounded-lg border border-signal/40 bg-signal/10 p-6 text-center">
            <p className="text-sm text-signal">
              The card service didn't answer. It may be offline, or the API address may be wrong.
            </p>
            <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
              Try loading again
            </Button>
          </div>
        )}

        {!isLoading && !isError && filtered.length === 0 && (
          <div className="rounded-xl border border-dashed border-border bg-card px-6 py-14 text-center">
            <div className="mx-auto flex w-fit -rotate-2 gap-2">
              <span className="h-16 w-28 rounded-md border border-border bg-background shadow-[var(--shadow-card)]" />
              <span className="h-16 w-28 rotate-3 rounded-md border border-border bg-background shadow-[var(--shadow-card)]" />
            </div>
            <h2 className="mt-6 text-lg font-semibold">
              {query ? "Nothing filed under that name" : "Your card file is empty"}
            </h2>
            <p className="mx-auto mt-2 max-w-sm text-sm text-muted-foreground">
              {query
                ? "Try a shorter search, or check the spelling of the company."
                : "Shoot the next card you're handed and it lands here — name, company and numbers already typed out for you."}
            </p>
            {!query && (
              <Button asChild className="mt-6">
                <Link to="/">Scan your first card</Link>
              </Button>
            )}
          </div>
        )}

        {filtered.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((card) => (
              <CardTile key={card.id} card={card} onOpen={() => setSelected(card)} />
            ))}
          </div>
        )}
      </div>
    </main>
  );
}