import { useEffect, useRef, useState } from "react";

export default function CameraCapture({ onFileReady }) {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const [active, setActive] = useState(false);
  const [error, setError] = useState(null);

  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setActive(true);
      setError(null);
    } catch (err) {
      setError("Camera access denied or unavailable.");
    }
  };

  const stop = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    setActive(false);
  };

  const capture = () => {
    const video = videoRef.current;
    if (!video) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d").drawImage(video, 0, 0);

    canvas.toBlob(
      (blob) => {
        if (blob) {
          onFileReady(new File([blob], "capture.jpg", { type: "image/jpeg" }));
        }
      },
      "image/jpeg",
      0.85
    );

    stop();
  };

  useEffect(() => stop, []);

  return (
    <div className="camera-capture">
      {error && <p className="error-text">{error}</p>}
      {active ? (
        <>
          <video ref={videoRef} autoPlay playsInline className="camera-preview" />
          <div className="camera-actions">
            <button onClick={capture}>Capture</button>
            <button onClick={stop}>Cancel</button>
          </div>
        </>
      ) : (
        <button onClick={start}>Use camera</button>
      )}
    </div>
  );
}
