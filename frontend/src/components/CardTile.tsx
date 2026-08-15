import { LOW_CONFIDENCE, logoSrc, type BusinessCard } from "@/lib/api";

export function CardTile({ card, onOpen }: { card: BusinessCard; onOpen: () => void }) {
  const needsReview = card.confidence < LOW_CONFIDENCE;
  const contact = card.emails[0] ?? card.phones[0] ?? card.website ?? "No contact details";

  return (
    <button
      type="button"
      onClick={onOpen}
      className="group relative block aspect-[1.75/1] w-full overflow-hidden rounded-lg border border-border bg-card p-4 text-left shadow-[var(--shadow-card)] transition-transform hover:-translate-y-0.5"
    >
      <span
        aria-hidden="true"
        className={`absolute right-4 top-0 h-5 w-9 rounded-b-[3px] ${
          needsReview ? "bg-signal" : "bg-brass"
        }`}
      />
      <span className="sr-only">{needsReview ? "Needs review" : "Confident extraction"}</span>
      <div className="flex h-full flex-col justify-between">
        <div className="flex min-w-0 items-start gap-3 pr-12">
          {card.logoImageBase64 ? (
            <img
              src={logoSrc(card.logoImageBase64)}
              alt={`Logo of ${card.company ?? "saved business card"}`}
              className="size-8 shrink-0 rounded border border-border object-contain p-0.5"
            />
          ) : null}
          <div className="min-w-0">
            <p className="truncate font-display text-base font-semibold">
              {card.name?.trim() || "Unnamed contact"}
            </p>
            <p className="truncate text-sm text-muted-foreground">
              {card.company?.trim() || card.designation?.trim() || "—"}
            </p>
          </div>
        </div>
        <p className="field-mono truncate text-muted-foreground">{contact}</p>
      </div>
    </button>
  );
}