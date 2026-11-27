# SuperAwesomeMagical Browser for Fedora (and others with dnf): our build of Ladybird (with Bitwarden in
# the toolbar), Bitwarden's command-line client, and the SuperAwesomeMagical presets, start page and
# Lexend. desktop/build.sh rpm builds it from the staged files, compiled beforehand by CI. What it needs
# from Fedora was worked out where Ladybird was built (desktop/ladybird/build-ladybird.sh); rpm's own
# dependency generator stays off, as the bundled programs would otherwise ask for their own libraries.

Name:           superawesomemagicalbrowser
Version:        %{sam_version}
Release:        %{sam_release}
Summary:        Ladybird with your tabs down the side and Bitwarden built in
License:        GPL-3.0-only AND BSD-2-Clause AND OFL-1.1 AND LGPL-2.1-or-later AND LGPL-3.0-only
URL:            https://github.com/603Madison/SuperAwesomeMagicalBrowser

AutoReqProv:    no
%global debug_package %{nil}
%global __os_install_post %{nil}
%define _build_id_links none
%define _binary_payload w19T.zstdio

Requires:       %{ladybird_requires}
Requires:       liberation-sans-fonts, liberation-serif-fonts, liberation-mono-fonts
Requires:       python3
# Ladybird's own package, if it is there too, keeps to /opt/ladybird and to its own profiles.

%description
SuperAwesomeMagical Browser is Ladybird, the independent browser, set up the SuperAwesomeMagical
way: tabs down the left side, Lexend for the interface and for pages, Ecosia for searches, ads and
trackers blocked, a start page with a big clock over the northern lights, and Bitwarden in the
toolbar to fill in your logins. It runs in a Ladybird profile of its own.

%install
cp -a %{stage}/. %{buildroot}/

%files
/usr/bin/superawesomemagicalbrowser
/usr/lib/superawesomemagicalbrowser
/usr/share/superawesomemagicalbrowser
/usr/share/applications/io.github._603madison.SuperAwesomeMagicalBrowser.desktop
/usr/share/metainfo/io.github._603madison.SuperAwesomeMagicalBrowser.metainfo.xml
/usr/share/icons/hicolor/*/apps/io.github._603madison.SuperAwesomeMagicalBrowser.*
%license /usr/share/licenses/superawesomemagicalbrowser
