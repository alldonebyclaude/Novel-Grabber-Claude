package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The novel page lists the chapters by volume in accordions (and, for some novels, further sections such as
 * in-story forum posts). Early-access chapters show "(Unlocks on &lt;date&gt;)" and are left out until then.
 */
public class re_library_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Re:Library";
    private final String url = "https://re-library.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public re_library_com(Novel novel) {
        this.novel = novel;
    }

    public re_library_com() {
    }

    public String getName() {
        return name;
    }

    public boolean canHeadless() {
        return canHeadless;
    }

    public String toString() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public List<Chapter> getChapterList() {
        List<Chapter> chapterList = new ArrayList<>();
        try {
            toc = fetch(novel.novelLink);
            chapterList = parseChapterList(toc);
            int locked = countLockedChapters(toc);
            if (locked > 0) {
                GrabberUtils.info(novel.window, locked + " chapters are early access and can't be read yet, "
                        + "so they are left out.");
            }
            if (chapterList.isEmpty() && locked == 0) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        Element chapterBody = null;
        try {
            chapterBody = parseChapterBody(fetch(chapter.chapterURL));
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterBody;
    }

    public NovelMetadata getMetadata() {
        if (toc == null) return new NovelMetadata();

        NovelMetadata metadata = parseMetadata(toc);
        String coverUrl = parseCoverUrl(toc);
        if (coverUrl != null) metadata.setBufferedCover(coverUrl);
        return metadata;
    }

    public List<String> getBlacklistedTags() {
        List<String> blacklistedTags = new ArrayList<>();
        blacklistedTags.add(".sharedaddy");
        blacklistedTags.add(".code-block");
        blacklistedTags.add(".ezoic-adpicker-ad");
        blacklistedTags.add(".ezoic-ad");
        blacklistedTags.add(".su-button");
        blacklistedTags.add("table:has(a[href])");
        blacklistedTags.add("div[style=margin:0 auto;width:100px]");
        blacklistedTags.add(".prevPageLink");
        blacklistedTags.add(".nextPageLink");
        blacklistedTags.add("a:contains(Next)");
        blacklistedTags.add("a:contains(Previous)");
        blacklistedTags.add("table:has(span[style=font-size:8pt;color:#999999])");
        blacklistedTags.add("h2:contains(References)");
        blacklistedTags.add("table#fixed");
        blacklistedTags.add("a:contains(Index)");
        // Navigation, the author/translator/editor credits and the view counter and like buttons
        blacklistedTags.add(".PageLink");
        blacklistedTags.add("table:matches((?i)translator|editor)");
        blacklistedTags.add(".post-views");
        blacklistedTags.add(".wpulike");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the chapters in page order: the volumes, then any further sections. Early-access chapters are left out. */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select(".entry-content .su-accordion li a[href]")) {
            if (link.selectFirst(".rl-unlock-text") != null || link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** The number of early-access chapters, which show when they unlock instead of a readable link. */
    static int countLockedChapters(Document novelPage) {
        return novelPage.select(".entry-content .su-accordion li a:has(.rl-unlock-text)").size();
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("article .entry-content");
    }

    /**
     * Reads title, author, synopsis and categories. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".entry-title");
        Element author = infoValue(novelPage, "Author");
        Element categories = infoValue(novelPage, "Categories");
        Element synopsis = novelPage.selectFirst(".entry-content .su-box .su-box-content");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (synopsis != null) metadata.setDescription(synopsis.text());
        if (categories != null) {
            List<String> subjects = new ArrayList<>();
            for (String category : categories.text().split(",")) {
                if (!category.isBlank()) subjects.add(category.strip());
            }
            metadata.setSubjects(subjects);
        }
        return metadata;
    }

    /** The value next to a label in the novel's info box, e.g. "Author" (a table cell) or "Categories" (a div). */
    private static Element infoValue(Document novelPage, String label) {
        Element labelElement = novelPage.selectFirst(".entry-content table.rounded *:matchesOwn(^" + label + "$)");
        return labelElement == null ? null : labelElement.nextElementSibling();
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".entry-content table.rounded img[src]");
        return cover == null ? null : cover.absUrl("src");
    }
}
