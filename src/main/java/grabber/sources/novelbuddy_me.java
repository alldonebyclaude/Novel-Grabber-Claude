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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * novelbuddy.me (formerly novelbuddy.com) is a Next.js site. The novel page carries the novel's data in its
 * {@code __NEXT_DATA__} script, but only the latest chapters; the full list comes from the site's API.
 */
public class novelbuddy_me implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://novelbuddy.me";
    private static final String API_URL = "https://api.novelbuddy.me";

    private final String name = "NovelBuddy";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public novelbuddy_me() {
    }

    public novelbuddy_me(Novel novel) {
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
            toc = fetch(novel.novelLink).get();
            String titleId = parseTitleId(toc);
            if (titleId == null) {
                GrabberUtils.err(novel.window, "Could not find the novel on the page. Correct novel link?");
                return chapterList;
            }
            String listUrl = chapterListUrl(titleId, parseCacheVersion(toc));
            chapterList = parseChapterList(fetch(listUrl).ignoreContentType(true).execute().body());
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list from " + listUrl);
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
        if (toc == null) return new NovelMetadata();

        NovelMetadata metadata = parseMetadata(toc);
        String coverUrl = parseCoverUrl(toc);
        if (coverUrl != null) metadata.setBufferedCover(coverUrl);
        return metadata;
    }

    public List<String> getBlacklistedTags() {
        List<String> blacklistedTags = new ArrayList<>();
        // Empty ad slots between the parts of the text
        blacklistedTags.add("div.my-4");
        // The chapter title, repeated at the top of the text
        blacklistedTags.add("h3");
        return blacklistedTags;
    }

    private Connection fetch(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies);
    }

    /**
     * Returns the novel ("title") object from the page's {@code __NEXT_DATA__} script, or null.
     */
    private static JSONObject parseNovelData(Document novelPage) {
        Element script = novelPage.selectFirst("script#__NEXT_DATA__");
        if (script == null) return null;
        try {
            JSONObject data = (JSONObject) new JSONParser().parse(script.data());
            JSONObject props = (JSONObject) data.get("props");
            JSONObject pageProps = props == null ? null : (JSONObject) props.get("pageProps");
            return pageProps == null ? null : (JSONObject) pageProps.get("initialManga");
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    private static String stringField(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value == null ? null : value.toString();
    }

    /** The novel's API id, e.g. {@code eDk1Rg8Q}, or null. */
    static String parseTitleId(Document novelPage) {
        return stringField(parseNovelData(novelPage), "id");
    }

    /** The cache version of the novel's data. The API returns an outdated chapter list without it. */
    static String parseCacheVersion(Document novelPage) {
        return stringField(parseNovelData(novelPage), "cv");
    }

    static String chapterListUrl(String titleId, String cacheVersion) {
        String listUrl = API_URL + "/titles/" + titleId + "/chapters";
        return cacheVersion == null ? listUrl : listUrl + "?cv=" + cacheVersion;
    }

    /**
     * Reads the API's chapter list, which is newest first, and returns it oldest first. Returns an empty list
     * for an error response or anything that is not the expected JSON.
     */
    static List<Chapter> parseChapterList(String json) {
        List<Chapter> chapterList = new ArrayList<>();
        try {
            JSONObject response = (JSONObject) new JSONParser().parse(json);
            JSONObject data = (JSONObject) response.get("data");
            JSONArray chapters = data == null ? null : (JSONArray) data.get("chapters");
            if (chapters == null) return chapterList;
            for (Object entry : chapters) {
                JSONObject chapter = (JSONObject) entry;
                String chapterName = stringField(chapter, "name");
                String chapterPath = stringField(chapter, "url");
                if (chapterName == null || chapterPath == null) continue;
                chapterList.add(new Chapter(chapterName, BASE_URL + chapterPath));
            }
        } catch (ParseException | ClassCastException e) {
            return new ArrayList<>();
        }
        Collections.reverse(chapterList);
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".novel-tts-content");
    }

    /**
     * Reads title, authors, description and genres from the page's data. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        JSONObject novelData = parseNovelData(novelPage);
        if (novelData == null) return metadata;

        String title = stringField(novelData, "name");
        String summary = stringField(novelData, "summary");
        if (title != null) metadata.setTitle(title);
        if (summary != null) metadata.setDescription(summary.strip());
        // The same person is often listed as author and artist
        Set<String> authors = names(novelData.get("authors"));
        if (!authors.isEmpty()) metadata.setAuthor(String.join(", ", authors));
        metadata.setSubjects(new ArrayList<>(names(novelData.get("genres"))));
        return metadata;
    }

    private static Set<String> names(Object list) {
        Set<String> names = new LinkedHashSet<>();
        if (list instanceof JSONArray array) {
            for (Object entry : array) {
                if (entry instanceof JSONObject object && object.get("name") != null) {
                    names.add(object.get("name").toString());
                }
            }
        }
        return names;
    }

    static String parseCoverUrl(Document novelPage) {
        return stringField(parseNovelData(novelPage), "cover");
    }
}
