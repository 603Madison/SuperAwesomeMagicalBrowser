/* Gives Firefox's own pages (Settings, Add-ons and themes, Downloads...) the SuperAwesomeMagical
 * colours and Lexend: they are drawn with the same design tokens as the windows, so the same
 * theme.css re-colours them. Loaded as an author sheet before the page is first drawn.
 */
const THEME_CSS = "resource://superawesomemagical/styles/theme.css";
const ABOUT_CSS = "resource://superawesomemagical/styles/about.css";

export class SAMAboutThemeChild extends JSWindowActorChild {
  handleEvent(event) {
    if (event.type !== "DOMDocElementInserted" || event.target !== this.document) {
      return;
    }
    const utils = this.contentWindow?.windowUtils;
    if (!utils) {
      return;
    }
    try {
      utils.loadSheetUsingURIString(THEME_CSS, utils.AUTHOR_SHEET);
      utils.loadSheetUsingURIString(ABOUT_CSS, utils.AUTHOR_SHEET);
    } catch (e) {
      console.error("SuperAwesomeMagical: could not style", this.document.documentURI, e);
    }
  }
}
