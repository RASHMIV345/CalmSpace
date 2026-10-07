import { useEffect, useState } from "react";
import { api, setToken } from "./api";
import UserHome from "./User";
import { Therapist, Admin } from "./Staff";

const HELP = [["Tele-MANAS (India, 24x7)", "14416"], ["Emergency", "112"]]; // edit for your region

function Auth({ onAuth }) {
  const [reg, setReg] = useState(false); const [f, setF] = useState({ name: "", email: "", password: "" }); const [err, setErr] = useState("");
  const go = async () => { try { const r = await api(reg ? "/auth/register" : "/auth/login", "POST", f); setToken(r.token); onAuth(r.user); } catch (e) { setErr(e.message); } };
  return (<section className="card"><h2>{reg ? "Create your space" : "Welcome back"}</h2>
    {reg && <input placeholder="Name" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} />}
    <input type="email" placeholder="Email" value={f.email} onChange={e => setF({ ...f, email: e.target.value })} />
    <input type="password" placeholder="Password (8+ characters)" value={f.password} onChange={e => setF({ ...f, password: e.target.value })} />
    {err && <p role="alert" className="err">{err}</p>}
    <button onClick={go}>{reg ? "Sign up" : "Sign in"}</button>
    <button className="link" onClick={() => { setReg(!reg); setErr(""); }}>{reg ? "I already have an account" : "New here? Create an account"}</button></section>);
}

export default function App() {
  const [user, setUser] = useState(null); const [ready, setReady] = useState(false);
  useEffect(() => { api("/me").then(setUser).catch(() => setToken("")).finally(() => setReady(true)); }, []);
  const out = () => { setToken(""); setUser(null); };
  if (!ready) return null;
  return (<div className="app">
    <header><h1>🌿 Calm Space</h1><p>Gentle check-ins. No streaks, no pressure.</p></header>
    {!user ? <Auth onAuth={setUser} /> : <>
      <p className="muted">Hi {user.name} · {user.role.toLowerCase()} · <button className="link" onClick={out}>Sign out</button></p>
      {user.role === "ADMIN" ? <Admin me={user} /> : user.role === "THERAPIST" ? <Therapist /> : <UserHome user={user} setUser={setUser} />}</>}
    <aside className="help"><strong>Need help right now?</strong>
      {HELP.map(([n, p]) => <a key={p} href={"tel:" + p}>{n}: {p}</a>)}
      <small>Calm Space is a self-care aid, not a substitute for professional care.</small></aside></div>);
}
