/* SuperAwesomeMagical Browser's default preferences. Startup.sys.mjs sets them as defaults (as a
 * defaults/pref file would, but after Firefox's own defaults, which a defaults/pref file can't
 * override), so anything here can still be changed in Settings or about:config.
 */

export const DEFAULT_PREFS = {
  // ---- Tabs down the left side ----
  "sidebar.revamp": true,
  "sidebar.verticalTabs": true,
  "sidebar.position_start": true,
  "sidebar.visibility": "always-show",
  "sidebar.main.tools": "history,bookmarks,syncedtabs",
  // The toolbar: sidebar toggle, back, forward, reload, the address bar, downloads, extensions and
  // account. Without it Firefox's first run with vertical tabs puts back and forward after the
  // address bar. Firefox upgrades this layout itself when it adds buttons (currentVersion).
  "browser.uiCustomization.state": JSON.stringify({
    placements: {
      "widget-overflow-fixed-list": [],
      "unified-extensions-area": [],
      "nav-bar": [
        "sidebar-button",
        "back-button",
        "forward-button",
        "stop-reload-button",
        "vertical-spacer",
        "urlbar-container",
        "downloads-button",
        "unified-extensions-button",
        "fxa-toolbar-menu-button",
      ],
      "toolbar-menubar": ["menubar-items"],
      TabsToolbar: [],
      "vertical-tabs": ["tabbrowser-tabs"],
      PersonalToolbar: ["personal-bookmarks"],
    },
    seen: [
      "developer-button",
      "screenshot-button",
      "reset-pbm-toolbar-button",
      "smartwindow-group-tabs-button",
      "ai-window-toggle",
      "alltabs-button",
    ],
    dirtyAreaCache: ["nav-bar", "vertical-tabs", "toolbar-menubar", "TabsToolbar", "PersonalToolbar"],
    currentVersion: 28,
    newElementCount: 0,
  }),
  "browser.download.autohideButton": true,
  // Bookmarks toolbar only when asked for (View › Toolbars).
  "browser.toolbars.bookmarks.visibility": "never",

  // ---- Lexend ----
  // Pages that don't choose a font get Lexend (bundled in fonts/), like the interface. Settings ›
  // Fonts changes it back.
  "font.default.x-western": "sans-serif",
  "font.name.sans-serif.x-western": "Lexend",
  "font.name.sans-serif.x-unicode": "Lexend",
  "font.name.sans-serif.x-cyrillic": "Lexend",
  "font.name.sans-serif.el": "Lexend",

  // ---- The start page (start/) as new tab and home page; false for Firefox's own ----
  "superawesomemagical.startpage.enabled": true,

  // ---- Quiet, private defaults (telemetry and studies are off by policy) ----
  "browser.aboutwelcome.enabled": false,
  "browser.shell.checkDefaultBrowser": false,
  "browser.newtabpage.activity-stream.showSponsored": false,
  "browser.newtabpage.activity-stream.showSponsoredTopSites": false,
  "browser.newtabpage.activity-stream.feeds.section.topstories": false,
  "browser.urlbar.suggest.quicksuggest.sponsored": false,
  "browser.urlbar.suggest.quicksuggest.nonsponsored": false,
  "browser.urlbar.trending.featureGate": false,
  "extensions.htmlaboutaddons.recommendations.enabled": false,
  "browser.discovery.enabled": false,
  "browser.preferences.moreFromMozilla": false,
  "privacy.globalprivacycontrol.enabled": true,
  "dom.security.https_only_mode_pbm": true,
};

export function setDefaultPrefs(prefs = DEFAULT_PREFS) {
  const branch = Services.prefs.getDefaultBranch("");
  for (const [name, value] of Object.entries(prefs)) {
    try {
      switch (typeof value) {
        case "boolean":
          branch.setBoolPref(name, value);
          break;
        case "number":
          branch.setIntPref(name, value);
          break;
        default:
          branch.setStringPref(name, String(value));
      }
    } catch (e) {
      console.error("SuperAwesomeMagical: could not set", name, e);
    }
  }
}
