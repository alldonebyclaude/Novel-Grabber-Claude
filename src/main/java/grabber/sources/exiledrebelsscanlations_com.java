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
 * Exiled Rebels Scanlations, a WordPress site. The novel page lists the chapters, and its text holds the author
 * ("by ...") and the summary between "SUMMARY:" and "CHAPTERS:" (og:description is empty).
 */
public class exiledrebelsscanlations_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Exiled Rebels Scanlations";
    private final String url = "https://exiledrebelsscanlations.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public exiledrebelsscanlations_com(Novel novel) {
        this.novel = novel;
    }

    public exiledrebelsscanlations_com() {
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
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the chapter list on the novel page, in page order.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select(".entry-content .lcp_catlist a[href]")) {
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#wtr-content");
    }

    /**
     * Reads title, author and summary. The cover is left to {@link #parseCoverUrl(Document)}, because setting it on
     * {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("meta[property=og:title]");
        Element byline = novelPage.selectFirst(".entry-content h1");
        if (title != null) metadata.setTitle(title.attr("content").strip());
        if (byline != null && byline.text().startsWith("by ")) metadata.setAuthor(byline.text().substring(3).strip());

        // The summary: the paragraphs after "SUMMARY:" up to "CHAPTERS:" (or the chapter list)
        List<String> summary = new ArrayList<>();
        boolean inSummary = false;
        for (Element element : novelPage.select(".entry-content > *")) {
            String text = element.text().strip();
            if (text.equalsIgnoreCase("SUMMARY:")) {
                inSummary = true;
            } else if (text.equalsIgnoreCase("CHAPTERS:") || element.hasClass("lcp_catlist")) {
                if (inSummary) break;
            } else if (inSummary && !text.isEmpty()) {
                summary.add(text);
            }
        }
        metadata.setDescription(String.join("\n", summary));
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.attr("content");
    }
}
