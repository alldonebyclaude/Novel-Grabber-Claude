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
 * Betwixted Translations, a WordPress/Elementor site. The novel page lists the chapters in tabs.
 */
public class betwixtedbutterfly_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Betwixted Translations";
    private final String url = "https://betwixtedbutterfly.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public betwixtedbutterfly_com(Novel novel) {
        this.novel = novel;
    }

    public betwixtedbutterfly_com() {
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
        blacklistedTags.add("hr.wp-block-separator");
        blacklistedTags.add("h2");
        blacklistedTags.add("div.code-bloc");
        blacklistedTags.add("div.wp-block-columns");
        blacklistedTags.add("h3");
        blacklistedTags.add("nav");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the chapters from the tabs in page order. Some chapters are linked in two tabs; each is kept once.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element link : novelPage.select("div[id^=elementor-tab-content] a[href]")) {
            String chapterUrl = link.attr("abs:href");
            if (chapterUrl.isEmpty() || !seen.add(chapterUrl)) continue;
            chapterList.add(new Chapter(link.text(), chapterUrl));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("div.entry-inner");
    }

    /**
     * Reads title, description and tags. The description is the first text block without the "Author" details.
     * The cover is left to {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads
     * the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h2.elementor-heading-title");
        Element description = novelPage.selectFirst(".elementor-widget-text-editor:not(:contains(Author))");

        if (title != null) metadata.setTitle(title.text());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(novelPage.select("div.elementor-button-wrapper a").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".elementor-widget-image img");
        return cover == null ? null : cover.absUrl("src");
    }
}
