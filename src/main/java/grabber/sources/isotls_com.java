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
 * ISO Translations, now at www.isotls.com. The novel page lists all chapters; its synopsis section starts with
 * an "Author: ..." line.
 */
public class isotls_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String AUTHOR_PREFIX = "Author:";

    private final String name = "ISO Translations";
    private final String url = "https://www.isotls.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public isotls_com(Novel novel) {
        this.novel = novel;
    }

    public isotls_com() {
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
     * Reads the novel's chapter list, in page order (oldest first). The page also links the latest chapters of
     * other novels; those are not list items with a {@code data-title}.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element item : novelPage.select("li.content-list-item[data-title]")) {
            Element link = item.selectFirst("a.content-item-anchor[href]");
            String title = item.attr("data-title").strip();
            if (link == null || title.isEmpty()) continue;
            chapterList.add(new Chapter(title, link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, including translator footnotes, or null if the page has none. The article around
     * it also holds the title, a notice, navigation, an ad slot and a footer.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("article .content.flow-md");
    }

    /**
     * Reads title, author and description. The site shows no genres. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        // The og:title tag has its apostrophes escaped twice (Won&amp;#39;t); the heading has the plain title
        Element title = novelPage.selectFirst("h1");
        Element ogTitle = novelPage.selectFirst("meta[property=og:title]");
        if (title != null) metadata.setTitle(title.text());
        else if (ogTitle != null) metadata.setTitle(ogTitle.attr("content").strip());

        Element synopsis = novelPage.selectFirst("h2:containsOwn(Synopsis) + div");
        if (synopsis != null) {
            List<String> paragraphs = new ArrayList<>();
            for (Element paragraph : synopsis.select("p")) {
                String text = paragraph.text().strip();
                if (text.isEmpty()) continue;
                if (text.startsWith(AUTHOR_PREFIX)) {
                    metadata.setAuthor(text.substring(AUTHOR_PREFIX.length()).strip());
                } else {
                    paragraphs.add(text);
                }
            }
            metadata.setDescription(String.join("\n", paragraphs));
        }
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("img.novel-cover");
        return cover == null ? null : cover.absUrl("src");
    }
}
