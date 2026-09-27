package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.nodes.Element;

import java.util.List;

public interface Source {
    String getName();

    String getUrl();

    boolean canHeadless();

    List<Chapter> getChapterList();

    Element getChapterContent(Chapter chapter);

    NovelMetadata getMetadata();

    List<String> getBlacklistedTags();

    String toString();

    /**
     * Whether this source is marked {@code @Deprecated}: the site is gone, has moved or can't be supported any more,
     * and support will be removed.
     */
    default boolean isDeprecated() {
        return getClass().isAnnotationPresent(Deprecated.class);
    }

    /**
     * Whether this source is marked {@link grabber.PaywallSite}: the site sells its chapters.
     */
    default boolean isPaywallSite() {
        return getClass().isAnnotationPresent(grabber.PaywallSite.class);
    }

    /**
     * Whether this source is marked {@link grabber.NeedsRealBrowser}: the app's built-in browser can't load the site.
     */
    default boolean needsRealBrowser() {
        return getClass().isAnnotationPresent(grabber.NeedsRealBrowser.class);
    }
}
