import { useEffect, useState } from "react";
import { deleteCard, listCards } from "../api/client";

export default function CardList() {
  const [cards, setCards] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listCards();
      setCards(res.data);
    } catch (err) {
      setCards([]);
      setError(
        err?.response?.data?.message ||
          "Could not load saved cards right now. Please try again later."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleDelete = async (id) => {
    await deleteCard(id);
    load();
  };

  if (loading) return <p>Loading saved cards...</p>;
  if (error) return <p className="error-text">{error}</p>;
  if (cards.length === 0) return <p>No cards saved yet.</p>;

  return (
    <ul className="card-list">
      {cards.map((card) => (
        <li key={card.id} className="card-list-item">
          <div>
            <strong>{card.name || "Unnamed"}</strong>
            <span>{card.company}</span>
            <span>{(card.emails || []).join(", ")}</span>
          </div>
          <button onClick={() => handleDelete(card.id)}>Delete</button>
        </li>
      ))}
    </ul>
  );
}
