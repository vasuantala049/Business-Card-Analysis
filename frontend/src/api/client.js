import axios from "axios";

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
});

export const uploadCard = (file) => {
  const formData = new FormData();
  formData.append("file", file);
  return client.post("/cards/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const listCards = () => client.get("/cards");

export const updateCard = (id, card) => client.put(`/cards/${id}`, card);

export const deleteCard = (id) => client.delete(`/cards/${id}`);

export default client;
