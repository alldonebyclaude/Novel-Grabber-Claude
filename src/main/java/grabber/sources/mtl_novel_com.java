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
 * MTL-Novel (mtl-novel.com), machine-translated novels in English. The novel page lists every chapter, in groups of
 * 250, so one request gives the whole list.
 */
public class mtl_novel_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "MTL-Novel";
    private final String url = "https://mtl-novel.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public mtl_novel_com(Novel novel) {
        this.novel = novel;
    }

    public mtl_novel_com() {
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
            for (String warning : checkChapterList(chapterList)) {
                GrabberUtils.info(novel.window, "Warning: " + warning);
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

    /** Reads the chapter list in site order, from every group. Titles are the site's ("#12 Title"). */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select("div.ch-accordion a.ch-link[href]")) {
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** Warns about chapters the site lists more than once; the list itself is left as the site has it. */
    static List<String> checkChapterList(List<Chapter> chapterList) {
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (Chapter chapter : chapterList) {
            if (!seen.add(chapter.chapterURL) && reported.add(chapter.chapterURL)) {
                warnings.add("the site lists this chapter more than once: " + chapter.chapterURL);
            }
        }
        return warnings;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("div#chapter-par");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h1");
        Element author = novelPage.selectFirst("a[href*=/novel-author/]");
        Element description = novelPage.selectFirst("div.desc");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(novelPage.select("a[href*=/genre/]").eachText());
        return metadata;
    }

    /** The novel's own cover; the related novels further down the page have covers of their own. */
    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("div.nov-head img.wp-post-image");
        return cover == null ? null : cover.absUrl("src");
    }
}
