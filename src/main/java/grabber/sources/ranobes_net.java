package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The novel page ({@code /novels/<id>-<slug>.html}) has the details; the table of contents
 * ({@code /chapters/<id>/}, then {@code page/2/} and so on) renders from its {@code window.__DATA__}, 25 chapters
 * per page, newest first.
 */
public class ranobes_net implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final long LIST_PAGE_DELAY_MS = 1000;
    private static final Pattern NOVEL_ID = Pattern.compile("ranobes\\.net/(?:novels|chapters)/(\\d+)");
    private static final String DATA_PREFIX = "window.__DATA__";

    private final String name = "Ranobes";
    private final String url = "https://ranobes.net/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public ranobes_net() {
    }

    public ranobes_net(Novel novel) {
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
        String tocUrl = tocUrl(novel.novelLink);
        if (tocUrl == null) {
            GrabberUtils.err(novel.window, "Could not find the novel in the link. Correct novel link?");
            return chapterList;
        }
        try {
            toc = fetch(novel.novelLink);
            List<List<Chapter>> pages = new ArrayList<>();
            Thread.sleep(LIST_PAGE_DELAY_MS);
            Document firstPage = fetch(tocUrl);
            pages.add(parseChapterListPage(firstPage));
            int pageCount = parsePageCount(firstPage);
            for (int page = 2; page <= pageCount; page++) {
                Thread.sleep(LIST_PAGE_DELAY_MS);
                pages.add(parseChapterListPage(fetch(tocPageUrl(tocUrl, page))));
            }
            chapterList = oldestFirst(pages);
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
        blacklistedTags.add("div[align=center]");
        // Ad slots inside the text
        blacklistedTags.add("script");
        blacklistedTags.add(".free-support-top");
        blacklistedTags.add("[id^=bg-ssp]");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The table of contents for a link to the novel or its table of contents, or null. */
    static String tocUrl(String link) {
        Matcher id = NOVEL_ID.matcher(link);
        return id.find() ? "https://ranobes.net/chapters/" + id.group(1) + "/" : null;
    }

    static String tocPageUrl(String tocUrl, int page) {
        return page <= 1 ? tocUrl : tocUrl + "page/" + page + "/";
    }

    /** The page's {@code window.__DATA__} object, or null. */
    private static JSONObject pageData(Document tocPage) {
        for (Element script : tocPage.select("script:not([src])")) {
            String data = script.data().strip();
            if (!data.startsWith(DATA_PREFIX)) continue;
            String json = data.substring(data.indexOf('=') + 1).strip();
            if (json.endsWith(";")) json = json.substring(0, json.length() - 1);
            try {
                return (JSONObject) new JSONParser().parse(json);
            } catch (ParseException | ClassCastException e) {
                return null;
            }
        }
        return null;
    }

    /** The number of table of contents pages, or 0 if the page has no data. */
    static int parsePageCount(Document tocPage) {
        JSONObject data = pageData(tocPage);
        Object pages = data == null ? null : data.get("pages_count");
        return pages instanceof Number number ? number.intValue() : 0;
    }

    /** Reads the chapters on one table of contents page, newest first as the site lists them. */
    static List<Chapter> parseChapterListPage(Document tocPage) {
        List<Chapter> chapterList = new ArrayList<>();
        JSONObject data = pageData(tocPage);
        if (data == null || !(data.get("chapters") instanceof JSONArray chapters)) return chapterList;
        for (Object item : chapters) {
            if (!(item instanceof JSONObject chapter)) continue;
            Object title = chapter.get("title");
            Object link = chapter.get("link");
            if (title == null || link == null) continue;
            chapterList.add(new Chapter(title.toString(), link.toString()));
        }
        return chapterList;
    }

    /** Joins the table of contents pages (page 1 first, each newest first) into one list, oldest first. */
    static List<Chapter> oldestFirst(List<List<Chapter>> pages) {
        List<Chapter> chapterList = new ArrayList<>();
        for (List<Chapter> page : pages) chapterList.addAll(page);
        Collections.reverse(chapterList);
        return chapterList;
    }

    /** Returns the chapter text, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#arrticle");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("meta[property=og:title]");
        Element author = novelPage.selectFirst("a[href*=/tags/authors/]");
        Element description = novelPage.selectFirst(".moreless__full");

        if (title != null) metadata.setTitle(title.attr("content"));
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        // The novel's genres; the site menu links genres too
        metadata.setSubjects(novelPage.select("div.links a[href*=/tags/genre/]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.absUrl("content");
    }
}
