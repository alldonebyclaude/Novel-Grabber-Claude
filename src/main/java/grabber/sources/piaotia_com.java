package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Piaotian (飘天文学), formerly ptwxz.com. A book has an info page ({@code /bookinfo/<group>/<id>.html}) and a
 * chapter index ({@code /html/<group>/<id>/index.html}). The pages are GBK; jsoup reads the charset from the page.
 */
public class piaotia_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://www.piaotia.com";
    /** The book's group and id in any of its links, e.g. {@code /bookinfo/2/2072.html} or {@code /html/2/2072/}. */
    private static final Pattern BOOK_ID = Pattern.compile("/(?:bookinfo|html)/(\\d+)/(\\d+)(?:[/.]|$)");

    private final String name = "Piaotian";
    private final String url = "https://www.piaotia.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public piaotia_com(Novel novel) {
        this.novel = novel;
    }

    public piaotia_com() {
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
        String indexUrl = indexUrl(novel.novelLink);
        if (indexUrl == null) {
            GrabberUtils.err(novel.window, "Could not find the book in the link. Correct novel link?");
            return chapterList;
        }
        try {
            toc = fetch(bookPageUrl(novel.novelLink));
            Thread.sleep(1000);
            chapterList = parseChapterList(fetch(indexUrl));
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
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The book's info page for any link to the book, its index or a chapter (also on ptwxz.com), or null. */
    static String bookPageUrl(String link) {
        Matcher id = BOOK_ID.matcher(link);
        return id.find() ? BASE_URL + "/bookinfo/" + id.group(1) + "/" + id.group(2) + ".html" : null;
    }

    /** The book's chapter index for any link to the book, its index or a chapter (also on ptwxz.com), or null. */
    static String indexUrl(String link) {
        Matcher id = BOOK_ID.matcher(link);
        return id.find() ? BASE_URL + "/html/" + id.group(1) + "/" + id.group(2) + "/index.html" : null;
    }

    /** Reads the chapter index, in order. */
    static List<Chapter> parseChapterList(Document index) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : index.select(".centent li a[href]")) {
            if (link.text().isBlank()) continue;
            chapterList.add(new Chapter(link.text(), link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none. The text is loose in {@code <body>}: the lines and
     * line breaks after the top navigation's table, up to the bottom links.
     */
    static Element parseChapterBody(Document chapterPage) {
        Element topLinks = chapterPage.selectFirst("body > div.toplink");
        if (topLinks == null) return null;

        Element text = new Element("div");
        boolean started = false;
        for (Node node : new ArrayList<>(chapterPage.body().childNodes())) {
            if (!started) {
                // The text starts after the first table below the top links
                started = node instanceof Element element && element.normalName().equals("table")
                        && element.elementSiblingIndex() > topLinks.elementSiblingIndex();
                continue;
            }
            if (node instanceof Element element && !element.normalName().equals("br")) break;
            if (node instanceof TextNode || node instanceof Element) text.appendChild(node.clone());
        }
        return text.text().isBlank() ? null : text;
    }

    /**
     * Reads title, author and introduction. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document bookPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = bookPage.selectFirst("h1");
        Element author = bookPage.selectFirst("td:matchesOwn(^作\\s*者：)");
        // The introduction is the text next to its label, without the label and the links around it
        Element introduction = bookPage.selectFirst("div:has(> span:containsOwn(内容简介))");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.ownText().replaceFirst("^作\\s*者：", "").strip());
        if (introduction != null) metadata.setDescription(introduction.ownText());
        return metadata;
    }

    static String parseCoverUrl(Document bookPage) {
        Element cover = bookPage.selectFirst("img[src*=/files/article/image/]");
        return cover == null ? null : cover.absUrl("src");
    }
}
