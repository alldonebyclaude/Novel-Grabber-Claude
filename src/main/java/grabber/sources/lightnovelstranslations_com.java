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
 * The novel page's "Table of Contents" tab lists every chapter by volume. Members-only chapters would be marked
 * {@code lock} instead of {@code unlock} and are left out.
 */
public class lightnovelstranslations_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Light Novels Translations";
    private final String url = "https://lightnovelstranslations.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public lightnovelstranslations_com(Novel novel) {
        this.novel = novel;
    }

    public lightnovelstranslations_com() {
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
        blacklistedTags.add("div.code-block");
        blacklistedTags.add(".sharedaddy");
        blacklistedTags.add("#textbox");
        // The chapter title, the translator credit and the buttons below the text
        blacklistedTags.add(".text_story > h2");
        blacklistedTags.add("p:has(> strong:matches((?i)translat))");
        blacklistedTags.add(".text_story > div.row");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the chapters, in order. The site sometimes lists a chapter twice (under two volumes); it is kept once.
     * Locked chapters are left out.
     */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element item : novelPage.select("li.chapter-item:not(.lock)")) {
            // The number before the link ("1:") is the place in the volume
            Element link = item.selectFirst("a[href]");
            if (link == null || link.text().isBlank()) continue;
            String chapterUrl = link.attr("abs:href");
            if (seen.add(chapterUrl)) chapterList.add(new Chapter(link.text(), chapterUrl));
        }
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".text_story");
    }

    /**
     * Reads title, author, the About text and tags. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("div.novel_title h3");
        Element author = novelPage.selectFirst("div.novel_detail_info li:has(> span:containsOwn(Author:))");
        Element description = novelPage.selectFirst("div.novel_text > p");

        if (title != null) metadata.setTitle(title.text());
        // "Author: <name>"
        if (author != null) metadata.setAuthor(author.text().replaceFirst("^Author:", "").strip());
        if (description != null) metadata.setDescription(description.text());
        metadata.setSubjects(novelPage.select("div.novel_tags_item > span").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".novel-image img");
        return cover == null ? null : cover.absUrl("src");
    }
}
