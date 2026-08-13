import { useState } from "react";
import { uploadCard } from "./api/client";
import CameraCapture from "./components/CameraCapture";
import CardList from "./components/CardList";
import CardResultForm from "./components/CardResultForm";
import CardUpload from "./components/CardUpload";

export default function App() {
  const [tab, setTab] = useState("scan");
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);

  const handleFile = async (file) => {
    setPreview(URL.createObjectURL(file));
    setResult(null);
    setError(null);
    setUploading(true);
    try {
      const res = await uploadCard(file);
      setResult(res.data);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Could not process this card right now. Please try again later."
      );
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="app">
      <header>
        <h1>Business card analyzer</h1>
        <nav>
          <button
            className={tab === "scan" ? "active" : ""}
            onClick={() => setTab("scan")}
          >
            Scan new card
          </button>
          <button
            className={tab === "list" ? "active" : ""}
            onClick={() => setTab("list")}
          >
            My cards
          </button>
        </nav>
      </header>

      {tab === "scan" && (
        <main>
          <div className="capture-options">
            <CardUpload onFileReady={handleFile} />
            <CameraCapture onFileReady={handleFile} />
          </div>

          {preview && <img className="preview" src={preview} alt="Selected card" />}
          {uploading && <p>Analyzing card...</p>}
          {error && <p className="error-text">{error}</p>}
          {result && <CardResultForm card={result} onSaved={() => setTab("list")} />}
        </main>
      )}

      {tab === "list" && (
        <main>
          <CardList />
        </main>
      )}
    </div>
  );
}
