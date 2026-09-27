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

public class royalroad_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Royal Road";
    private final String url = "https://royalroad.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public royalroad_com(Novel novel) {
        this.novel = novel;
    }

    public royalroad_com() {
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
     * Reads the chapter table. Each row links the chapter twice (title and release date); the first cell's
     * link is the one with the title.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select("tr.chapter-row td:first-child a[href]")) {
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none. Royal Road hides an anti-piracy notice in each chapter
     * with a {@code display: none} rule for a random class; it is kept unless the user chooses to remove hidden text
     * (see {@link grabber.HiddenText}).
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".chapter-content");
    }

    /**
     * Reads title, author, description and tags. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h1");
        Element author = novelPage.selectFirst("h4 span a");
        Element description = novelPage.selectFirst(".description");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(novelPage.select(".tags a.fiction-tag").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("img.thumbnail");
        return cover == null ? null : cover.absUrl("src");
    }
}
