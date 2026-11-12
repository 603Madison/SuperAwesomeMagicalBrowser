#!/usr/bin/env bash
# Builds SuperAwesomeMagical Browser for Linux (x86_64) from Mozilla's Firefox release.
#
#   desktop/build.sh            the browser folder and its .tar.xz (in desktop/out)
#   desktop/build.sh deb        ... and the .deb (Ubuntu, Debian, Mint, Pop!_OS); needs dpkg-deb
#   desktop/build.sh rpm        ... and the .rpm (Fedora and others with dnf); needs rpmbuild
#   desktop/build.sh all        all three
#
# Firefox itself is not modified: the build unpacks Mozilla's tarball (after checking it against
# Mozilla's signed checksums) and adds our files beside Firefox's own (desktop/layer), plus Lexend.
# Versions and checksums are in desktop/release.env. Downloads are cached in desktop/.cache.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=release.env
source "$here/release.env"
# CI's "follow Firefox" run builds a new Firefox before release.env is updated.
FIREFOX_VERSION=${SAM_FIREFOX_VERSION:-$FIREFOX_VERSION}
RELEASE=${SAM_RELEASE:-$RELEASE}

APP=superawesomemagicalbrowser
APP_ID=io.github._603madison.SuperAwesomeMagicalBrowser
VERSION="$FIREFOX_VERSION-$RELEASE"
cache="$here/.cache"
out="$here/out"
root="$out/$APP"

log() { printf '\033[1;35m==>\033[0m %s\n' "$*"; }
fetch() { # url dest (kept in the cache once downloaded)
  if [ ! -s "$2" ]; then
    curl -fsSL --retry 5 -o "$2.part" "$1"
    mv "$2.part" "$2"
  fi
}

get_firefox() {
  local dir="$cache/firefox-$FIREFOX_VERSION" base="https://archive.mozilla.org/pub/firefox/releases/$FIREFOX_VERSION"
  local file="linux-x86_64/en-US/firefox-$FIREFOX_VERSION.tar.xz"
  mkdir -p "$dir"
  fetch "$base/KEY" "$dir/KEY"
  fetch "$base/SHA256SUMS" "$dir/SHA256SUMS"
  fetch "$base/SHA256SUMS.asc" "$dir/SHA256SUMS.asc"
  fetch "$base/$file" "$dir/firefox.tar.xz"

  log "Checking Firefox $FIREFOX_VERSION against Mozilla's signature"
  local gnupg
  gnupg=$(mktemp -d)
  gpg --batch --quiet --homedir "$gnupg" --import "$dir/KEY" 2>/dev/null
  # The signature must be good and made by a key whose primary key is Mozilla's release key.
  gpg --batch --homedir "$gnupg" --status-fd 1 --verify "$dir/SHA256SUMS.asc" "$dir/SHA256SUMS" 2>/dev/null |
    grep -E "^\[GNUPG:\] VALIDSIG .* $MOZILLA_KEY_FPR\$" >/dev/null ||
    { echo "SHA256SUMS is not signed by Mozilla's release key" >&2; exit 1; }
  rm -rf "$gnupg"
  local want
  want=$(awk -v f="$file" '$2 == f { print $1 }' "$dir/SHA256SUMS")
  [ -n "$want" ] || { echo "$file is not in SHA256SUMS" >&2; exit 1; }
  echo "$want  $dir/firefox.tar.xz" | sha256sum -c --quiet -
}

get_lexend() {
  local dir="$cache/lexend-$LEXEND_REV" base="https://raw.githubusercontent.com/google/fonts/$LEXEND_REV/ofl/lexend"
  mkdir -p "$dir"
  fetch "$base/Lexend%5Bwght%5D.ttf" "$dir/Lexend[wght].ttf"
  fetch "$base/OFL.txt" "$dir/OFL.txt"
  echo "$LEXEND_SHA256  $dir/Lexend[wght].ttf" | sha256sum -c --quiet -
}

assemble() {
  get_firefox
  get_lexend
  log "Assembling $root"
  rm -rf "$root"
  mkdir -p "$out"
  tar -xJf "$cache/firefox-$FIREFOX_VERSION/firefox.tar.xz" -C "$out"
  mv "$out/firefox" "$root"
  # Our files go beside Firefox's; none of Firefox's own files is changed (so the check in
  # check.py can compare them with Mozilla's, and Firefox's integrity is what Mozilla shipped).
  ( cd "$here/layer" && find . -type f ) | while read -r f; do
    if [ -e "$root/$f" ]; then echo "layer would replace Firefox's $f" >&2; exit 1; fi
  done
  cp -r "$here/layer/." "$root/"
  cp "$cache/lexend-$LEXEND_REV/Lexend[wght].ttf" "$root/fonts/"
  mkdir -p "$root/licenses"
  cp "$cache/lexend-$LEXEND_REV/OFL.txt" "$root/licenses/Lexend-OFL.txt"
  cp "$here/../LICENSE" "$root/licenses/SuperAwesomeMagicalBrowser-GPL-3.0.txt"
  cp "$here/layer/superawesomemagical/start/CREDITS.md" "$root/licenses/start-page-photo-CREDITS.md"
  printf '{"version": "%s", "firefox": "%s", "release": %s}\n' "$VERSION" "$FIREFOX_VERSION" "$RELEASE" \
    >"$root/superawesomemagical/version.json"
  # The launcher and desktop integration, for the tarball (the packages put these in /usr).
  install -m755 "$here/linux/$APP" "$root/$APP"
  install -m644 "$here/linux/$APP_ID.desktop" "$root/$APP_ID.desktop"
  cp -r "$here/linux/icons" "$root/icons-hicolor"
}

tarball() {
  log "Packing SuperAwesomeMagicalBrowser-$VERSION-linux-x86_64.tar.xz"
  tar -C "$out" -I "xz -T0" -cf "$out/SuperAwesomeMagicalBrowser-$VERSION-linux-x86_64.tar.xz" "$APP"
}

# Lay out the system install (/usr) in $1: the browser in /usr/lib/superawesomemagicalbrowser, and
# the licences in $2 (Debian and Fedora keep them in different places).
stage_system() {
  local dest=$1 licenses=$2
  rm -rf "$dest"
  mkdir -p "$dest/usr/lib" "$dest/usr/bin" "$dest/usr/share/applications" "$dest/usr/share/metainfo"
  cp -a "$root" "$dest/usr/lib/$APP"
  rm -f "$dest/usr/lib/$APP/$APP" "$dest/usr/lib/$APP/$APP_ID.desktop"
  rm -rf "$dest/usr/lib/$APP/icons-hicolor"
  install -m755 "$here/linux/$APP" "$dest/usr/bin/$APP"
  sed -i "s|^APPDIR=.*|APPDIR=/usr/lib/$APP|" "$dest/usr/bin/$APP"
  install -m644 "$here/linux/$APP_ID.desktop" "$dest/usr/share/applications/"
  install -m644 "$here/linux/$APP_ID.metainfo.xml" "$dest/usr/share/metainfo/"
  mkdir -p "$dest/usr/share/icons"
  cp -r "$here/linux/icons" "$dest/usr/share/icons/hicolor"
  mkdir -p "$dest$licenses"
  cp "$root/licenses/"* "$dest$licenses/"
}

deb() {
  log "Building ${APP}_${VERSION}_amd64.deb"
  local stage="$out/deb"
  stage_system "$stage" "/usr/share/doc/$APP"
  mkdir -p "$stage/DEBIAN"
  local size
  size=$(du -sk --exclude=DEBIAN "$stage" | cut -f1)
  sed -e "s/@VERSION@/$VERSION/" -e "s/@SIZE@/$size/" "$here/linux/debian/control" >"$stage/DEBIAN/control"
  install -m755 "$here/linux/debian/postinst" "$here/linux/debian/prerm" "$stage/DEBIAN/"
  dpkg-deb --root-owner-group -Zxz --build "$stage" "$out/${APP}_${VERSION}_amd64.deb"
  rm -rf "$stage"
}

rpm() {
  log "Building $APP-$VERSION.x86_64.rpm"
  local stage="$out/rpm" top="$out/rpmbuild"
  stage_system "$stage" "/usr/share/licenses/$APP"
  rm -rf "$top"
  mkdir -p "$top"/{BUILD,RPMS,SPECS,SRPMS,SOURCES}
  rpmbuild -bb "$here/linux/$APP.spec" \
    --define "_topdir $top" --define "stage $stage" \
    --define "ff_version $FIREFOX_VERSION" --define "sam_release $RELEASE" --quiet
  mv "$top"/RPMS/x86_64/*.rpm "$out/"
  rm -rf "$stage" "$top"
}

case "${1:-}" in
  "") assemble; tarball ;;
  assemble) assemble ;;
  deb) assemble; tarball; deb ;;
  rpm) assemble; tarball; rpm ;;
  all) assemble; tarball; deb; rpm ;;
  *) echo "usage: $0 [assemble|deb|rpm|all]" >&2; exit 2 ;;
esac
log "Done: $(ls "$out" | grep -v "^$APP\$" | tr '\n' ' ')"
