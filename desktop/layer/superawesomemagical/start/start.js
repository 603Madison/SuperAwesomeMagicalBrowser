// SuperAwesomeMagical Browser's start page. It is a chrome:// page (registered by
// modules/Startup.sys.mjs), so it runs in the browser's own process like Settings does and reads
// the top sites and search engine directly. Everything from outside (titles, addresses, favicons)
// goes into the page as text, attributes or data: images only; never as markup.
"use strict";

const $ = id => document.getElementById(id);
const privileged = typeof ChromeUtils !== "undefined";
const lazy = {};
if (privileged) {
  document.documentElement.setAttribute("data-browser", "");
  ChromeUtils.defineESModuleGetters(lazy, {
    NewTabUtils: "resource://gre/modules/NewTabUtils.sys.mjs",
    SearchService: "moz-src:///toolkit/components/search/SearchService.sys.mjs",
  });
}

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

// ---- Search: what is typed opens as the address bar would open it ----
// An address opens; anything else is searched for with the default engine (URI fixup with keyword
// lookup). Only web and about: addresses open from here.

const OPENABLE = new Set(["http", "https", "about", "view-source"]);

$("search").addEventListener("submit", event => {
  event.preventDefault();
  const text = $("query").value.trim();
  if (!text) {
    return;
  }
  if (!privileged) {
    if (/^https?:\/\//i.test(text)) {
      location.href = text;
    }
    return;
  }
  let uri = null;
  try {
    uri = Services.uriFixup.getFixupURIInfo(
      text,
      Ci.nsIURIFixup.FIXUP_FLAG_ALLOW_KEYWORD_LOOKUP | Ci.nsIURIFixup.FIXUP_FLAG_FIX_SCHEME_TYPOS
    ).preferredURI;
  } catch {}
  if (uri && OPENABLE.has(uri.scheme)) {
    location.href = uri.spec;
  }
});

// ---- Top sites ----

// The tile colours for sites without a favicon, as in the apps' avatars.
const TINTS = ["primary", "secondary", "tertiary"];

function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

function nameOf(site) {
  const host = hostOf(site.url);
  const label = host.split(".")[0] || host;
  // A page title is often long ("Home - Site name"); the site's name reads better on a tile.
  return site.title && site.title.length <= 14 ? site.title : label.charAt(0).toUpperCase() + label.slice(1);
}

function showSites(sites) {
  const list = $("sites");
  const template = $("site");
  list.replaceChildren();
  sites.forEach((site, i) => {
    const item = template.content.firstElementChild.cloneNode(true);
    const link = item.querySelector(".site-link");
    link.href = site.url;
    link.title = site.title ? `${site.title}\n${site.url}` : site.url;
    const name = nameOf(site);
    item.querySelector(".name").textContent = name;
    const tile = item.querySelector(".tile");
    const img = item.querySelector(".favicon");
    if (site.icon) {
      img.src = site.icon;
      tile.classList.add("has-icon");
    } else {
      img.remove();
      item.querySelector(".letter").textContent = name.charAt(0).toUpperCase();
      tile.dataset.tint = TINTS[i % TINTS.length];
    }
    item.querySelector(".remove").addEventListener("click", () => removeSite(site.url));
    list.append(item);
  });
  list.hidden = sites.length === 0;
}

// The most visited sites (Firefox's top sites, without the ones removed here), topped up with a few
// well-known ones for a new profile.
const MAX_SITES = 8;
const STARTER_SITES = [
  { url: "https://www.wikipedia.org/", title: "Wikipedia" },
  { url: "https://www.youtube.com/", title: "YouTube" },
  { url: "https://github.com/", title: "GitHub" },
  { url: "https://www.reddit.com/", title: "Reddit" },
];

async function topSites() {
  let sites = [];
  try {
    sites = (await lazy.NewTabUtils.activityStreamLinks.getTopSites({ numItems: MAX_SITES }))
      .filter(link => /^https?:/.test(link.url))
      .map(link => ({
        url: link.url,
        title: link.title || "",
        icon: typeof link.favicon === "string" && link.favicon.startsWith("data:image/") ? link.favicon : "",
      }));
  } catch (e) {
    console.error(e);
  }
  for (const starter of STARTER_SITES) {
    if (sites.length >= 4) {
      break;
    }
    const taken = sites.some(s => hostOf(s.url) === hostOf(starter.url));
    if (!taken && !lazy.NewTabUtils.blockedLinks.isBlocked({ url: starter.url })) {
      sites.push({ ...starter, icon: "" });
    }
  }
  return sites;
}

function removeSite(url) {
  if (privileged && /^https?:/.test(url)) {
    lazy.NewTabUtils.activityStreamLinks.blockURL({ url });
    refresh();
  }
}

async function refresh() {
  if (!privileged) {
    return;
  }
  try {
    const engine = await (Services.search ?? lazy.SearchService).getDefault();
    if (engine?.name) {
      const text = `Search with ${engine.name} or enter address`;
      $("query").placeholder = text;
      $("query").setAttribute("aria-label", text);
    }
  } catch (e) {
    console.error(e);
  }
  showSites(await topSites());
}

// A preloaded or restored tab is shown later; bring the clock and the sites up to date then.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    tick();
    refresh();
  }
});

tick();
refresh();
