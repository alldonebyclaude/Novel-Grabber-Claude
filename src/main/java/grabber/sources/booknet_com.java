package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.PaywallSite;
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
 * BookNet sells chapters. The book page lists every chapter and marks the ones the visitor cannot read as locked;
 * with the user's own login cookies, chapters they bought are unlocked. Locked chapters are left out.
 */
@PaywallSite("Chapters you haven't bought are locked and left out. Add your BookNet login in the account settings "
        + "to download the chapters you bought.")
public class booknet_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "BookNet";
    private final String url = "https://booknet.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public booknet_com(Novel novel) {
        this.novel = novel;
    }

    public booknet_com() {
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
            int locked = countLockedChapters(toc);
            if (locked > 0) {
                GrabberUtils.info(novel.window, locked + " chapters of this book are paid and locked, so they are left out. "
                        + "To download chapters you bought, add your BookNet login in the account settings.");
            }
            if (chapterList.isEmpty() && locked == 0) {
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
        blacklistedTags.add(".reader-pagination");
        blacklistedTags.add(".clearfix");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the chapters the visitor can open, in order. Locked (paid) chapters are left out.
     */
    static List<Chapter> parseChapterList(Document bookPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element item : bookPage.select(".bn_book__chapters-item:not(.lock)")) {
            Element link = item.selectFirst("a.bn_book__chapters-item-link[href]");
            Element title = item.selectFirst(".bn_book__chapters-item-title");
            if (link == null || title == null || title.text().isBlank()) continue;
            chapterList.add(new Chapter(title.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** The number of chapters the page marks as locked for this visitor. */
    static int countLockedChapters(Document bookPage) {
        return bookPage.select(".bn_book__chapters-item.lock").size();
    }

    /**
     * Returns the chapter text, or null if the page has none (e.g. a locked chapter).
     */
    static Element parseChapterBody(Document readerPage) {
        return readerPage.selectFirst(".reader-text");
    }

    /**
     * Reads title, author, description, genre and tags. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document bookPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = bookPage.selectFirst("h1.bn_book__header-title");
        Element author = bookPage.selectFirst(".bn_book__header-author-item-name");
        Element description = bookPage.selectFirst(".bn_book__about-content");

        if (title != null) metadata.setTitle(title.text());
        // The name is followed by the person's role, e.g. "Anna Románova · autor"
        if (author != null) metadata.setAuthor(author.text().split(" · ")[0].strip());
        if (description != null) metadata.setDescription(description.text());
        List<String> subjects = new ArrayList<>(bookPage.select("a.bn_book__header-genre").eachText());
        subjects.addAll(bookPage.select(".bn_book__tags a").eachText());
        metadata.setSubjects(subjects);
        return metadata;
    }

    static String parseCoverUrl(Document bookPage) {
        Element cover = bookPage.selectFirst("img.bn_book__header-image-src");
        return cover == null ? null : cover.absUrl("src");
    }
}
