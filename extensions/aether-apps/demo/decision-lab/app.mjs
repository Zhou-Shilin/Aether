// Decision Lab — Aether Hosted Web App Demo
// Exercises: AetherAppInfo theme, AetherHosted bridge, model_access streaming + cancel,
// persistent app storage, DocumentExports, and Android Back (aether:back + <dialog>).

const PRESETS = [
  {
    id: "athens-diplomacy",
    title: "城邦外交危机",
    badge: "历史推演",
    desc: "公元前 431 年伯罗奔尼撒前夜，你是雅典执政官，面对盟邦动摇与粮道威胁。",
    topic: "公元前431年伯罗奔尼撒战争前夜的雅典执政官外交与城防危机",
  },
  {
    id: "prod-outage",
    title: "核心服务雪崩事故",
    badge: "工程决策",
    desc: "双十一零点前 15 分钟，主库连接池耗尽、缓存穿透，作为值班架构师你必须立刻拍板。",
    topic: "高并发电商大促前15分钟主数据库雪崩与缓存穿透的架构应急决策",
  },
  {
    id: "er-triage",
    title: "急诊多发伤分诊",
    badge: "情景演练",
    desc: "暴雨夜多车连环事故，三位伤员同时送达而抢救室资源有限，需要快速临床权衡。",
    topic: "暴雨夜多车事故急诊室有限医疗资源下的分诊与救治演练（教学模拟）",
  },
];

// ---------- Host Bridge Client (with streaming support) ----------
const pendingRequests = new Map();

function initHostBridge() {
  const bridge = globalThis.AetherHosted;
  if (!bridge || typeof bridge.postMessage !== "function") return false;
  bridge.onmessage = (event) => {
    let msg;
    try {
      if (typeof event.data !== "string") return;
      msg = JSON.parse(event.data);
    } catch {
      return;
    }
    const entry = pendingRequests.get(msg.id);
    if (!entry) return;
    if (msg.stream === true) {
      entry.onStream?.(msg.event, msg.data || {});
      return;
    }
    pendingRequests.delete(msg.id);
    entry.cleanup();
    if (msg.ok) entry.resolve(msg.result);
    else entry.reject(new Error(msg.error || "Host request failed."));
  };
  return true;
}

function hostRequest(op, args = {}, { signal, onStream } = {}) {
  const bridge = globalThis.AetherHosted;
  if (!bridge || typeof bridge.postMessage !== "function") {
    return Promise.reject(new Error("未检测到 AetherHosted 宿主桥接。"));
  }
  const id = crypto.randomUUID();
  const payload = JSON.stringify({ id, op, args });
  return new Promise((resolve, reject) => {
    const abort = () => {
      if (!pendingRequests.delete(id)) return;
      clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
      try {
        bridge.postMessage(JSON.stringify({ id: crypto.randomUUID(), op: "cancel", target: id }));
      } catch { /* ignore */ }
      reject(new DOMException("已取消请求", "AbortError"));
    };
    const cleanup = () => {
      clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
    };
    const timer = setTimeout(abort, 125000);
    pendingRequests.set(id, { resolve, reject, cleanup, onStream });
    signal?.addEventListener("abort", abort, { once: true });
    if (signal?.aborted) {
      abort();
      return;
    }
    bridge.postMessage(payload);
  });
}

// ---------- Theme Injection ----------
function applyHostTheme(theme) {
  if (!theme) return;
  const root = document.documentElement.style;
  if (theme.background) root.setProperty("--bg", theme.background);
  if (theme.surface) root.setProperty("--surface", theme.surface);
  if (theme.text) root.setProperty("--text", theme.text);
  if (theme.muted) root.setProperty("--muted", theme.muted);
  if (theme.line) root.setProperty("--line", theme.line);
  if (theme.primary) root.setProperty("--primary", theme.primary);
  if (theme.onPrimary) root.setProperty("--on-primary", theme.onPrimary);
  if (theme.container) root.setProperty("--container", theme.container);
  if (theme.onContainer) root.setProperty("--on-container", theme.onContainer);
  if (theme.errorContainer) root.setProperty("--err-bg", theme.errorContainer);
  if (theme.onErrorContainer) root.setProperty("--err-fg", theme.onErrorContainer);
}

// ---------- State & Persistence ----------
const state = {
  screen: "lobby", // lobby | sim | deck
  lobbyMode: "sim", // sim | cards
  selection: null,
  runs: [],
  activeRun: null,
  decks: [],
  activeDeck: null,
  cardIndex: 0,
  cardFlipped: false,
  abortController: null,
  busy: false,
};

const STORAGE_KEY = "decision-lab-state-v1";

async function loadSavedState() {
  try {
    const remote = await hostRequest("storage.get", { key: STORAGE_KEY });
    if (remote && remote.value && typeof remote.value === "object") {
      state.runs = Array.isArray(remote.value.runs) ? remote.value.runs : [];
      state.decks = Array.isArray(remote.value.decks) ? remote.value.decks : [];
      return;
    }
  } catch { /* fallback to localStorage */ }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      state.runs = Array.isArray(parsed.runs) ? parsed.runs : [];
      state.decks = Array.isArray(parsed.decks) ? parsed.decks : [];
    }
  } catch { /* ignore */ }
}

async function saveState() {
  const snapshot = {
    runs: state.runs.slice(0, 12),
    decks: state.decks.slice(0, 12),
  };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
  } catch { /* ignore */ }
  try {
    await hostRequest("storage.set", { key: STORAGE_KEY, value: snapshot });
  } catch { /* ignore */ }
}

// ---------- JSON Extraction Helper ----------
function extractJsonObject(text) {
  const trimmed = String(text || "").trim();
  if (!trimmed) throw new Error("模型返回为空。");
  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced ? fenced[1].trim() : trimmed;
  try {
    return JSON.parse(candidate);
  } catch {
    const first = candidate.indexOf("{");
    const last = candidate.lastIndexOf("}");
    if (first >= 0 && last > first) {
      return JSON.parse(candidate.slice(first, last + 1));
    }
    throw new Error("未能解析模型返回的结构化 JSON，请重试。");
  }
}

function assistantTextFromMessage(message) {
  const content = message?.content;
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content.filter((b) => b?.type === "text" && typeof b.text === "string").map((b) => b.text).join("");
}

function formatUsage(message) {
  const u = message?.usage;
  if (!u) return "";
  const parts = [];
  const input = u.input ?? u.input_tokens;
  const output = u.output ?? u.output_tokens;
  const reasoning = u.reasoning ?? u.reasoning_tokens;
  if (typeof input === "number") parts.push(`输入 ${input}`);
  if (typeof output === "number") parts.push(`输出 ${output}`);
  if (typeof reasoning === "number" && reasoning > 0) parts.push(`思考 ${reasoning}`);
  if (message.model) parts.unshift(message.model);
  return parts.join(" · ");
}

// ---------- UI Helpers ----------
const $ = (id) => document.getElementById(id);
let toastTimer;
function showToast(text) {
  const el = $("toast");
  el.textContent = text;
  el.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), 2600);
}

async function refreshSelection() {
  try {
    const sel = await hostRequest("ai.selection", {});
    state.selection = sel;
    const modelId = sel?.model?.id || "未选择模型";
    const effort = sel?.thinkingLevel && sel.thinkingLevel !== "off" ? ` · ${sel.thinkingLevel}` : "";
    $("modelBadge").textContent = `${modelId}${effort}`;
    return sel;
  } catch (err) {
    $("modelBadge").textContent = "模型未就绪";
    throw err;
  }
}

function renderLobby() {
  $("headerTitle").textContent = "Decision Lab";
  $("backBtn").classList.add("hidden");
  $("lobbyView").classList.remove("hidden");
  $("simView").classList.add("hidden");
  $("deckView").classList.add("hidden");

  document.querySelectorAll(".mode-tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.mode === state.lobbyMode);
  });
  $("simLobby").classList.toggle("hidden", state.lobbyMode !== "sim");
  $("cardsLobby").classList.toggle("hidden", state.lobbyMode !== "cards");

  const grid = $("presetGrid");
  grid.innerHTML = "";
  for (const preset of PRESETS) {
    const btn = document.createElement("button");
    btn.className = "preset-card";
    btn.innerHTML = `
      <div class="preset-head">
        <span class="preset-title">${preset.title}</span>
        <span class="preset-badge">${preset.badge}</span>
      </div>
      <div class="preset-desc">${preset.desc}</div>
    `;
    btn.onclick = () => startSimulation(preset.title, preset.topic);
    grid.appendChild(btn);
  }

  // Saved runs
  const runsSec = $("savedRunsSection");
  const runsList = $("savedRunsList");
  runsList.innerHTML = "";
  if (state.runs.length > 0) {
    runsSec.classList.remove("hidden");
    for (const run of state.runs) {
      const row = document.createElement("div");
      row.className = "saved-row";
      row.innerHTML = `
        <div class="saved-row-main">
          <div class="saved-row-title">${escapeHtml(run.title)}</div>
          <div class="muted small">第 ${run.turn} 回合 · ${escapeHtml(run.lastBeatTitle || "")}</div>
        </div>
        <button class="ghost-btn">继续</button>
      `;
      const open = () => {
        state.activeRun = run;
        state.screen = "sim";
        renderSim();
      };
      row.querySelector(".saved-row-main").onclick = open;
      row.querySelector("button").onclick = open;
      runsList.appendChild(row);
    }
  } else {
    runsSec.classList.add("hidden");
  }

  // Saved decks
  const decksSec = $("savedDecksSection");
  const decksList = $("savedDecksList");
  decksList.innerHTML = "";
  if (state.decks.length > 0) {
    decksSec.classList.remove("hidden");
    for (const deck of state.decks) {
      const mastered = (deck.cards || []).filter((c) => c.mastered).length;
      const row = document.createElement("div");
      row.className = "saved-row";
      row.innerHTML = `
        <div class="saved-row-main">
          <div class="saved-row-title">${escapeHtml(deck.title)}</div>
          <div class="muted small">已掌握 ${mastered} / ${(deck.cards || []).length} 张</div>
        </div>
        <button class="ghost-btn">学习</button>
      `;
      const open = () => {
        state.activeDeck = deck;
        state.cardIndex = 0;
        state.cardFlipped = false;
        state.screen = "deck";
        renderDeck();
      };
      row.querySelector(".saved-row-main").onclick = open;
      row.querySelector("button").onclick = open;
      decksList.appendChild(row);
    }
  } else {
    decksSec.classList.add("hidden");
  }
}

function escapeHtml(s) {
  return String(s ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

// ---------- Simulator Logic ----------
const SIM_SCHEMA_INSTRUCTIONS = `必须只输出严格合法的 JSON 对象（不要 Markdown 代码块、不要多余说明），且字段名必须严格使用以下英文键名：
{
  "title": "本回合场景标题（简短有力，15字内）",
  "feedback": "对玩家上一步决策后果的直接点评（第1回合填\"\"）",
  "narrative": "当前局势描述（100~180字，生动具体、有紧迫感）",
  "metrics": [
    {"name": "指标1（2-4字）", "value": 65},
    {"name": "指标2（2-4字）", "value": 50},
    {"name": "指标3（2-4字）", "value": 45}
  ],
  "choices": [
    {"id": "a", "label": "决策标题（14字内）", "hint": "收益与代价提示（24字内）"},
    {"id": "b", "label": "决策标题（14字内）", "hint": "收益与代价提示（24字内）"},
    {"id": "c", "label": "决策标题（14字内）", "hint": "收益与代价提示（24字内）"}
  ]
}
所有 value 必须是 0 到 100 的整数；所有内容使用简体中文；始终提供 3 个指标和 3 个有代价的差异化选项。`;

const SIM_SYSTEM_PROMPT = `你是 Decision Lab 交互推演引擎。\n${SIM_SCHEMA_INSTRUCTIONS}`;

function parseMetricValue(raw) {
  if (typeof raw === "number" && Number.isFinite(raw)) return Math.max(0, Math.min(100, Math.round(raw)));
  const match = String(raw ?? "").match(/-?\d+/);
  return match ? Math.max(0, Math.min(100, Number(match[0]))) : 50;
}

function normalizeSimBeat(parsed, fallbackTitle) {
  const title = parsed.title || parsed["标题"] || fallbackTitle;
  const feedback = parsed.feedback || parsed["反馈"] || parsed["后果"] || "";
  const narrative = parsed.narrative || parsed["开局"] || parsed["局势"] || parsed["描述"] || parsed["剧情"] || "";
  const rawMetrics = Array.isArray(parsed.metrics) ? parsed.metrics : Array.isArray(parsed["指标"]) ? parsed["指标"] : [];
  const metrics = rawMetrics.slice(0, 3).map((m, idx) => ({
    name: String(m?.name || m?.["名称"] || `指标${idx + 1}`).slice(0, 8),
    value: parseMetricValue(m?.value ?? m?.["当前值"] ?? m?.["数值"]),
  }));
  const rawChoices = Array.isArray(parsed.choices) ? parsed.choices : Array.isArray(parsed["选项"]) ? parsed["选项"] : Array.isArray(parsed["决策"]) ? parsed["决策"] : [];
  const choices = rawChoices.slice(0, 4).map((c, idx) => ({
    id: String(c?.id || String.fromCharCode(97 + idx)),
    label: String(c?.label || c?.title || c?.["标题"] || c?.["选项"] || `方案 ${idx + 1}`),
    hint: String(c?.hint || c?.description || c?.["提示"] || c?.["影响"] || c?.["说明"] || ""),
  }));
  return { title, feedback, narrative, metrics, choices };
}

async function startSimulation(title, topic) {
  const run = {
    id: crypto.randomUUID(),
    title,
    topic,
    turn: 0,
    metrics: [
      { name: "声望", value: 60 },
      { name: "资源", value: 60 },
      { name: "稳定", value: 60 },
    ],
    history: [],
    current: null,
    lastUsage: "",
    lastBeatTitle: "",
  };
  state.activeRun = run;
  state.runs.unshift(run);
  state.screen = "sim";
  renderSim();
  await advanceSimulation(null);
}

function renderSim() {
  const run = state.activeRun;
  if (!run) return renderLobby();
  $("headerTitle").textContent = "情境沙盘";
  $("backBtn").classList.remove("hidden");
  $("lobbyView").classList.add("hidden");
  $("simView").classList.remove("hidden");
  $("deckView").classList.add("hidden");

  $("simTurnBadge").textContent = `第 ${Math.max(1, run.turn)} 回合`;
  $("simScenarioTitle").textContent = run.title;

  const metricsEl = $("metricsGrid");
  metricsEl.innerHTML = "";
  for (const m of run.metrics || []) {
    const val = Math.max(0, Math.min(100, Number(m.value) || 0));
    const box = document.createElement("div");
    box.className = "metric-box";
    box.innerHTML = `
      <div class="metric-head"><span>${escapeHtml(m.name)}</span><span>${val}</span></div>
      <div class="metric-bar"><div class="metric-fill" style="width:${val}%"></div></div>
    `;
    metricsEl.appendChild(box);
  }

  if (run.current) {
    $("simNarrativeCard").classList.remove("hidden");
    $("simBeatTitle").textContent = run.current.title || run.title;
    $("simNarrative").textContent = run.current.narrative || "";
    if (run.current.feedback) {
      $("simFeedback").textContent = `结果反馈：${run.current.feedback}`;
      $("simFeedback").classList.remove("hidden");
    } else {
      $("simFeedback").classList.add("hidden");
    }
    $("simUsageLine").textContent = run.lastUsage ? `本次消耗：${run.lastUsage}` : "";

    const choicesSec = $("simChoicesSection");
    const choicesList = $("simChoicesList");
    choicesList.innerHTML = "";
    const choices = Array.isArray(run.current.choices) ? run.current.choices : [];
    if (choices.length > 0 && !state.busy) {
      choicesSec.classList.remove("hidden");
      for (const ch of choices) {
        const btn = document.createElement("button");
        btn.className = "choice-btn";
        btn.innerHTML = `
          <span class="choice-label">${escapeHtml(ch.label)}</span>
          <span class="choice-hint">${escapeHtml(ch.hint || "")}</span>
        `;
        btn.onclick = () => advanceSimulation(ch);
        choicesList.appendChild(btn);
      }
    } else {
      choicesSec.classList.add("hidden");
    }
  } else {
    $("simNarrativeCard").classList.add("hidden");
    $("simChoicesSection").classList.add("hidden");
  }

  if (run.history.length > 0) {
    $("simHistoryDetails").classList.remove("hidden");
    $("simHistoryCount").textContent = String(run.history.length);
    const list = $("simHistoryList");
    list.innerHTML = "";
    for (const h of run.history) {
      const div = document.createElement("div");
      div.className = "timeline-item";
      div.innerHTML = `<strong>第 ${h.turn} 回合</strong> · 选择了「${escapeHtml(h.choice)}」`;
      list.appendChild(div);
    }
  } else {
    $("simHistoryDetails").classList.add("hidden");
  }
}

async function advanceSimulation(choice) {
  const run = state.activeRun;
  if (!run || state.busy) return;
  state.busy = true;
  $("simErrorBox").classList.add("hidden");
  $("simChoicesSection").classList.add("hidden");
  $("simStreamBox").classList.remove("hidden");
  $("simThinkingDetails").classList.add("hidden");
  $("simThinkingText").textContent = "";
  $("simRawStream").textContent = "";
  $("simStreamStatus").textContent = "连接宿主模型流式推演中…";

  const controller = new AbortController();
  state.abortController = controller;

  try {
    const sel = await refreshSelection();
    const expected = {
      provider: sel.model.provider,
      id: sel.model.id,
      thinkingLevel: sel.thinkingLevel,
      selectionId: sel.selectionId,
    };
    const nextTurn = run.turn + 1;
    const historySummary = run.history.slice(-6).map((h) => `第${h.turn}回合(${h.beatTitle}) -> 选择「${h.choice}」`).join("\n");
    const userPrompt = choice
      ? `${SIM_SCHEMA_INSTRUCTIONS}\n\n主题：${run.topic}\n当前指标：${JSON.stringify(run.metrics)}\n历史轨迹：\n${historySummary}\n上回合局势：${run.current?.narrative || ""}\n玩家刚刚选择了：「${choice.label}」（${choice.hint || ""}）。\n请推演第 ${nextTurn} 回合的发展，保持相同 3 个指标名称并合理调整 0-100 数值，严格按上述英文键名 JSON 输出。`
      : `${SIM_SCHEMA_INSTRUCTIONS}\n\n主题：${run.topic}\n请设计开局（第 1 回合），给出 3 个贴合主题的指标和 3 个有张力的初始决策选项，严格按上述英文键名 JSON 输出。`;

    let thinkingBuf = "";
    let textBuf = "";
    const response = await hostRequest(
      "ai.complete",
      {
        expected,
        stream: true,
        maxTokens: 2048,
        session_id: run.id.replace(/[^A-Za-z0-9_-]/g, ""),
        context: {
          systemPrompt: SIM_SYSTEM_PROMPT,
          messages: [{ role: "user", content: userPrompt, timestamp: Date.now() }],
        },
      },
      {
        signal: controller.signal,
        onStream: (event, data) => {
          if (event === "assistant_reasoning_delta" && data.delta) {
            thinkingBuf += data.delta;
            $("simThinkingDetails").classList.remove("hidden");
            $("simThinkingText").textContent = thinkingBuf;
            $("simStreamStatus").textContent = "模型思考中（流式）…";
          } else if (event === "assistant_text_delta" && data.delta) {
            textBuf += data.delta;
            $("simRawStream").textContent = textBuf;
            $("simStreamStatus").textContent = "正在生成局势推演…";
          }
        },
      },
    );

    const rawText = assistantTextFromMessage(response.message) || textBuf;
    const parsed = normalizeSimBeat(extractJsonObject(rawText), run.title);
    if (choice) {
      run.history.push({ turn: run.turn, beatTitle: run.current?.title || "", choice: choice.label });
    }
    run.turn = nextTurn;
    if (Array.isArray(parsed.metrics) && parsed.metrics.length > 0) {
      run.metrics = parsed.metrics.slice(0, 3);
    }
    run.current = parsed;
    run.lastBeatTitle = parsed.title || "";
    run.lastUsage = formatUsage(response.message);
    await saveState();
  } catch (err) {
    if (err.name === "AbortError") {
      showToast("已取消本次推演");
    } else {
      $("simErrorBox").textContent = `推演失败：${err.message || err}`;
      $("simErrorBox").classList.remove("hidden");
    }
  } finally {
    state.busy = false;
    state.abortController = null;
    $("simStreamBox").classList.add("hidden");
    renderSim();
  }
}

// ---------- Flashcard Studio Logic ----------
const CARDS_SCHEMA_INSTRUCTIONS = `必须只输出严格合法的 JSON 对象（不要 Markdown 代码块、不要多余说明），字段名必须严格使用英文键名：
{
  "title": "卡组简短标题",
  "cards": [
    {"front": "核心概念或提问（清晰简短）", "back": "精准解释（60~120字）", "example": "一句话实战例子或类比", "tag": "分类标签（2-5字）"}
  ]
}
生成 5 张高质量、循序渐进的简体中文学习闪卡。`;

const CARDS_SYSTEM_PROMPT = `你是精炼的知识闪卡生成器。\n${CARDS_SCHEMA_INSTRUCTIONS}`;

async function generateDeck(topicText) {
  const topic = topicText.trim();
  if (!topic || state.busy) return;
  const deck = {
    id: crypto.randomUUID(),
    title: topic.slice(0, 28),
    topic,
    cards: [],
  };
  state.activeDeck = deck;
  state.cardIndex = 0;
  state.cardFlipped = false;
  state.screen = "deck";
  renderDeck();

  state.busy = true;
  $("deckErrorBox").classList.add("hidden");
  $("flashcardStage").classList.add("hidden");
  $("deckStreamBox").classList.remove("hidden");
  $("deckThinkingDetails").classList.add("hidden");
  $("deckThinkingText").textContent = "";
  $("deckRawStream").textContent = "";

  const controller = new AbortController();
  state.abortController = controller;

  try {
    const sel = await refreshSelection();
    const expected = {
      provider: sel.model.provider,
      id: sel.model.id,
      thinkingLevel: sel.thinkingLevel,
      selectionId: sel.selectionId,
    };
    let thinkingBuf = "";
    let textBuf = "";
    const response = await hostRequest(
      "ai.complete",
      {
        expected,
        stream: true,
        maxTokens: 2048,
        session_id: deck.id.replace(/[^A-Za-z0-9_-]/g, ""),
        context: {
          systemPrompt: CARDS_SYSTEM_PROMPT,
          messages: [{ role: "user", content: `${CARDS_SCHEMA_INSTRUCTIONS}\n\n请为以下主题/笔记生成 5 张学习闪卡，严格按上述英文键名 JSON 输出：\n\n${topic}`, timestamp: Date.now() }],
        },
      },
      {
        signal: controller.signal,
        onStream: (event, data) => {
          if (event === "assistant_reasoning_delta" && data.delta) {
            thinkingBuf += data.delta;
            $("deckThinkingDetails").classList.remove("hidden");
            $("deckThinkingText").textContent = thinkingBuf;
            $("deckStreamStatus").textContent = "模型思考中…";
          } else if (event === "assistant_text_delta" && data.delta) {
            textBuf += data.delta;
            $("deckRawStream").textContent = textBuf;
            $("deckStreamStatus").textContent = "正在流式构建闪卡…";
          }
        },
      },
    );
    const parsed = extractJsonObject(assistantTextFromMessage(response.message) || textBuf);
    deck.title = parsed.title || deck.title;
    deck.cards = (Array.isArray(parsed.cards) ? parsed.cards : []).map((c) => ({
      front: c.front || "",
      back: c.back || "",
      example: c.example || "",
      tag: c.tag || "要点",
      mastered: false,
    }));
    if (deck.cards.length === 0) throw new Error("未生成有效闪卡。");
    state.decks.unshift(deck);
    await saveState();
    showToast(`已生成 ${deck.cards.length} 张闪卡`);
  } catch (err) {
    if (err.name === "AbortError") {
      showToast("已取消生成");
    } else {
      $("deckErrorBox").textContent = `生成失败：${err.message || err}`;
      $("deckErrorBox").classList.remove("hidden");
    }
  } finally {
    state.busy = false;
    state.abortController = null;
    $("deckStreamBox").classList.add("hidden");
    renderDeck();
  }
}

function renderDeck() {
  const deck = state.activeDeck;
  if (!deck) return renderLobby();
  $("headerTitle").textContent = "速记卡片";
  $("backBtn").classList.remove("hidden");
  $("lobbyView").classList.add("hidden");
  $("simView").classList.add("hidden");
  $("deckView").classList.remove("hidden");
  $("deckTitle").textContent = deck.title;

  const cards = deck.cards || [];
  if (cards.length === 0) {
    $("deckProgressLabel").textContent = "准备中";
    $("flashcardStage").classList.add("hidden");
    return;
  }
  state.cardIndex = ((state.cardIndex % cards.length) + cards.length) % cards.length;
  const card = cards[state.cardIndex];
  const masteredCount = cards.filter((c) => c.mastered).length;
  $("deckProgressLabel").textContent = `第 ${state.cardIndex + 1} / ${cards.length} 张`;
  $("deckMasteryStat").textContent = `已掌握 ${masteredCount} / ${cards.length}`;
  $("cardFrontText").textContent = card.front;
  $("cardTagPill").textContent = card.mastered ? `${card.tag} · 已掌握` : card.tag;
  $("cardBackText").textContent = card.back;
  $("cardExampleText").textContent = card.example ? `示例：${card.example}` : "";
  $("flashcard").classList.toggle("flipped", state.cardFlipped);
  $("flashcardStage").classList.remove("hidden");
}

// ---------- Native Document Export ----------
async function exportBackup() {
  try {
    const payload = JSON.stringify(
      { app: "decision-lab", exportedAt: new Date().toISOString(), runs: state.runs, decks: state.decks },
      null,
      2,
    );
    const bytes = new TextEncoder().encode(payload);
    const { transfer } = await hostRequest("export.begin", {
      name: `decision-lab-backup-${Date.now()}.json`,
      bytes: bytes.length,
    });
    const chunkSize = 32768;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      const slice = bytes.subarray(offset, offset + chunkSize);
      let binary = "";
      for (let i = 0; i < slice.length; i++) binary += String.fromCharCode(slice[i]);
      await hostRequest("export.chunk", { transfer, offset, base64: btoa(binary) });
    }
    const res = await hostRequest("export.finish", { transfer });
    showToast(res.saved ? "已导出备份文件" : "已取消导出");
  } catch (err) {
    showToast(`导出失败：${err.message || err}`);
  }
}

// ---------- Event Wiring ----------
function goBack() {
  if (state.abortController) {
    state.abortController.abort();
    return true;
  }
  if (state.screen !== "lobby") {
    state.screen = "lobby";
    renderLobby();
    return true;
  }
  return false;
}

window.addEventListener("aether:back", (event) => {
  if (goBack()) event.preventDefault();
});

document.querySelectorAll(".mode-tab").forEach((tab) => {
  tab.onclick = () => {
    state.lobbyMode = tab.dataset.mode;
    renderLobby();
  };
});

document.querySelectorAll("[data-card-preset]").forEach((chip) => {
  chip.onclick = () => {
    $("cardTopicInput").value = chip.dataset.cardPreset;
  };
});

$("backBtn").onclick = () => goBack();
$("startCustomBtn").onclick = () => {
  const topic = $("customTopicInput").value.trim();
  if (!topic) return showToast("请输入推演主题");
  startSimulation(topic.slice(0, 18), topic);
};
$("restartSimBtn").onclick = () => {
  if (state.activeRun) startSimulation(state.activeRun.title, state.activeRun.topic);
};
$("cancelSimBtn").onclick = () => state.abortController?.abort();
$("cancelDeckBtn").onclick = () => state.abortController?.abort();

$("generateDeckBtn").onclick = () => {
  const text = $("cardTopicInput").value.trim();
  if (!text) return showToast("请输入主题或笔记");
  generateDeck(text);
};

$("flashcard").onclick = () => {
  state.cardFlipped = !state.cardFlipped;
  $("flashcard").classList.toggle("flipped", state.cardFlipped);
};
$("prevCardBtn").onclick = () => {
  state.cardIndex -= 1;
  state.cardFlipped = false;
  renderDeck();
};
$("nextCardBtn").onclick = () => {
  state.cardIndex += 1;
  state.cardFlipped = false;
  renderDeck();
};
$("masteredBtn").onclick = async () => {
  const card = state.activeDeck?.cards?.[state.cardIndex];
  if (!card) return;
  card.mastered = true;
  state.cardIndex += 1;
  state.cardFlipped = false;
  await saveState();
  renderDeck();
};
$("needReviewBtn").onclick = async () => {
  const card = state.activeDeck?.cards?.[state.cardIndex];
  if (!card) return;
  card.mastered = false;
  state.cardIndex += 1;
  state.cardFlipped = false;
  await saveState();
  renderDeck();
};
$("deckResetBtn").onclick = async () => {
  if (!state.activeDeck) return;
  for (const c of state.activeDeck.cards || []) c.mastered = false;
  state.cardIndex = 0;
  state.cardFlipped = false;
  await saveState();
  renderDeck();
  showToast("已重置掌握进度");
};

$("exportBtn").onclick = () => exportBackup();
$("infoBtn").onclick = async () => {
  let info = globalThis.AetherAppInfo || {};
  try {
    info = await hostRequest("app.info", {});
  } catch { /* use injected info */ }
  $("hostDiagPre").textContent = JSON.stringify(
    {
      appId: info.id,
      version: info.version,
      origin: location.origin,
      streaming: info.streaming === true,
      model: state.selection?.model?.id || null,
      thinkingLevel: state.selection?.thinkingLevel || null,
    },
    null,
    2,
  );
  $("infoDialog").showModal();
};
$("closeInfoBtn").onclick = () => $("infoDialog").close();

// ---------- Bootstrap ----------
(async function boot() {
  if (globalThis.AetherAppInfo?.theme) {
    applyHostTheme(globalThis.AetherAppInfo.theme);
  }
  const hasBridge = initHostBridge();
  renderLobby();
  if (!hasBridge) {
    $("modelBadge").textContent = "非宿主预览模式";
    return;
  }
  try {
    const info = await hostRequest("app.info", {});
    if (info?.theme) applyHostTheme(info.theme);
  } catch { /* ignore */ }
  await loadSavedState();
  renderLobby();
  try {
    await refreshSelection();
  } catch (err) {
    showToast(err.message || "请在 Aether 中选择模型");
  }
})();
