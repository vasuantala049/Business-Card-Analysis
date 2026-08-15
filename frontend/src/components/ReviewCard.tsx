import { useEffect, useState } from "react";
import { AlertTriangle, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { LOW_CONFIDENCE, logoSrc, splitList, type BusinessCard } from "@/lib/api";

interface Props {
  card: BusinessCard;
  previewUrl?: string | null;
  saving?: boolean;
  onSave: (card: BusinessCard) => void;
  onDiscard: () => void;
  onDelete?: () => void;
  discardLabel?: string;
}

function Field({
  label,
  value,
  onChange,
  mono,
  hint,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  mono?: boolean;
  hint?: string;
}) {
  const id = `f-${label.toLowerCase().replace(/\W+/g, "-")}`;
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground">
        {label}
      </Label>
      <Input
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={mono ? "field-mono bg-background" : "bg-background"}
      />
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

export function ReviewCard({
  card,
  previewUrl,
  saving,
  onSave,
  onDiscard,
  onDelete,
  discardLabel = "Discard",
}: Props) {
  const [draft, setDraft] = useState(card);
  const [phones, setPhones] = useState(card.phones.join(", "));
  const [emails, setEmails] = useState(card.emails.join(", "));

  useEffect(() => {
    setDraft(card);
    setPhones(card.phones.join(", "));
    setEmails(card.emails.join(", "));
  }, [card]);

  const set = (k: keyof BusinessCard) => (v: string) => setDraft((d) => ({ ...d, [k]: v }));
  const lowConfidence = draft.confidence < LOW_CONFIDENCE;

  return (
    <section className="mx-auto w-full max-w-3xl overflow-hidden rounded-xl border border-border bg-card shadow-[var(--shadow-card)]">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4 sm:px-7">
        <div className="flex min-w-0 items-center gap-3">
          {draft.logoImageBase64 ? (
            <img
              src={logoSrc(draft.logoImageBase64)}
              alt={`Detected logo for ${draft.company ?? "this business card"}`}
              className="size-11 shrink-0 rounded-md border border-border bg-background object-contain p-1"
            />
          ) : null}
          <div className="min-w-0">
            <h2 className="truncate text-lg font-semibold">Review the record</h2>
            <p className="field-mono truncate text-muted-foreground">
              {draft.extractionSource} · {Math.round(draft.confidence * 100)}% confidence
            </p>
          </div>
        </div>
        {previewUrl && (
          <img
            src={previewUrl}
            alt="Preview of the scanned business card"
            className="h-14 w-24 shrink-0 rounded-md border border-border object-cover"
          />
        )}
      </header>

      {lowConfidence && (
        <div className="flex items-start gap-3 border-b border-signal/30 bg-signal/10 px-5 py-3 sm:px-7">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-signal" aria-hidden="true" />
          <p className="text-sm text-signal">
            The reader wasn't sure about this card. Check every field below — names, digits and
            domains are the usual suspects — before you file it.
          </p>
        </div>
      )}

      <div className="grid gap-4 px-5 py-6 sm:grid-cols-2 sm:px-7">
        <Field label="Name" value={draft.name ?? ""} onChange={set("name")} />
        <Field label="Designation" value={draft.designation ?? ""} onChange={set("designation")} />
        <Field label="Company" value={draft.company ?? ""} onChange={set("company")} />
        <Field label="Website" value={draft.website ?? ""} onChange={set("website")} mono />
        <Field
          label="Phones"
          value={phones}
          onChange={setPhones}
          mono
          hint="Separate multiple numbers with commas"
        />
        <Field
          label="Emails"
          value={emails}
          onChange={setEmails}
          mono
          hint="Separate multiple addresses with commas"
        />
        <div className="space-y-1.5 sm:col-span-2">
          <Label
            htmlFor="f-address"
            className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground"
          >
            Address
          </Label>
          <Textarea
            id="f-address"
            rows={3}
            value={draft.address ?? ""}
            onChange={(e) => set("address")(e.target.value)}
            className="field-mono bg-background"
          />
        </div>
      </div>

      <footer className="flex flex-wrap items-center gap-2 border-t border-border bg-secondary/60 px-5 py-4 sm:px-7">
        <Button
          disabled={saving}
          onClick={() =>
            onSave({ ...draft, phones: splitList(phones), emails: splitList(emails) })
          }
        >
          {saving ? "Filing…" : "Save to collection"}
        </Button>
        <Button variant="ghost" onClick={onDiscard} disabled={saving}>
          {discardLabel}
        </Button>
        {onDelete && (
          <Button
            variant="ghost"
            onClick={onDelete}
            disabled={saving}
            className="ml-auto text-signal hover:bg-signal/10 hover:text-signal"
          >
            <Trash2 className="size-4" aria-hidden="true" /> Delete card
          </Button>
        )}
      </footer>
    </section>
  );
}