#!/usr/bin/env python3
"""Starts SuperAwesomeMagical Browser and checks that everything we add to Firefox works.

    python3 desktop/check.py                      the browser built by desktop/build.sh
    python3 desktop/check.py /usr/bin/superawesomemagicalbrowser     an installed package
    python3 desktop/check.py --screenshots DIR    ... and save screenshots (light and dark)

It runs the real launcher with a new, throwaway profile (and its own Xvfb display if there is
none), drives the browser over Marionette, and fails if any check fails. CI runs it on the
tarball and on the installed .deb and .rpm.
"""
import argparse
import http.server
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from marionette import Marionette  # noqa: E402

HERE = Path(__file__).resolve().parent
START_URL = "chrome://superawesomemagical/content/index.html"
APP_NAME = "SuperAwesomeMagical Browser"
PAGES = {
    "/aurora.html": "Aurora forecast",
    "/recipes.html": "Recipes — Weeknight pasta",
    "/trail.html": "Trail map: Upper Geyser Basin",
}

failures = []


def check(name, ok, detail=""):
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  ({detail})" if detail and not ok else ""))
    if not ok:
        failures.append(name)


def serve_pages():
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            title = PAGES.get(self.path)
            if not title:
                self.send_error(404)
                return
            body = (f"<!doctype html><meta charset=utf-8><title>{title}</title>"
                    f"<body style='margin:40px'><h1>{title}</h1><p>Text in the browser's default "
                    "font.</p><p><a href='#'>A link</a> <button>A button</button> "
                    "<input placeholder='An input'></p>").encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return f"http://127.0.0.1:{server.server_address[1]}"


def start_display():
    if os.environ.get("DISPLAY"):
        return None
    for n in range(91, 99):
        if not Path(f"/tmp/.X11-unix/X{n}").exists():
            proc = subprocess.Popen(["Xvfb", f":{n}", "-screen", "0", "1440x900x24", "-nolisten", "tcp"],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            os.environ["DISPLAY"] = f":{n}"
            time.sleep(1.5)
            return proc
    raise RuntimeError("no free X display")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("launcher", nargs="?",
                        default=str(HERE / "out/superawesomemagicalbrowser/superawesomemagicalbrowser"))
    parser.add_argument("--screenshots", type=Path)
    args = parser.parse_args()

    xvfb = start_display()
    base = serve_pages()
    data = Path(tempfile.mkdtemp(prefix="samb-check-"))
    env = dict(os.environ, XDG_DATA_HOME=str(data), MOZ_REMOTE_ALLOW_SYSTEM_ACCESS="1",
               MOZ_CRASHREPORTER_DISABLE="1")
    print(f"Starting {args.launcher}")
    log = open(data / "browser.log", "w")
    browser = subprocess.Popen([args.launcher, "--marionette"], env=env, stdout=log, stderr=log)
    m = None
    try:
        m = Marionette()
        run_checks(m, base, args.screenshots)
    except Exception as e:
        check("the browser starts and answers", False, str(e))
        print((data / "browser.log").read_text()[-4000:])
    finally:
        if m:
            m.quit()
        try:
            browser.wait(20)
        except subprocess.TimeoutExpired:
            browser.kill()
        if xvfb:
            xvfb.terminate()
        shutil.rmtree(data, ignore_errors=True)

    if failures:
        print(f"\n{len(failures)} check(s) failed: " + "; ".join(failures))
        sys.exit(1)
    print("\nAll checks passed.")


def run_checks(m, base, shots):
    # Give the first window time to finish starting (delayed startup, the start page).
    m.js("""
        for (let i = 0; i < 100; i++) {
          if (window.gBrowserInit?.delayedStartupFinished &&
              gBrowser.selectedBrowser.contentDocument?.getElementById("sites")?.children.length) return;
          await new Promise(r => setTimeout(r, 200));
        }""")

    print("Preferences")
    prefs = m.js("""
        const get = n => { const t = Services.prefs.getPrefType(n);
          return t == 128 ? Services.prefs.getBoolPref(n) : t == 64 ? Services.prefs.getIntPref(n)
               : t == 32 ? Services.prefs.getStringPref(n) : null; };
        return Object.fromEntries(["sidebar.revamp", "sidebar.verticalTabs", "sidebar.position_start",
          "font.name.sans-serif.x-western", "font.default.x-western", "browser.startup.homepage",
          "general.config.filename"].map(n => [n, get(n)]));""")
    check("tabs down the left (sidebar.verticalTabs)", prefs["sidebar.verticalTabs"] is True and prefs["sidebar.position_start"] is True, prefs)
    check("Lexend is the default font for pages", prefs["font.name.sans-serif.x-western"] == "Lexend" and prefs["font.default.x-western"] == "sans-serif", prefs)
    check("the start page is the home page", prefs["browser.startup.homepage"] == START_URL, prefs["browser.startup.homepage"])

    print("Window")
    win = m.js("""
        const root = getComputedStyle(document.documentElement);
        const tabs = document.getElementById("tabbrowser-tabs");
        const fonts = Cc["@mozilla.org/gfx/fontenumerator;1"].getService(Ci.nsIFontEnumerator).EnumerateAllFonts();
        return {
          samBg: root.getPropertyValue("--sam-bg").trim(),
          font: root.fontFamily,
          urlbarFont: getComputedStyle(document.getElementById("urlbar-input") ?? document.documentElement).fontFamily,
          orient: tabs.getAttribute("orient"),
          tabsX: Math.round(tabs.getBoundingClientRect().x),
          tabsW: Math.round(tabs.getBoundingClientRect().width),
          cardRadius: getComputedStyle(document.getElementById("tabbrowser-tabbox")).borderTopRightRadius,
          lexend: fonts.includes("Lexend"),
          nav: CustomizableUI.getWidgetIdsInArea("nav-bar").slice(0, 4),
          newTab: BROWSER_NEW_TAB_URL,
        };""")
    check("the theme is loaded (theme.css)", win["samBg"] in ("#0b1220", "#f5f7fb"), win["samBg"])
    check("the interface is in Lexend", "Lexend" in win["font"] and "Lexend" in win["urlbarFont"], win["font"])
    check("Lexend is installed with the browser", win["lexend"])
    check("tabs are vertical, on the left", win["orient"] == "vertical" and win["tabsX"] < 20 and win["tabsW"] > 100, win)
    check("the page is a rounded card (browser.css)", win["cardRadius"] == "16px", win["cardRadius"])
    check("toolbar starts with sidebar, back, forward, reload",
          win["nav"] == ["sidebar-button", "back-button", "forward-button", "stop-reload-button"], win["nav"])
    check("the start page is the new tab page", win["newTab"] == START_URL, win["newTab"])

    print("Start page")
    start = m.js("""
        const b = gBrowser.selectedBrowser, doc = b.contentDocument;
        return { url: b.currentURI.spec, time: doc?.getElementById("time")?.textContent,
                 placeholder: doc?.getElementById("query")?.placeholder,
                 sites: doc?.querySelectorAll("#sites .site").length ?? 0,
                 font: doc && doc.defaultView.getComputedStyle(doc.body).fontFamily };""")
    check("opens at startup", start["url"] == START_URL, start["url"])
    check("shows the clock", bool((start["time"] or "").strip()), start["time"])
    check("searches with Ecosia", "Ecosia" in (start["placeholder"] or ""), start["placeholder"])
    check("shows top sites", start["sites"] >= 4, start["sites"])
    check("is in Lexend", "Lexend" in (start["font"] or ""), start["font"])
    engine = m.js("""
        const { SearchService } = ChromeUtils.importESModule("moz-src:///toolkit/components/search/SearchService.sys.mjs");
        await SearchService.init();
        return (await SearchService.getDefault()).name;""")
    check("Ecosia is the default search engine", engine == "Ecosia", engine)

    newtab = m.js("""
        BrowserCommands.openTab();
        await new Promise(r => setTimeout(r, 1500));
        return { url: gBrowser.selectedBrowser.currentURI.spec, urlbar: gURLBar.value };""")
    check("a new tab opens the start page, address bar empty",
          newtab["url"] == START_URL and newtab["urlbar"] == "", newtab)

    searched = m.js("""
        const b = gBrowser.selectedBrowser, doc = b.contentDocument;
        doc.getElementById("query").value = "northern lights";
        doc.getElementById("search").requestSubmit();
        for (let i = 0; i < 50 && b.currentURI.spec.startsWith("chrome:"); i++) await new Promise(r => setTimeout(r, 200));
        return b.currentURI.spec;""")
    check("its search box searches", searched.startswith("https://www.ecosia.org/search?q=northern"), searched)

    print("Pages")
    page = m.js(f"""
        for (const p of {list(PAGES)!r}) gBrowser.addTrustedTab("{base}" + p);
        gBrowser.selectedTab = gBrowser.tabs.at(-2);
        await new Promise(r => setTimeout(r, 2500));
        return {{ title: document.title }};""")
    check("window titles end in our name", page["title"].endswith("— " + APP_NAME), page["title"])

    settings = m.js("""
        const tab = gBrowser.addTrustedTab("about:preferences");
        gBrowser.selectedTab = tab;
        await new Promise(r => setTimeout(r, 2500));
        const doc = tab.linkedBrowser.contentDocument, s = doc.defaultView.getComputedStyle(doc.body);
        const root = doc.defaultView.getComputedStyle(doc.documentElement);
        return { bg: s.backgroundColor, font: s.fontFamily, accent: root.getPropertyValue("--color-accent-primary").trim() };""")
    check("Settings has our colours and Lexend",
          settings["bg"] in ("rgb(11, 18, 32)", "rgb(245, 247, 251)") and "Lexend" in settings["font"], settings)

    errors = m.js("""
        return (Services.console.getMessageArray() || [])
          .filter(e => e instanceof Ci.nsIScriptError && !(e.flags & Ci.nsIScriptError.warningFlag))
          .map(e => `${e.errorMessage} (${e.sourceName}:${e.lineNumber})`)
          .filter(t => /superawesomemagical/i.test(t));""")
    check("no errors from our files", not errors, "; ".join(errors)[:600])

    if shots:
        shots.mkdir(parents=True, exist_ok=True)
        print(f"Screenshots in {shots}")
        # Marionette's "remote control" stripes in the address bar aren't part of the browser.
        m.js("""const css = "data:text/css," + encodeURIComponent(
                  ".urlbar-background{background-image:none!important} #remote-control-box{display:none!important}");
                window.windowUtils.loadSheetUsingURIString(css, window.windowUtils.AUTHOR_SHEET);""")
        for scheme, dark in (("light", 0), ("dark", 1)):
            m.js(f"""Services.prefs.setIntPref("ui.systemUsesDarkTheme", {dark});
                     gBrowser.selectedTab = gBrowser.tabs[0];
                     await new Promise(r => setTimeout(r, 1200));""")
            m.screenshot(str(shots / f"start-{scheme}.png"))
            m.js("""gBrowser.selectedTab = gBrowser.tabs.find(t => t.linkedBrowser.currentURI.spec.endsWith("recipes.html"));
                    await new Promise(r => setTimeout(r, 900));""")
            m.screenshot(str(shots / f"page-{scheme}.png"))
            m.js("""gBrowser.selectedTab = gBrowser.tabs.at(-1);
                    await new Promise(r => setTimeout(r, 900));""")
            m.screenshot(str(shots / f"settings-{scheme}.png"))


if __name__ == "__main__":
    main()
