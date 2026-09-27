package grabber.sources;

import grabber.Chapter;
import grabber.Driver;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * honeyfeed.fm answers plain requests with a Cloudflare bot check, so every page is loaded in the app's browser.
 * The novel page ({@code /novels/<id>}) has the details; its chapter list is at {@code /novels/<id>/chapters}.
 */
public class honeyfeed_fm implements Source {
    private static final Duration BOT_CHECK_WAIT = Duration.ofSeconds(30);

    private final String name = "Honeyfeed";
    private final String url = "https://www.honeyfeed.fm/";
    private final boolean canHeadless = true;
    private Novel novel;
    private Document toc;

    public honeyfeed_fm(Novel novel) {
        this.novel = novel;
    }

    public honeyfeed_fm() {
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
            String listUrl = chapterListUrl(novel.novelLink);
            toc = load(listUrl.substring(0, listUrl.length() - "/chapters".length()));
            Thread.sleep(1000);
            chapterList = parseChapterList(load(listUrl));
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            }
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        browser.driver.navigate().to(pageUrl);
        long deadline = System.currentTimeMillis() + BOT_CHECK_WAIT.toMillis();
        while (String.valueOf(browser.driver.getTitle()).contains("Just a moment") && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Jsoup.parse(browser.driver.getPageSource(), browser.driver.getCurrentUrl());
    }

    /** The chapter list page for a link to the novel or its chapter list. */
    static String chapterListUrl(String novelLink) {
        String novelUrl = novelLink.replaceAll("[?#].*$", "").replaceAll("/+$", "");
        return novelUrl.endsWith("/chapters") ? novelUrl : novelUrl + "/chapters";
    }

    /** Reads the chapter list, in order. */
    static List<Chapter> parseChapterList(Document chapterListPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : chapterListPage.select("li.list-group-item a[href*=/chapters/]")) {
            Element title = link.selectFirst(".text-bold");
            String name = title != null ? title.text() : link.text();
            if (name.isBlank()) continue;
            chapterList.add(new Chapter(name, link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Returns the chapter text (all of its page blocks), or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#chapter-body .wrap-body > div");
    }

    /**
     * Reads title, author, synopsis and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("meta[property=og:title]");
        // The first link to the author's page is their avatar; the second has their name
        Element author = novelPage.select("#wrap-novel a[href^=/u/]").stream()
                .filter(link -> !link.text().isBlank()).findFirst().orElse(null);
        Element synopsis = novelPage.selectFirst("#wrap-novel div.wrap-novel-body");

        if (title != null) metadata.setTitle(title.attr("content"));
        if (author != null) metadata.setAuthor(author.text());
        if (synopsis != null) metadata.setDescription(synopsis.text());
        // Shown twice, for small and for large screens
        metadata.setSubjects(novelPage.select("#wrap-novel btn.pt4").eachText().stream().distinct().toList());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".wrap-img-novel-mask img");
        return cover == null ? null : cover.absUrl("src");
    }
}
