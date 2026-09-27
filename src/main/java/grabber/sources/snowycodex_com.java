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
 * The novel page ({@code /novels/<slug>/}) has the title, author, summary and a table of contents linking the
 * chapters ({@code /novels/<slug>/chapter-N/}).
 */
public class snowycodex_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Snowy Codex";
    private final String url = "https://snowycodex.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public snowycodex_com(Novel novel) {
        this.novel = novel;
    }

    public snowycodex_com() {
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
        blacklistedTags.add("p:has(a:contains(Table of Content))");
        blacklistedTags.add(".wpulike");
        blacklistedTags.add(".sharedaddy");
        // The chapter title above the text
        blacklistedTags.add(".entry-content > h2");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the table of contents: the novel page's links to its own chapters, in order. */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        String novelUrl = novelPage.location().endsWith("/") ? novelPage.location() : novelPage.location() + "/";
        for (Element link : novelPage.select(".entry-content p a[href]")) {
            String chapterUrl = link.attr("abs:href");
            if (!chapterUrl.startsWith(novelUrl) || chapterUrl.equals(novelUrl) || link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), chapterUrl));
        }
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".entry-content");
    }

    /**
     * Reads title, author and summary (the paragraphs after "Summary:" up to the separator above the table of
     * contents). The cover is left to {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata}
     * downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".entry-content > h2");
        // The labels are in <strong>: "<strong>Author:</strong> Mo Jing"
        Element author = novelPage.selectFirst(".entry-content > p:matches(^\\s*Author:)");
        Element summaryLabel = novelPage.selectFirst(".entry-content > p:matches(^\\s*Summary:\\s*$)");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text().replaceFirst("^\\s*Author:", "").strip());
        if (summaryLabel != null) {
            List<String> paragraphs = new ArrayList<>();
            for (Element next = summaryLabel.nextElementSibling(); next != null && next.normalName().equals("p");
                 next = next.nextElementSibling()) {
                if (!next.text().isBlank()) paragraphs.add(next.text());
            }
            metadata.setDescription(String.join("\n", paragraphs));
        }
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.absUrl("content");
    }
}
