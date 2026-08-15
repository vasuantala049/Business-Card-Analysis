import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, CircleDot, X } from "lucide-react";
import { Button } from "@/components/ui/button";

export function CameraCapture({ onCapture }: { onCapture: (file: File) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [active, setActive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setActive(false);
  }, []);

  useEffect(() => stop, [stop]);

  const start = async () => {
    setError(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("This browser can't open a camera. Upload a photo of the card instead.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" } },
        audio: false,
      });
      streamRef.current = stream;
      setActive(true);
      requestAnimationFrame(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          void videoRef.current.play();
        }
      });
    } catch (e) {
      const name = (e as DOMException)?.name;
      setError(
        name === "NotAllowedError"
          ? "Camera access was blocked. Allow camera permission in your browser settings, then try again."
          : name === "NotFoundError"
            ? "No camera was found on this device. Upload a photo of the card instead."
            : "The camera couldn't start. Close other apps using it, then try again.",
      );
    }
  };

  const shoot = () => {
    const video = videoRef.current;
    if (!video) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d")?.drawImage(video, 0, 0);
    canvas.toBlob((blob) => {
      if (!blob) {
        setError("The frame couldn't be captured. Hold steady and try again.");
        return;
      }
      stop();
      onCapture(new File([blob], "camera-card.jpg", { type: "image/jpeg" }));
    }, "image/jpeg", 0.92);
  };

  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 rounded-lg border border-border bg-card p-8 text-center">
      {active ? (
        <div className="w-full space-y-3">
          <div className="overflow-hidden rounded-md border border-border bg-foreground/90">
            <video ref={videoRef} playsInline muted className="aspect-[7/4] w-full object-cover" />
          </div>
          <div className="flex justify-center gap-2">
            <Button onClick={shoot}>
              <CircleDot className="size-4" aria-hidden="true" /> Capture card
            </Button>
            <Button variant="ghost" onClick={stop}>
              <X className="size-4" aria-hidden="true" /> Cancel
            </Button>
          </div>
        </div>
      ) : (
        <>
          <span className="grid size-11 place-items-center rounded-full bg-accent text-brass">
            <Camera className="size-5" aria-hidden="true" />
          </span>
          <span className="font-display text-base font-medium">Capture with camera</span>
          <span className="max-w-[24ch] text-sm text-muted-foreground">
            Use the rear camera to shoot the card flat under even light
          </span>
          <Button variant="outline" onClick={start} className="mt-1">
            Open camera
          </Button>
          {error && <p className="field-mono max-w-[34ch] text-signal">{error}</p>}
        </>
      )}
    </div>
  );
}