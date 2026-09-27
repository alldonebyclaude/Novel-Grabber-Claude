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
 * Shōsetsuka ni Narō (syosetu.com). A serialized novel's page lists its episodes 100 at a time ({@code ?p=2} and
 * so on); a short story (短編) has no list, and its text is on the novel page itself.
 */
public class ncode_syosetu_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final long LIST_PAGE_DELAY_MS = 1000;

    private final String name = "Syosetu";
    private final String url = "https://ncode.syosetu.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public ncode_syosetu_com() {
    }

    public ncode_syosetu_com(Novel novel) {
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
        return parseMetadata(toc);
    }

    public List<String> getBlacklistedTags() {
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the episodes on one page of the table of contents, in order. A short story is one chapter: the novel
     * page itself.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select(".p-eplist a.p-eplist__subtitle[href]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        Element title = novelPage.selectFirst(".p-novel__title");
        if (chapterList.isEmpty() && title != null && parseChapterBody(novelPage) != null) {
            chapterList.add(new Chapter(title.text(), novelPage.location()));
        }
        return chapterList;
    }

    /** The next page of the table of contents, or null on the last page (where "next" is not a link). */
    static String parseNextPageUrl(Document novelPage) {
        Element next = novelPage.selectFirst("a.c-pager__item--next[href]");
        return next == null ? null : next.attr("abs:href");
    }

    /** Returns the text with its foreword and afterword, if any, or null if the page has none. */
    static Element parseChapterBody(Document page) {
        return page.selectFirst(".p-novel__body");
    }

    /** Reads title, author and summary. The site has no cover images. */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".p-novel__title");
        Element author = novelPage.selectFirst(".p-novel__author");
        Element summary = novelPage.selectFirst("#novel_ex");

        if (title != null) metadata.setTitle(title.text());
        // "作者：<name>", the name linked to the author's page when they have one
        if (author != null) {
            Element authorLink = author.selectFirst("a");
            metadata.setAuthor(authorLink != null ? authorLink.text() : author.text().replaceFirst("^作者[：:]", "").strip());
        }
        if (summary != null) metadata.setDescription(summary.text());
        return metadata;
    }
}
