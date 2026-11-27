#!/usr/bin/env bash
# Builds Ladybird for SuperAwesomeMagical Browser: Ladybird's release LADYBIRD_VERSION (desktop/release.env) with our
# patches (desktop/ladybird/patches, applied in order), as Ladybird builds its own releases, installed under
# /usr/lib/superawesomemagicalbrowser/ladybird.
#
#   build-ladybird.sh OUT [CACHE]
#
# Runs as root in a fresh fedora:44 or ubuntu:24.04 container (CI's "Ladybird" jobs): it installs what the build needs
# with Ladybird's own script. On Ubuntu 24.04, whose Qt is too old, Qt is built too and comes with the browser.
# Leaves OUT/ladybird-<system>.tar.gz (the installed files) and OUT/ladybird-<system>.depends (the .deb's Depends, on
# Ubuntu). CACHE (default /cache) keeps vcpkg's built packages and ccache's objects between builds.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=../release.env
source "$here/../release.env"
out=$(mkdir -p "$1" && cd "$1" && pwd)
cache=${2:-/cache}
prefix=/usr/lib/superawesomemagicalbrowser/ladybird
src=/tmp/ladybird
. /etc/os-release
system="$ID-$VERSION_ID"
mkdir -p "$cache/ccache" "$cache/ladybird"
# The cache is saved by CI's own user, outside the container.
trap 'chmod -R a+rwX "$cache" "$out" 2>/dev/null || true' EXIT

log() { printf '\033[1;35m==>\033[0m %s\n' "$*"; }

log "Fetching Ladybird $LADYBIRD_VERSION"
case $ID in
    fedora) dnf install -y --setopt=install_weak_deps=False git-core ca-certificates ccache ;;
    ubuntu)
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -q
        apt-get install -y -q --no-install-recommends git ca-certificates ccache dpkg-dev ;;
esac
rm -rf "$src"
git -c advice.detachedHead=false clone -q --depth 1 --branch "$LADYBIRD_VERSION" \
    https://github.com/LadybirdBrowser/ladybird.git "$src"
for patch in "$here"/patches/*.patch; do
    [ -e "$patch" ] || continue
    log "Applying $(basename "$patch")"
    git -C "$src" apply --whitespace=nowarn "$patch"
done

log "Installing what the build needs ($PRETTY_NAME)"
"$src/Meta/Docker/ci/install-build-dependencies.sh"
export RUSTUP_HOME=/opt/rustup CARGO_HOME=/opt/cargo PATH=/opt/cargo/bin:$PATH

log "Building"
export CC=clang CXX=clang++ LADYBIRD_SOURCE_DIR=$src
export CCACHE_DIR=$cache/ccache CCACHE_MAXSIZE=3G CCACHE_BASEDIR=$src CCACHE_COMPILERCHECK="%compiler% -v"
build_qt=OFF
if [ "$ID" = ubuntu ] && dpkg --compare-versions "$VERSION_ID" lt 26; then build_qt=ON; fi
cd "$src"
# Ladybird's Distribution build, but without link-time optimization and symbol files (they double the time and the
# memory a 4-core CI machine has), and without Ladybird's update check: updates come from us.
cmake --preset Distribution -B Build \
    -DLADYBIRD_BUILD_QT="$build_qt" \
    -DLADYBIRD_ENABLE_LTO=OFF \
    -DLADYBIRD_GENERATE_SYMBOL_FILES=OFF \
    -DLADYBIRD_ENABLE_UPDATE_CHECK=OFF \
    -DLADYBIRD_CACHE_DIR="$cache/ladybird" \
    -DLADYBIRD_VERSION="$LADYBIRD_VERSION" \
    -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_INSTALL_PREFIX="$prefix"
cmake --build Build
ccache --show-stats || true

log "Installing into the package's layout"
stage=/tmp/stage
rm -rf "$stage"
DESTDIR=$stage cmake --install Build --strip --component ladybird_Runtime
test -x "$stage$prefix/bin/Ladybird"
tar -czf "$out/ladybird-$system.tar.gz" -C "$stage" .

if [ "$ID" = ubuntu ]; then
    # What the installed programs need from Ubuntu, worked out as Debian's own tools do. Libraries that come with the
    # browser (its own, and Qt here) are found in its lib folder, so they don't become dependencies.
    log "Working out the .deb's dependencies"
    work=$(mktemp -d)
    mkdir -p "$work/debian"
    touch "$work/debian/control"
    mapfile -t elves < <(find "$stage$prefix" -type f -exec sh -c 'file -b "$1" | grep -q "^ELF"' _ {} \; -print)
    (cd "$work" && dpkg-shlibdeps -O --ignore-missing-info -l"$stage$prefix/lib" "${elves[@]}") |
        sed -n 's/^shlibs:Depends=//p' > "$out/ladybird-$system.depends"
    cat "$out/ladybird-$system.depends"
fi
log "Done: $(ls "$out")"
