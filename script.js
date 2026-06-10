const STORAGE_KEY = "lucky-pick-saved";
const RECENT_KEY = "lucky-pick-recent";

const state = {
  fixed: new Set(),
  excluded: new Set(),
  currentGames: [],
  saved: readJson(STORAGE_KEY, []),
  recent: readJson(RECENT_KEY, []),
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

const gameCount = $("#gameCount");
const gameCountLabel = $("#gameCountLabel");
const fixedInput = $("#fixedInput");
const excludeInput = $("#excludeInput");
const quickGrid = $("#quickGrid");
const resultList = $("#resultList");
const savedList = $("#savedList");
const statsGrid = $("#statsGrid");
const statsCount = $("#statsCount");
const notice = $("#notice");
const drawDate = $("#drawDate");
const gameTemplate = $("#gameTemplate");
const installBtn = $("#installBtn");

let deferredInstallPrompt = null;

function readJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) || fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function parseNumbers(value) {
  return [...new Set(
    value
      .split(/[\s,]+/)
      .map((part) => Number(part.trim()))
      .filter((number) => Number.isInteger(number) && number >= 1 && number <= 45)
  )];
}

function syncFromInputs() {
  state.fixed = new Set(parseNumbers(fixedInput.value).slice(0, 5));
  state.excluded = new Set(parseNumbers(excludeInput.value));

  for (const number of state.fixed) {
    state.excluded.delete(number);
  }

  fixedInput.value = [...state.fixed].sort((a, b) => a - b).join(", ");
  excludeInput.value = [...state.excluded].sort((a, b) => a - b).join(", ");
  renderQuickGrid();
}

function setNotice(message, isError = false) {
  notice.textContent = message;
  notice.classList.toggle("is-error", isError);
}

function renderQuickGrid() {
  quickGrid.innerHTML = "";

  for (let number = 1; number <= 45; number += 1) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "quick-number";
    button.textContent = number;
    button.setAttribute("aria-label", `${number}번 선택`);

    if (state.fixed.has(number)) {
      button.classList.add("is-fixed");
      button.title = "고정 번호";
    }

    if (state.excluded.has(number)) {
      button.classList.add("is-excluded");
      button.title = "제외 번호";
    }

    button.addEventListener("click", () => toggleQuickNumber(number));
    quickGrid.appendChild(button);
  }
}

function toggleQuickNumber(number) {
  if (state.fixed.has(number)) {
    state.fixed.delete(number);
    state.excluded.add(number);
  } else if (state.excluded.has(number)) {
    state.excluded.delete(number);
  } else if (state.fixed.size < 5) {
    state.fixed.add(number);
  } else {
    setNotice("고정 번호는 5개까지만 선택할 수 있어요.", true);
    return;
  }

  fixedInput.value = [...state.fixed].sort((a, b) => a - b).join(", ");
  excludeInput.value = [...state.excluded].sort((a, b) => a - b).join(", ");
  setNotice("번호 버튼은 고정, 제외, 해제 순서로 바뀝니다.");
  renderQuickGrid();
}

function shuffle(numbers) {
  const copy = [...numbers];

  for (let index = copy.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1));
    [copy[index], copy[randomIndex]] = [copy[randomIndex], copy[index]];
  }

  return copy;
}

function makeGame() {
  const available = [];

  for (let number = 1; number <= 45; number += 1) {
    if (!state.fixed.has(number) && !state.excluded.has(number)) {
      available.push(number);
    }
  }

  if (state.fixed.size + available.length < 6) {
    throw new Error("선택 가능한 번호가 부족해요. 제외 번호를 줄여 주세요.");
  }

  return [...state.fixed, ...shuffle(available).slice(0, 6 - state.fixed.size)]
    .sort((a, b) => a - b);
}

function numberTier(number) {
  if (number <= 10) return "tier-1";
  if (number <= 20) return "tier-2";
  if (number <= 30) return "tier-3";
  if (number <= 40) return "tier-4";
  return "tier-5";
}

function createBalls(numbers) {
  const balls = document.createElement("div");
  balls.className = "balls";

  numbers.forEach((number, index) => {
    const ball = document.createElement("span");
    ball.className = `ball ${numberTier(number)}`;
    ball.textContent = number;
    ball.style.animationDelay = `${index * 38}ms`;
    balls.appendChild(ball);
  });

  return balls;
}

function combinationScore(numbers) {
  let score = 100;
  const odd = numbers.filter((number) => number % 2 === 1).length;
  const low = numbers.filter((number) => number <= 22).length;
  let consecutivePairs = 0;
  let maxGap = 0;

  for (let index = 1; index < numbers.length; index += 1) {
    const gap = numbers[index] - numbers[index - 1];
    if (gap === 1) consecutivePairs += 1;
    maxGap = Math.max(maxGap, gap);
  }

  if (odd === 0 || odd === 6) score -= 18;
  else if (odd === 1 || odd === 5) score -= 8;
  if (low === 0 || low === 6) score -= 14;
  else if (low === 1 || low === 5) score -= 6;
  if (consecutivePairs >= 2) score -= 14;
  else if (consecutivePairs === 1) score -= 5;
  if (maxGap >= 18) score -= 10;
  if (hasArithmeticRun(numbers)) score -= 12;

  return Math.max(58, score);
}

function hasArithmeticRun(numbers) {
  for (let index = 0; index < numbers.length - 2; index += 1) {
    const firstGap = numbers[index + 1] - numbers[index];
    const secondGap = numbers[index + 2] - numbers[index + 1];
    if (firstGap === secondGap && firstGap <= 8) return true;
  }
  return false;
}

function combinationReport(numbers) {
  const odd = numbers.filter((number) => number % 2 === 1).length;
  const zones = [0, 0, 0, 0, 0];
  let consecutivePairs = 0;

  numbers.forEach((number) => {
    zones[Math.floor((number - 1) / 10)] += 1;
  });

  for (let index = 1; index < numbers.length; index += 1) {
    if (numbers[index] - numbers[index - 1] === 1) consecutivePairs += 1;
  }

  let pattern = "패턴 안정";
  if (hasArithmeticRun(numbers)) pattern = "등차 패턴 주의";
  else if (consecutivePairs >= 2) pattern = "연속수 주의";
  else if (consecutivePairs === 1) pattern = "연속수 1쌍";

  return `조합 리포트 ${combinationScore(numbers)}점 · 홀짝 ${odd}:${numbers.length - odd} · 구간 ${zones.join("-")} · ${pattern}`;
}

function renderGame(numbers, index, source = "current") {
  const card = gameTemplate.content.firstElementChild.cloneNode(true);
  const metaTitle = card.querySelector(".game-meta strong");
  const metaSub = card.querySelector(".game-meta span");
  const balls = card.querySelector(".balls");
  const saveButton = card.querySelector('[data-action="save"]');
  const copyButton = card.querySelector('[data-action="copy"]');

  metaTitle.textContent = source === "saved" ? "저장 조합" : `${index + 1}게임`;
  metaSub.textContent = numbers.join(" · ");
  balls.replaceWith(createBalls(numbers));

  const report = document.createElement("p");
  report.className = "combo-report";
  report.textContent = combinationReport(numbers);
  card.querySelector(".game-actions").before(report);

  if (source === "saved") {
    saveButton.textContent = "삭제";
    saveButton.addEventListener("click", () => deleteSaved(index));
  } else {
    saveButton.addEventListener("click", () => saveGame(numbers));
  }

  copyButton.addEventListener("click", () => copyGame(numbers));
  return card;
}

function generateGames() {
  syncFromInputs();
  resultList.innerHTML = "";

  try {
    const count = Number(gameCount.value);
    state.currentGames = Array.from({ length: count }, makeGame);
    state.recent = [...state.currentGames, ...state.recent].slice(0, 60);
    writeJson(RECENT_KEY, state.recent);

    state.currentGames.forEach((numbers, index) => {
      resultList.appendChild(renderGame(numbers, index));
    });

    renderStats();
    setNotice("마음에 드는 조합은 저장하거나 복사할 수 있어요.");
  } catch (error) {
    setNotice(error.message, true);
  }
}

function saveGame(numbers) {
  const key = numbers.join("-");
  const exists = state.saved.some((item) => item.numbers.join("-") === key);

  if (!exists) {
    state.saved.unshift({
      numbers,
      createdAt: new Date().toISOString(),
    });
    state.saved = state.saved.slice(0, 30);
    writeJson(STORAGE_KEY, state.saved);
  }

  renderSaved();
  setNotice(exists ? "이미 보관함에 있는 조합입니다." : "보관함에 저장했어요.");
}

function deleteSaved(index) {
  state.saved.splice(index, 1);
  writeJson(STORAGE_KEY, state.saved);
  renderSaved();
}

async function copyGame(numbers) {
  const text = `럭키픽 추천 번호: ${numbers.join(", ")}`;

  try {
    await navigator.clipboard.writeText(text);
    setNotice("번호를 클립보드에 복사했어요.");
  } catch {
    setNotice(text);
  }
}

function renderSaved() {
  savedList.innerHTML = "";

  if (state.saved.length === 0) {
    savedList.innerHTML = '<p class="empty">아직 저장한 조합이 없습니다.</p>';
    return;
  }

  state.saved.forEach((item, index) => {
    savedList.appendChild(renderGame(item.numbers, index, "saved"));
  });
}

function renderStats() {
  const counts = new Map(Array.from({ length: 45 }, (_, index) => [index + 1, 0]));

  state.recent.flat().forEach((number) => {
    counts.set(number, counts.get(number) + 1);
  });

  const max = Math.max(...counts.values(), 1);
  const top = [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0] - b[0])
    .slice(0, 12);

  statsGrid.innerHTML = "";
  statsCount.textContent = `${state.recent.length}개 조합`;

  top.forEach(([number, count]) => {
    const row = document.createElement("div");
    row.className = "stat-row";
    row.innerHTML = `
      <span class="ball ${numberTier(number)}">${number}</span>
      <span class="stat-bar"><span style="width: ${(count / max) * 100}%"></span></span>
      <strong>${count}</strong>
    `;
    statsGrid.appendChild(row);
  });
}

function formatDrawDate() {
  const today = new Date();
  const saturday = new Date(today);
  const daysUntilSaturday = (6 - today.getDay() + 7) % 7;
  saturday.setDate(today.getDate() + daysUntilSaturday);

  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(saturday);
}

function switchTab(tabId) {
  $$(".tab").forEach((tab) => {
    tab.classList.toggle("is-active", tab.dataset.tab === tabId);
  });

  $$(".screen").forEach((screen) => {
    screen.classList.toggle("is-active", screen.id === tabId);
  });
}

gameCount.addEventListener("input", () => {
  gameCountLabel.textContent = `${gameCount.value}게임`;
});

fixedInput.addEventListener("change", syncFromInputs);
excludeInput.addEventListener("change", syncFromInputs);

$("#lottoForm").addEventListener("submit", (event) => {
  event.preventDefault();
  generateGames();
});

$("#resetBtn").addEventListener("click", () => {
  state.fixed.clear();
  state.excluded.clear();
  fixedInput.value = "";
  excludeInput.value = "";
  gameCount.value = "5";
  gameCountLabel.textContent = "5게임";
  setNotice("조건을 초기화했어요.");
  renderQuickGrid();
  generateGames();
});

$("#clearSavedBtn").addEventListener("click", () => {
  state.saved = [];
  writeJson(STORAGE_KEY, state.saved);
  renderSaved();
});

$$(".tab").forEach((tab) => {
  tab.addEventListener("click", () => switchTab(tab.dataset.tab));
});

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  deferredInstallPrompt = event;
  installBtn.hidden = false;
});

installBtn.addEventListener("click", async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  installBtn.hidden = true;
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("service-worker.js").catch(() => {});
}

drawDate.textContent = `다음 추첨: ${formatDrawDate()}`;
renderQuickGrid();
renderSaved();
renderStats();
generateGames();
