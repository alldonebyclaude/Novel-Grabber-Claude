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
 * The novel page is rendered by Next.js and shows only a few episodes. The whole table of contents is in the
 * page's {@code __NEXT_DATA__} (its Apollo state); the episode pages are plain HTML.
 */
public class kakuyomu_jp implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "Kakuyomu";
    private final String url = "https://kakuyomu.jp/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public kakuyomu_jp() {
    }

    public kakuyomu_jp(Novel novel) {
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

    /** The page's Apollo state: objects by key, such as {@code Work:<id>} and {@code Episode:<id>}. Null if missing. */
    private static JSONObject apolloState(Document novelPage) {
        Element script = novelPage.selectFirst("script#__NEXT_DATA__");
        if (script == null) return null;
        try {
            JSONObject data = (JSONObject) new JSONParser().parse(script.data());
            JSONObject props = (JSONObject) data.get("props");
            JSONObject pageProps = props == null ? null : (JSONObject) props.get("pageProps");
            return pageProps == null ? null : (JSONObject) pageProps.get("__APOLLO_STATE__");
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    /** Follows a {@code {"__ref": "Type:id"}} link to the object it points to. */
    private static JSONObject resolve(JSONObject state, Object ref) {
        if (!(ref instanceof JSONObject link) || !(link.get("__ref") instanceof String key)) return null;
        return state.get(key) instanceof JSONObject object ? object : null;
    }

    /**
     * The page's own work. The state also holds recommended works, so this follows the page's query
     * ({@code work({"id":"..."})}) rather than taking the first work.
     */
    private static JSONObject work(JSONObject state) {
        if (state == null || !(state.get("ROOT_QUERY") instanceof JSONObject rootQuery)) return null;
        for (Object key : rootQuery.keySet()) {
            if (key.toString().startsWith("work(")) return resolve(state, rootQuery.get(key));
        }
        return null;
    }

    private static String stringField(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value == null ? null : value.toString();
    }

    /** Reads the whole table of contents, in order. Empty if the page has no novel data. */
    static List<Chapter> parseChapterList(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        JSONObject state = apolloState(novelPage);
        JSONObject work = work(state);
        String workId = stringField(work, "id");
        if (workId == null || !(work.get("tableOfContentsV2") instanceof JSONArray sections)) return chapterList;

        for (Object sectionRef : sections) {
            JSONObject section = resolve(state, sectionRef);
            if (section == null || !(section.get("episodeUnions") instanceof JSONArray episodes)) continue;
            for (Object episodeRef : episodes) {
                JSONObject episode = resolve(state, episodeRef);
                String episodeId = stringField(episode, "id");
                String title = stringField(episode, "title");
                if (episodeId == null || title == null) continue;
                chapterList.add(new Chapter(title, "https://kakuyomu.jp/works/" + workId + "/episodes/" + episodeId));
            }
        }
        return chapterList;
    }

    /** Returns the episode text, or null if the page has none. */
    static Element parseChapterBody(Document episodePage) {
        return episodePage.selectFirst("div.widget-episodeBody");
    }

    /**
     * Reads title, author, introduction and tags. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        JSONObject state = apolloState(novelPage);
        JSONObject work = work(state);
        if (work == null) return metadata;

        String title = stringField(work, "title");
        String author = stringField(work, "alternateAuthorName");
        if (author == null) author = stringField(resolve(state, work.get("author")), "activityName");
        String introduction = stringField(work, "introduction");

        if (title != null) metadata.setTitle(title);
        if (author != null) metadata.setAuthor(author);
        if (introduction != null) metadata.setDescription(introduction);
        if (work.get("tagLabels") instanceof JSONArray tags) {
            List<String> subjects = new ArrayList<>();
            for (Object tag : tags) subjects.add(tag.toString());
            metadata.setSubjects(subjects);
        }
        return metadata;
    }

    /** The book cover if the work has one, otherwise the site's generated title image. */
    static String parseCoverUrl(Document novelPage) {
        JSONObject work = work(apolloState(novelPage));
        String cover = stringField(work, "adminCoverImageUrl");
        return cover != null ? cover : stringField(work, "ogImageUrl");
    }
}
