package grabber.sources;

import grabber.Chapter;
import grabber.Driver;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * asianovel.net answers plain requests with a Cloudflare bot check, so every page is loaded in the app's browser.
 * The story page ({@code /story/<id>/}) lists the chapters ({@code /chapter/<slug>/}).
 */
public class asianovel_net implements Source {
    private static final Duration BOT_CHECK_WAIT = Duration.ofSeconds(30);

    private final String name = "asianovel";
    private final String url = "https://www.asianovel.net/";
    private final boolean canHeadless = true;
    private Novel novel;
    private Document toc;

    public asianovel_net(Novel novel) {
        this.novel = novel;
    }

    public asianovel_net() {
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
            toc = load(novel.novelLink);
            chapterList = parseChapterList(toc);
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            }
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        try {
            return parseChapterBody(load(chapter.chapterURL));
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
            return null;
        }
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
        // Ad slots above and below the text
        blacklistedTags.add("[class*=asian-ads]");
        blacklistedTags.add("script");
        return blacklistedTags;
    }

    private Driver browser() {
        if (novel.headlessDriver == null) novel.headlessDriver = new Driver(novel.window, novel.browser);
        return novel.headlessDriver;
    }

    /** Loads a page in the browser. If the site shows its bot check first, waits for the browser to get past it. */
    private Document load(String pageUrl) {
        Driver browser = browser();
        browser.navigate(pageUrl);
        long deadline = System.currentTimeMillis() + BOT_CHECK_WAIT.toMillis();
        while (String.valueOf(browser.driver.getTitle()).contains("Just a moment") && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return browser.pageDocument();
    }

    /** Reads the story's chapter list, in order. */
    static List<Chapter> parseChapterList(Document storyPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : storyPage.select("ol.chapter-group__list li a[href*=/chapter/]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(apostrophes(link.text()), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("article.chapter__article div.chapter-formatting");
    }

    /**
     * Reads title, author, summary and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document storyPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = storyPage.selectFirst("h1");
        Element author = storyPage.selectFirst("a[href*=/author/]");
        Element summary = storyPage.selectFirst("section.story__summary");

        if (title != null) metadata.setTitle(apostrophes(title.text()));
        if (author != null) metadata.setAuthor(author.text());
        if (summary != null) metadata.setDescription(summary.text());
        metadata.setSubjects(storyPage.select("a[href*=/genre/]").eachText().stream().distinct().toList());
        return metadata;
    }

    /** The site writes apostrophes as "#*#" in titles, e.g. "Sect#*#s"; this puts them back. */
    static String apostrophes(String text) {
        return text.replace("#*#", "'");
    }

    static String parseCoverUrl(Document storyPage) {
        Element cover = storyPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.absUrl("content");
    }
}
