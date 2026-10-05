import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { LOW_CONFIDENCE, logoSrc, splitList, type BusinessCard } from "@/lib/api";
import { AlertTriangle, Copy, Trash2, UserPlus } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

interface Props {
  card: BusinessCard;
  previewUrl?: string | null;
  saving?: boolean;
  onSave: (card: BusinessCard) => void;
  onDiscard: () => void;
  onDelete?: () => void;
  discardLabel?: string;
}

function escapeVCardValue(value: string) {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/\r?\n/g, "\\n")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,");
}

function buildVCard(card: BusinessCard) {
  const displayName = card.name?.trim() || card.company?.trim() || "Saved contact";
  const lines = ["BEGIN:VCARD", "VERSION:3.0", `FN:${escapeVCardValue(displayName)}`, "N:;;;;"];

  if (card.company?.trim()) lines.push(`ORG:${escapeVCardValue(card.company.trim())}`);
  if (card.designation?.trim()) lines.push(`TITLE:${escapeVCardValue(card.designation.trim())}`);

  card.phones
    .map((phone) => phone.trim())
    .filter(Boolean)
    .forEach((phone) => {
      lines.push(`TEL;TYPE=CELL,VOICE:${escapeVCardValue(phone)}`);
    });

  card.emails
    .map((email) => email.trim())
    .filter(Boolean)
    .forEach((email) => {
      lines.push(`EMAIL;TYPE=INTERNET:${escapeVCardValue(email)}`);
    });

  if (card.website?.trim()) lines.push(`URL:${escapeVCardValue(card.website.trim())}`);
  if (card.address?.trim()) lines.push(`ADR:;;${escapeVCardValue(card.address.trim())};;;;`);

  lines.push("END:VCARD");
  return lines.join("\r\n");
}

function downloadContact(card: BusinessCard) {
  const vcard = buildVCard(card);
  const contactName =
    (card.name?.trim() || card.company?.trim() || "contact")
      .toLowerCase()
      .replace(/[^a-z0-9]+/gi, "_")
      .replace(/^_+|_+$/g, "") || "contact";

  const blob = new Blob([vcard], { type: "text/vcard;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${contactName}.vcf`;
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function Field({
  label,
  value,
  onChange,
  mono,
  hint,
  copyValue,
  copyLabel,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  mono?: boolean;
  hint?: string;
  copyValue?: string;
  copyLabel?: string;
}) {
  const id = `f-${label.toLowerCase().replace(/\W+/g, "-")}`;
  const handleCopy = async () => {
    if (!copyValue?.trim()) {
      toast.error(`No ${copyLabel ?? label.toLowerCase()} to copy yet.`);
      return;
    }

    try {
      await navigator.clipboard.writeText(copyValue);
      toast.success(`${copyLabel ?? label} copied to clipboard`);
    } catch {
      toast.error(`Couldn't copy ${copyLabel ?? label.toLowerCase()}.`);
    }
  };

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <Label
          htmlFor={id}
          className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground"
        >
          {label}
        </Label>
        {copyValue !== undefined && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            className="h-7 gap-1 px-2 text-[0.68rem] uppercase tracking-[0.12em] text-muted-foreground"
          >
            <Copy className="size-3.5" aria-hidden="true" />
            Copy
          </Button>
        )}
      </div>
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

type VisibleFieldKey = "name" | "designation" | "company" | "website" | "phones" | "emails" | "address";

const FIELD_LABELS: Record<VisibleFieldKey, string> = {
  name: "Name",
  designation: "Designation",
  company: "Company",
  website: "Website",
  phones: "Phones",
  emails: "Emails",
  address: "Address",
};

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
  const [visibleFields, setVisibleFields] = useState<Record<VisibleFieldKey, boolean>>({
    name: Boolean(card.name?.trim()),
    designation: Boolean(card.designation?.trim()),
    company: Boolean(card.company?.trim()),
    website: Boolean(card.website?.trim()),
    phones: card.phones.length > 0,
    emails: card.emails.length > 0,
    address: Boolean(card.address?.trim()),
  });

  useEffect(() => {
    setDraft(card);
    setPhones(card.phones.join(", "));
    setEmails(card.emails.join(", "));
    setVisibleFields({
      name: Boolean(card.name?.trim()),
      designation: Boolean(card.designation?.trim()),
      company: Boolean(card.company?.trim()),
      website: Boolean(card.website?.trim()),
      phones: card.phones.length > 0,
      emails: card.emails.length > 0,
      address: Boolean(card.address?.trim()),
    });
  }, [card]);

  const set = (k: keyof BusinessCard) => (v: string) => setDraft((d) => ({ ...d, [k]: v }));
  const lowConfidence = draft.confidence < LOW_CONFIDENCE;
  const contactCard = { ...draft, phones: splitList(phones), emails: splitList(emails) };
  const hasValue = (key: VisibleFieldKey) => {
    switch (key) {
      case "phones":
        return phones.trim().length > 0;
      case "emails":
        return emails.trim().length > 0;
      case "name":
        return Boolean(draft.name?.trim());
      case "designation":
        return Boolean(draft.designation?.trim());
      case "company":
        return Boolean(draft.company?.trim());
      case "website":
        return Boolean(draft.website?.trim());
      case "address":
        return Boolean(draft.address?.trim());
    }
  };
  const showField = (key: VisibleFieldKey) => visibleFields[key] || hasValue(key);
  const hiddenFields = (Object.keys(FIELD_LABELS) as VisibleFieldKey[]).filter((key) => !showField(key));

  const revealField = (key: VisibleFieldKey) =>
    setVisibleFields((current) => ({
      ...current,
      [key]: true,
    }));

  const handleAddToContacts = () => {
    const hasContactDetails =
      Boolean(contactCard.name?.trim()) ||
      contactCard.phones.length > 0 ||
      contactCard.emails.length > 0;

    if (!hasContactDetails) {
      toast.error("Add a name, phone number, or email before exporting the contact.");
      return;
    }

    downloadContact(contactCard);
    toast.success("Contact file downloaded");
  };

  const ocrDebugVariants = draft.ocrVariantsDebug ?? [];

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

      {draft.originalImageBase64 && (
        <div className="border-b border-border bg-secondary/20 px-5 py-4 sm:px-7">
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground">
              Original card
            </p>
          </div>
          <img
            src={`data:image/jpeg;base64,${draft.originalImageBase64}`}
            alt="Original uploaded business card"
            className="max-h-72 w-full rounded-md border border-border bg-background object-contain"
          />
        </div>
      )}

      <div className="grid gap-4 px-5 py-6 sm:grid-cols-2 sm:px-7">
        {showField("name") && <Field label="Name" value={draft.name ?? ""} onChange={set("name")} />}
        {showField("designation") && (
          <Field label="Designation" value={draft.designation ?? ""} onChange={set("designation")} />
        )}
        {showField("company") && <Field label="Company" value={draft.company ?? ""} onChange={set("company")} />}
        {showField("website") && <Field label="Website" value={draft.website ?? ""} onChange={set("website")} mono />}
        {showField("phones") && (
          <Field
            label="Phones"
            value={phones}
            onChange={setPhones}
            mono
            hint="Separate multiple numbers with commas"
            copyValue={phones.trim() ? phones : undefined}
            copyLabel="phone number"
          />
        )}
        {showField("emails") && (
          <Field
            label="Emails"
            value={emails}
            onChange={setEmails}
            mono
            hint="Separate multiple addresses with commas"
            copyValue={emails.trim() ? emails : undefined}
            copyLabel="email address"
          />
        )}
        {showField("address") && (
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
        )}
      </div>

      {hiddenFields.length > 0 && (
        <div className="border-t border-border px-5 py-4 sm:px-7">
          <p className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground">
            Add missing fields
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {hiddenFields.map((key) => (
              <Button key={key} type="button" variant="outline" size="sm" onClick={() => revealField(key)}>
                Add {FIELD_LABELS[key]}
              </Button>
            ))}
          </div>
        </div>
      )}

      {ocrDebugVariants.length > 0 && (
        <details className="border-t border-border px-5 py-4 sm:px-7">
          <summary className="cursor-pointer text-[0.7rem] font-medium uppercase tracking-[0.14em] text-muted-foreground">
            OCR debug view
          </summary>
          <div className="mt-4 space-y-3">
            {ocrDebugVariants.map((variant) => (
              <div key={variant.variant} className="rounded-md border border-border bg-secondary/20 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium">{variant.variant}</p>
                  <p className="text-xs text-muted-foreground">
                    score {variant.score.toFixed(1)} · confidence {Math.round(variant.avgConfidence * 100)}%
                  </p>
                </div>
                <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-words rounded bg-background p-3 text-xs leading-5 text-foreground">
                  {variant.text || "<no text detected>"}
                </pre>
              </div>
            ))}
          </div>
        </details>
      )}

      <footer className="flex flex-wrap items-center gap-2 border-t border-border bg-secondary/60 px-5 py-4 sm:px-7">
        <Button type="button" variant="outline" onClick={handleAddToContacts} disabled={saving}>
          <UserPlus className="size-4" aria-hidden="true" /> Add to contacts
        </Button>
        <Button
          type="button"
          disabled={saving}
          onClick={() => onSave({ ...draft, phones: splitList(phones), emails: splitList(emails) })}
        >
          {saving ? "Filing…" : "Save to collection"}
        </Button>
        <Button type="button" variant="ghost" onClick={onDiscard} disabled={saving}>
          {discardLabel}
        </Button>
        {onDelete && (
          <Button
            type="button"
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
