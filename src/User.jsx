import { useEffect, useState } from "react";
import { api } from "./api";

// Inner weather: moods are weather, and every check-in grows something in your garden.
// Hard days grow things too. There are no streaks, and nothing ever withers.
const WEATHER = [["⛈️", "Stormy", "🍄"], ["🌧️", "Rainy", "🌿"], ["⛅", "Cloudy", "🌱"], ["🌤️", "Mild", "🌷"], ["☀️", "Sunny", "🌻"]];
const PROMPTS = ["What is one small thing that went okay today?", "What am I feeling, and where do I feel it?", "What would I tell a friend who felt this way?", "Name one thing I'm grateful for."];

function Weather() {
  const [list, setList] = useState([]); const [note, setNote] = useState("");
  const load = () => api("/moods").then(setList).catch(() => {});
  useEffect(() => { load(); }, []);
  const save = async k => { await api("/moods", "POST", { mood: k + 1, note }); setNote(""); load(); };
  return (<>
    <section className="card"><h2>What's your inner weather?</h2>
      <div className="moods">{WEATHER.map(([e, l], k) => <button key={k} className="mood" onClick={() => save(k)} aria-label={l}>{e}<small>{l}</small></button>)}</div>
      <input value={note} onChange={e => setNote(e.target.value)} placeholder="Anything to add? (optional)" maxLength={500} />
    </section>
    <section className="card"><h2>Your garden</h2>
      <div className="garden" aria-label="Garden">{list.length ? [...list].reverse().map(m => <span key={m.id} title={m.note || WEATHER[m.mood - 1][1]}>{WEATHER[m.mood - 1][2]}</span>) : <p className="muted">Your first check-in plants the first seed.</p>}</div>
      <p className="muted">Every kind of weather helps something grow.</p>
    </section></>);
}

function Journal() {
  const [list, setList] = useState([]); const [text, setText] = useState(""); const [prompt, setPrompt] = useState(PROMPTS[0]);
  const load = () => api("/journal").then(setList).catch(() => {});
  useEffect(() => { load(); }, []);
  const save = async () => { if (!text.trim()) return; await api("/journal", "POST", { prompt, content: text }); setText(""); load(); };
  return (<section className="card"><h2>Private journal</h2>
    <p className="muted">Only you can read this. Therapists and admins cannot.</p>
    <select value={prompt} onChange={e => setPrompt(e.target.value)}>{PROMPTS.map(p => <option key={p}>{p}</option>)}</select>
    <textarea rows={4} value={text} onChange={e => setText(e.target.value)} maxLength={5000} />
    <button onClick={save}>Save</button>
    {list.map(j => <article key={j.id} className="entry"><em>{j.prompt}</em><p>{j.content}</p>
      <button className="link" onClick={() => api("/journal/" + j.id, "DELETE").then(load)}>Delete</button></article>)}</section>);
}

function Breathing() {
  const [on, setOn] = useState(false); const [ph, setPh] = useState("Ready");
  useEffect(() => {
    if (!on) return setPh("Ready");
    const s = ["Breathe in…", "Hold…", "Breathe out…", "Hold…"]; let i = 0; setPh(s[0]);
    const t = setInterval(() => setPh(s[++i % 4]), 4000); return () => clearInterval(t);
  }, [on]);
  return (<section className="card center"><h2>Box breathing</h2>
    <div className={"orb " + (on ? "run" : "")} aria-live="polite">{ph}</div>
    <button onClick={() => setOn(!on)}>{on ? "Stop" : "Start"}</button></section>);
}

const FIELDS = [["warningSigns", "My warning signs"], ["coping", "Things I can do on my own"], ["people", "People I can reach out to"], ["reasons", "What matters to me"]];
function Plan({ user }) {
  const [p, setP] = useState({}); const [saved, setSaved] = useState(false);
  useEffect(() => { api("/safety-plan").then(setP).catch(() => {}); }, []);
  return (<><Breathing />
    <section className="card"><h2>My safety plan</h2>
      <p className="muted">Write this when you feel okay, so it's ready on a harder day.</p>
      {FIELDS.map(([k, l]) => <label key={k}>{l}<textarea rows={3} maxLength={2000} value={p[k] || ""} onChange={e => { setSaved(false); setP({ ...p, [k]: e.target.value }); }} /></label>)}
      <button onClick={() => api("/safety-plan", "PUT", p).then(() => setSaved(true))}>{saved ? "Saved ✓" : "Save plan"}</button></section></>);
}

function Steps({ user, setUser }) {
  const [list, setList] = useState([]);
  const load = () => api("/assignments").then(setList).catch(() => {});
  useEffect(() => { load(); }, []);
  return (<><section className="card"><h2>Tiny steps</h2>
    {list.length === 0 && <p className="muted">No steps right now. If you have a therapist, small practices they suggest appear here.</p>}
    {list.map(a => <label key={a.id} className="step"><input type="checkbox" checked={a.done} onChange={() => api(`/assignments/${a.id}/done`, "PUT").then(load)} />
      <span><strong>{a.title}</strong><br /><small>{a.detail}</small></span></label>)}</section>
    {user.therapistId > 0 && <section className="card"><h2>Sharing with your therapist</h2>
      <label className="step"><input type="checkbox" checked={user.shareConsent} onChange={e => api("/me/consent", "PUT", { share: e.target.checked }).then(setUser)} />
        <span>Let my therapist see my weather check-ins. Journals stay private. You can turn this off any time.</span></label></section>}</>);
}

export default function UserHome({ user, setUser }) {
  const [tab, setTab] = useState("w");
  const T = [["w", "Weather"], ["j", "Journal"], ["p", "Calm & plan"], ["s", "Steps"]];
  return (<><nav>{T.map(([k, l]) => <button key={k} className={tab === k ? "active" : ""} onClick={() => setTab(k)}>{l}</button>)}</nav>
    {tab === "w" && <Weather />}{tab === "j" && <Journal />}{tab === "p" && <Plan user={user} />}{tab === "s" && <Steps user={user} setUser={setUser} />}</>);
}
