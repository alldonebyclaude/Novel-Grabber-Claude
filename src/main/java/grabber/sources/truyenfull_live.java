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
 * truyenfull.live (formerly truyenfull.vn). The novel page shows 50 chapters per page; the complete list
 * comes from the chapter-jump menu, which the site loads with {@code ajax.php?type=chapter_option}.
 */
public class truyenfull_live implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://truyenfull.live";

    private final String name = "Truyen Full";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public truyenfull_live() {
    }

    public truyenfull_live(Novel novel) {
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
            String storyId = parseStoryId(toc);
            if (storyId == null) {
                GrabberUtils.err(novel.window, "Could not find the story id. Correct novel link?");
                return chapterList;
            }
            chapterList = parseChapterList(fetch(chapterListUrl(storyId)), novel.novelLink);
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list of story " + storyId);
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
        // Ad slots inside the text
        blacklistedTags.add("#ads-chapter-top");
        blacklistedTags.add(".ads-responsive");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The site's id of the novel, e.g. {@code 45202}, or null. */
    static String parseStoryId(Document novelPage) {
        Element storyId = novelPage.selectFirst("#truyen-id");
        return storyId == null || storyId.val().isBlank() ? null : storyId.val();
    }

    static String chapterListUrl(String storyId) {
        return BASE_URL + "/ajax.php?type=chapter_option&data=" + storyId;
    }

    /**
     * Reads the chapter-jump menu: {@code <option value="chuong-1">title</option>}, oldest first. The chapter
     * URL is the novel URL plus the value.
     */
    static List<Chapter> parseChapterList(Document chapterMenu, String novelUrl) {
        List<Chapter> chapterList = new ArrayList<>();
        String base = novelUrl.endsWith("/") ? novelUrl : novelUrl + "/";
        for (Element option : chapterMenu.select("option[value]")) {
            String chapterPath = option.val().strip();
            if (chapterPath.isEmpty() || option.text().isBlank()) continue;
            chapterList.add(new Chapter(option.text(), base + chapterPath + "/"));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#chapter-c");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h3.title");
        Element author = novelPage.selectFirst("a[itemprop=author]");
        Element description = novelPage.selectFirst(".desc-text");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        // Scoped to the novel's info box; the page's genre menu uses the same markup
        metadata.setSubjects(novelPage.select(".info a[itemprop=genre]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".book img");
        return cover == null ? null : cover.absUrl("src");
    }
}
