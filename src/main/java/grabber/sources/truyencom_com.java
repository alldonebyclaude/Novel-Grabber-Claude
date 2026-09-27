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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * truyencom.com (webtruyen.com redirects here). The chapter list comes from the site's API, 50 chapters per
 * page; the chapter links are built from the chapter names the way the site's own script does.
 */
public class truyencom_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    /** The API returns at most 50 chapters per page, whatever page size is asked for. */
    static final int CHAPTERS_PER_PAGE = 50;
    private static final long LIST_PAGE_DELAY_MS = 1000;
    private static final String BASE_URL = "https://truyencom.com";
    private static final Pattern STORY_ID = Pattern.compile("\\bstoryID\\s*=\\s*(\\d+)");
    private static final Pattern STORY_ALIAS = Pattern.compile("\\bstoryAlias\\s*=\\s*['\"]([^'\"]+)['\"]");

    private final String name = "TruyenCom";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public truyencom_com() {
    }

    public truyencom_com(Novel novel) {
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
            String storyId = parseStoryId(toc);
            String storyAlias = parseStoryAlias(toc);
            if (storyId == null || storyAlias == null) {
                GrabberUtils.err(novel.window, "Could not find the story id. Correct novel link?");
                return chapterList;
            }
            Set<String> seen = new HashSet<>();
            for (int page = 1; ; page++) {
                if (page > 1) Thread.sleep(LIST_PAGE_DELAY_MS);
                List<Chapter> chapters = parseChapterList(
                        fetch(chapterListUrl(storyId, page)).ignoreContentType(true).execute().body(), storyAlias);
                // Stop at the last (short) page, or if the API ignores the page number and repeats itself
                if (chapters.isEmpty() || !seen.add(chapters.getFirst().chapterURL)) break;
                chapterList.addAll(chapters);
                if (chapters.size() < CHAPTERS_PER_PAGE) break;
            }
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list of story " + storyId);
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
        // The chapter text holds only text and line breaks (checked 2026-09-24)
        return new ArrayList<>();
    }

    private Connection fetch(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies);
    }

    /** Reads a variable the novel page sets for the site's script, e.g. {@code storyID=66;}. */
    private static String scriptVariable(Document novelPage, Pattern variable) {
        for (Element script : novelPage.select("script:not([src])")) {
            Matcher matcher = variable.matcher(script.data());
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    /** The site's id of the novel, e.g. {@code 66}, or null. */
    static String parseStoryId(Document novelPage) {
        return scriptVariable(novelPage, STORY_ID);
    }

    /** The novel's path in chapter links, e.g. {@code con-duong-ba-chu}, or null. */
    static String parseStoryAlias(Document novelPage) {
        return scriptVariable(novelPage, STORY_ALIAS);
    }

    static String chapterListUrl(String storyId, int page) {
        return BASE_URL + "/api/chapters/" + storyId + "/" + page + "/" + CHAPTERS_PER_PAGE;
    }

    /**
     * Reads one page of the API's chapter list, oldest first. The chapter link is built from the part of the
     * name before the colon, e.g. "Chương 1: ..." becomes {@code /<alias>/chuong-1.html}. Returns an empty list
     * for anything that is not the expected JSON.
     */
    static List<Chapter> parseChapterList(String json, String storyAlias) {
        List<Chapter> chapterList = new ArrayList<>();
        try {
            JSONObject response = (JSONObject) new JSONParser().parse(json);
            JSONArray items = (JSONArray) response.get("items");
            if (items == null) return chapterList;
            for (Object entry : items) {
                Object chapterName = ((JSONObject) entry).get("chapter_name");
                if (chapterName == null || chapterName.toString().isBlank()) continue;
                String slug = str2url(chapterName.toString().split(":")[0]);
                chapterList.add(new Chapter(chapterName.toString(), BASE_URL + "/" + storyAlias + "/" + slug + ".html"));
            }
        } catch (ParseException | ClassCastException e) {
            return new ArrayList<>();
        }
        return chapterList;
    }

    /**
     * Port of {@code str2url()} from the site's main.js: lowercase, strip Vietnamese accents, turn punctuation
     * and spaces into hyphens, collapse repeated hyphens and trim them.
     */
    static String str2url(String text) {
        String slug = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        slug = slug.replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a");
        slug = slug.replaceAll("[èéẹẻẽêềếệểễ]", "e");
        slug = slug.replaceAll("[ìíịỉĩ]", "i");
        slug = slug.replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o");
        slug = slug.replaceAll("[ùúụủũưừứựửữ]", "u");
        slug = slug.replaceAll("[ỳýỵỷỹ]", "y");
        slug = slug.replace("đ", "d");
        slug = slug.replaceAll("[!@%^*()+=<>?/,.:;' \"&#\\[\\]“”~_]", "-");
        slug = slug.replaceAll("-+-", "-");
        return slug.replaceAll("^-+|-+$", "");
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#chapter-c");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h1.title");
        Element author = novelPage.selectFirst("a[itemprop=author]");
        Element description = novelPage.selectFirst(".desc-text");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        // Scoped to the novel's info box; the page's genre menu uses the same markup
        metadata.setSubjects(novelPage.select(".info a[itemprop=genre]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".book img");
        return cover == null ? null : cover.absUrl("src");
    }
}
