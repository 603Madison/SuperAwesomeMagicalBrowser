# SuperAwesomeMagical Browser for Fedora (and others with dnf): Ladybird's own package with the
# SuperAwesomeMagical presets, start page and Lexend. desktop/build.sh rpm builds it from the staged
# files; there is nothing to compile. It is built for x86_64 (the name SuperAwesomeMagicalLinux's
# image looks for), though nothing in it is specific to a processor.

Name:           superawesomemagicalbrowser
Version:        %{sam_version}
Release:        %{sam_release}
Summary:        Ladybird with your tabs down the side, in the SuperAwesomeMagical look
License:        GPL-3.0-only AND OFL-1.1
URL:            https://github.com/603Madison/SuperAwesomeMagicalBrowser

AutoReqProv:    no
%global debug_package %{nil}
%global __os_install_post %{nil}
%define _build_id_links none
%define _binary_payload w19T.zstdio

# Ladybird from Ladybird's repository (rpm.ladybird.org), which keeps it up to date.
Requires:       ladybird >= %{ladybird_min}
Requires:       python3

%description
SuperAwesomeMagical Browser is Ladybird, the independent browser, set up the SuperAwesomeMagical
way: tabs down the left side, Lexend for the interface and for pages, the Lavender colour, Ecosia
for searches, ads and trackers blocked, and a start page with a big clock over the northern lights.
It runs in a Ladybird profile of its own.

%install
cp -a %{stage}/. %{buildroot}/

%files
/usr/bin/superawesomemagicalbrowser
/usr/share/superawesomemagicalbrowser
/usr/share/applications/io.github._603madison.SuperAwesomeMagicalBrowser.desktop
/usr/share/metainfo/io.github._603madison.SuperAwesomeMagicalBrowser.metainfo.xml
/usr/share/icons/hicolor/*/apps/io.github._603madison.SuperAwesomeMagicalBrowser.*
%license /usr/share/licenses/superawesomemagicalbrowser
