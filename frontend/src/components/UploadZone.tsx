import { useRef, useState } from "react";
import { Upload } from "lucide-react";

export function UploadZone({ onFile }: { onFile: (file: File) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);

  const pick = (files: FileList | null) => {
    const file = files?.[0];
    if (file) onFile(file);
  };

  return (
    <div
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        pick(e.dataTransfer.files);
      }}
      className="h-full"
    >
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className={`flex h-full w-full flex-col items-center justify-center gap-3 rounded-lg border border-dashed bg-card p-8 text-center transition-colors ${
          over ? "border-brass bg-accent" : "border-border hover:border-brass"
        }`}
      >
        <span className="grid size-11 place-items-center rounded-full bg-accent text-brass">
          <Upload className="size-5" aria-hidden="true" />
        </span>
        <span className="font-display text-base font-medium">Upload a card image</span>
        <span className="max-w-[24ch] text-sm text-muted-foreground">
          Drag a photo or scan here, or click to browse your files
        </span>
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="sr-only"
        onChange={(e) => pick(e.target.files)}
      />
    </div>
  );
}