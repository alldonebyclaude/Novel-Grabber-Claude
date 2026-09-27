package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Connection;
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
 * fujossy.jp. A book's chapters ("stories") and details come from {@code api/books/<id>.json}; the chapter text is
 * written with plain line breaks.
 */
public class fujossy_jp implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://fujossy.jp";
    private static final Pattern BOOK_ID = Pattern.compile("/books/(\\d+)");

    private final String name = "fujossy";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private String bookJson;

    public fujossy_jp(Novel novel) {
        this.novel = novel;
    }

    public fujossy_jp() {
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
        String bookId = parseBookId(novel.novelLink);
        if (bookId == null) {
            GrabberUtils.err(novel.window, "Not a fujossy book link: " + novel.novelLink);
            return chapterList;
        }
        try {
            bookJson = fetch(bookApiUrl(bookId)).ignoreContentType(true).execute().body();
            chapterList = parseChapterList(bookJson, bookId);
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list of book " + bookId);
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
            chapterBody = parseChapterBody(fetch(chapter.chapterURL).get());
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterBody;
    }

    public NovelMetadata getMetadata() {
        if (bookJson == null) return new NovelMetadata();

        NovelMetadata metadata = parseMetadata(bookJson);
        String coverUrl = parseCoverUrl(bookJson);
        if (coverUrl != null) metadata.setBufferedCover(coverUrl);
        return metadata;
    }

    public List<String> getBlacklistedTags() {
        return new ArrayList<>();
    }

    private Connection fetch(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies);
    }

    /** The book id in a book or chapter link, e.g. {@code 24584}, or null. */
    static String parseBookId(String link) {
        Matcher bookId = BOOK_ID.matcher(link == null ? "" : link);
        return bookId.find() ? bookId.group(1) : null;
    }

    static String bookApiUrl(String bookId) {
        return BASE_URL + "/api/books/" + bookId + ".json";
    }

    private static JSONObject parseBook(String json) {
        try {
            Object book = ((JSONObject) new JSONParser().parse(json)).get("book");
            return book instanceof JSONObject bookObject ? bookObject : null;
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    private static String stringField(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Reads the book's chapters from the book API's answer, in order. Empty for anything that is not the expected
     * JSON.
     */
    static List<Chapter> parseChapterList(String json, String bookId) {
        List<Chapter> chapterList = new ArrayList<>();
        JSONObject book = parseBook(json);
        if (book == null || !(book.get("stories") instanceof JSONArray stories)) return chapterList;
        for (Object entry : stories) {
            if (!(entry instanceof JSONObject story) || story.get("id") == null) continue;
            chapterList.add(new Chapter(String.valueOf(story.get("title")),
                    BASE_URL + "/books/" + bookId + "/stories/" + story.get("id")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text with its line breaks as {@code <br>} (the site writes plain line breaks, which an
     * EPUB would join into one line), or null if the page has no text.
     */
    static Element parseChapterBody(Document chapterPage) {
        Element text = chapterPage.selectFirst(".story__body");
        if (text == null) return null;
        chapterPage.outputSettings().prettyPrint(false);
        return new Element("div").html(text.html().replace("\n", "<br>"));
    }

    /**
     * Reads title, author, description and tags from the book API's answer. The cover is left to
     * {@link #parseCoverUrl(String)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(String json) {
        NovelMetadata metadata = new NovelMetadata();
        JSONObject book = parseBook(json);
        if (book == null) return metadata;

        String title = stringField(book, "title");
        String description = stringField(book, "description");
        if (title != null) metadata.setTitle(title);
        if (book.get("user") instanceof JSONObject user && user.get("display_name") != null) {
            metadata.setAuthor(user.get("display_name").toString());
        }
        if (description != null) metadata.setDescription(description.strip());
        List<String> subjects = new ArrayList<>();
        if (book.get("tag_names") instanceof JSONArray tags) {
            for (Object tag : tags) {
                if (tag instanceof JSONObject tagObject && tagObject.get("name") != null) subjects.add(tagObject.get("name").toString());
            }
        }
        metadata.setSubjects(subjects);
        return metadata;
    }

    static String parseCoverUrl(String json) {
        JSONObject book = parseBook(json);
        return book != null && book.get("cover") instanceof JSONObject cover ? stringField(cover, "url") : null;
    }
}
