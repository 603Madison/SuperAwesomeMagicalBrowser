# Notes for Claude

SuperAwesomeMagical Browser for Linux is Ladybird, built by our CI from Ladybird's release with our
patches (`desktop/ladybird`: Bitwarden in the toolbar), started by our launcher with our presets,
start page and Lexend (`desktop/edition`); and an Android app on GeckoView (`android/`) in the
same look, since Ladybird has no Android version. The README explains the layout; this is what to
know when changing it.

## Design

Match the family (SuperAwesomeMagicalLinux, Music, Weather, Fitness): Lexend for all text,
Material 3, the slate neutrals (`#0B1220` dark / `#F5F7FB` light), soft rounded corners, the
aurora's purple (primary `#CFBDFF` dark / `#5B3FD1` light) and green (secondary `#6EF2B0` /
`#0B7A4F`), tertiary blue `#8DB4FF` / `#2563EB`, and a faint glow of them at the top. The values
come from SuperAwesomeMagicalLinux's `tools/schemes.py`; the start page's `start.css` repeats
them. The start page echoes the Linux sign-in screen. The owner likes her tabs on the left and
Lexend; Ecosia is the default search engine (for now, the owner's choice), and she chose Ladybird
over Firefox for Linux in November 2026.

Ladybird's interface uses its Lavender colour theme, the nearest to the aurora's purple. Now that we
build Ladybird, an exact slate and violet would be one more patch; every patch is more to carry
from one Ladybird release to the next, so keep them few and small.

The owner needs a password manager to use a browser at all: Bitwarden must keep working.

## How the edition works (Ladybird 2026.48)

- The launcher (`desktop/linux/superawesomemagicalbrowser`) runs
  `/usr/lib/superawesomemagicalbrowser/ladybird/bin/Ladybird --profile superawesomemagical`. When that profile has no
  `Settings.json` yet, it writes `edition/settings.json` there (with `@SHARE@` filled in), and
  first copies the Firefox-based versions' bookmarks (`~/.local/share/superawesomemagicalbrowser/profile`)
  into `Bookmarks.json` with `firefox-bookmarks.py`. After that the settings are the person's:
  never rewrite them, and add new presets for new profiles only.
- Settings keys are Ladybird's (`Libraries/LibWebView/Settings.cpp` in its source; take the tag of
  the version you check against). Ladybird rewrites the file in its own words, dropping what it
  doesn't know, which is how `check.py` proves every preset is understood.
- Lexend comes from `edition/fonts.conf` (`FONTCONFIG_FILE`): `<alias>` prefer rules before the
  system's `fonts.conf`, so the generic families are Lexend and named fonts stay themselves.
  `serif` is aliased too, since Ladybird's own stylesheet starts pages in `serif`. Test with
  `fc-match 'sans\-serif'`: unescaped, `fc-match sans-serif` asks for a family called "sans".
- The start page is an ordinary `file://` page (`newTabPageURL`), with no privileges: its
  shortcuts live in its `localStorage`. Put text from outside into it only as text, never markup.
- Ladybird names its windows "Ladybird" and its app ID is `org.ladybird.Ladybird`, which is why the
  desktop file's `StartupWMClass` says so.
- SuperAwesomeMagicalLinux installs the latest release's rpm; a release that would break that
  image goes out as a pre-release first.

## Our Ladybird (desktop/ladybird)

- `build-ladybird.sh` builds `LADYBIRD_VERSION` with `patches/*.patch` in a fresh Fedora 44 or
  Ubuntu 24.04 container, as Ladybird's own release build does (Distribution preset, without LTO,
  symbol files or the update check), and installs it under `/usr/lib/superawesomemagicalbrowser/ladybird`.
  Ubuntu 24.04's Qt is too old, so that build has Qt built in (`LADYBIRD_BUILD_QT`); its .deb works
  on 26.04 too. It also works out the packages' dependencies (`dpkg-shlibdeps`, `rpm -qf`).
- `.github/workflows/ladybird.yml` runs it with vcpkg's and ccache's caches; a first build takes
  hours, later ones much less, and the same Ladybird and patches are never built twice. The
  Linux workflow follows Ladybird's releases (`download.ladybird.org/releases.json`) every six
  hours. When a patch no longer applies to a new release, that run fails: fix the patch.
- Make a patch in a checkout of Ladybird at the tag (`git clone --depth 1 --branch <tag>`): edit,
  `git add -N` new files, `git diff > patches/NNNN-name.patch`. Keep our code in files of our own
  (`UI/Qt/Bitwarden.*`) and the hooks into Ladybird's files few, marked "SuperAwesomeMagical Browser".
- Bitwarden (`UI/Qt/Bitwarden.cpp`): the button and Ctrl+Shift+L (in `WindowToolbar.cpp`; the
  shortcut is browser-reserved in `WebContentView.cpp` so pages can't take it) open a popover that
  signs in, unlocks and lists logins through Bitwarden's command-line client. That client is
  `/usr/lib/superawesomemagicalbrowser/bitwarden/bw`, the "oss" build (`BITWARDEN_CLI_VERSION`),
  or `SAMB_BITWARDEN_CLI`; its data is in `~/.local/share/superawesomemagicalbrowser/bitwarden`.
  The session key lives only in memory and goes to `bw` in `BW_SESSION`; master passwords go in
  `SAMB_BW_PASSWORD` (`--passwordenv`), never on a command line. The fill script checks
  `location.origin` against the page the login was looked up for, and takes its values as JSON.
- Security: Ladybird publishes advisories at github.com/LadybirdBrowser/ladybird/security. Every
  one up to Nov 2026 is fixed in 2026.48.0 (GHSA-3v9p-556w-v6vp only from 2026.48.0). Following
  releases quickly is what keeps the browser patched; backport a fix as a patch only if a release
  is slow to come.

## The Android app (android/)

Kotlin and Compose on GeckoView, one Gradle project. `Browser.kt` holds the tabs and all of
GeckoView's delegates (progress, navigation, content, history, prompts); `ui/` is the interface
(tabs in a left drawer on phones, a permanent left panel at 840dp and wider; the start page;
dialogs). The version comes from `android/release.env` (versionName `FIREFOX_VERSION-RELEASE`,
versionCode from it, always rising), and so does the GeckoView version, which must be the same
Firefox. GeckoView's API changes between versions: check signatures against its sources jar
(`geckoview-<version>-sources.jar` on maven.mozilla.org) when bumping. GeckoView won't run
runtime-registered JSWindowActors in web content processes, so page features go through
GeckoView's delegates.

The APK must always be signed with the same key (alias `browser`), or installed copies can't
update. It's not in git: CI reads `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD`, local
builds `android/keystore/release.properties` (git-ignored). Never generate a replacement.

## Checks before pushing

```bash
desktop/build.sh all && python3 desktop/check.py
```

`check.py` checks the installed package (install the .deb or .rpm first): it runs the real
launcher in a throwaway home with an old Firefox-based profile there, then drives Ladybird over
its own WebDriver (W3C, `/usr/lib/superawesomemagicalbrowser/ladybird/bin/WebDriver`) in the same
profile, and starts Xvfb itself when there's no display. Bitwarden is checked with a stand-in
`bw` (`SAMB_BITWARDEN_CLI`), with Ctrl+Shift+L and the master password typed through XTEST
(it needs libXtst). Add a check when adding a feature. Look at the screenshots (`--screenshots
DIR`) after visual changes, light and dark. CI runs it with the packages installed on Ubuntu 24.04
and 26.04 and Fedora 44 and 45.

For the Android app: `cd android && ./gradlew testDebugUnitTest lintDebug assembleRelease`.

## Releases

Linux: `desktop/release.env` holds our `VERSION` (the date), `RELEASE`, `LADYBIRD_VERSION` and
`BITWARDEN_CLI_VERSION`. New Ladybird releases are followed and released by CI on their own. For a
release of our own changes, raise `VERSION`/`RELEASE`, write `release-notes/vVERSION-RELEASE.md`,
push, then run the **Linux** workflow with *release* ticked
(sessions can't push tags; the workflow makes the tag). Tick *prerelease* too to publish without
making it the latest release; running it again with *release* alone makes it the latest.

Android: releases are `android-v<FIREFOX_VERSION>-<RELEASE>`, never "latest". The **Android**
workflow follows GeckoView on its own (every six hours, once the signing secrets exist); for our
own changes raise `RELEASE` in `android/release.env` and run it with *release* ticked. Check every
job is green, and send the owner the APK in chat as well.

## Building in a Claude cloud sandbox

- Ladybird can't be built here: vcpkg fetches its dependencies' sources from GitHub archives,
  which the sandbox can't reach (and the Ubuntu build adds LLVM's and Kitware's apt repositories,
  which the sandbox refuses). Build in CI and download the `packages` artifact, or for trying
  things in Ladybird itself, its official `.deb` (URL and SHA-256 in
  `download.ladybird.org/releases.json`; check the signature with `apt.ladybird.org/ladybird.asc`),
  `dpkg -i` with its dependencies from Ubuntu's archive.
- Bitwarden's client comes from GitHub's release downloads, which the sandbox can't reach either.
- `apt-get install -y rpm` gives `rpmbuild`. GitHub release downloads aren't reachable.
- Ladybird's IPC sockets live in `XDG_RUNTIME_DIR`, and a socket path must be under 108 bytes:
  keep that directory short, and use `--profile NAME` rather than a long `--profile-path`.
- `Ladybird --version` still needs a display: use `QT_QPA_PLATFORM=offscreen`.
- With no GPU, Ladybird shows a "Pages are painted on the CPU" bar; the WebDriver's
  `--force-cpu-painting` makes that explicit. For README screenshots, start the launcher on Xvfb
  with `QT_SCALE_FACTOR=2`, set `window.size` in the profile's settings to fill the screen, and
  dismiss the bar with a click on its × (X's XTEST through `ctypes` works; there's no xdotool).
- Android: command-line tools in `/opt/android-sdk/cmdline-tools/latest`, then
  `sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"` and
  `sdk.dir=/opt/android-sdk` in `android/local.properties` (git-ignored). Serve Maven Central from
  Google's mirror with the init script in SuperAwesomeMagicalMusic's CLAUDE.md;
  maven.mozilla.org (GeckoView, ~250 MB) is reachable directly.
