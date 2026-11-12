/* SuperAwesomeMagical Browser's startup: everything superawesomemagical.cfg starts.
 *
 * - Our default preferences (Prefs.sys.mjs): tabs down the left, Lexend, quiet defaults.
 * - The look: styles/theme.css (colours and Lexend) in every Firefox window and dialog, and
 *   styles/browser.css (layout) in browser windows. Both are added per window as author sheets,
 *   so they override Firefox's design tokens without changing any of Firefox's own files.
 * - The same colours in Firefox's own pages (Settings, Add-ons and themes...), through the
 *   SAMAboutTheme window actor.
 * - The start page (start/, registered as chrome://superawesomemagical/content/) as the new tab
 *   page and the default home page.
 *
 * It runs very early (while preferences are first read), so it registers things and leaves the
 * rest to observers.
 */

import { setDefaultPrefs } from "resource://superawesomemagical/modules/Prefs.sys.mjs";

const ROOT = "resource://superawesomemagical/";
export const START_URL = "chrome://superawesomemagical/content/index.html";
const THEME_CSS = ROOT + "styles/theme.css";
const BROWSER_CSS = ROOT + "styles/browser.css";
const BROWSER_WINDOW = "chrome://browser/content/browser.xhtml";
const START_PREF = "superawesomemagical.startpage.enabled";
const APP_NAME = "SuperAwesomeMagical Browser";

// Firefox's own pages drawn with its design tokens, which therefore take our colours too.
const THEMED_ABOUT_PAGES = [
  "about:preferences",
  "about:settings",
  "about:addons",
  "about:downloads",
  "about:profiles",
  "about:logins",
  "about:protections",
  "about:privatebrowsing",
  "about:firefoxview",
  "about:certerror",
  "about:neterror",
];

export const Startup = {
  initialized: false,

  init() {
    if (this.initialized) {
      return;
    }
    this.initialized = true;

    setDefaultPrefs();

    // The start page's folder as chrome://superawesomemagical/content/ (chrome.manifest).
    const manifest = Services.dirsvc.get("GreD", Ci.nsIFile);
    manifest.append("superawesomemagical");
    manifest.append("chrome.manifest");
    Components.manager.QueryInterface(Ci.nsIComponentRegistrar).autoRegister(manifest);

    Services.obs.addObserver(this, "chrome-document-global-created");
    Services.obs.addObserver(this, "final-ui-startup");
    Services.obs.addObserver(this, "browser-delayed-startup-finished");
    Services.prefs.addObserver(START_PREF, this);
    this.applyStartPage();

    ChromeUtils.registerWindowActor("SAMAboutTheme", {
      child: {
        esModuleURI: ROOT + "actors/SAMAboutThemeChild.sys.mjs",
        events: { DOMDocElementInserted: { capture: true } },
      },
      matches: THEMED_ABOUT_PAGES.flatMap(page => [page, page + "?*", page + "#*"]),
      allFrames: true,
    });
  },

  observe(subject, topic) {
    switch (topic) {
      case "chrome-document-global-created":
        // A Firefox window or dialog is being made: add the sheets before it is first drawn.
        subject.addEventListener("DOMContentLoaded", () => this.styleWindow(subject), {
          once: true,
        });
        break;
      case "final-ui-startup":
        this.applyNewTab();
        break;
      case "nsPref:changed":
        this.applyStartPage();
        this.applyNewTab();
        break;
      case "browser-delayed-startup-finished":
        // The address bar stays empty (ready to type in) on the start page, as on about:home.
        if (subject.gInitialPages && !subject.gInitialPages.includes(START_URL)) {
          subject.gInitialPages.push(START_URL);
        }
        this.arrangeToolbar(subject);
        break;
    }
  },

  // Once per profile: the sidebar button, back, forward and reload at the left of the toolbar.
  // (A new profile goes through Firefox's old toolbar migrations, which drop the sidebar button
  // and add it back at the far end.) After this the layout is the user's to customise.
  arrangeToolbar(win) {
    const done = "superawesomemagical.toolbarArranged";
    if (Services.prefs.getBoolPref(done, false) || !win.CustomizableUI) {
      return;
    }
    try {
      ["sidebar-button", "back-button", "forward-button", "stop-reload-button"].forEach((id, i) =>
        win.CustomizableUI.addWidgetToArea(id, win.CustomizableUI.AREA_NAVBAR, i)
      );
      Services.prefs.setBoolPref(done, true);
    } catch (e) {
      console.error("SuperAwesomeMagical: could not arrange the toolbar", e);
    }
  },

  styleWindow(win) {
    const url = win.location?.href ?? "";
    // Developer tools keep their own look (and monospace fonts); the start page has its own.
    if (
      !url.startsWith("chrome://") ||
      url.startsWith("chrome://devtools/") ||
      url.startsWith("chrome://superawesomemagical/") ||
      !win.windowUtils
    ) {
      return;
    }
    const utils = win.windowUtils;
    try {
      utils.loadSheetUsingURIString(THEME_CSS, utils.AUTHOR_SHEET);
      if (url === BROWSER_WINDOW) {
        utils.loadSheetUsingURIString(BROWSER_CSS, utils.AUTHOR_SHEET);
        this.nameWindow(win);
      }
    } catch (e) {
      console.error("SuperAwesomeMagical: could not style", url, e);
    }
  },

  // Window titles end in our name ("Page — SuperAwesomeMagical Browser") rather than Firefox's.
  // Firefox fills these hidden strings from its localisation and reads them when it first sets a
  // title, so they are renamed as soon as they are filled in (and again if the language changes).
  // About Firefox still says what the browser is built on.
  nameWindow(win) {
    const rename = () => {
      for (const id of ["mainWindowTitle", "privateWindowTitle"]) {
        const el = win.document.getElementById(id);
        const text = el?.textContent ?? "";
        const named = text.replace(/(Mozilla )?Firefox/, APP_NAME);
        if (named !== text) {
          el.textContent = named;
        }
      }
    };
    const strings = win.document.getElementById("titlestrings");
    if (strings) {
      new win.MutationObserver(rename).observe(strings, {
        subtree: true,
        childList: true,
        characterData: true,
      });
      rename();
    }
  },

  get startPageEnabled() {
    return Services.prefs.getBoolPref(START_PREF, true);
  },

  // The start page as the default home page. A home page chosen in Settings is a user value and
  // wins over this default.
  applyStartPage() {
    Services.prefs
      .getDefaultBranch("")
      .setStringPref("browser.startup.homepage", this.startPageEnabled ? START_URL : "about:home");
  },

  // And as the new tab page (or Firefox's own when the pref is false). Done once the profile is
  // up (final-ui-startup), as AboutNewTab needs it.
  applyNewTab() {
    try {
      const { AboutNewTab } = ChromeUtils.importESModule(
        "resource:///modules/AboutNewTab.sys.mjs"
      );
      if (this.startPageEnabled) {
        AboutNewTab.newTabURL = START_URL;
      } else if (AboutNewTab.newTabURL === START_URL) {
        AboutNewTab.resetNewTabURL();
      }
    } catch (e) {
      console.error("SuperAwesomeMagical: could not set the new tab page", e);
    }
  },
};
