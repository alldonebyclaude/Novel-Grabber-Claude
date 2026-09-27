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

/**
 * WoopRead's series pages ({@code /series/<slug>}) are rendered by Next.js. The page shows only a few chapters; the
 * whole list is in its React Server Components payload (the {@code self.__next_f.push(...)} scripts). A chapter's
 * page is {@code /series/<slug>/<chapter slug>}.
 */
public class woopread_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String RSC_PUSH_PREFIX = "self.__next_f.push(";

    private final String name = "WoopRead";
    private final String url = "https://woopread.com/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public woopread_com(Novel novel) {
        this.novel = novel;
    }

    public woopread_com() {
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
        blacklistedTags.add(".adbox");
        // Each paragraph has a button next to it
        blacklistedTags.add("button");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** The series page's own URL, without a trailing slash, query or fragment. */
    private static String seriesUrl(Document seriesPage) {
        String location = seriesPage.location().replaceAll("[?#].*$", "");
        return location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
    }

    /** Reads the whole chapter list from the page's RSC payload, in order. Empty if the page has none. */
    static List<Chapter> parseChapterList(Document seriesPage) {
        List<Chapter> chapterList = new ArrayList<>();
        JSONArray chapters = findJsonArray(readRscPayload(seriesPage), "\"chapters\":[");
        if (chapters == null) return chapterList;

        String seriesUrl = seriesUrl(seriesPage);
        for (Object item : chapters) {
            if (!(item instanceof JSONObject chapter)) continue;
            Object title = chapter.get("title");
            Object slug = chapter.get("slug");
            if (title == null || slug == null) continue;
            chapterList.add(new Chapter(title.toString(), seriesUrl + "/" + slug));
        }
        return chapterList;
    }

    /**
     * Returns the chapter's paragraphs, or null if the page has none. The page streams them into a hidden
     * placeholder that its script then shows, so they are read from there.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("div[id^=chapter-] div.space-y-4");
    }

    /**
     * Reads title, author, tagline and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document seriesPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = seriesPage.selectFirst("h1");
        Element authorLabel = seriesPage.selectFirst("span:containsOwn(Author:)");
        Element tagline = seriesPage.selectFirst("meta[property=og:description]");

        if (title != null) metadata.setTitle(title.text());
        if (authorLabel != null && authorLabel.nextElementSibling() != null) {
            metadata.setAuthor(authorLabel.nextElementSibling().text());
        }
        if (tagline != null) metadata.setDescription(tagline.attr("content"));
        metadata.setSubjects(seriesPage.select("a[href*=/browse?genres=]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document seriesPage) {
        Element cover = seriesPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.absUrl("content");
    }

    /** Joins the string chunks of all {@code self.__next_f.push([1, "..."])} scripts into the RSC payload. */
    private static String readRscPayload(Document page) {
        StringBuilder payload = new StringBuilder();
        JSONParser parser = new JSONParser();
        for (Element script : page.select("script")) {
            String data = script.data().strip();
            if (!data.startsWith(RSC_PUSH_PREFIX) || !data.endsWith(")")) continue;
            try {
                Object chunk = parser.parse(data.substring(RSC_PUSH_PREFIX.length(), data.length() - 1));
                if (chunk instanceof JSONArray array && array.size() > 1 && array.get(1) instanceof String s) {
                    payload.append(s);
                }
            } catch (ParseException e) {
                // Not a data chunk; the payload also carries module references we don't need.
            }
        }
        return payload.toString();
    }

    /** Finds {@code marker} (which must end with '[') in {@code json} and parses the array that follows it. */
    private static JSONArray findJsonArray(String json, String marker) {
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length() - 1;

        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && --depth == 0) {
                try {
                    return (JSONArray) new JSONParser().parse(json.substring(start, i + 1));
                } catch (ParseException | ClassCastException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
