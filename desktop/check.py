#!/usr/bin/env python3
"""Starts SuperAwesomeMagical Browser and checks that everything we add to Ladybird works.

    python3 desktop/check.py                      the installed browser (the .deb or .rpm)
    python3 desktop/check.py --screenshots DIR    ... and save screenshots (light and dark)

It runs the real launcher in a throwaway home (and its own Xvfb display if there is none), so the
presets are written as on a first start, with an old Firefox-based profile there to carry the
bookmarks over from. Then it drives the browser through Ladybird's own WebDriver, in the same
profile, and fails if any check fails. Bitwarden is a stand-in that answers as Bitwarden's
command-line client does; the keys that open it (Ctrl+Shift+L, then the master password) are
pressed through X, as a person would. CI runs it on the packages installed in Ubuntu and Fedora.
"""
import argparse
import ctypes
import http.server
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path

LAUNCHER = "/usr/bin/superawesomemagicalbrowser"
SHARE = "/usr/share/superawesomemagicalbrowser"
LADYBIRD = "/usr/lib/superawesomemagicalbrowser/ladybird/bin/Ladybird"
PROFILE = "superawesomemagical"
START = f"file://{SHARE}/start/index.html"

failures = []


def check(name, ok, detail=""):
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  ({detail})" if detail and not ok else ""))
    if not ok:
        failures.append(name)


# ---- A page to visit, served here ----

def serve_page():
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/login.html":
                body = (b"<!doctype html><meta charset=utf-8><title>Aurora forum - Sign in</title><body style='margin:40px'>"
                        b"<h1>Sign in</h1><form><p><input id=user name=user autocomplete=username></p>"
                        b"<p><input id=pass name=pass type=password></p><button>Sign in</button></form>")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(body)
                return
            body = (b"<!doctype html><meta charset=utf-8><title>Recipes - Weeknight pasta</title>"
                    b"<body style='margin:40px'><h1>Weeknight pasta</h1><p>Text in the browser's default "
                    b"font.</p><p><a href='#'>A link</a> <button>A button</button> <input></p>")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return f"http://127.0.0.1:{server.server_address[1]}/recipes.html"


# ---- An old Firefox-based profile, with a bookmark in its toolbar and one in its menu ----

def old_firefox_profile(home: Path):
    profile = home / ".local/share/superawesomemagicalbrowser/profile"
    profile.mkdir(parents=True)
    db = sqlite3.connect(profile / "places.sqlite")
    db.executescript("""
        CREATE TABLE moz_places (id INTEGER PRIMARY KEY, url TEXT);
        CREATE TABLE moz_bookmarks (id INTEGER PRIMARY KEY, type INTEGER, fk INTEGER, parent INTEGER,
            position INTEGER, title TEXT, dateAdded INTEGER, lastModified INTEGER, guid TEXT);
        INSERT INTO moz_places VALUES (1, 'https://music.example.org/'), (2, 'https://weather.example.org/');
        INSERT INTO moz_bookmarks VALUES
            (1, 2, NULL, 0, 0, '', 0, 0, 'root________'),
            (2, 2, NULL, 1, 0, 'menu', 0, 0, 'menu________'),
            (3, 2, NULL, 1, 1, 'toolbar', 0, 0, 'toolbar_____'),
            (4, 1, 1, 3, 0, 'Music', 1700000000000000, 1700000000000000, 'aaaaaaaaaaaa'),
            (5, 1, 2, 2, 0, 'Weather', 1700000000000000, 1700000000000000, 'bbbbbbbbbbbb');
    """)
    db.commit()
    db.close()


# ---- Bitwarden: a stand-in for its command-line client, with one login for the test page ----

MASTER_PASSWORD = "aurora2026"
LOGIN = {"username": "aurora", "password": "p@ss \"w0rd\" </script> ✨"}

FAKE_BW = r"""#!/usr/bin/env python3
import json, os, sys
args = [a for a in sys.argv[1:] if a != "--nointeraction"]
with open(os.environ["SAMB_FAKE_BW_LOG"], "a") as log:
    log.write(json.dumps({"args": args, "nointeraction": "--nointeraction" in sys.argv[1:],
                          "appdata": os.environ.get("BITWARDENCLI_APPDATA_DIR", "")}) + "\n")
session = os.environ.get("BW_SESSION")
if args == ["status"]:
    print(json.dumps({"serverUrl": "https://vault.example.org", "userEmail": "aurora@example.org",
                      "status": "unlocked" if session == "test-session" else "locked"}))
elif args[:1] == ["unlock"]:
    if os.environ.get("SAMB_BW_PASSWORD") != %(master)r:
        sys.exit("Invalid master password.")
    print("test-session")
elif args[:2] == ["list", "items"]:
    if session != "test-session":
        sys.exit("Vault is locked.")
    url = args[args.index("--url") + 1]
    items = [{"type": 1, "id": "1", "name": "Aurora forum", "login": dict(%(login)r, totp=None)}] if url.startswith(%(origin)r) else []
    print(json.dumps(items))
elif args in (["sync"], ["lock"]):
    pass
else:
    sys.exit("unexpected: " + " ".join(args))
"""


class XInput:
    """Presses keys and clicks on the X display, through the XTEST extension, as a person would."""

    def __init__(self):
        self.x11 = ctypes.CDLL("libX11.so.6")
        self.xtest = ctypes.CDLL("libXtst.so.6")
        self.x11.XOpenDisplay.restype = ctypes.c_void_p
        self.x11.XStringToKeysym.restype = ctypes.c_ulong
        self.x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        self.display = ctypes.c_void_p(self.x11.XOpenDisplay(None))
        if not self.display:
            raise RuntimeError("can't open the X display")

    def _flush(self, pause=0.05):
        self.x11.XFlush(self.display)
        time.sleep(pause)

    def keys(self, *names):
        codes = [self.x11.XKeysymToKeycode(self.display, self.x11.XStringToKeysym(n.encode())) for n in names]
        for code in codes:
            self.xtest.XTestFakeKeyEvent(self.display, code, True, 0)
            self._flush()
        for code in reversed(codes):
            self.xtest.XTestFakeKeyEvent(self.display, code, False, 0)
            self._flush()

    def type(self, text):
        for ch in text:
            self.keys(ch)

    def click(self, x, y):
        self.xtest.XTestFakeMotionEvent(self.display, -1, x, y, 0)
        self._flush(0.2)
        self.xtest.XTestFakeButtonEvent(self.display, 1, True, 0)
        self.xtest.XTestFakeButtonEvent(self.display, 1, False, 0)
        self._flush(0.3)


# ---- Ladybird's WebDriver (W3C WebDriver over HTTP) ----

class WebDriver:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        for _ in range(100):
            try:
                self.session = self.call("POST", "/session", {"capabilities": {}})["sessionId"]
                return
            except OSError:
                time.sleep(0.5)
        raise RuntimeError("WebDriver did not answer")

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method,
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read() or b"{}").get("value", {})

    def go(self, url):
        self.call("POST", f"/session/{self.session}/url", {"url": url})

    def url(self):
        return self.call("GET", f"/session/{self.session}/url")

    def js(self, script, *args):
        return self.call("POST", f"/session/{self.session}/execute/sync", {"script": script, "args": list(args)})

    def quit(self):
        try:
            self.call("DELETE", f"/session/{self.session}")
        except Exception:
            pass


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
    parser.add_argument("--screenshots", type=Path)
    args = parser.parse_args()

    xvfb = start_display()
    page = serve_page()
    # A short path: Ladybird's sockets live in XDG_RUNTIME_DIR, and socket paths are short.
    home = Path(tempfile.mkdtemp(prefix="samb-"))
    runtime = home / "run"
    runtime.mkdir(mode=0o700)
    env = dict(os.environ, HOME=str(home), XDG_RUNTIME_DIR=str(runtime), LANG="C.UTF-8")
    for key in ("XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME"):
        env.pop(key, None)
    old_firefox_profile(home)
    profile = home / ".config/Ladybird/Profiles" / PROFILE

    try:
        print("First start")
        version = subprocess.run([LAUNCHER, "--version"], env=dict(env, QT_QPA_PLATFORM="offscreen"),
                                 capture_output=True, text=True, timeout=60)
        check("the launcher starts Ladybird", version.stdout.startswith("Version "), version.stdout + version.stderr[-300:])
        check("bookmarks are brought over quietly", "bookmarks" in version.stderr and "bookmarks" not in version.stdout)
        check_presets(profile / "Settings.json", "written")
        check_bookmarks(profile / "Bookmarks.json")

        print("Browsing")
        # WebDriver starts Ladybird in the same profile, with the launcher's fonts, and the stand-in for Bitwarden.
        fake_bw = home / "bw"
        origin = page.rsplit("/", 1)[0]
        fake_bw.write_text(FAKE_BW % {"master": MASTER_PASSWORD, "login": LOGIN, "origin": origin})
        fake_bw.chmod(0o755)
        bw_log = home / "bw.log"
        driver_env = dict(env, FONTCONFIG_FILE=f"{SHARE}/fonts.conf", SAMB_BITWARDEN_CLI=str(fake_bw),
                          SAMB_FAKE_BW_LOG=str(bw_log))
        port = 4444 + os.getpid() % 1000
        log = open(home / "webdriver.log", "w")
        webdriver = subprocess.Popen([str(Path(LADYBIRD).with_name("WebDriver")), "--port", str(port),
                                      "--profile", PROFILE, "--force-cpu-painting"],
                                     env=driver_env, stdout=log, stderr=log)
        driver = None
        try:
            driver = WebDriver(port)
            browse(driver, page, args.screenshots)
            bitwarden(driver, page, bw_log, home, args.screenshots)
        except Exception as e:
            check("WebDriver drives the browser", False, repr(e))
            print((home / "webdriver.log").read_text()[-3000:])
        finally:
            if driver:
                driver.quit()
            webdriver.terminate()
            try:
                webdriver.wait(20)
            except subprocess.TimeoutExpired:
                webdriver.kill()

        print("After browsing")
        check_presets(profile / "Settings.json", "kept by Ladybird")
    finally:
        if xvfb:
            xvfb.terminate()
        shutil.rmtree(home, ignore_errors=True)

    if failures:
        print(f"\n{len(failures)} check(s) failed: " + "; ".join(failures))
        sys.exit(1)
    print("\nAll checks passed.")


def check_presets(path, how):
    # Ladybird rewrites the file in its own words when it saves settings, so after browsing this also
    # shows that it understood every preset (one it didn't would come back as its default).
    try:
        settings = json.loads(path.read_text())
    except (OSError, ValueError) as e:
        check(f"presets {how}", False, repr(e))
        return
    tabs = settings.get("tabs", {})
    check(f"tabs down the left ({how})", tabs.get("verticalTabsEnabled") is True and tabs.get("verticalTabsPosition") == "left", tabs)
    check(f"Ecosia searches ({how})", settings.get("searchEngine", {}).get("engine") == "Ecosia", settings.get("searchEngine"))
    check(f"the Lavender colour ({how})", settings.get("appearance", {}).get("colorTheme") == "lavender", settings.get("appearance"))
    check(f"the start page for new tabs ({how})", settings.get("newTabPageURL") == START, settings.get("newTabPageURL"))
    check(f"ads and trackers blocked ({how})", settings.get("contentBlockers", {}).get("enabled") is True)
    check(f"no welcome page ({how})", settings.get("firstRunComplete") is True)


def check_bookmarks(path):
    try:
        items = json.loads(path.read_text())["items"]
    except (OSError, ValueError, KeyError) as e:
        check("bookmarks brought over from Firefox", False, repr(e))
        return
    top = [i.get("title") for i in items]
    menu = next((i for i in items if i.get("type") == "folder" and i.get("title") == "Bookmarks Menu"), {})
    check("bookmarks brought over from Firefox",
          "Music" in top and [c.get("title") for c in menu.get("children", [])] == ["Weather"], items)


def browse(driver, page, shots):
    driver.go(START)
    start = driver.js("""
        const width = font => { const c = document.createElement("canvas").getContext("2d");
                                c.font = "40px " + font; return c.measureText("Wonderful aurora 0123").width; };
        return { time: document.getElementById("time").textContent.trim(),
                 placeholder: document.getElementById("query").placeholder,
                 tiles: document.querySelectorAll("#sites .site-link").length,
                 // Lexend measures differently from monospace; "serif" and "sans-serif" are Lexend.
                 lexend: width("Lexend") !== width("monospace"),
                 serif: width("serif") === width("Lexend"),
                 sans: width("sans-serif") === width("Lexend"),
                 editable: document.documentElement.hasAttribute("data-editable") };""")
    check("the start page shows the clock", bool(start.get("time")), start)
    check("the start page searches with Ecosia", "Ecosia" in start.get("placeholder", ""), start)
    check("the start page shows shortcuts", start.get("tiles", 0) >= 5, start)
    check("shortcuts can be added and removed", start.get("editable") is True, start)
    check("Lexend is there", start.get("lexend") is True, start)
    check("pages that name no font get Lexend", start.get("serif") is True and start.get("sans") is True, start)

    driver.js("""const q = document.getElementById("query"); q.value = "northern lights";
                 document.getElementById("search").requestSubmit();""")
    for _ in range(40):
        url = driver.url()
        if not url.startswith("file:"):
            break
        time.sleep(0.25)
    check("its search box searches Ecosia", url.startswith("https://www.ecosia.org/search?q=northern"), url)

    driver.go(page)
    title = driver.js("return document.title")
    check("a page opens", title == "Recipes - Weeknight pasta", title)

    if shots:
        shots.mkdir(parents=True, exist_ok=True)
        driver.go(START)
        time.sleep(2)
        subprocess.run(["import", "-window", "root", str(shots / "start.png")], check=False)
        driver.go(page)
        time.sleep(2)
        subprocess.run(["import", "-window", "root", str(shots / "page.png")], check=False)
        print(f"Screenshots in {shots}")


def bitwarden(driver, page, bw_log, home, shots):
    login_page = page.rsplit("/", 1)[0] + "/login.html"
    driver.go(login_page)
    time.sleep(1)
    x = XInput()
    # Into the page first, so the window has the keyboard (there's no window manager here to give it).
    x.click(700, 450)
    x.keys("Control_L", "Shift_L", "l")
    time.sleep(4)  # The popover asks for the master password, as the vault starts locked.
    x.type(MASTER_PASSWORD)
    x.keys("Return")
    filled = {}
    for _ in range(60):
        filled = driver.js("""return { user: document.getElementById("user").value,
                                       pass: document.getElementById("pass").value };""")
        if filled.get("pass"):
            break
        time.sleep(0.5)
    check("Bitwarden fills in the login (Ctrl+Shift+L, after unlocking)",
          filled == {"user": LOGIN["username"], "pass": LOGIN["password"]}, filled)

    calls = [json.loads(line) for line in bw_log.read_text().splitlines()] if bw_log.exists() else []
    check("Bitwarden is asked for this page's logins",
          any(c["args"][:2] == ["list", "items"] and c["args"][-1] == login_page for c in calls), calls)
    check("Bitwarden's client never waits for input", bool(calls) and all(c["nointeraction"] for c in calls), calls)
    check("Bitwarden's client keeps the browser's sign-in apart",
          bool(calls) and all(c["appdata"] == str(home / ".local/share/superawesomemagicalbrowser/bitwarden") for c in calls), calls)

    # Another origin has no logins; the shortcut says so rather than filling anything.
    other = login_page.replace("127.0.0.1", "localhost")
    driver.go(other)
    time.sleep(1)
    x.click(700, 450)
    x.keys("Control_L", "Shift_L", "l")
    time.sleep(4)
    untouched = driver.js("""return document.getElementById("pass").value""")
    check("Bitwarden fills nothing in on a site it has no login for", untouched == "", untouched)
    if shots:
        subprocess.run(["import", "-window", "root", str(shots / "bitwarden.png")], check=False)
    x.keys("Escape")


if __name__ == "__main__":
    main()
