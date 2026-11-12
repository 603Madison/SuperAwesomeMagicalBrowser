// SuperAwesomeMagical Browser: run superawesomemagical.cfg at startup (Firefox's AutoConfig). It
// sets our default preferences (superawesomemagical/modules/Prefs.sys.mjs; they can't go in this
// file, as Firefox reads its own defaults after this folder's) and loads the look and start page.
pref("general.config.filename", "superawesomemagical.cfg");
pref("general.config.obscure_value", 0);
pref("general.config.sandbox_enabled", false);
