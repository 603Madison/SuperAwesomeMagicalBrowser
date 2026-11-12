# SuperAwesomeMagical Browser for Fedora (and others with dnf). desktop/build.sh rpm builds it from
# the already assembled browser (the stage directory), so there is no source package: Firefox is
# Mozilla's own build, unpacked and checked against Mozilla's signature.

Name:           superawesomemagicalbrowser
Version:        %{ff_version}
Release:        %{sam_release}
Summary:        Firefox with your tabs down the side, in the SuperAwesomeMagical look
License:        GPL-3.0-only AND MPL-2.0 AND OFL-1.1
URL:            https://github.com/603Madison/SuperAwesomeMagicalBrowser
ExclusiveArch:  x86_64

# Firefox's bundled libraries are its own: don't list them as provided by this package or
# required by it, and don't strip or re-link Mozilla's binaries.
AutoReqProv:    no
%global debug_package %{nil}
%global __os_install_post %{nil}
%define _build_id_links none
%define _binary_payload w19T.zstdio

Requires:       gtk3
Requires:       alsa-lib
Requires:       dbus-libs
Requires:       libX11-xcb
Requires:       libXtst
Recommends:     pulseaudio-libs
Recommends:     google-noto-color-emoji-fonts

%description
SuperAwesomeMagical Browser is Mozilla Firefox (unmodified, as Mozilla releases it) with the
SuperAwesomeMagical family's look: Lexend everywhere, the aurora's purple and green over cool
slate, soft rounded cards, tabs down the left, and a start page with a big clock over the northern
lights. It keeps its own profile, so it never mixes with a separately installed Firefox.

%install
cp -a %{stage}/. %{buildroot}/

%files
/usr/bin/superawesomemagicalbrowser
/usr/lib/superawesomemagicalbrowser
/usr/share/applications/io.github._603madison.SuperAwesomeMagicalBrowser.desktop
/usr/share/metainfo/io.github._603madison.SuperAwesomeMagicalBrowser.metainfo.xml
/usr/share/icons/hicolor/*/apps/io.github._603madison.SuperAwesomeMagicalBrowser.*
%license /usr/share/licenses/superawesomemagicalbrowser
