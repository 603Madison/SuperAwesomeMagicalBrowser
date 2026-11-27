// SuperAwesomeMagical Browser's start page: Ladybird's new tab page (the launcher's presets point
// newTabPageURL here). It is an ordinary local page: the clock, a search box that opens addresses and
// searches Ecosia, and shortcuts the person keeps here (in this page's local storage). Everything
// typed in goes into the page as text or attributes only, never as markup.
"use strict";

const $ = id => document.getElementById(id);

// ---- The clock: "11:03" large, with "PM" small where the locale uses a 12-hour clock ----

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" });

let clockTimer = 0;
function tick() {
  clearTimeout(clockTimer);
  const now = new Date();
  const time = $("time");
  time.replaceChildren();
  for (const part of timeFormat.formatToParts(now)) {
    if (part.type === "dayPeriod") {
      const small = document.createElement("span");
      small.className = "period";
      small.textContent = part.value;
      time.append(small);
    } else if (part.type !== "literal" || part.value.trim()) {
      time.append(part.value);
    }
  }
  $("date").textContent = dateFormat.format(now);
  clockTimer = setTimeout(tick, 60_000 - (now.getSeconds() * 1000 + now.getMilliseconds()) + 50);
}

// ---- Search: an address opens, anything else is searched for with Ecosia ----
// The same rules as the Android app's address bar (android/.../Address.kt).

const SEARCH = "https://www.ecosia.org/search?q=";
const IPV4 = /^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$/;
const DOMAIN = /^([\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+\p{L}{2,}(:\d{1,5})?$/u;

function toUrl(input) {
  const text = input.trim();
  if (!text) return null;
  if (/\s/.test(text)) return SEARCH + encodeURIComponent(text);
  if (/^(https?:\/\/|about:|view-source:)/i.test(text)) return text;
  const host = text.split(/[/?#]/)[0];
  if (/^localhost(:\d+)?$/i.test(host) || IPV4.test(host)) return "http://" + text;
  if (DOMAIN.test(host)) return "https://" + text;
  return SEARCH + encodeURIComponent(text);
}

$("search").addEventListener("submit", event => {
  event.preventDefault();
  const url = toUrl($("query").value);
  if (url) location.href = url;
});

// ---- Shortcuts ----

const STARTERS = [
  { url: "https://www.wikipedia.org/", name: "Wikipedia" },
  { url: "https://www.youtube.com/", name: "YouTube" },
  { url: "https://github.com/", name: "GitHub" },
  { url: "https://www.reddit.com/", name: "Reddit" },
];
const KEY = "superawesomemagical.shortcuts";
const MAX = 8;
// The tile colours, in turn, as in the apps' avatars.
const TINTS = ["primary", "secondary", "tertiary"];

// Local storage may be missing for local pages; then the starters show and can't be changed.
const storage = (() => {
  try {
    const probe = "superawesomemagical.probe";
    localStorage.setItem(probe, "1");
    localStorage.removeItem(probe);
    return localStorage;
  } catch {
    return null;
  }
})();

function load() {
  try {
    const saved = JSON.parse(storage?.getItem(KEY) ?? "null");
    if (Array.isArray(saved)) {
      return saved.filter(s => s && typeof s.url === "string" && /^https?:/.test(s.url) && typeof s.name === "string");
    }
  } catch {}
  return STARTERS.slice();
}

function save(sites) {
  storage?.setItem(KEY, JSON.stringify(sites));
}

function hostName(url) {
  try {
    const label = new URL(url).hostname.replace(/^www\./, "").split(".")[0];
    return label.charAt(0).toUpperCase() + label.slice(1);
  } catch {
    return url;
  }
}

function show() {
  const sites = load();
  const list = $("sites");
  const template = $("site");
  list.replaceChildren();
  sites.forEach((site, i) => {
    const item = template.content.firstElementChild.cloneNode(true);
    const link = item.querySelector(".site-link");
    link.href = site.url;
    link.title = site.url;
    item.querySelector(".name").textContent = site.name;
    item.querySelector(".letter").textContent = site.name.charAt(0).toUpperCase();
    item.querySelector(".tile").dataset.tint = TINTS[i % TINTS.length];
    item.querySelector(".remove").addEventListener("click", () => {
      save(load().filter(s => s.url !== site.url));
      show();
    });
    list.append(item);
  });
  if (storage && sites.length < MAX) {
    list.append($("add-site").content.firstElementChild.cloneNode(true));
    list.lastElementChild.querySelector("button").addEventListener("click", openAdd);
  }
}

// Adding one: an address, and a name (the site's, if left empty).
function openAdd() {
  $("add").hidden = false;
  $("add-url").value = "";
  $("add-name").value = "";
  $("add-url").focus();
}

$("add").addEventListener("submit", event => {
  event.preventDefault();
  const url = toUrl($("add-url").value);
  if (!url || url.startsWith(SEARCH) || !/^https?:/.test(url)) {
    $("add-url").focus();
    return;
  }
  const name = $("add-name").value.trim() || hostName(url);
  save([...load().filter(s => s.url !== url), { url, name }].slice(0, MAX));
  $("add").hidden = true;
  show();
});

$("add-cancel").addEventListener("click", () => {
  $("add").hidden = true;
});

if (storage) document.documentElement.dataset.editable = "";

// A tab that was in the background shows later; bring the clock up to date then.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") tick();
});

tick();
show();
