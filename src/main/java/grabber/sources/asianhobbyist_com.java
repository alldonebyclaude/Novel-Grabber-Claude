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

public class asianhobbyist_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Asian Hobbyist";
    private final String url = "https://www.asianhobbyist.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public asianhobbyist_com(Novel novel) {
        this.novel = novel;
    }

    public asianhobbyist_com() {
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
        blacklistedTags.add(".code-block");
        blacklistedTags.add(".themify_builder_content");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the release list, oldest first.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select(".releases-wrap a.cell[href]")) {
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".entry-content");
    }

    /**
     * Reads title and description. The cover is left to {@link #parseCoverUrl(Document)}, because setting it on
     * {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".details .entry-title");
        Element description = novelPage.selectFirst(".details .description");

        if (title != null) metadata.setTitle(title.text());
        if (description != null) metadata.setDescription(description.text());
        return metadata;
    }

    /**
     * The cover is lazy loaded: {@code src} holds an inline placeholder until the page's script swaps in the image
     * from {@code data-src} (older pages: {@code data-lazy-src}).
     */
    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".thumb img");
        if (cover == null) return null;
        for (String attribute : List.of("data-src", "data-lazy-src", "src")) {
            String coverUrl = cover.absUrl(attribute);
            if (coverUrl.startsWith("http")) return coverUrl;
        }
        return null;
    }
}
