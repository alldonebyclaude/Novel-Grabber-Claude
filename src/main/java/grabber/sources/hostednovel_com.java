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
 * The novel page lists the chapters 100 at a time, oldest first; further pages are {@code ?page=2} and so on.
 */
public class hostednovel_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final long LIST_PAGE_DELAY_MS = 1000;

    private final String name = "Hosted Novel";
    private final String url = "https://hostednovel.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public hostednovel_com() {
    }

    public hostednovel_com(Novel novel) {
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
            Document listPage = toc;
            Set<String> visited = new HashSet<>();
            while (true) {
                chapterList.addAll(parseChapterList(listPage));
                String nextPage = parseNextPageUrl(listPage);
                if (nextPage == null || !visited.add(nextPage)) break;
                Thread.sleep(LIST_PAGE_DELAY_MS);
                listPage = fetch(nextPage);
            }
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        blacklistedTags.add("[id^=ezoic-pub-ad]");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the chapters listed on one page of the novel's chapter list, in order. */
    static List<Chapter> parseChapterList(Document listPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : listPage.select("#chapters li > a[href]")) {
            // The title is followed by the chapter's age, e.g. "5 years ago"
            Element title = link.selectFirst("div");
            String name = title != null ? title.ownText() : link.text();
            if (name.isBlank()) continue;
            chapterList.add(new Chapter(name.strip(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /** The next page of the chapter list, or null on the last page. */
    static String parseNextPageUrl(Document listPage) {
        Element next = listPage.selectFirst("a[href]:has(span.sr-only:containsOwn(Next))");
        if (next == null) return null;
        String nextUrl = next.attr("abs:href");
        int fragment = nextUrl.indexOf('#');
        return fragment < 0 ? nextUrl : nextUrl.substring(0, fragment);
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#chapter-content");
    }

    /**
     * Reads title, author, synopsis and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h1");
        Element author = details(novelPage, "Author:");
        Element genres = details(novelPage, "Genres:");
        Element synopsis = novelPage.selectFirst("div:has(> h3 > span:containsOwn(Synopsis)) + div.prose");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (synopsis != null) metadata.setDescription(synopsis.text());
        if (genres != null) metadata.setSubjects(genres.children().eachText());
        return metadata;
    }

    /** The value next to a label in the novel's details list, e.g. "Author:". */
    private static Element details(Document novelPage, String label) {
        Element term = novelPage.selectFirst("dt:containsOwn(" + label + ")");
        return term == null ? null : term.nextElementSibling();
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("img[src*=/covers/]");
        return cover == null ? null : cover.absUrl("src");
    }
}
