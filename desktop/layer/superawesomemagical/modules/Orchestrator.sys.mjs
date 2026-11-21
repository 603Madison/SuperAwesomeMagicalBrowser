/* SuperAwesomeMagicalOrchestrator in the browser: the person's other devices.
 *
 * - "Send page to…", "Send link to…" (the page's menu) and "Send tab to…" (a tab's menu): a
 *   submenu of their other devices; choosing one opens the address there.
 * - "Carry on" on the start page: what their other devices are doing, and the family's apps.
 *
 * The browser has no account, so it asks the orchestrator's agent on this computer, through its
 * command (sam-orchestrator, from SuperAwesomeMagicalOrchestrator's package). When that isn't
 * installed, or nobody is signed in, none of this shows.
 *
 * Nothing is ever sent without that choice in a menu. In particular the browser does not publish
 * what is being browsed as an "activity": browsing isn't told to other devices by itself.
 *
 * The command is always run directly with its arguments as a list (never through a shell), and
 * everything it prints is treated as text from outside: device names go into label attributes,
 * ids must look like ids, and only http(s) addresses are sent or opened.
 */

const lazy = {};
ChromeUtils.defineESModuleGetters(lazy, {
  Subprocess: "resource://gre/modules/Subprocess.sys.mjs",
  setTimeout: "resource://gre/modules/Timer.sys.mjs",
  clearTimeout: "resource://gre/modules/Timer.sys.mjs",
});

const COMMAND = "/usr/bin/sam-orchestrator";
// For the checks (desktop/check.py puts a stand-in here). An environment variable of the browser's
// own process: nothing a web page, a preference or an add-on's page can set.
const COMMAND_ENV = "SAM_ORCHESTRATOR_COMMAND";

export const SEND_PREF = "superawesomemagical.orchestrator.send";
export const CARRY_ON_PREF = "superawesomemagical.orchestrator.carryOn";

const FTL = "superawesomemagical/orchestrator.ftl";
const MAX_OUTPUT = 512 * 1024;
const MAX_DEVICES = 24;
const DEVICES_FRESH_MS = 5_000;
const CARRY_ON_FRESH_MS = 20_000;
// Ids are the server's (base64url); anything else isn't passed on.
const ID = /^[A-Za-z0-9_-]{1,128}$/;

const text = (value, max) => (typeof value === "string" ? value.trim().slice(0, max) : "");

// The apps' names without the family's: "Music", "Health".
const shortName = name => name.replace(/^SuperAwesomeMagical\s*/i, "") || name;

/** The address if it is one that may be sent to a device or opened from one: http(s) only. */
export function webAddress(value) {
  if (typeof value !== "string" || value.length > 4096 || /[\s\u0000-\u001f\u007f]/.test(value)) {
    return "";
  }
  let url;
  try {
    url = new URL(value);
  } catch {
    return "";
  }
  if ((url.protocol !== "http:" && url.protocol !== "https:") || !url.hostname || url.username || url.password) {
    return "";
  }
  return url.href;
}

export const Orchestrator = {
  _devices: null, // { at, list }: the last answer, so a menu can show at once
  _devicesPending: null,
  _carryOn: null,
  _carryOnPending: null,
  _l10n: false,

  get sendEnabled() {
    return Services.prefs.getBoolPref(SEND_PREF, true);
  },
  get carryOnEnabled() {
    return Services.prefs.getBoolPref(CARRY_ON_PREF, true);
  },

  get command() {
    const override = Services.env.get(COMMAND_ENV);
    return override.startsWith("/") ? override : COMMAND;
  },

  /** Runs the agent's command; { ok, out }. Never throws; gives up (and stops it) after `timeout`. */
  async run(args, timeout = 5000) {
    const command = this.command;
    let proc = null;
    let timer = null;
    try {
      if (!(await IOUtils.exists(command))) {
        return { ok: false, out: "", missing: true };
      }
      proc = await lazy.Subprocess.call({ command, arguments: args, stderr: "ignore" });
      const work = (async () => {
        proc.stdin.close().catch(() => {});
        let out = "";
        for (let chunk; (chunk = await proc.stdout.readString()); ) {
          out += chunk;
          if (out.length > MAX_OUTPUT) {
            throw new Error("too much output");
          }
        }
        const { exitCode } = await proc.wait();
        return { ok: exitCode === 0, out };
      })();
      const late = new Promise(resolve => {
        timer = lazy.setTimeout(() => resolve(null), timeout);
      });
      const done = await Promise.race([work, late]);
      if (!done) {
        work.catch(() => {});
        proc.kill(0);
        return { ok: false, out: "" };
      }
      return done;
    } catch (e) {
      proc?.kill(0);
      return { ok: false, out: "" };
    } finally {
      lazy.clearTimeout(timer);
    }
  },

  async json(args, timeout) {
    const { ok, out } = await this.run([...args, "--json"], timeout);
    if (!ok) {
      return null;
    }
    try {
      return JSON.parse(out);
    } catch {
      return null;
    }
  },

  /**
   * The person's other devices, online ones first: [{ id, name, online }]. Empty when the agent
   * isn't installed, nobody is signed in, or this is their only device.
   */
  devices() {
    if (this._devices && Date.now() - this._devices.at < DEVICES_FRESH_MS) {
      return Promise.resolve(this._devices.list);
    }
    this._devicesPending ??= this._readDevices()
      .catch(() => [])
      .then(list => {
        this._devices = { at: Date.now(), list };
        this._devicesPending = null;
        return list;
      });
    return this._devicesPending;
  },

  async _readDevices() {
    const status = await this.json(["status"]);
    if (!status?.signedIn) {
      return [];
    }
    const here = text(status.device?.id, 200);
    const devices = await this.json(["devices"]);
    if (!Array.isArray(devices)) {
      return [];
    }
    return devices
      .filter(d => d && typeof d === "object" && !d.current && d.id !== here)
      .map(d => ({ id: d.id, name: text(d.name, 80), online: d.online === true }))
      .filter(d => typeof d.id === "string" && ID.test(d.id) && d.name)
      .sort((a, b) => b.online - a.online || a.name.localeCompare(b.name))
      .slice(0, MAX_DEVICES);
  },

  /** Opens `url` on a device. The id and the address are arguments of their own, after "--". */
  async send(device, url) {
    const address = webAddress(url);
    if (!address || !ID.test(device?.id ?? "")) {
      return false;
    }
    const { ok } = await this.run(["send-link", "--", device.id, address], 10_000);
    if (!ok) {
      this._devices = null;
    }
    return ok;
  },

  /**
   * For the start page: { activities: [{ title, app, device, url }], apps: [{ name, url }] }, with
   * only what can be opened here (http and https). Both empty when there's nothing, or no agent.
   */
  carryOn() {
    if (this._carryOn && Date.now() - this._carryOn.at < CARRY_ON_FRESH_MS) {
      return Promise.resolve(this._carryOn.value);
    }
    this._carryOnPending ??= this._readCarryOn()
      .catch(() => ({ activities: [], apps: [] }))
      .then(value => {
        this._carryOn = { at: Date.now(), value };
        this._carryOnPending = null;
        return value;
      });
    return this._carryOnPending;
  },

  async _readCarryOn() {
    const [activities, listed] = await Promise.all([this.json(["activities"]), this.json(["apps"])]);
    const apps = (Array.isArray(listed) ? listed : [])
      .map(a => ({ id: text(a?.id, 40), name: shortName(text(a?.name, 60)), url: webAddress(a?.url) }))
      .filter(a => a.name && a.url)
      .slice(0, 8);
    const appName = id => apps.find(a => a.id === id)?.name || id.charAt(0).toUpperCase() + id.slice(1);
    return {
      activities: (Array.isArray(activities) ? activities : [])
        .map(a => ({
          title: text(a?.title, 120),
          app: appName(text(a?.app, 40)),
          device: text(a?.deviceName, 80),
          url: webAddress(a?.url),
          at: Number(a?.at) || 0,
        }))
        .filter(a => a.title && a.url)
        .sort((a, b) => b.at - a.at)
        .slice(0, 4)
        .map(({ title, app, device, url }) => ({ title, app, device, url })),
      apps: apps.map(({ name, url }) => ({ name, url })),
    };
  },

  // ---- The menus (per browser window) ----

  attach(win) {
    const doc = win.document;
    const pageMenu = doc.getElementById("contentAreaContextMenu");
    const tabMenu = doc.getElementById("tabContextMenu");
    if (!pageMenu || !tabMenu || doc.getElementById("sam-send-page")) {
      return;
    }
    const page = this._menu(win, "sam-send-page", "Send page to…", () => {
      const menu = win.gContextMenu;
      const plain =
        menu &&
        !menu.onLink &&
        !menu.onPlainTextLink &&
        !menu.onTextInput &&
        !menu.isContentSelected &&
        !menu.onImage &&
        !menu.onVideo &&
        !menu.onAudio &&
        !menu.onCanvas;
      return plain ? menu.browser?.currentURI?.spec : "";
    });
    const link = this._menu(win, "sam-send-link", "Send link to…", () => {
      const menu = win.gContextMenu;
      return menu?.onLink || menu?.onPlainTextLink ? menu.linkURL : "";
    });
    const tab = this._menu(win, "sam-send-tab", "Send tab to…", () => {
      return win.TabContextMenu?.contextTab?.linkedBrowser?.currentURI?.spec;
    });

    // Beside Firefox's own "send to device" items (which need a Mozilla account, and stay hidden
    // without one).
    const place = (item, id, parent) => {
      const beside = doc.getElementById(id);
      if (beside?.parentNode === parent) {
        beside.after(item);
      } else {
        parent.append(item);
      }
    };
    place(page, "context-sendpagetodevice", pageMenu);
    place(link, "context-sendlinktodevice", pageMenu);
    place(tab, "context_sendTabToDevice", tabMenu);

    const showing = (popup, items) =>
      popup.addEventListener("popupshowing", event => {
        if (event.target === popup) {
          this._offer(popup, items);
        }
      });
    showing(pageMenu, [page, link]);
    showing(tabMenu, [tab]);
  },

  _menu(win, id, label, address) {
    const doc = win.document;
    const menu = doc.createXULElement("menu");
    menu.id = id;
    menu.setAttribute("label", label);
    menu.hidden = true;
    menu.samAddress = address;
    const popup = doc.createXULElement("menupopup");
    popup.id = id + "-popup";
    popup.addEventListener("command", event => {
      const device = event.target.samDevice;
      if (device && menu.samUrl) {
        this._chosen(win, device, menu.samUrl, id === "sam-send-tab" ? win.TabContextMenu?.contextTab : null);
      }
    });
    menu.append(popup);
    return menu;
  },

  // A menu is opening. The items show at once when the devices are already known (and are
  // brought up to date while it's open); the first time, they appear when the agent has answered.
  // Either way the agent is only asked when a menu opens, never while the browser starts.
  _offer(popup, items) {
    for (const item of items) {
      let url = "";
      try {
        url = this.sendEnabled ? webAddress(item.samAddress() || "") : "";
      } catch {}
      item.samUrl = url;
      this._fill(item, url ? (this._devices?.list ?? []) : []);
    }
    if (!items.some(item => item.samUrl)) {
      return;
    }
    this.devices().then(list => {
      if (popup.state === "showing" || popup.state === "open") {
        for (const item of items) {
          if (item.samUrl && !item.hasAttribute("open")) {
            this._fill(item, list);
          }
        }
      }
    });
  },

  _fill(menu, devices) {
    const doc = menu.ownerDocument;
    menu.menupopup.replaceChildren(
      ...devices.map(device => {
        const item = doc.createXULElement("menuitem");
        // A label attribute: the name shows as written, whatever is in it.
        item.setAttribute("label", device.online ? device.name : `${device.name} (away)`);
        item.samDevice = device;
        return item;
      })
    );
    menu.hidden = !menu.samUrl || !devices.length;
  },

  async _chosen(win, device, url, tab) {
    const sent = await this.send(device, url);
    if (win.closed) {
      return;
    }
    try {
      if (!this._l10n) {
        // Our words for Firefox's confirmation hint (localization/, beside this folder).
        const source = new L10nFileSource(
          "superawesomemagical",
          "app",
          ["en-US"],
          "resource://superawesomemagical/localization/{locale}/"
        );
        L10nRegistry.getInstance().registerSources([source]);
        this._l10n = true;
      }
      win.MozXULElement.insertFTLIfNeeded(FTL);
      // Beside the tab it was about, as Firefox's own "sent" hint is; the menu button if the tabs
      // aren't showing.
      const anchor = [tab, win.gBrowser.selectedTab, win.document.getElementById("PanelUI-menu-button")].find(
        el => el?.isConnected && el.checkVisibility()
      );
      const l10nArgs = { device: device.name };
      if (!sent) {
        win.ConfirmationHint.show(anchor, "sam-orchestrator-not-sent", {
          l10nArgs,
          hideCheckmark: true,
          descriptionId: "sam-orchestrator-not-sent-description",
          showDescription: true,
        });
      } else if (device.online) {
        win.ConfirmationHint.show(anchor, "sam-orchestrator-sent", { l10nArgs });
      } else {
        win.ConfirmationHint.show(anchor, "sam-orchestrator-sent-away", {
          l10nArgs,
          descriptionId: "sam-orchestrator-sent-away-description",
          showDescription: true,
        });
      }
    } catch (e) {
      console.error("SuperAwesomeMagical: could not say how sending went", e);
    }
  },
};
