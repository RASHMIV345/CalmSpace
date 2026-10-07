let token = sessionStorage.getItem("t") || "";
export const setToken = t => { token = t; t ? sessionStorage.setItem("t", t) : sessionStorage.removeItem("t"); };
export const api = async (path, method = "GET", body) => {
  const r = await fetch("/api" + path, {
    method, body: body && JSON.stringify(body),
    headers: { "Content-Type": "application/json", ...(token && { Authorization: "Bearer " + token }) },
  });
  const text = await r.text();
  if (!r.ok) { let m = text; try { m = JSON.parse(text).message || text; } catch {} throw new Error(m || "Something went wrong"); }
  return text ? JSON.parse(text) : null;
};
