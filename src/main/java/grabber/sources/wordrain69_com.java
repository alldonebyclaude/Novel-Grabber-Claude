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
 * A WordPress site with the Madara theme. The novel page loads its chapter list with a POST to
 * {@code <novel>/ajax/chapters/}, newest first. Chapters that cost coins are left out.
 */
public class wordrain69_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Wordrain";
    private final String url = "https://wordrain69.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public wordrain69_com(Novel novel) {
        this.novel = novel;
    }

    public wordrain69_com() {
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
            toc = connect(novel.novelLink).get();
            Thread.sleep(1000);
            Document list = connect(chapterListUrl(novel.novelLink)).header("X-Requested-With", "XMLHttpRequest").post();
            chapterList = parseChapterList(list);
            int locked = countLockedChapters(list);
            if (locked > 0) {
                GrabberUtils.info(novel.window, locked + " chapters cost coins, so they are left out.");
            }
            if (chapterList.isEmpty() && locked == 0) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        Element chapterBody = null;
        try {
            chapterBody = parseChapterBody(connect(chapter.chapterURL).get());
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
        blacklistedTags.add("center");
        blacklistedTags.add("meta");
        blacklistedTags.add("script");
        blacklistedTags.add("iframe");
        blacklistedTags.add(".google-auto-placed");
        blacklistedTags.add(".sharedaddy");
        return blacklistedTags;
    }

    private org.jsoup.Connection connect(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).timeout(30 * 1000);
    }

    /** The URL the novel page loads its chapter list from. */
    static String chapterListUrl(String novelUrl) {
        return (novelUrl.endsWith("/") ? novelUrl : novelUrl + "/") + "ajax/chapters/";
    }

    /** Whether a chapter in the list can be read without coins. */
    private static boolean isFree(Element item) {
        return !item.hasClass("premium") && item.selectFirst(".coin:not(.free)") == null;
    }

    /** Reads the free chapters, oldest first (the site lists the newest first). */
    static List<Chapter> parseChapterList(Document list) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element item : list.select("li.wp-manga-chapter")) {
            Element link = item.selectFirst("a[href]");
            if (!isFree(item) || link == null || link.attr("href").equals("#") || link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        Collections.reverse(chapterList);
        return chapterList;
    }

    /** The number of chapters that cost coins. */
    static int countLockedChapters(Document list) {
        return (int) list.select("li.wp-manga-chapter").stream().filter(item -> !isFree(item)).count();
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".text-left");
    }

    /**
     * Reads title, author, summary and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".post-title h1");
        Element author = novelPage.selectFirst(".author-content a");
        Element summary = novelPage.selectFirst(".summary__content");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (summary != null) metadata.setDescription(summary.text());
        metadata.setSubjects(novelPage.select(".genres-content a").eachText());
        return metadata;
    }

    /** The cover is lazy-loaded: its address is in data-src, and src holds a placeholder. */
    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".summary_image img");
        if (cover == null) return null;
        return cover.hasAttr("data-src") ? cover.absUrl("data-src") : cover.absUrl("src");
    }
}
