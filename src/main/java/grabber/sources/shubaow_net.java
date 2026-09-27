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
 * The book page ({@code /book/<id>.html}) has the details and the full chapter directory; chapter pages are
 * {@code /book/<id>/<chapter>.html}. The pages are GBK; jsoup reads the charset from the page. The site can be
 * slow, so requests wait up to a minute.
 */
public class shubaow_net implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "shubaow";
    private final String url = "https://www.shubaow.net/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public shubaow_net(Novel novel) {
        this.novel = novel;
    }

    public shubaow_net() {
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
        return parseMetadata(toc);
    }

    public List<String> getBlacklistedTags() {
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).timeout(60 * 1000).get();
    }

    /** Reads the chapter directory, in order. The "latest chapter" link above it is not part of the list. */
    static List<Chapter> parseChapterList(Document bookPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : bookPage.select("#list-chapterAll a.chapter-item-link[href]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Returns the chapter text (lines separated by line breaks), or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#htmlContent");
    }

    /** Reads title, author and description. The site shows no cover. */
    static NovelMetadata parseMetadata(Document bookPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = bookPage.selectFirst("h1.book-title-meta");
        Element author = bookPage.selectFirst(".book-status-tags span:matchesOwn(^作者：)");
        Element description = bookPage.selectFirst(".book-description-text");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text().replaceFirst("^作者：", "").strip());
        if (description != null) metadata.setDescription(description.text());
        return metadata;
    }
}
