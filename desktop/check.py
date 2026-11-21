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
import json
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

# A stand-in for SuperAwesomeMagicalOrchestrator's agent command (sam-orchestrator): it writes down
# what it was run with (one JSON list a line, in "calls") and prints what "answers.json" says for
# that command, or fails when there's nothing there. The browser finds it through
# SAM_ORCHESTRATOR_COMMAND.
FAKE_AGENT = """#!/usr/bin/env python3
import json, os, sys
here = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[1:]
with open(os.path.join(here, "calls"), "a") as calls:
    calls.write(json.dumps(args) + "\\n")
with open(os.path.join(here, "answers.json")) as answers:
    answer = json.load(answers).get(args[0] if args else "")
if answer is None or answer is False:
    sys.stderr.write("Nobody's signed in on this computer.\\n")
    sys.exit(1)
print("The link is on its way." if args[0] == "send-link" else json.dumps(answer))
"""
PHONE, TABLET, DASH = "Zx9bT0aPq3LmN7cRk2VwYg", "Qm4dS8eHj1UoX5fAi6BnCt", "-Ab3_dE7gH1jK5mN9pQ2sT"
HOSTILE = """<img src=x onerror=alert(1)>"'; rm -rf ~ $(id) --json"""


class FakeAgent:
    def __init__(self, folder, base):
        self.folder = folder
        self.base = base
        folder.mkdir()
        self.command = folder / "sam-orchestrator"
        self.command.write_text(FAKE_AGENT)
        self.command.chmod(0o755)
        self.calls_file = folder / "calls"
        self.answer()

    def answer(self, signed_in=True, devices=None, send=True, tidy=False):
        here = {"id": "Hh1aaaaaaaaaaaaaaaaaaa", "name": "Den laptop"}
        if devices is None:
            devices = [
                {"id": TABLET, "name": "Kitchen tablet", "kind": "ANDROID", "online": False, "current": False},
                {**here, "kind": "LINUX", "online": True, "current": True},
                {"id": PHONE, "name": "Maddie's phone", "kind": "ANDROID", "online": True, "current": False},
            ]
        answers = {"status": {"signedIn": signed_in, "server": "https://orchestrator.example.com", "username": "maddie",
                              "displayName": "Maddie", "connected": signed_in, "device": here}}
        if signed_in:
            answers.update({
                "devices": devices,
                "send-link": send,
                "apps": [{"id": "music", "name": "SuperAwesomeMagicalMusic", "url": "https://music.example.com"},
                         {"id": "video", "name": "SuperAwesomeMagicalVideo", "url": "https://video.example.com/"},
                         {"id": "bad", "name": "<i>Bad</i>", "url": "javascript:alert(2)"}],
                "activities": [
                    {"device": PHONE, "deviceName": "Maddie's phone", "app": "video", "title": "Northern lights tonight",
                     "detail": "", "url": self.base + "/aurora.html", "at": 1795000000000},
                    {"device": TABLET, "deviceName": "Kitchen tablet" if tidy else "<b>Kitchen</b> tablet", "app": "music",
                     "title": "Weeknight pasta playlist" if tidy else "<img src=x onerror=alert(1)> & co",
                     "detail": "", "url": "https://music.example.com/album/9", "at": 1794999990000},
                    {"device": PHONE, "deviceName": "Maddie's phone", "app": "video", "title": "Sneaky",
                     "detail": "", "url": "javascript:alert(1)", "at": 1794999980000},
                    {"device": PHONE, "deviceName": "Maddie's phone", "app": "music", "title": "A song in the app",
                     "detail": "", "url": "sammusic://track/9", "at": 1794999970000},
                ],
            })
        (self.folder / "answers.json").write_text(json.dumps(answers))

    def calls(self):
        if not self.calls_file.exists():
            return []
        return [json.loads(line) for line in self.calls_file.read_text().splitlines()]

    def sends(self, wait=0):
        until = time.time() + wait
        while True:
            found = [c for c in self.calls() if c[:1] == ["send-link"]]
            if found or time.time() >= until:
                return found
            time.sleep(0.2)

    def forget(self):
        self.calls_file.unlink(missing_ok=True)


# For the orchestrator checks, in the browser window: the module, and what a menu offers once the
# agent has answered.
ORCHESTRATOR_JS = """
    const { Orchestrator } = ChromeUtils.importESModule("resource://superawesomemagical/modules/Orchestrator.sys.mjs");
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const offered = async (popupId, ids) => {
      const popup = document.getElementById(popupId);
      for (let i = 0; i < 50 && popup.state !== "open"; i++) await sleep(100);
      await Orchestrator.devices();
      await sleep(150);
      const all = Object.fromEntries(ids.map(id => {
        const menu = document.getElementById(id);
        return [id, { shown: popup.state === "open" && !menu.hidden, label: menu.getAttribute("label"),
          devices: [...menu.menupopup.children].map(item => item.getAttribute("label")),
          elements: menu.menupopup.querySelectorAll(":scope > :not(menuitem), :scope > * > *").length }];
      }));
      return ids.length === 1 ? all[ids[0]] : all;
    };
    const close = async id => {
      const popup = document.getElementById(id);
      popup?.hidePopup();
      for (let i = 0; popup && i < 30 && popup.state !== "closed"; i++) await sleep(100);
    };
    const choose = async (menuId, label) => {
      await close("confirmation-hint");
      const menu = document.getElementById(menuId);
      [...menu.menupopup.children].find(item => item.getAttribute("label") === label).doCommand();
      menu.parentNode.hidePopup();
    };
    const hint = async () => {
      const text = () => (document.getElementById("confirmation-hint-message")?.textContent ?? "").replace(/[\\u2068\\u2069]/g, "");
      for (let i = 0; i < 50 && !(document.getElementById("confirmation-hint")?.state === "open" && text()); i++) await sleep(100);
      return text();
    };
"""


def check(name, ok, detail=""):
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  ({detail})" if detail and not ok else ""))
    if not ok:
        failures.append(name)


def serve_pages():
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            title = PAGES.get(self.path.split("?")[0])
            if not title:
                self.send_error(404)
                return
            body = (f"<!doctype html><meta charset=utf-8><title>{title}</title>"
                    f"<body style='margin:40px'><h1>{title}</h1><p>Text in the browser's default "
                    "font.</p><p><a id='link' href='/trail.html?from=here'>A link</a> <button>A button</button> "
                    "<input placeholder='An input'> <a id='mail' href='mailto:someone@example.org'>Write</a></p>").encode()
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
    # A throwaway home too: Firefox won't start as root in a home owned by someone else, which is
    # how CI's containers run (HOME is the runner's).
    agent = FakeAgent(data / "agent", base)
    env = dict(os.environ, HOME=str(data), XDG_DATA_HOME=str(data / "share"), MOZ_REMOTE_ALLOW_SYSTEM_ACCESS="1",
               MOZ_CRASHREPORTER_DISABLE="1", SAM_ORCHESTRATOR_COMMAND=str(agent.command))
    print(f"Starting {args.launcher}")
    log = open(data / "browser.log", "w")
    browser = subprocess.Popen([args.launcher, "--marionette"], env=env, stdout=log, stderr=log)
    m = None
    try:
        m = Marionette()
        run_checks(m, base, args.screenshots, agent)
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


def grab_display(path):
    """The whole X display: menus and other popups aren't in Marionette's screenshots."""
    if shutil.which("import"):
        subprocess.run(["import", "-window", "root", str(path)], check=False, stderr=subprocess.DEVNULL)


def orchestrator_checks(m, base, agent):
    page_url, link_url = base + "/recipes.html", base + "/trail.html?from=here"
    asked_early = [c for c in agent.calls() if c[:1] in (["devices"], ["status"], ["send-link"])]
    check("the agent isn't asked about devices before a menu opens", not asked_early, asked_early)

    m.right_click("h1", content=True)
    menu = m.js(ORCHESTRATOR_JS + """return offered("contentAreaContextMenu", ["sam-send-page", "sam-send-link"]);""")
    check("the page's menu has Send page to…, with the other devices (online first, not this one)",
          menu["sam-send-page"]["shown"] and menu["sam-send-page"]["label"] == "Send page to…"
          and menu["sam-send-page"]["devices"] == ["Maddie's phone", "Kitchen tablet (away)"]
          and not menu["sam-send-link"]["shown"], menu)
    check("nothing is sent by opening a menu", not agent.sends(), agent.sends())
    said = m.js(ORCHESTRATOR_JS + """await choose("sam-send-page", "Maddie's phone"); return hint();""")
    sends = agent.sends(wait=5)
    check("choosing a device runs send-link with that device's id and the page's address, and nothing else",
          sends == [["send-link", "--", PHONE, page_url]], sends)
    check("and says so (Sent to Maddie's phone)", said == "Sent to Maddie's phone", said)

    agent.forget()
    m.right_click("#link", content=True)
    menu = m.js(ORCHESTRATOR_JS + """return offered("contentAreaContextMenu", ["sam-send-page", "sam-send-link"]);""")
    check("a link's menu has Send link to…", menu["sam-send-link"]["shown"] and menu["sam-send-link"]["label"] == "Send link to…"
          and len(menu["sam-send-link"]["devices"]) == 2 and not menu["sam-send-page"]["shown"], menu)
    said = m.js(ORCHESTRATOR_JS + """await choose("sam-send-link", "Kitchen tablet (away)"); return hint();""")
    sends = agent.sends(wait=5)
    check("which sends the link's address", sends == [["send-link", "--", TABLET, link_url]], sends)
    check("and says a device that's away will get it later", said == "Kitchen tablet is away", said)

    agent.forget()
    m.right_click(".tabbrowser-tab[selected]")
    menu = m.js(ORCHESTRATOR_JS + """return offered("tabContextMenu", ["sam-send-tab"]);""")
    check("a tab's menu has Send tab to…", menu["shown"] and menu["label"] == "Send tab to…" and len(menu["devices"]) == 2, menu)
    m.js(ORCHESTRATOR_JS + """await choose("sam-send-tab", "Maddie's phone"); await hint();""")
    sends = agent.sends(wait=5)
    check("which sends that tab's address", sends == [["send-link", "--", PHONE, page_url]], sends)

    agent.forget()
    agent.answer(send=False)
    m.right_click("h1", content=True)
    said = m.js(ORCHESTRATOR_JS + """await offered("contentAreaContextMenu", ["sam-send-page"]);
        await choose("sam-send-page", "Maddie's phone"); return hint();""")
    check("a send that fails says so plainly", said == "That didn't reach Maddie's phone" and len(agent.sends(wait=5)) == 1, said)

    # Names are the person's own, and could be anything; ids come from the server.
    agent.forget()
    agent.answer(devices=[
        {"id": PHONE, "name": HOSTILE, "online": True, "current": False},
        {"id": DASH, "name": "--version", "online": True, "current": False},
        {"id": "a b; touch /tmp/x", "name": "Not an id", "online": True, "current": False},
        {"id": "", "name": "Nor this", "online": True, "current": False},
    ])
    m.js(ORCHESTRATOR_JS + "Orchestrator._devices = null;")
    m.right_click("h1", content=True)
    menu = m.js(ORCHESTRATOR_JS + """return offered("contentAreaContextMenu", ["sam-send-page"]);""")
    check("a device name with markup, quotes and dashes shows as text",
          HOSTILE in menu["devices"] and "--version" in menu["devices"] and menu["elements"] == 0, menu)
    check("devices whose ids aren't ids are left out", len(menu["devices"]) == 2, menu)
    m.js(ORCHESTRATOR_JS + f"""await choose("sam-send-page", {json.dumps(HOSTILE)}); await hint();""")
    sends = agent.sends(wait=5)
    check("and doesn't change the command", sends == [["send-link", "--", PHONE, page_url]], sends)
    agent.forget()
    m.right_click("h1", content=True)
    m.js(ORCHESTRATOR_JS + """await offered("contentAreaContextMenu", ["sam-send-page"]);
        await choose("sam-send-page", "--version"); await hint();""")
    sends = agent.sends(wait=5)
    check("an id that starts with a dash is still only an id", sends == [["send-link", "--", DASH, page_url]], sends)

    agent.forget()
    agent.answer()
    m.js(ORCHESTRATOR_JS + "Orchestrator._devices = null; await Orchestrator.devices();")
    m.right_click("#mail", content=True)
    menu = m.js(ORCHESTRATOR_JS + """const o = await offered("contentAreaContextMenu", ["sam-send-page", "sam-send-link"]);
        await close("contentAreaContextMenu"); return o;""")
    check("a link that isn't a web address offers nothing", not menu["sam-send-link"]["shown"] and not menu["sam-send-page"]["shown"], menu)
    menu = m.js(ORCHESTRATOR_JS + """
        const before = gBrowser.selectedTab;
        gBrowser.selectedTab = gBrowser.addTrustedTab("about:license");
        await sleep(1200);
        const { webAddress } = ChromeUtils.importESModule("resource://superawesomemagical/modules/Orchestrator.sys.mjs");
        const refused = ["about:config", "javascript:alert(1)", "file:///etc/passwd", "data:text/html,x", "sammusic://track/9",
          "https://someone:secret@example.org/", "https://example.org/a b", "-https://example.org"].filter(webAddress);
        return { before: gBrowser.tabs.indexOf(before), refused, sent: await Orchestrator.send({ id: "%s" }, "about:config") };""" % PHONE)
    m.right_click(".tabbrowser-tab[selected]")
    tab_menu = m.js(ORCHESTRATOR_JS + """const o = await offered("tabContextMenu", ["sam-send-tab"]);
        await close("tabContextMenu"); return o;""")
    check("a page that isn't on the web offers nothing (about:license)", not tab_menu["shown"] and not menu["refused"]
          and menu["sent"] is False and not agent.sends(), [tab_menu, menu])
    m.js(f"""const tab = gBrowser.selectedTab; gBrowser.selectedTab = gBrowser.tabs[{menu["before"]}]; gBrowser.removeTab(tab);
             await new Promise(r => setTimeout(r, 500));""")

    def hidden(why):
        m.js(ORCHESTRATOR_JS + "Orchestrator._devices = null;")
        m.right_click("h1", content=True)
        page = m.js(ORCHESTRATOR_JS + """const o = await offered("contentAreaContextMenu", ["sam-send-page"]);
            await close("contentAreaContextMenu"); return o;""")
        m.right_click(".tabbrowser-tab[selected]")
        tab = m.js(ORCHESTRATOR_JS + """const o = await offered("tabContextMenu", ["sam-send-tab"]);
            await close("tabContextMenu"); return o;""")
        check(f"nothing is offered when {why}", not page["shown"] and not tab["shown"], [page, tab])

    agent.answer(signed_in=False)
    hidden("nobody is signed in")
    agent.answer(devices=[{"id": "Hh1aaaaaaaaaaaaaaaaaaa", "name": "Den laptop", "online": True, "current": True}])
    hidden("this is the only device")
    agent.answer()
    m.js("""Services.env.set("SAM_ORCHESTRATOR_COMMAND", "/nonexistent/sam-orchestrator");""")
    hidden("the agent isn't installed")
    m.js(f"""Services.env.set("SAM_ORCHESTRATOR_COMMAND", {json.dumps(str(agent.command))});
             Services.prefs.setBoolPref("superawesomemagical.orchestrator.send", false);""")
    hidden("it's turned off (superawesomemagical.orchestrator.send)")
    m.js("""Services.prefs.clearUserPref("superawesomemagical.orchestrator.send");""")

    publishes = [c for c in agent.calls() if c and c[0] not in ("status", "devices", "apps", "activities", "send-link")]
    check("the agent is only ever asked those five things (browsing is never published)", not publishes, publishes)


def run_checks(m, base, shots, agent):
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
    carry = m.js("""
        const doc = gBrowser.selectedBrowser.contentDocument, section = doc.getElementById("carry-on");
        for (let i = 0; i < 30 && section.hidden; i++) await new Promise(r => setTimeout(r, 100));
        return { shown: !section.hidden && section.getBoundingClientRect().height > 0,
          activities: [...doc.querySelectorAll("#activities .activity")].map(a => [
            a.querySelector(".activity-title").textContent, a.querySelector(".activity-where").textContent, a.href]),
          apps: [...doc.querySelectorAll("#apps .app")].map(a => [a.textContent, a.href]),
          markup: section.querySelectorAll("b, i, img, script, [onerror]").length,
          text: section.textContent };""")
    check("Carry on shows what other devices are doing, as text",
          carry["shown"] and carry["activities"] == [
              ["Northern lights tonight", "Video on Maddie's phone", base + "/aurora.html"],
              ["<img src=x onerror=alert(1)> & co", "Music on <b>Kitchen</b> tablet", "https://music.example.com/album/9"],
          ] and carry["markup"] == 0, carry)
    check("and the family's apps", carry["apps"] == [["Music", "https://music.example.com/"], ["Video", "https://video.example.com/"]], carry["apps"])
    check("but nothing that isn't a web address (javascript:, an app's own link)",
          "Sneaky" not in carry["text"] and "A song in the app" not in carry["text"] and "Bad" not in carry["text"], carry["text"])
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

    carried = m.js(f"""
        const b = gBrowser.selectedBrowser, doc = b.contentDocument;
        for (let i = 0; i < 30 && !doc.querySelector(".activity"); i++) await new Promise(r => setTimeout(r, 100));
        doc.querySelector(".activity")?.click();
        for (let i = 0; i < 50 && b.currentURI.spec.startsWith("chrome:"); i++) await new Promise(r => setTimeout(r, 200));
        const opened = b.currentURI.spec, tabs = gBrowser.tabs.length;
        Services.prefs.setBoolPref("superawesomemagical.orchestrator.carryOn", false);
        BrowserCommands.openTab();
        await new Promise(r => setTimeout(r, 1500));
        const off = gBrowser.selectedBrowser.contentDocument.getElementById("carry-on").hidden;
        Services.prefs.clearUserPref("superawesomemagical.orchestrator.carryOn");
        gBrowser.removeTab(gBrowser.selectedTab);
        return {{ opened, tabs, off }};""")
    check("choosing one opens it in this tab", carried["opened"] == base + "/aurora.html", carried)
    check("Carry on can be turned off (superawesomemagical.orchestrator.carryOn)", carried["off"], carried)
    m.js("""BrowserCommands.openTab(); await new Promise(r => setTimeout(r, 1500));""")

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

    print("Send to device (SuperAwesomeMagicalOrchestrator)")
    orchestrator_checks(m, base, agent)

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
        agent.answer(tidy=True)
        m.js(ORCHESTRATOR_JS + """Orchestrator._carryOn = null; gBrowser.tabs[0].linkedBrowser.reload(); await sleep(1500);""")
        for scheme, dark in (("light", 0), ("dark", 1)):
            m.js(f"""Services.prefs.setIntPref("ui.systemUsesDarkTheme", {dark});
                     gBrowser.selectedTab = gBrowser.tabs[0];
                     await new Promise(r => setTimeout(r, 1200));""")
            m.screenshot(str(shots / f"start-{scheme}.png"))
            m.js("""gBrowser.selectedTab = gBrowser.tabs.find(t => t.linkedBrowser.currentURI.spec.endsWith("recipes.html"));
                    await new Promise(r => setTimeout(r, 900));""")
            m.screenshot(str(shots / f"page-{scheme}.png"))
            # The menus and the confirmation, from the display (popups aren't in Marionette's own).
            m.right_click("#link", content=True)
            m.js(ORCHESTRATOR_JS + """await offered("contentAreaContextMenu", ["sam-send-link"]);
                    document.getElementById("sam-send-link").openMenu(true); await sleep(900);""")
            grab_display(shots / f"send-link-menu-{scheme}.png")
            m.js(ORCHESTRATOR_JS + """await choose("sam-send-link", "Maddie's phone"); await hint(); await sleep(700);""")
            grab_display(shots / f"sent-{scheme}.png")
            m.js(ORCHESTRATOR_JS + """await close("confirmation-hint");""")
            m.right_click(".tabbrowser-tab[selected]")
            m.js(ORCHESTRATOR_JS + """await offered("tabContextMenu", ["sam-send-tab"]);
                    document.getElementById("sam-send-tab").openMenu(true); await sleep(900);""")
            grab_display(shots / f"send-tab-menu-{scheme}.png")
            m.js(ORCHESTRATOR_JS + """await close("tabContextMenu");""")
            m.js("""gBrowser.selectedTab = gBrowser.tabs.at(-1);
                    await new Promise(r => setTimeout(r, 900));""")
            m.screenshot(str(shots / f"settings-{scheme}.png"))


if __name__ == "__main__":
    main()
