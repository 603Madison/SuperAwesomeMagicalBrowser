#!/usr/bin/env bash
# Builds SuperAwesomeMagical Browser for Linux: our part of it (the launcher, the presets, the start
# page and Lexend), packaged to depend on Ladybird's own 'ladybird' package, which brings the
# browser itself and keeps it up to date.
#
#   desktop/build.sh            the files, laid out as installed (in desktop/out/stage)
#   desktop/build.sh deb        ... and the .deb (Ubuntu); needs dpkg-deb
#   desktop/build.sh rpm        ... and the .rpm (Fedora); needs rpmbuild
#   desktop/build.sh all        both
#
# Versions and checksums are in desktop/release.env. Downloads are cached in desktop/.cache.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=release.env
source "$here/release.env"

APP=superawesomemagicalbrowser
APP_ID=io.github._603madison.SuperAwesomeMagicalBrowser
SHARE=/usr/share/$APP
FULL_VERSION="$VERSION-$RELEASE"
cache="$here/.cache"
out="$here/out"

log() { printf '\033[1;35m==>\033[0m %s\n' "$*"; }
fetch() { # url dest (kept in the cache once downloaded)
  if [ ! -s "$2" ]; then
    curl -fsSL --retry 5 -o "$2.part" "$1"
    mv "$2.part" "$2"
  fi
}

get_lexend() {
  local dir="$cache/lexend-$LEXEND_REV" base="https://raw.githubusercontent.com/google/fonts/$LEXEND_REV/ofl/lexend"
  mkdir -p "$dir"
  fetch "$base/Lexend%5Bwght%5D.ttf" "$dir/Lexend[wght].ttf"
  fetch "$base/OFL.txt" "$dir/OFL.txt"
  echo "$LEXEND_SHA256  $dir/Lexend[wght].ttf" | sha256sum -c --quiet -
}

# Lay out the installed files in $1, with the licences in $2 (Debian and Fedora keep them apart).
stage() {
  local dest=$1 licenses=$2
  get_lexend
  rm -rf "$dest"
  install -d "$dest/usr/bin" "$dest$SHARE/fonts" "$dest/usr/share/applications" "$dest/usr/share/metainfo" "$dest$licenses"
  # Only on the SHARE= line: the launcher's own sed of settings.json names @SHARE@ too.
  sed "/^SHARE=/s|@SHARE@|$SHARE|" "$here/linux/$APP" > "$dest/usr/bin/$APP"
  chmod 755 "$dest/usr/bin/$APP"
  # settings.json keeps @SHARE@: the launcher fills it in when it writes the presets.
  install -m644 "$here/edition/settings.json" "$dest$SHARE/settings.json"
  sed "s|@SHARE@|$SHARE|g" "$here/edition/fonts.conf" > "$dest$SHARE/fonts.conf"
  install -m755 "$here/edition/firefox-bookmarks.py" "$dest$SHARE/firefox-bookmarks.py"
  cp -r "$here/edition/start" "$dest$SHARE/start"
  install -m644 "$cache/lexend-$LEXEND_REV/Lexend[wght].ttf" "$dest$SHARE/fonts/"
  install -m644 "$here/linux/$APP_ID.desktop" "$dest/usr/share/applications/"
  install -m644 "$here/linux/$APP_ID.metainfo.xml" "$dest/usr/share/metainfo/"
  install -d "$dest/usr/share/icons"
  cp -r "$here/linux/icons" "$dest/usr/share/icons/hicolor"
  install -m644 "$cache/lexend-$LEXEND_REV/OFL.txt" "$dest$licenses/Lexend-OFL.txt"
  install -m644 "$here/../LICENSE" "$dest$licenses/SuperAwesomeMagicalBrowser-GPL-3.0.txt"
  install -m644 "$here/edition/start/CREDITS.md" "$dest$licenses/start-page-photo-CREDITS.md"
  printf '{"version": "%s", "ladybird_min": "%s"}\n' "$FULL_VERSION" "$LADYBIRD_MIN_VERSION" > "$dest$SHARE/version.json"
}

deb() {
  log "Building ${APP}_${FULL_VERSION}_all.deb"
  local dir="$out/deb"
  stage "$dir" "/usr/share/doc/$APP"
  install -d "$dir/DEBIAN"
  sed -e "s/@VERSION@/$FULL_VERSION/" -e "s/@SIZE@/$(du -sk "$dir" | cut -f1)/" \
    -e "s/@LADYBIRD_MIN@/$LADYBIRD_MIN_VERSION/" "$here/linux/debian/control" > "$dir/DEBIAN/control"
  install -m755 "$here/linux/debian/postinst" "$here/linux/debian/prerm" "$dir/DEBIAN/"
  dpkg-deb --root-owner-group -Zxz --build "$dir" "$out/${APP}_${FULL_VERSION}_all.deb"
  rm -rf "$dir"
}

rpm() {
  log "Building $APP-$FULL_VERSION.x86_64.rpm"
  local dir="$out/rpm" top="$out/rpmbuild"
  stage "$dir" "/usr/share/licenses/$APP"
  rm -rf "$top"
  install -d "$top"/{BUILD,RPMS,SPECS,SRPMS,SOURCES}
  rpmbuild -bb "$here/linux/$APP.spec" --target x86_64 --quiet \
    --define "_topdir $top" --define "stage $dir" --define "sam_version $VERSION" \
    --define "sam_release $RELEASE" --define "ladybird_min $LADYBIRD_MIN_VERSION"
  mv "$top"/RPMS/x86_64/*.rpm "$out/"
  rm -rf "$dir" "$top"
}

mkdir -p "$out"
case "${1:-}" in
  "") stage "$out/stage" "/usr/share/licenses/$APP" ;;
  deb) deb ;;
  rpm) rpm ;;
  all) deb; rpm ;;
  *) echo "usage: $0 [deb|rpm|all]" >&2; exit 2 ;;
esac
log "Done: $(ls "$out" | grep -E '\.(deb|rpm)$' | tr '\n' ' ')"
