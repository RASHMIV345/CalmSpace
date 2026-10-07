import { useEffect, useState } from "react";
import { api } from "./api";

const W = ["⛈️", "🌧️", "⛅", "🌤️", "☀️"];

export function Therapist() {
  const [clients, setClients] = useState([]); const [d, setD] = useState(null); const [s, setS] = useState({ title: "", detail: "" });
  useEffect(() => { api("/therapist/clients").then(setClients).catch(() => {}); }, []);
  const open = id => api("/therapist/clients/" + id).then(setD);
  const add = async () => { await api(`/therapist/clients/${d.client.id}/assignments`, "POST", s); setS({ title: "", detail: "" }); open(d.client.id); };
  return (<><section className="card"><h2>My clients</h2>
    {clients.length === 0 && <p className="muted">No clients assigned yet. An admin assigns clients to you.</p>}
    {clients.map(c => <button key={c.id} className="link" onClick={() => open(c.id)}>{c.name}</button>)}</section>
    {d && <section className="card"><h2>{d.client.name}</h2>
      {d.client.shareConsent ? <div className="garden">{[...d.moods].reverse().map(m => <span key={m.id} title={m.note}>{W[m.mood - 1]}</span>)}</div>
        : <p className="muted">This client hasn't chosen to share check-ins.</p>}
      <h3>Assign a tiny step</h3>
      <input placeholder="e.g. 5-minute walk" value={s.title} onChange={e => setS({ ...s, title: e.target.value })} />
      <textarea rows={2} placeholder="Details" value={s.detail} onChange={e => setS({ ...s, detail: e.target.value })} />
      <button onClick={add}>Assign</button>
      {d.assignments.map(a => <p key={a.id}>{a.done ? "✅" : "⬜"} {a.title}</p>)}</section>}</>);
}

export function Admin({ me }) {
  const [st, setSt] = useState({}); const [users, setUsers] = useState([]); const [f, setF] = useState({ name: "", email: "", password: "" }); const [err, setErr] = useState("");
  const load = () => { api("/admin/stats").then(setSt); api("/admin/users").then(setUsers); };
  useEffect(load, []);
  const run = p => p.then(() => { setErr(""); load(); }).catch(e => setErr(e.message));
  const therapists = users.filter(u => u.role === "THERAPIST");
  return (<><section className="card"><h2>Overview (anonymous)</h2>
    <p>People: {st.users} · Therapists: {st.therapists} · Check-ins this week: {st.checkinsLast7Days} · Average weather: {st.avgMoodLast7Days}/5</p>
    <p className="muted">Admins manage accounts only. Journals and individual moods are never visible here.</p></section>
    <section className="card"><h2>Add therapist</h2>
      {["name", "email", "password"].map(k => <input key={k} type={k === "password" ? "password" : "text"} placeholder={k} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}
      <button onClick={() => run(api("/admin/therapists", "POST", f).then(() => setF({ name: "", email: "", password: "" })))}>Create</button>
      {err && <p role="alert" className="err">{err}</p>}</section>
    <section className="card"><h2>Accounts</h2>
      {users.map(u => <div key={u.id} className="row"><span>{u.name}<br /><small>{u.email}</small></span>
        <select value={u.role} onChange={e => run(api("/admin/users/" + u.id, "PUT", { role: e.target.value, therapistId: u.therapistId }))}>
          {["USER", "THERAPIST", "ADMIN"].map(r => <option key={r}>{r}</option>)}</select>
        {u.role === "USER" && <select value={u.therapistId} onChange={e => run(api("/admin/users/" + u.id, "PUT", { role: u.role, therapistId: +e.target.value }))}>
          <option value={0}>No therapist</option>{therapists.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select>}
        {u.id !== me.id && <button className="link" onClick={() => window.confirm("Delete this account and all its data?") && run(api("/admin/users/" + u.id, "DELETE"))}>Delete</button>}</div>)}</section></>);
}
