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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wuxia Box, formerly readwn.com. The novel page ({@code /novel/<slug>.html}) lists the first 100 chapters; further
 * pages of the list are {@code /e/extend/fy.php?page=N&wjm=<slug>}, counting from 0.
 */
public class wuxiabox_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final long LIST_PAGE_DELAY_MS = 1000;

    private final String name = "Wuxia Box";
    private final String url = "https://www.wuxiabox.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public wuxiabox_com(Novel novel) {
        this.novel = novel;
    }

    public wuxiabox_com() {
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
            Document listPage = toc;
            Set<String> visited = new HashSet<>();
            while (true) {
                chapterList.addAll(parseChapterList(listPage));
                String nextPage = parseNextPageUrl(listPage);
                if (nextPage == null || !visited.add(nextPage)) break;
                Thread.sleep(LIST_PAGE_DELAY_MS);
                listPage = fetch(nextPage);
            }
            if (chapterList.isEmpty()) {
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
        // Ad slots inside the text
        blacklistedTags.add(".chapter-content div");
        blacklistedTags.add("script");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the chapters on one page of the list, in order. The chapter's number is shown apart from its title. */
    static List<Chapter> parseChapterList(Document listPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : listPage.select(".chapter-list a[href]")) {
            Element title = link.selectFirst(".chapter-title");
            String name = title != null ? title.text() : link.text();
            if (name.isBlank()) continue;
            chapterList.add(new Chapter(name, link.attr("abs:href")));
        }
        return chapterList;
    }

    /** The next page of the chapter list (the "&gt;" link after the current page), or null on the last page. */
    static String parseNextPageUrl(Document listPage) {
        Element active = listPage.selectFirst(".pagination li.active");
        if (active == null) return null;
        for (Element item = active.nextElementSibling(); item != null; item = item.nextElementSibling()) {
            Element link = item.selectFirst("a[href]");
            if (link != null && link.text().strip().equals(">")) return link.attr("abs:href");
        }
        return null;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".chapter-content");
    }

    /**
     * Reads title, author, summary and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        // Recommended novels further down use the same class for their titles
        Element title = novelPage.selectFirst("h1.novel-title");
        Element author = novelPage.selectFirst(".author span[itemprop=author]");
        Element summary = novelPage.selectFirst(".summary");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (summary != null) metadata.setDescription(summary.text());
        metadata.setSubjects(novelPage.select("a.property-item").eachText());
        return metadata;
    }

    /** The cover is lazy-loaded: its address is in data-src, and src holds a placeholder. */
    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("figure.cover img, .cover img");
        if (cover == null) return null;
        return cover.hasAttr("data-src") ? cover.absUrl("data-src") : cover.absUrl("src");
    }
}
