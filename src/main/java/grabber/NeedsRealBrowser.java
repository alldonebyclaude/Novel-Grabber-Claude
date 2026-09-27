package grabber;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the source of a site that the app's built-in browser (HtmlUnit) can't load: the site's bot check stops it, or
 * its pages never finish loading in it. The source is kept for people who choose a real browser (Chrome) in the
 * settings; that hasn't been tested.
 * <p>
 * The {@code value} is shown to the user when a download uses the source.
 * <p>
 * This lives in {@code grabber}, not {@code grabber.sources}: the app loads every class in the sources folder as a
 * site, and an annotation there would break that.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NeedsRealBrowser {
    /** What happens with the built-in browser, e.g. "The site's bot check blocks the built-in browser." */
    String value();
}
