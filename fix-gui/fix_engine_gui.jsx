import { useState, useEffect, useRef, useCallback } from "react";

// ── Palette & helpers ──────────────────────────────────────────────────────
const COLORS = {
  bg: "#0a0d14",
  surface: "#111827",
  card: "#161d2e",
  border: "#1e2d45",
  accent: "#00d4ff",
  accentDim: "#0099bb",
  green: "#00e676",
  red: "#ff3d3d",
  amber: "#ffb300",
  purple: "#a78bfa",
  text: "#e2e8f0",
  muted: "#64748b",
  hover: "#1a2540",
};

// ── Mock Data ──────────────────────────────────────────────────────────────
const MOCK_NODES = [
  { id: "node-1", host: "10.0.1.10", port: 8080, status: "ACTIVE", isLeader: true, cpu: 34, mem: 52, sessions: 3 },
  { id: "node-2", host: "10.0.1.11", port: 8080, status: "STANDBY", isLeader: false, cpu: 12, mem: 41, sessions: 0 },
  { id: "node-3", host: "10.0.1.12", port: 8080, status: "STANDBY", isLeader: false, cpu: 8, mem: 38, sessions: 0 },
];

const MOCK_SESSIONS = [
  { id: "MY_FIRM→BROKER_A", beginString: "FIX.4.4", senderCompId: "MY_FIRM", targetCompId: "BROKER_A", mode: "INITIATOR", host: "192.168.10.50", port: 9876, status: "CONNECTED", nextSenderSeq: 1042, nextTargetSeq: 988, heartbeat: 30, node: "node-1" },
  { id: "MY_FIRM→BROKER_B", beginString: "FIX.4.4", senderCompId: "MY_FIRM", targetCompId: "BROKER_B", mode: "ACCEPTOR", host: "", port: 9877, status: "CONNECTED", nextSenderSeq: 257, nextTargetSeq: 249, heartbeat: 30, node: "node-1" },
  { id: "MY_FIRM→EXCHANGE_C", beginString: "FIX.4.2", senderCompId: "MY_FIRM", targetCompId: "EXCHANGE_C", mode: "INITIATOR", host: "10.20.30.40", port: 5001, status: "DISCONNECTED", nextSenderSeq: 88, nextTargetSeq: 75, heartbeat: 20, node: "node-1" },
  { id: "MY_FIRM→OMS_D", beginString: "FIX.5.0", senderCompId: "MY_FIRM", targetCompId: "OMS_D", mode: "ACCEPTOR", host: "", port: 9878, status: "CONNECTING", nextSenderSeq: 3, nextTargetSeq: 1, heartbeat: 30, node: "node-1" },
];

const MSG_TYPES = { D: "NewOrderSingle", 8: "ExecutionReport", G: "OrderCancelReplace", F: "OrderCancelRequest", V: "MarketDataRequest", W: "MarketDataSnap", "0": "Heartbeat", A: "Logon", "5": "Logout" };

function randSeq() { return Math.floor(Math.random() * 5000) + 1; }
function randPx() { return (Math.random() * 200 + 10).toFixed(2); }
function randQty() { return Math.floor(Math.random() * 1000) * 100; }

const SYMBOLS = ["AAPL", "MSFT", "GOOG", "AMZN", "TSLA", "NVDA", "META", "JPM", "GS", "BAC"];
const SIDES = ["1", "2"];
const DIR = ["I", "O"];
const SESSIONS_IDS = MOCK_SESSIONS.map(s => s.id);
const ALL_MSG_TYPES = Object.keys(MSG_TYPES);

function genMessage(i) {
  const msgType = ALL_MSG_TYPES[i % ALL_MSG_TYPES.length];
  const sessionId = SESSIONS_IDS[i % SESSIONS_IDS.length];
  const [sender, target] = sessionId.split("→");
  const dir = DIR[i % 2];
  const sym = SYMBOLS[i % SYMBOLS.length];
  const side = SIDES[i % 2];
  const seq = 1000 + i;
  const ts = new Date(Date.now() - (500 - i) * 60000);

  const tags = {
    8: "FIX.4.4", 9: "148", 35: msgType, 49: dir === "O" ? sender : target,
    56: dir === "O" ? target : sender, 34: seq, 52: ts.toISOString().replace("T", "-").slice(0, 21),
    ...(msgType === "D" ? { 11: `ORD-${1000 + i}`, 55: sym, 54: side, 38: randQty(), 44: randPx(), 40: "2", 59: "0" } : {}),
    ...(msgType === "8" ? { 11: `ORD-${900 + i}`, 37: `EXEC-${2000 + i}`, 17: `${3000 + i}`, 150: "2", 39: "2", 55: sym, 54: side, 38: randQty(), 32: randQty(), 31: randPx(), 151: "0", 14: randQty(), 6: randPx() } : {}),
    10: "123",
  };

  const raw = Object.entries(tags).map(([k, v]) => `${k}=${v}`).join("|");
  return { id: i + 1, sessionId, senderCompId: sender, targetCompId: target, msgType, msgTypeName: MSG_TYPES[msgType] || msgType, seqNum: seq, direction: dir, raw, tags, sendingTime: ts.toISOString() };
}

const MOCK_MESSAGES = Array.from({ length: 200 }, (_, i) => genMessage(i));

// ── Reusable Components ────────────────────────────────────────────────────
const css = (obj) => Object.entries(obj).map(([k, v]) => `${k.replace(/([A-Z])/g, m => `-${m.toLowerCase()}`)}:${v}`).join(";");

function Badge({ label, color }) {
  const c = color === "green" ? COLORS.green : color === "red" ? COLORS.red : color === "amber" ? COLORS.amber : color === "purple" ? COLORS.purple : COLORS.accent;
  return (
    <span style={{ background: c + "22", color: c, border: `1px solid ${c}44`, borderRadius: 4, padding: "2px 8px", fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>
      {label}
    </span>
  );
}

function StatusBadge({ status }) {
  const map = { CONNECTED: "green", DISCONNECTED: "red", CONNECTING: "amber", ACTIVE: "green", STANDBY: "purple", DOWN: "red", STANDBY_WARM: "amber" };
  return <Badge label={status} color={map[status] || "accent"} />;
}

function Btn({ children, onClick, variant = "primary", small, disabled }) {
  const bg = variant === "primary" ? COLORS.accent : variant === "danger" ? COLORS.red : variant === "ghost" ? "transparent" : COLORS.surface;
  const col = variant === "primary" ? "#000" : variant === "danger" ? "#fff" : COLORS.text;
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: bg, color: col, border: `1px solid ${variant === "ghost" ? COLORS.border : bg}`,
      borderRadius: 6, padding: small ? "4px 10px" : "7px 16px", fontSize: small ? 11 : 13, fontWeight: 600,
      cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.5 : 1,
      transition: "all 0.15s", letterSpacing: 0.3,
    }}>
      {children}
    </button>
  );
}

function Card({ children, style }) {
  return (
    <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 10, padding: 16, ...style }}>
      {children}
    </div>
  );
}

function Input({ value, onChange, placeholder, style }) {
  return (
    <input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
      style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 6, color: COLORS.text, padding: "6px 10px", fontSize: 13, outline: "none", ...style }} />
  );
}

function Select({ value, onChange, options, style }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 6, color: COLORS.text, padding: "6px 10px", fontSize: 13, outline: "none", ...style }}>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

// ── Node Status Panel ──────────────────────────────────────────────────────
function NodePanel({ nodes, onSimulateFailover }) {
  return (
    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
      {nodes.map(n => (
        <Card key={n.id} style={{ flex: 1, minWidth: 200 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 14, color: COLORS.text }}>{n.id}</div>
              <div style={{ color: COLORS.muted, fontSize: 12 }}>{n.host}:{n.port}</div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 4, alignItems: "flex-end" }}>
              <StatusBadge status={n.status} />
              {n.isLeader && <Badge label="LEADER" color="accent" />}
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
            {[["CPU", n.cpu + "%", n.cpu > 80 ? COLORS.red : COLORS.green],
              ["MEM", n.mem + "%", n.mem > 80 ? COLORS.red : COLORS.amber],
              ["SESS", n.sessions, COLORS.accent]].map(([label, val, color]) => (
              <div key={label} style={{ background: COLORS.bg, borderRadius: 6, padding: "6px 8px", textAlign: "center" }}>
                <div style={{ color: COLORS.muted, fontSize: 10, marginBottom: 2 }}>{label}</div>
                <div style={{ color, fontWeight: 700, fontSize: 16, fontFamily: "monospace" }}>{val}</div>
              </div>
            ))}
          </div>
          {n.status === "ACTIVE" && (
            <button onClick={() => onSimulateFailover(n.id)}
              style={{ marginTop: 10, width: "100%", background: COLORS.red + "22", color: COLORS.red, border: `1px solid ${COLORS.red}44`, borderRadius: 6, padding: "5px", fontSize: 11, cursor: "pointer", fontWeight: 600 }}>
              ⚡ Simulate Failover
            </button>
          )}
        </Card>
      ))}
    </div>
  );
}

// ── Session Management ─────────────────────────────────────────────────────
function SessionRow({ session, onAction }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <>
      <tr onClick={() => setExpanded(!expanded)}
        style={{ cursor: "pointer", borderBottom: `1px solid ${COLORS.border}`, background: expanded ? COLORS.hover : "transparent", transition: "background 0.1s" }}>
        <td style={{ padding: "10px 12px", fontSize: 13, color: COLORS.accent, fontFamily: "monospace" }}>{session.id}</td>
        <td style={{ padding: "10px 12px" }}><Badge label={session.mode} color={session.mode === "INITIATOR" ? "accent" : "purple"} /></td>
        <td style={{ padding: "10px 12px" }}><StatusBadge status={session.status} /></td>
        <td style={{ padding: "10px 12px", fontSize: 12, color: COLORS.muted, fontFamily: "monospace" }}>{session.beginString}</td>
        <td style={{ padding: "10px 12px", fontSize: 12, color: COLORS.muted }}>{session.host || "—"}:{session.port}</td>
        <td style={{ padding: "10px 12px", fontSize: 12, fontFamily: "monospace", color: COLORS.green }}>{session.nextSenderSeq}</td>
        <td style={{ padding: "10px 12px", fontSize: 12, fontFamily: "monospace", color: COLORS.amber }}>{session.nextTargetSeq}</td>
        <td style={{ padding: "10px 12px" }}>
          <div style={{ display: "flex", gap: 6 }}>
            <Btn small onClick={e => { e.stopPropagation(); onAction(session.id, "start"); }}>▶ Start</Btn>
            <Btn small variant="ghost" onClick={e => { e.stopPropagation(); onAction(session.id, "stop"); }}>■ Stop</Btn>
            <Btn small variant="danger" onClick={e => { e.stopPropagation(); onAction(session.id, "reset"); }}>↺ Reset</Btn>
          </div>
        </td>
      </tr>
      {expanded && (
        <tr style={{ background: COLORS.bg }}>
          <td colSpan={8} style={{ padding: "12px 20px" }}>
            <SessionEditor session={session} onAction={onAction} />
          </td>
        </tr>
      )}
    </>
  );
}

function SessionEditor({ session, onAction }) {
  const [form, setForm] = useState({ ...session });
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }));
  return (
    <div>
      <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 10, fontWeight: 700, letterSpacing: 1 }}>SESSION CONFIGURATION</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 }}>
        {[["Begin String", "beginString"], ["Sender CompID", "senderCompId"], ["Target CompID", "targetCompId"], ["Host / IP", "host"]].map(([label, key]) => (
          <div key={key}>
            <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>{label}</div>
            <Input value={form[key]} onChange={set(key)} style={{ width: "100%", boxSizing: "border-box" }} />
          </div>
        ))}
        <div>
          <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>Port</div>
          <Input value={form.port} onChange={set("port")} style={{ width: "100%", boxSizing: "border-box" }} />
        </div>
        <div>
          <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>Mode</div>
          <Select value={form.mode} onChange={set("mode")} options={[{ value: "INITIATOR", label: "Initiator" }, { value: "ACCEPTOR", label: "Acceptor" }]} style={{ width: "100%" }} />
        </div>
        <div>
          <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>Heartbeat (s)</div>
          <Input value={form.heartbeat} onChange={set("heartbeat")} style={{ width: "100%", boxSizing: "border-box" }} />
        </div>
        <div style={{ display: "flex", alignItems: "flex-end" }}>
          <Btn onClick={() => onAction(session.id, "update", form)}>💾 Save Config</Btn>
        </div>
      </div>
    </div>
  );
}

function SessionsView({ sessions, onAction, onAddSession }) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <h2 style={{ color: COLORS.text, margin: 0, fontSize: 18 }}>FIX Sessions</h2>
        <Btn onClick={onAddSession}>+ New Session</Btn>
      </div>
      <Card style={{ padding: 0, overflow: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ background: COLORS.bg, borderBottom: `1px solid ${COLORS.border}` }}>
              {["Session ID", "Mode", "Status", "FIX Version", "Host:Port", "Next Sender Seq", "Next Target Seq", "Actions"].map(h => (
                <th key={h} style={{ padding: "10px 12px", textAlign: "left", color: COLORS.muted, fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sessions.map(s => <SessionRow key={s.id} session={s} onAction={onAction} />)}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

// ── Message Grid & Detail ──────────────────────────────────────────────────
function MessageGrid({ messages, selectedMsg, onSelect, filter, onFilterChange }) {
  const dirColor = (d) => d === "O" ? COLORS.accent : COLORS.purple;
  const typeColor = (t) => ({ D: COLORS.green, "8": COLORS.amber, G: COLORS.accent, F: COLORS.red, A: COLORS.purple, "5": COLORS.red, "0": COLORS.muted }[t] || COLORS.text);

  const filtered = messages.filter(m => {
    if (filter.session && !m.sessionId.toLowerCase().includes(filter.session.toLowerCase())) return false;
    if (filter.msgType && m.msgType !== filter.msgType) return false;
    if (filter.direction && m.direction !== filter.direction) return false;
    if (filter.search && !m.raw.toLowerCase().includes(filter.search.toLowerCase()) && !m.msgTypeName.toLowerCase().includes(filter.search.toLowerCase())) return false;
    return true;
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      {/* Filter bar */}
      <div style={{ display: "flex", gap: 8, marginBottom: 10, flexWrap: "wrap" }}>
        <Input value={filter.search} onChange={v => onFilterChange("search", v)} placeholder="🔍 Search messages..." style={{ flex: 2, minWidth: 140 }} />
        <Select value={filter.session} onChange={v => onFilterChange("session", v)}
          options={[{ value: "", label: "All Sessions" }, ...SESSIONS_IDS.map(s => ({ value: s, label: s }))]}
          style={{ flex: 2, minWidth: 140 }} />
        <Select value={filter.msgType} onChange={v => onFilterChange("msgType", v)}
          options={[{ value: "", label: "All Types" }, ...Object.entries(MSG_TYPES).map(([k, v]) => ({ value: k, label: `${k} – ${v}` }))]}
          style={{ flex: 2, minWidth: 140 }} />
        <Select value={filter.direction} onChange={v => onFilterChange("direction", v)}
          options={[{ value: "", label: "In + Out" }, { value: "I", label: "Inbound" }, { value: "O", label: "Outbound" }]}
          style={{ flex: 1, minWidth: 100 }} />
        <div style={{ color: COLORS.muted, fontSize: 12, alignSelf: "center", whiteSpace: "nowrap" }}>{filtered.length} msgs</div>
      </div>

      {/* Grid */}
      <div style={{ flex: 1, overflowY: "auto", borderRadius: 8, border: `1px solid ${COLORS.border}` }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead style={{ position: "sticky", top: 0, background: COLORS.bg, zIndex: 1 }}>
            <tr>
              {["#", "Time", "Session", "Dir", "Type", "SeqNum", "Sender", "Target"].map(h => (
                <th key={h} style={{ padding: "8px 10px", textAlign: "left", color: COLORS.muted, fontSize: 10, fontWeight: 700, letterSpacing: 0.5, borderBottom: `1px solid ${COLORS.border}` }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.slice(0, 150).map(m => (
              <tr key={m.id} onClick={() => onSelect(m)}
                style={{ borderBottom: `1px solid ${COLORS.border}22`, cursor: "pointer", background: selectedMsg?.id === m.id ? COLORS.hover : "transparent", transition: "background 0.1s" }}>
                <td style={{ padding: "7px 10px", color: COLORS.muted, fontFamily: "monospace" }}>{m.id}</td>
                <td style={{ padding: "7px 10px", color: COLORS.muted, fontFamily: "monospace", whiteSpace: "nowrap" }}>{new Date(m.sendingTime).toLocaleTimeString()}</td>
                <td style={{ padding: "7px 10px", color: COLORS.accent, maxWidth: 140, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{m.sessionId}</td>
                <td style={{ padding: "7px 10px" }}>
                  <span style={{ color: dirColor(m.direction), fontWeight: 700, fontSize: 10 }}>{m.direction === "I" ? "▼ IN" : "▲ OUT"}</span>
                </td>
                <td style={{ padding: "7px 10px" }}>
                  <span style={{ color: typeColor(m.msgType), fontFamily: "monospace", fontWeight: 600 }}>{m.msgType}</span>
                  <span style={{ color: COLORS.muted, marginLeft: 4 }}>{m.msgTypeName}</span>
                </td>
                <td style={{ padding: "7px 10px", fontFamily: "monospace", color: COLORS.text }}>{m.seqNum}</td>
                <td style={{ padding: "7px 10px", color: COLORS.text }}>{m.senderCompId}</td>
                <td style={{ padding: "7px 10px", color: COLORS.text }}>{m.targetCompId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const TAG_NAMES = {
  8: "BeginString", 9: "BodyLength", 35: "MsgType", 49: "SenderCompID", 56: "TargetCompID",
  34: "MsgSeqNum", 52: "SendingTime", 11: "ClOrdID", 55: "Symbol", 54: "Side", 38: "OrderQty",
  44: "Price", 40: "OrdType", 59: "TimeInForce", 37: "OrderID", 17: "ExecID", 150: "ExecType",
  39: "OrdStatus", 32: "LastQty", 31: "LastPx", 151: "LeavesQty", 14: "CumQty", 6: "AvgPx",
  10: "CheckSum", 58: "Text",
};

const TAG_VALUES = {
  35: { D: "NewOrderSingle", "8": "ExecutionReport", G: "OrderCancelReplace", F: "OrderCancelRequest", "0": "Heartbeat", A: "Logon", "5": "Logout" },
  54: { "1": "Buy", "2": "Sell" },
  40: { "1": "Market", "2": "Limit", "3": "Stop" },
  59: { "0": "Day", "1": "GTC", "3": "IOC", "4": "FOK" },
  150: { "0": "New", "1": "PartFill", "2": "Fill", "4": "Cancelled", "8": "Rejected" },
  39: { "0": "New", "1": "PartFill", "2": "Fill", "4": "Cancelled", "8": "Rejected" },
};

function MessageDetail({ message }) {
  if (!message) return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: COLORS.muted, fontSize: 14, textAlign: "center" }}>
      <div>
        <div style={{ fontSize: 40, marginBottom: 8 }}>📩</div>
        Click a message to inspect its FIX tags
      </div>
    </div>
  );

  const tags = message.tags;
  const groups = [
    { label: "Header", keys: [8, 9, 35, 49, 56, 34, 52] },
    { label: "Body", keys: Object.keys(tags).map(Number).filter(k => ![8, 9, 35, 49, 56, 34, 52, 10].includes(k)) },
    { label: "Trailer", keys: [10] },
  ];

  return (
    <div style={{ height: "100%", overflowY: "auto" }}>
      <div style={{ marginBottom: 14 }}>
        <div style={{ color: COLORS.accent, fontWeight: 700, fontSize: 14, marginBottom: 4 }}>{message.msgTypeName}</div>
        <div style={{ color: COLORS.muted, fontSize: 11 }}>Session: {message.sessionId} | Seq: {message.seqNum} | {new Date(message.sendingTime).toLocaleString()}</div>
      </div>

      {/* Raw message */}
      <div style={{ background: COLORS.bg, borderRadius: 6, padding: 10, marginBottom: 14, fontFamily: "monospace", fontSize: 11, color: COLORS.muted, wordBreak: "break-all", lineHeight: 1.6 }}>
        {message.raw.split("|").map((part, i) => {
          const [tag] = part.split("=");
          return (
            <span key={i}>
              <span style={{ color: COLORS.accent }}>{tag}</span>
              <span style={{ color: COLORS.border }}>=</span>
              <span style={{ color: COLORS.text }}>{part.split("=")[1]}</span>
              <span style={{ color: COLORS.border }}>|</span>
            </span>
          );
        })}
      </div>

      {/* Parsed tags */}
      {groups.map(g => {
        const entries = g.keys.filter(k => tags[k] !== undefined);
        if (!entries.length) return null;
        return (
          <div key={g.label} style={{ marginBottom: 14 }}>
            <div style={{ color: COLORS.muted, fontSize: 10, fontWeight: 700, letterSpacing: 1, marginBottom: 6, textTransform: "uppercase" }}>{g.label}</div>
            <div style={{ background: COLORS.bg, borderRadius: 6, overflow: "hidden" }}>
              {entries.map((tag, i) => {
                const val = String(tags[tag]);
                const enumVal = TAG_VALUES[tag]?.[val];
                return (
                  <div key={tag} style={{ display: "flex", borderBottom: i < entries.length - 1 ? `1px solid ${COLORS.border}22` : "none", alignItems: "center" }}>
                    <div style={{ width: 44, padding: "7px 10px", color: COLORS.accent, fontFamily: "monospace", fontSize: 12, fontWeight: 700, flexShrink: 0 }}>{tag}</div>
                    <div style={{ flex: 1, padding: "7px 10px", color: COLORS.muted, fontSize: 11, borderLeft: `1px solid ${COLORS.border}22` }}>{TAG_NAMES[tag] || `Tag${tag}`}</div>
                    <div style={{ flex: 2, padding: "7px 10px", color: COLORS.text, fontFamily: "monospace", fontSize: 12 }}>
                      {val}
                      {enumVal && <span style={{ color: COLORS.muted, marginLeft: 6 }}>({enumVal})</span>}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ── Add Session Modal ──────────────────────────────────────────────────────
function AddSessionModal({ onClose, onSave }) {
  const [form, setForm] = useState({ beginString: "FIX.4.4", senderCompId: "MY_FIRM", targetCompId: "", mode: "INITIATOR", host: "", port: "", heartbeat: 30 });
  const set = k => v => setForm(f => ({ ...f, [k]: v }));
  return (
    <div style={{ position: "fixed", inset: 0, background: "#000a", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <Card style={{ width: 480, background: COLORS.surface }}>
        <div style={{ fontWeight: 700, fontSize: 16, color: COLORS.text, marginBottom: 16 }}>New FIX Session</div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 16 }}>
          {[["Begin String", "beginString"], ["Sender CompID", "senderCompId"], ["Target CompID", "targetCompId"], ["Host / IP", "host"], ["Port", "port"], ["Heartbeat (s)", "heartbeat"]].map(([label, key]) => (
            <div key={key}>
              <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>{label}</div>
              <Input value={form[key]} onChange={set(key)} style={{ width: "100%", boxSizing: "border-box" }} />
            </div>
          ))}
          <div>
            <div style={{ color: COLORS.muted, fontSize: 11, marginBottom: 4 }}>Mode</div>
            <Select value={form.mode} onChange={set("mode")} options={[{ value: "INITIATOR", label: "Initiator" }, { value: "ACCEPTOR", label: "Acceptor" }]} style={{ width: "100%" }} />
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
          <Btn onClick={() => { onSave(form); onClose(); }}>Create Session</Btn>
        </div>
      </Card>
    </div>
  );
}

// ── Cluster Config View ────────────────────────────────────────────────────
function ClusterView({ nodes, onSimulateFailover }) {
  return (
    <div>
      <h2 style={{ color: COLORS.text, margin: "0 0 16px", fontSize: 18 }}>Cluster Topology</h2>
      <NodePanel nodes={nodes} onSimulateFailover={onSimulateFailover} />

      <div style={{ marginTop: 20 }}>
        <h3 style={{ color: COLORS.text, fontSize: 15, marginBottom: 12 }}>Failover Architecture</h3>
        <Card>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 }}>
            {[
              { title: "Leader Election", icon: "🏆", desc: "Apache ZooKeeper manages leader election across all 3 nodes. When the active leader node goes down, ZooKeeper detects the session timeout (~5s) and promotes the fastest standby." },
              { title: "Distributed Cache", icon: "🗄️", desc: "Hazelcast IMap replicates all FIX session state (sequence numbers, logon status) across all 3 nodes with backup-count=2. Full data survives any single node failure." },
              { title: "Session Recovery", icon: "🔄", desc: "On failover, the new leader reads session state from Hazelcast and reconnects each FIX session using the persisted sequence numbers, ensuring no message gaps." },
            ].map(({ title, icon, desc }) => (
              <div key={title} style={{ background: COLORS.bg, borderRadius: 8, padding: 14 }}>
                <div style={{ fontSize: 24, marginBottom: 8 }}>{icon}</div>
                <div style={{ fontWeight: 700, color: COLORS.accent, marginBottom: 6, fontSize: 13 }}>{title}</div>
                <div style={{ color: COLORS.muted, fontSize: 12, lineHeight: 1.6 }}>{desc}</div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <div style={{ marginTop: 20 }}>
        <h3 style={{ color: COLORS.text, fontSize: 15, marginBottom: 12 }}>Node Configuration</h3>
        {nodes.map(node => (
          <Card key={node.id} style={{ marginBottom: 10 }}>
            <div style={{ display: "flex", gap: 20, flexWrap: "wrap", alignItems: "center" }}>
              <div style={{ minWidth: 100 }}><StatusBadge status={node.status} /></div>
              {[["Node ID", node.id], ["Host", node.host], ["API Port", node.port], ["ZK Port", "2181"], ["HZ Port", "5701"]].map(([k, v]) => (
                <div key={k}>
                  <div style={{ color: COLORS.muted, fontSize: 10, marginBottom: 2 }}>{k}</div>
                  <Input value={String(v)} onChange={() => { }} style={{ width: 120, fontFamily: "monospace" }} />
                </div>
              ))}
              <Btn small>💾 Update</Btn>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

// ── Messages View ──────────────────────────────────────────────────────────
function MessagesView({ messages }) {
  const [selected, setSelected] = useState(null);
  const [filter, setFilter] = useState({ search: "", session: "", msgType: "", direction: "" });
  const setF = (k, v) => setFilter(f => ({ ...f, [k]: v }));
  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <h2 style={{ color: COLORS.text, margin: "0 0 12px", fontSize: 18 }}>Message Log</h2>
      <div style={{ display: "flex", gap: 12, flex: 1, minHeight: 0 }}>
        <div style={{ flex: 3, display: "flex", flexDirection: "column", minWidth: 0 }}>
          <MessageGrid messages={messages} selectedMsg={selected} onSelect={setSelected} filter={filter} onFilterChange={setF} />
        </div>
        <div style={{ flex: 2, minWidth: 300 }}>
          <Card style={{ height: "100%", boxSizing: "border-box" }}>
            <div style={{ color: COLORS.muted, fontSize: 11, fontWeight: 700, letterSpacing: 1, marginBottom: 12 }}>MESSAGE INSPECTOR</div>
            <MessageDetail message={selected} />
          </Card>
        </div>
      </div>
    </div>
  );
}

// ── Live Feed ──────────────────────────────────────────────────────────────
function LiveFeed({ messages }) {
  const [live, setLive] = useState([]);
  const [running, setRunning] = useState(true);
  const ref = useRef(0);
  const containerRef = useRef(null);

  useEffect(() => {
    if (!running) return;
    const interval = setInterval(() => {
      const msg = messages[ref.current % messages.length];
      ref.current++;
      setLive(prev => [{ ...msg, liveId: ref.current, liveTime: new Date().toLocaleTimeString() }, ...prev].slice(0, 50));
    }, 800);
    return () => clearInterval(interval);
  }, [running, messages]);

  const typeColor = (t) => ({ D: COLORS.green, "8": COLORS.amber, G: COLORS.accent, F: COLORS.red, A: COLORS.purple, "5": COLORS.red, "0": COLORS.muted }[t] || COLORS.text);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h2 style={{ color: COLORS.text, margin: 0, fontSize: 18 }}>Live Message Feed</h2>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <div style={{ width: 8, height: 8, borderRadius: "50%", background: running ? COLORS.green : COLORS.red, boxShadow: running ? `0 0 8px ${COLORS.green}` : "none" }} />
          <span style={{ color: COLORS.muted, fontSize: 12 }}>{running ? "LIVE" : "PAUSED"}</span>
          <Btn small onClick={() => setRunning(r => !r)} variant={running ? "danger" : "primary"}>
            {running ? "⏸ Pause" : "▶ Resume"}
          </Btn>
          <Btn small variant="ghost" onClick={() => setLive([])}>Clear</Btn>
        </div>
      </div>
      <Card style={{ flex: 1, overflowY: "auto", padding: 0 }}>
        {live.map((m, i) => (
          <div key={m.liveId} style={{ display: "flex", gap: 12, padding: "8px 14px", borderBottom: `1px solid ${COLORS.border}22`, alignItems: "center", background: i === 0 ? COLORS.accent + "08" : "transparent", transition: "background 0.5s", animation: i === 0 ? "fadeIn 0.3s ease" : "none" }}>
            <span style={{ color: COLORS.muted, fontFamily: "monospace", fontSize: 11, width: 80, flexShrink: 0 }}>{m.liveTime}</span>
            <span style={{ color: m.direction === "O" ? COLORS.accent : COLORS.purple, fontSize: 10, fontWeight: 700, width: 40 }}>{m.direction === "O" ? "▲ OUT" : "▼ IN"}</span>
            <span style={{ color: typeColor(m.msgType), fontFamily: "monospace", fontSize: 12, fontWeight: 700, width: 50 }}>{m.msgType}</span>
            <span style={{ color: COLORS.muted, fontSize: 11, flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{m.msgTypeName}</span>
            <span style={{ color: COLORS.accent, fontSize: 11, maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{m.sessionId}</span>
            <span style={{ color: COLORS.muted, fontFamily: "monospace", fontSize: 11, width: 60 }}>#{m.seqNum}</span>
          </div>
        ))}
        {!live.length && <div style={{ color: COLORS.muted, textAlign: "center", padding: 40, fontSize: 13 }}>Waiting for messages…</div>}
      </Card>
      <style>{`@keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: none; } }`}</style>
    </div>
  );
}

// ── Main App ───────────────────────────────────────────────────────────────
const TABS = [
  { id: "sessions", label: "Sessions", icon: "⚡" },
  { id: "messages", label: "Messages", icon: "📋" },
  { id: "live", label: "Live Feed", icon: "📡" },
  { id: "cluster", label: "Cluster", icon: "🌐" },
];

export default function App() {
  const [tab, setTab] = useState("sessions");
  const [nodes, setNodes] = useState(MOCK_NODES);
  const [sessions, setSessions] = useState(MOCK_SESSIONS);
  const [showAddModal, setShowAddModal] = useState(false);
  const [toast, setToast] = useState(null);

  const showToast = (msg, color = COLORS.green) => {
    setToast({ msg, color });
    setTimeout(() => setToast(null), 3000);
  };

  const handleSessionAction = (sessionId, action, data) => {
    setSessions(prev => prev.map(s => {
      if (s.id !== sessionId) return s;
      if (action === "start") return { ...s, status: "CONNECTING" };
      if (action === "stop") return { ...s, status: "DISCONNECTED" };
      if (action === "reset") return { ...s, nextSenderSeq: 1, nextTargetSeq: 1, status: "DISCONNECTED" };
      if (action === "update") return { ...s, ...data };
      return s;
    }));
    showToast(`Session ${sessionId}: ${action.toUpperCase()} executed`);
  };

  const handleAddSession = (form) => {
    const newId = `${form.senderCompId}→${form.targetCompId}`;
    setSessions(prev => [...prev, { ...form, id: newId, status: "DISCONNECTED", nextSenderSeq: 1, nextTargetSeq: 1, node: "node-1" }]);
    showToast("New session created");
  };

  const handleSimulateFailover = (nodeId) => {
    setNodes(prev => {
      const standbyNodes = prev.filter(n => n.id !== nodeId && n.status === "STANDBY");
      const newLeaderId = standbyNodes[0]?.id;
      return prev.map(n => {
        if (n.id === nodeId) return { ...n, status: "DOWN", isLeader: false, cpu: 0, mem: 0, sessions: 0 };
        if (n.id === newLeaderId) return { ...n, status: "ACTIVE", isLeader: true, sessions: 3 };
        return n;
      });
    });
    showToast(`⚡ Failover triggered! ${nodeId} is DOWN — new leader elected.`, COLORS.amber);
  };

  const connectedCount = sessions.filter(s => s.status === "CONNECTED").length;
  const msgPerSec = "~142 msg/s";

  return (
    <div style={{ background: COLORS.bg, minHeight: "100vh", color: COLORS.text, fontFamily: "'IBM Plex Mono', 'Courier New', monospace", display: "flex", flexDirection: "column" }}>
      {/* Header */}
      <div style={{ background: COLORS.surface, borderBottom: `1px solid ${COLORS.border}`, padding: "0 20px", display: "flex", alignItems: "center", gap: 20, height: 54 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 28, height: 28, background: COLORS.accent, borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 14, fontWeight: 900, color: "#000" }}>F</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 14, letterSpacing: 1, color: COLORS.text }}>FIX ENGINE</div>
            <div style={{ fontSize: 9, color: COLORS.muted, letterSpacing: 2 }}>DISTRIBUTED CLUSTER v2.1</div>
          </div>
        </div>

        <div style={{ flex: 1, display: "flex", gap: 4 }}>
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              style={{ background: tab === t.id ? COLORS.accent + "18" : "transparent", color: tab === t.id ? COLORS.accent : COLORS.muted, border: `1px solid ${tab === t.id ? COLORS.accent + "44" : "transparent"}`, borderRadius: 6, padding: "6px 14px", fontSize: 12, cursor: "pointer", fontFamily: "inherit", fontWeight: tab === t.id ? 700 : 400, transition: "all 0.15s" }}>
              {t.icon} {t.label}
            </button>
          ))}
        </div>

        {/* Status bar */}
        <div style={{ display: "flex", gap: 16, alignItems: "center" }}>
          {[
            [connectedCount + "/" + sessions.length, "Sessions", COLORS.green],
            [nodes.filter(n => n.status !== "DOWN").length + "/3", "Nodes", COLORS.accent],
            [msgPerSec, "Throughput", COLORS.amber],
          ].map(([val, label, color]) => (
            <div key={label} style={{ textAlign: "center" }}>
              <div style={{ color, fontWeight: 700, fontSize: 13, fontFamily: "monospace" }}>{val}</div>
              <div style={{ color: COLORS.muted, fontSize: 9, letterSpacing: 0.5 }}>{label}</div>
            </div>
          ))}
          <div style={{ width: 8, height: 8, borderRadius: "50%", background: COLORS.green, boxShadow: `0 0 10px ${COLORS.green}` }} />
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, padding: 20, display: "flex", flexDirection: "column", minHeight: 0 }}>
        {tab === "sessions" && <SessionsView sessions={sessions} onAction={handleSessionAction} onAddSession={() => setShowAddModal(true)} />}
        {tab === "messages" && <div style={{ flex: 1, display: "flex", flexDirection: "column" }}><MessagesView messages={MOCK_MESSAGES} /></div>}
        {tab === "live" && <div style={{ flex: 1, display: "flex", flexDirection: "column" }}><LiveFeed messages={MOCK_MESSAGES} /></div>}
        {tab === "cluster" && <ClusterView nodes={nodes} onSimulateFailover={handleSimulateFailover} />}
      </div>

      {/* Modal */}
      {showAddModal && <AddSessionModal onClose={() => setShowAddModal(false)} onSave={handleAddSession} />}

      {/* Toast */}
      {toast && (
        <div style={{ position: "fixed", bottom: 24, right: 24, background: COLORS.card, border: `1px solid ${toast.color}44`, borderLeft: `3px solid ${toast.color}`, borderRadius: 8, padding: "12px 18px", color: COLORS.text, fontSize: 13, fontFamily: "monospace", zIndex: 2000, maxWidth: 380 }}>
          {toast.msg}
        </div>
      )}
    </div>
  );
}
