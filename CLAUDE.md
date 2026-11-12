# Notes for Claude

SuperAwesomeMagical Browser is Mozilla's Firefox release with our files added beside Firefox's
(`desktop/layer`), never changing any of Firefox's own, for Linux; and an Android app on
GeckoView (`android/`) in the same look. The README explains the layout; this is
what to know when changing it.

## Design

Match the family (SuperAwesomeMagicalLinux, Music, Weather, Fitness): Lexend for all text,
Material 3, the slate neutrals (`#0B1220` dark / `#F5F7FB` light), soft rounded corners, the
aurora's purple (primary `#CFBDFF` dark / `#5B3FD1` light) and green (secondary `#6EF2B0` /
`#0B7A4F`), tertiary blue `#8DB4FF` / `#2563EB`, and a faint glow of them at the top. The values
come from SuperAwesomeMagicalLinux's `tools/schemes.py`; `styles/theme.css` repeats them as
`--sam-*`. The start page echoes the Linux sign-in screen. The owner likes their tabs on the left
and Lexend; Ecosia is the default search engine (for now, the owner's choice).

## How the layer works (Firefox 160)

- `defaults/pref/superawesomemagical.js` only turns on AutoConfig. Default prefs go in
  `superawesomemagical/modules/Prefs.sys.mjs`: Firefox reads its own `firefox.js` defaults after
  `defaults/pref`, so a pref Firefox also sets can't be defaulted from that folder.
- `Startup.sys.mjs` adds `theme.css` (tokens + Lexend) to every chrome window and `browser.css` to
  browser windows as author sheets: unlayered rules beat Firefox's layered design tokens. Firefox's
  own pages get them through the `SAMAboutTheme` actor. Check token names against Firefox's
  `*.tokens.css` and `tokens-*.css` when Firefox changes its look (unpack `browser/omni.ja` and
  `omni.ja`).
- The start page is `chrome://superawesomemagical/content/` (chrome.manifest), so it runs in the
  parent process with privileges, like Settings. Firefox 160 refuses JSWindowActors registered at
  runtime in web content processes, which is why it isn't an unprivileged page with an actor.
  Put text from outside into it only as text/attributes, never markup.
- New profiles go through Firefox's old toolbar migrations, which move the sidebar button to the
  end; `arrangeToolbar` fixes the order once per profile.
- Window titles say "SuperAwesomeMagical Browser" (`nameWindow`); About Firefox still says Firefox,
  and so does the README: it is Firefox, not a Mozilla product.
- `distribution/policies.json` (Ecosia, no updater, no telemetry) is why Settings says "managed by
  your organization"; that's expected.

## The Android app (android/)

Kotlin and Compose on GeckoView, one Gradle project. `Browser.kt` holds the tabs and all of
GeckoView's delegates (progress, navigation, content, history, prompts); `ui/` is the interface
(tabs in a left drawer on phones, a permanent left panel at 840dp and wider; the start page;
dialogs). The version comes from `desktop/release.env` (versionName `FIREFOX-RELEASE`,
versionCode from it, always rising), and so does the GeckoView version, which must be the same
Firefox. GeckoView's API changes between versions: check signatures against its sources jar
(`geckoview-<version>-sources.jar` on maven.mozilla.org) when bumping. GeckoView won't run
runtime-registered JSWindowActors in web content processes either, so page features go through
GeckoView's delegates.

The APK must always be signed with the same key (alias `browser`), or installed copies can't
update. It's not in git: CI reads `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD`, local
builds `android/keystore/release.properties` (git-ignored). Never generate a replacement.

## Checks before pushing

```bash
desktop/build.sh && python3 desktop/check.py
```

`check.py` starts the real launcher with a new profile and checks every feature over Marionette
(it starts Xvfb itself when there's no display). Add a check when adding a feature. Look at the
screenshots (`--screenshots DIR`) after visual changes, light and dark. CI runs it on the tarball
and on the installed .deb (Ubuntu 24.04, Debian 13) and .rpm (Fedora).

For the Android app: `cd android && ./gradlew testDebugUnitTest lintDebug assembleRelease`.

## Releases

`desktop/release.env` holds `FIREFOX_VERSION` and our `RELEASE` (package version
`FIREFOX_VERSION-RELEASE`). The Linux workflow follows Firefox on its own (every six hours: new
Firefox → build, check, release, commit the bump). For a release of our own changes, raise
`RELEASE`, write `release-notes/vX-R.md`, push, then run the **Linux** workflow with *release*
ticked (sessions can't push tags; the workflow makes the tag). It then starts the **Android**
workflow, which adds the signed APKs to the same release. Check every job is green, and send the
owner the APK in chat as well.

## Building in a Claude cloud sandbox

- Mozilla's archive, raw.githubusercontent.com and product-details are reachable; GitHub release
  downloads (github.com/.../releases) are not, so local tools come from apt
  (`apt-get update && apt-get install -y rpm` gives `rpmbuild`).
- Firefox here can't reach the web (the proxy's CA isn't in NSS); serve test pages locally.
- Marionette needs `MOZ_REMOTE_ALLOW_SYSTEM_ACCESS=1` for chrome context. Its screenshots don't
  include popups (menus, panels): capture the X display (`import -window root`) for those, and
  remember Xvfb has no compositor, so popups lose their rounded corners there.
- Link hand-off to a running browser goes over D-Bus; try it under `dbus-run-session`.
- Android: command-line tools in `/opt/android-sdk/cmdline-tools/latest`, then
  `sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"` and
  `sdk.dir=/opt/android-sdk` in `android/local.properties` (git-ignored). Serve Maven Central from
  Google's mirror with the init script in SuperAwesomeMagicalMusic's CLAUDE.md;
  maven.mozilla.org (GeckoView, ~250 MB) is reachable directly.
