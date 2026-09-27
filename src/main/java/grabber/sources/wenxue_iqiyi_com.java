package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.PaywallSite;
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
 * iQIYI Literature. The book page links the catalog ({@code /book/catalog-<id>-1.html}), which lists the chapters;
 * VIP chapters are marked with a diamond icon and have to be bought, so they are left out.
 */
@PaywallSite("Only the free chapters are downloaded; VIP chapters are left out.")
public class wenxue_iqiyi_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final long LIST_PAGE_DELAY_MS = 1000;

    private final String name = "Wenxue";
    private final String url = "https://wenxue.iqiyi.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public wenxue_iqiyi_com(Novel novel) {
        this.novel = novel;
    }

    public wenxue_iqiyi_com() {
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
            String catalogUrl = parseCatalogUrl(toc);
            if (catalogUrl == null) {
                GrabberUtils.err(novel.window, "Could not find the book's catalog. Correct novel link?");
                return chapterList;
            }
            int vip = 0;
            Set<String> visited = new HashSet<>();
            String pageUrl = catalogUrl;
            while (pageUrl != null && visited.add(pageUrl)) {
                Thread.sleep(LIST_PAGE_DELAY_MS);
                Document catalog = fetch(pageUrl);
                chapterList.addAll(parseChapterList(catalog));
                vip += countVipChapters(catalog);
                pageUrl = parseNextPageUrl(catalog);
            }
            if (vip > 0) {
                GrabberUtils.info(novel.window, vip + " chapters of this book are VIP chapters, so they are left out.");
            }
            if (chapterList.isEmpty() && vip == 0) {
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
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The first page of the book's catalog, linked from the book page, or null. */
    static String parseCatalogUrl(Document bookPage) {
        Element link = bookPage.selectFirst(".chapter-all a[href]");
        return link == null ? null : link.attr("abs:href");
    }

    /** Reads the free chapters on one catalog page, in order. VIP chapters (with a diamond icon) are left out. */
    static List<Chapter> parseChapterList(Document catalog) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : catalog.select(".catalog-chapter:not(:has(i)) a[href]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** The number of VIP chapters on one catalog page. */
    static int countVipChapters(Document catalog) {
        return catalog.select(".catalog-chapter:has(i)").size();
    }

    /** The catalog's next page ("下一页"), or null on the last page. */
    static String parseNextPageUrl(Document catalog) {
        Element next = catalog.selectFirst(".mod-page a[href]:containsOwn(下一页)");
        return next == null ? null : next.attr("abs:href");
    }

    /** Returns the chapter text, or null if the page has none (e.g. a VIP chapter). */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".reader-article");
    }

    /**
     * Reads title, author, briefing and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document bookPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = bookPage.selectFirst(".book-details-tit h1");
        // The side bar lists other books with their authors
        Element author = bookPage.selectFirst(".book-details-wrapper .writerName");
        Element description = bookPage.selectFirst(".book-details-briefing");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(bookPage.select(".breadCrumbNav a[href^=//]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document bookPage) {
        Element cover = bookPage.selectFirst(".bookBigCover img[src]");
        return cover == null ? null : cover.absUrl("src");
    }
}
