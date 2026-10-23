package tv.fefetv.home;

/** One tile on the screen: an app, or an action with a drawn glyph. */
final class Card {
    final String title;
    final String sub;
    final Item item;
    final int glyph;
    final Runnable action;

    Card(String title, String sub, Item item, int glyph, Runnable action) {
        this.title = title;
        this.sub = sub;
        this.item = item;
        this.glyph = glyph;
        this.action = action;
    }
}
