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
 * MoonQuill's web fiction (moonquill.com is now the publisher's book site). The book page has the synopsis and the
 * whole table of contents in two tabs; chapter pages are plain HTML.
 */
public class moonquillnovels_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "MoonQuill";
    private final String url = "https://moonquillnovels.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public moonquillnovels_com() {
    }

    public moonquillnovels_com(Novel novel) {
        this.novel = novel;
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
            if (chapterList.isEmpty()) {
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
        blacklistedTags.add("ins.adsbygoogle");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the table of contents tab, in order. */
    static List<Chapter> parseChapterList(Document bookPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element item : bookPage.select("#toc .chapter-list-item")) {
            Element link = item.selectFirst("a[href*=/chapter/]");
            if (link == null) continue;
            Element title = link.selectFirst(".chapter-title");
            String name = title != null ? title.text() : link.text();
            if (name.isBlank()) continue;
            chapterList.add(new Chapter(name, link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#content.reading-content");
    }

    /**
     * Reads title, author, synopsis and genres from the book's header. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document bookPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = bookPage.selectFirst("h1");
        if (title == null) return metadata;
        // The author link, genres and buttons follow the title; recommended books further down have their own
        Element header = title.parent();
        Element author = header.selectFirst("a[href*=/user/]");
        Element synopsis = bookPage.selectFirst("#syn");

        metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (synopsis != null) metadata.setDescription(synopsis.text());
        metadata.setSubjects(header.select(".badge").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document bookPage) {
        Element cover = bookPage.selectFirst(".book-cover-container img[src]");
        return cover == null ? null : cover.absUrl("src");
    }
}
