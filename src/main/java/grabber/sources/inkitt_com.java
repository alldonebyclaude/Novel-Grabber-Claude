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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * inkitt.com answers plain requests with a Cloudflare bot check, so every page is loaded in the app's browser.
 * The story page ({@code /stories/<id>}) lists the chapters ({@code /stories/<id>/chapters/<n>}).
 */
public class inkitt_com implements Source {
    private static final Duration BOT_CHECK_WAIT = Duration.ofSeconds(30);
    private static final Pattern BACKGROUND_URL = Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)");

    private final String name = "Inkitt";
    private final String url = "https://www.inkitt.com/";
    private final boolean canHeadless = true;
    private Novel novel;
    private Document toc;

    public inkitt_com(Novel novel) {
        this.novel = novel;
    }

    public inkitt_com() {
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

    /** Reads the story's chapter list, in order. The chapter's number is shown apart from its title. */
    static List<Chapter> parseChapterList(Document storyPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : storyPage.select("a.chapter-link[href]")) {
            Element title = link.selectFirst(".chapter-title");
            String name = title != null ? title.text() : link.text();
            if (name.isBlank()) continue;
            chapterList.add(new Chapter(name, link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".story-page-text");
    }

    /**
     * Reads title, author and summary. The site's genre links belong to its menu, so no genres are read. The cover
     * is left to {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document storyPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = storyPage.selectFirst(".story-title");
        Element author = storyPage.selectFirst(".author-link span");
        Element summary = storyPage.selectFirst(".story-summary");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (summary != null) metadata.setDescription(summary.text());
        return metadata;
    }

    /** The cover shown on the page (a background image), or else the sharing image. */
    static String parseCoverUrl(Document storyPage) {
        Element cover = storyPage.selectFirst(".story-horizontal-cover div[itemprop=image]");
        if (cover != null) {
            Matcher background = BACKGROUND_URL.matcher(cover.attr("style"));
            if (background.find()) return background.group(1);
        }
        Element shared = storyPage.selectFirst("meta[property=og:image]");
        return shared == null || shared.attr("content").isBlank() ? null : shared.absUrl("content");
    }
}
