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
 * readnovelfull.com. The novel page shows part of the chapter list; the complete list comes from
 * {@code ajax/chapter-archive?novelId=<id>}.
 */
public class readnovelfull_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://readnovelfull.com";

    private final String name = "ReadNovelFull";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public readnovelfull_com(Novel novel) {
        this.novel = novel;
    }

    public readnovelfull_com() {
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
            String novelId = parseNovelId(toc);
            if (novelId == null) {
                GrabberUtils.err(novel.window, "Could not find the novel id. Correct novel link?");
                return chapterList;
            }
            chapterList = parseChapterList(fetch(chapterListUrl(novelId)));
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list of novel " + novelId);
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
        blacklistedTags.add("ads");
        blacklistedTags.add("div[align=left]");
        blacklistedTags.add(".adsbygoogle");
        blacklistedTags.add(".cha-tit p");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The site's id of the novel, e.g. {@code 2454}, or null. */
    static String parseNovelId(Document novelPage) {
        Element rating = novelPage.selectFirst("#rating[data-novel-id]");
        return rating == null || rating.attr("data-novel-id").isBlank() ? null : rating.attr("data-novel-id");
    }

    static String chapterListUrl(String novelId) {
        return BASE_URL + "/ajax/chapter-archive?novelId=" + novelId;
    }

    /**
     * Reads the chapter archive, oldest first.
     */
    static List<Chapter> parseChapterList(Document chapterArchive) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : chapterArchive.select(".list-chapter a[href]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#chr-content");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h3.title");
        Element author = novelPage.selectFirst(".info.info-meta li:contains(Author) a");
        Element description = novelPage.selectFirst(".desc-text");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(novelPage.select(".info.info-meta li:contains(Genre) a").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".book > img");
        return cover == null ? null : cover.absUrl("src");
    }
}
