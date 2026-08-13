import { useState } from "react";
import { updateCard } from "../api/client";

export default function CardResultForm({ card, onSaved }) {
  const [form, setForm] = useState({
    name: card.name || "",
    designation: card.designation || "",
    company: card.company || "",
    phones: (card.phones || []).join(", "),
    emails: (card.emails || []).join(", "),
    website: card.website || "",
    address: card.address || "",
  });
  const [saving, setSaving] = useState(false);

  const handleChange = (field) => (e) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSave = async () => {
    setSaving(true);
    const payload = {
      ...card,
      ...form,
      phones: form.phones.split(",").map((p) => p.trim()).filter(Boolean),
      emails: form.emails.split(",").map((e) => e.trim()).filter(Boolean),
    };
    const res = await updateCard(card.id, payload);
    setSaving(false);
    onSaved?.(res.data);
  };

  const lowConfidence = card.confidence < 0.6;

  return (
    <div className="card-result">
      {card.logoImageBase64 && (
        <img
          className="card-logo"
          src={`data:image/png;base64,${card.logoImageBase64}`}
          alt="Detected logo"
        />
      )}

      {lowConfidence && (
        <p className="confidence-warning">
          OCR confidence is low — please double-check the fields below.
        </p>
      )}

      <label>
        Name
        <input value={form.name} onChange={handleChange("name")} />
      </label>
      <label>
        Designation
        <input value={form.designation} onChange={handleChange("designation")} />
      </label>
      <label>
        Company
        <input value={form.company} onChange={handleChange("company")} />
      </label>
      <label>
        Phone(s)
        <input value={form.phones} onChange={handleChange("phones")} />
      </label>
      <label>
        Email(s)
        <input value={form.emails} onChange={handleChange("emails")} />
      </label>
      <label>
        Website
        <input value={form.website} onChange={handleChange("website")} />
      </label>
      <label>
        Address
        <input value={form.address} onChange={handleChange("address")} />
      </label>

      <button onClick={handleSave} disabled={saving}>
        {saving ? "Saving..." : "Save"}
      </button>
    </div>
  );
}
