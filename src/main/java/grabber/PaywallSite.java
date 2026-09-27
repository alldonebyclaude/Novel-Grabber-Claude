package grabber;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the source of a paywall site: a site that sells its chapters, so without paying only a free preview can be
 * read. Such sources may only download the chapters the user can open (free ones, or bought ones with the user's own
 * login) and must skip locked chapters.
 * <p>
 * The {@code value} is shown to the user when a download uses the source, e.g. to say that it doesn't work yet.
 * <p>
 * This lives in {@code grabber}, not {@code grabber.sources}: the app loads every class in the sources folder as a
 * site, and an annotation there would break that.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PaywallSite {
    /** A note for the user, e.g. what works and what doesn't. */
    String value();
}
