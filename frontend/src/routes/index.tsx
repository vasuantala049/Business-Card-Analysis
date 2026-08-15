import { useEffect, useRef, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { UploadZone } from "@/components/UploadZone";
import { CameraCapture } from "@/components/CameraCapture";
import { ReviewCard } from "@/components/ReviewCard";
import { ApiError, updateCard, uploadCard, type BusinessCard } from "@/lib/api";

export const Route = createFileRoute("/")({
  component: Index,
  head: () => ({
    meta: [
      { title: "Scan a business card — Cardfile" },
      {
        name: "description",
        content:
          "Upload or photograph a business card and get name, company, phones, emails and address typed out for review.",
      },
      { property: "og:title", content: "Scan a business card — Cardfile" },
      {
        property: "og:description",
        content:
          "Upload or photograph a business card and get name, company, phones, emails and address typed out for review.",
      },
    ],
  }),
});

function Index() {
  const navigate = useNavigate();
  const [preview, setPreview] = useState<string | null>(null);
  const [card, setCard] = useState<BusinessCard | null>(null);
  const [status, setStatus] = useState<"idle" | "analyzing" | "saving">("idle");
  const [error, setError] = useState<string | null>(null);
  const previewRef = useRef<string | null>(null);

  useEffect(() => () => {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
  }, []);

  const reset = () => {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
    previewRef.current = null;
    setPreview(null);
    setCard(null);
    setError(null);
    setStatus("idle");
  };

  const handleFile = async (file: File) => {
    if (!file.type.startsWith("image/")) {
      setError("That file isn't an image. Use a JPG or PNG photo of the card.");
      return;
    }
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
    const url = URL.createObjectURL(file);
    previewRef.current = url;
    setPreview(url);
    setCard(null);
    setError(null);
    setStatus("analyzing");
    try {
      setCard(await uploadCard(file, file.name || "card.jpg"));
      setStatus("idle");
    } catch (e) {
      setStatus("idle");
      setError(
        e instanceof ApiError
          ? e.status >= 500
            ? "The reader couldn't process this image. Try a sharper, well-lit shot of the whole card."
            : "This image was rejected as unreadable. Fill the frame with the card and avoid glare."
          : "Couldn't reach the card service. Check your connection, then try the scan again.",
      );
    }
  };

  const save = async (edited: BusinessCard) => {
    setStatus("saving");
    try {
      await updateCard(edited);
      toast.success("Card filed");
      reset();
      void navigate({ to: "/gallery" });
    } catch {
      setStatus("idle");
      toast.error("The card couldn't be saved. Check your connection and try again.");
    }
  };

  return (
    <main className="mx-auto max-w-5xl px-4 py-10 sm:py-16">
      {!card && (
        <>
          <div className="mx-auto max-w-xl text-center">
            <p className="field-mono uppercase tracking-[0.2em] text-brass">Card index</p>
            <h1 className="mt-3 text-3xl font-semibold sm:text-4xl">Scan a business card</h1>
            <p className="mt-3 text-sm text-muted-foreground sm:text-base">
              Lay the card flat, capture it, and the details come back typed out and ready to
              correct before filing.
            </p>
          </div>

          {status === "analyzing" ? (
            <div className="mx-auto mt-10 max-w-md rounded-xl border border-border bg-card p-6 text-center shadow-[var(--shadow-card)]">
              {preview && (
                <img
                  src={preview}
                  alt="Preview of the business card being analyzed"
                  className="mx-auto aspect-[1.75/1] w-full rounded-md border border-border object-cover"
                />
              )}
              <p className="mt-5 flex items-center justify-center gap-2 text-sm font-medium">
                <Loader2 className="size-4 animate-spin text-brass" aria-hidden="true" />
                Analyzing card…
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                Reading the text and lifting the logo. This takes a few seconds.
              </p>
            </div>
          ) : (
            <div className="mt-10 grid gap-4 md:grid-cols-2">
              <UploadZone onFile={handleFile} />
              <CameraCapture onCapture={handleFile} />
            </div>
          )}

          {error && (
            <div className="mx-auto mt-6 max-w-xl rounded-lg border border-signal/40 bg-signal/10 p-4 text-center">
              <p className="text-sm text-signal">{error}</p>
              <Button variant="outline" className="mt-3" onClick={reset}>
                Start over
              </Button>
            </div>
          )}
        </>
      )}

      {card && (
        <ReviewCard
          card={card}
          previewUrl={preview}
          saving={status === "saving"}
          onSave={save}
          onDiscard={reset}
          discardLabel="Discard & scan another"
        />
      )}
    </main>
  );
}
