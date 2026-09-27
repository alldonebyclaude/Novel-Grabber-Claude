package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.PaywallSite;
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
 * The story page renders from {@code window.__remixContext}, which holds the story and its parts. A part's text
 * comes from the URL that Wattpad's parts API gives for it. Parts of paid stories that are paywalled are left out.
 */
@PaywallSite("Some stories are paid. Their paywalled parts are left out; the free parts are downloaded.")
public class wattpad_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final Pattern PART_ID = Pattern.compile("wattpad\\.com/(\\d+)(?:-|$|/|\\?)");

    private final String name = "Wattpad";
    private final String url = "https://wattpad.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public wattpad_com(Novel novel) {
        this.novel = novel;
    }

    public wattpad_com() {
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
            chapterList = parseChapterList(toc);
            int locked = countLockedParts(toc);
            if (locked > 0) {
                GrabberUtils.info(novel.window, locked + " parts of this story are paid or not available, "
                        + "so they are left out.");
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
        String partId = partId(chapter.chapterURL);
        if (partId == null) {
            GrabberUtils.err(novel.window, "Could not find the part in " + chapter.chapterURL);
            return null;
        }
        try {
            String json = fetch("https://www.wattpad.com/v4/parts/" + partId + "?fields=text_url")
                    .ignoreContentType(true).execute().body();
            String textUrl = parseTextUrl(json);
            if (textUrl == null) {
                GrabberUtils.err(novel.window, "Could not find the text of " + chapter.name);
                return null;
            }
            Thread.sleep(1000);
            chapterBody = parseChapterBody(fetch(textUrl).ignoreContentType(true).execute().body());
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private org.jsoup.Connection fetch(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies);
    }

    /** The story object from the page's {@code window.__remixContext}, or null. */
    private static JSONObject story(Document storyPage) {
        for (Element script : storyPage.select("script:not([src])")) {
            String data = script.data().strip();
            if (!data.startsWith("window.__remixContext")) continue;
            String json = data.substring(data.indexOf('=') + 1).strip();
            if (json.endsWith(";")) json = json.substring(0, json.length() - 1);
            try {
                JSONObject context = (JSONObject) new JSONParser().parse(json);
                JSONObject state = (JSONObject) context.get("state");
                JSONObject loaderData = state == null ? null : (JSONObject) state.get("loaderData");
                if (loaderData == null) return null;
                for (Object route : loaderData.values()) {
                    if (route instanceof JSONObject routeData && routeData.get("story") instanceof JSONObject story) {
                        return story;
                    }
                }
            } catch (ParseException | ClassCastException e) {
                return null;
            }
        }
        return null;
    }

    private static List<JSONObject> parts(Document storyPage) {
        List<JSONObject> parts = new ArrayList<>();
        JSONObject story = story(storyPage);
        if (story != null && story.get("parts") instanceof JSONArray array) {
            for (Object part : array) {
                if (part instanceof JSONObject object) parts.add(object);
            }
        }
        return parts;
    }

    /** Whether the reader can open the part: not paywalled, not blocked and not a draft. */
    private static boolean isReadable(JSONObject part) {
        return !Boolean.TRUE.equals(part.get("paywalled")) && !Boolean.TRUE.equals(part.get("isBlocked"))
                && !Boolean.TRUE.equals(part.get("draft"));
    }

    /** Reads the parts the reader can open, in order. */
    static List<Chapter> parseChapterList(Document storyPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (JSONObject part : parts(storyPage)) {
            Object title = part.get("title");
            Object partUrl = part.get("url");
            if (!isReadable(part) || title == null || partUrl == null) continue;
            chapterList.add(new Chapter(title.toString(), partUrl.toString()));
        }
        return chapterList;
    }

    /** The number of parts that are paywalled, blocked or drafts. */
    static int countLockedParts(Document storyPage) {
        return (int) parts(storyPage).stream().filter(part -> !isReadable(part)).count();
    }

    /** The part's id from its URL, e.g. {@code https://www.wattpad.com/348867055-chapter-1}, or null. */
    static String partId(String partUrl) {
        Matcher id = PART_ID.matcher(partUrl);
        return id.find() ? id.group(1) : null;
    }

    /** The URL of the part's text from the parts API's answer, or null. */
    static String parseTextUrl(String json) {
        try {
            JSONObject answer = (JSONObject) new JSONParser().parse(json);
            JSONObject textUrl = (JSONObject) answer.get("text_url");
            Object text = textUrl == null ? null : textUrl.get("text");
            return text == null ? null : text.toString();
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    /** The part's paragraphs, or null if the answer has no text. */
    static Element parseChapterBody(String textHtml) {
        if (textHtml == null || textHtml.isBlank()) return null;
        return Jsoup.parseBodyFragment(textHtml).body();
    }

    /**
     * Reads title, author, description and tags. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document storyPage) {
        NovelMetadata metadata = new NovelMetadata();
        JSONObject story = story(storyPage);
        if (story == null) return metadata;

        Object title = story.get("title");
        Object description = story.get("description");
        if (title != null) metadata.setTitle(title.toString());
        if (story.get("user") instanceof JSONObject user && user.get("name") != null) {
            metadata.setAuthor(user.get("name").toString());
        }
        if (description != null) metadata.setDescription(description.toString());
        if (story.get("tags") instanceof JSONArray tags) {
            List<String> subjects = new ArrayList<>();
            for (Object tag : tags) subjects.add(tag.toString());
            metadata.setSubjects(subjects);
        }
        return metadata;
    }

    static String parseCoverUrl(Document storyPage) {
        JSONObject story = story(storyPage);
        Object cover = story == null ? null : story.get("cover");
        return cover == null ? null : cover.toString();
    }
}
