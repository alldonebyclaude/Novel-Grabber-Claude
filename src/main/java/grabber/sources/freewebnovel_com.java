package grabber.sources;

import grabber.Chapter;
import grabber.Driver;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * freewebnovel.com answers plain requests with a Cloudflare bot check, so every page is loaded in the app's
 * browser (the -headless option; without one chosen, the built-in HtmlUnit browser).
 * <p>
 * The novel page lists only the first 40 chapters: its page selector sends every page back to the first one.
 * The complete list is what the site's reader loads for its chapter menu: a POST to {@code /api/chapterlist.php}
 * with the {@code postData} a chapter page gives in {@code readerInitConfig}. The source sends that same request
 * from inside the loaded chapter page, which needs a browser with JavaScript.
 */
public class freewebnovel_com implements Source {
    private static final String BASE_URL = "https://freewebnovel.com";
    private static final Duration BOT_CHECK_WAIT = Duration.ofSeconds(30);
    private static final Pattern POST_DATA = Pattern.compile("postData\\s*:\\s*\\{([^}]*)}");
    private static final Pattern POST_FIELD = Pattern.compile("(\\w+)\\s*:\\s*\"([^\"]*)\"");
    /** The reader's chapter list request, sent from the loaded page: arguments[0] is the form-encoded body. */
    private static final String CHAPTER_LIST_REQUEST = """
            var done = arguments[arguments.length - 1];
            var request = new XMLHttpRequest();
            request.open('POST', '/api/chapterlist.php');
            request.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            request.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
            request.onload = function () { done(request.status === 200 ? request.responseText : ''); };
            request.onerror = function () { done(''); };
            request.send(arguments[0]);
            """;

    private final String name = "Free Web Novel";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = true;
    private Novel novel;
    private Document toc;

    public freewebnovel_com() {
    }

    public freewebnovel_com(Novel novel) {
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
            toc = load(novel.novelLink);
            List<Chapter> firstChapters = parseFirstChapters(toc);
            if (firstChapters.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
                return chapterList;
            }
            Map<String, String> listRequest = parseChapterListRequest(load(firstChapters.getFirst().chapterURL));
            chapterList = listRequest.isEmpty() ? chapterList : requestChapterList(listRequest);
            if (chapterList.isEmpty()) {
                GrabberUtils.info(novel.window, "Could only read the first " + firstChapters.size() + " chapters: the "
                        + "full chapter list needs a browser with JavaScript (-headless chrome, firefox, edge or headless).");
                chapterList = firstChapters;
            }
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        try {
            return parseChapterBody(load(chapter.chapterURL));
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
            return null;
        }
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
        // Ad slots inside the text
        blacklistedTags.add(".reader-ad-skip");
        blacklistedTags.add("script");
        return blacklistedTags;
    }

    private Driver browser() {
        if (novel.headlessDriver == null) novel.headlessDriver = new Driver(novel.window, novel.browser);
        return novel.headlessDriver;
    }

    /**
     * Loads a page in the browser. If the site shows its bot check first, waits for the browser to get past it.
     */
    private Document load(String pageUrl) {
        Driver browser = browser();
        browser.driver.navigate().to(pageUrl);
        long deadline = System.currentTimeMillis() + BOT_CHECK_WAIT.toMillis();
        while (String.valueOf(browser.driver.getTitle()).contains("Just a moment") && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Jsoup.parse(browser.driver.getPageSource(), browser.driver.getCurrentUrl());
    }

    /**
     * Sends the reader's chapter list request from the page loaded last. Returns an empty list if the browser
     * cannot run it (no JavaScript) or the site refuses.
     */
    private List<Chapter> requestChapterList(Map<String, String> listRequest) {
        if (!(browser().driver instanceof JavascriptExecutor javascript)) return new ArrayList<>();
        StringBuilder body = new StringBuilder();
        listRequest.forEach((key, value) -> body.append(body.isEmpty() ? "" : "&")
                .append(key).append('=').append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)));
        try {
            browser().driver.manage().timeouts().scriptTimeout(BOT_CHECK_WAIT);
            return parseChapterList(String.valueOf(javascript.executeAsyncScript(CHAPTER_LIST_REQUEST, body.toString())));
        } catch (WebDriverException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Reads the {@code readerInitConfig.chapterList.postData} of a chapter page, e.g.
     * {@code {aid: "3168", acode: "the-prodigies-war", cid: "1"}}. Empty if the page has none.
     */
    static Map<String, String> parseChapterListRequest(Document chapterPage) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element script : chapterPage.select("script:not([src])")) {
            if (!script.data().contains("readerInitConfig")) continue;
            Matcher postData = POST_DATA.matcher(script.data());
            if (!postData.find()) continue;
            Matcher field = POST_FIELD.matcher(postData.group(1));
            while (field.find()) fields.put(field.group(1), field.group(2));
            break;
        }
        return fields;
    }

    /**
     * Reads the chapter list request's answer, {@code {"html": "<option value=\"/novel/.../chapter-1\">CH.1: ...</option>..."}},
     * oldest first. Empty for an error or anything that is not the expected JSON (e.g. the bot check page).
     */
    static List<Chapter> parseChapterList(String json) {
        List<Chapter> chapterList = new ArrayList<>();
        Object options;
        try {
            options = ((JSONObject) new JSONParser().parse(json)).get("html");
        } catch (ParseException | ClassCastException e) {
            return chapterList;
        }
        if (options == null) return chapterList;
        for (Element option : Jsoup.parseBodyFragment("<select>" + options + "</select>", BASE_URL).select("option[value]")) {
            String chapterUrl = option.absUrl("value");
            if (chapterUrl.isEmpty() || option.text().isBlank()) continue;
            chapterList.add(new Chapter(option.text(), chapterUrl));
        }
        return chapterList;
    }

    /**
     * The chapter list on the novel page: the first 40 chapters only.
     */
    static List<Chapter> parseFirstChapters(Document novelPage) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element link : novelPage.select("#idData a[href]")) {
            String chapterName = link.text().isBlank() ? link.attr("title") : link.text();
            chapterList.add(new Chapter(chapterName, link.attr("abs:href")));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst("#article");
    }

    /**
     * Reads title, author, description and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("meta[property=og:novel:novel_name]");
        Element author = novelPage.selectFirst("meta[property=og:novel:author]");
        Element description = novelPage.selectFirst(".m-desc");
        Element genres = novelPage.selectFirst("meta[property=og:novel:genre]");

        if (title != null) metadata.setTitle(title.attr("content").strip());
        if (author != null) metadata.setAuthor(author.attr("content").strip());
        if (description != null) metadata.setDescription(description.text());
        if (genres != null) {
            List<String> subjects = new ArrayList<>();
            for (String genre : genres.attr("content").split(",")) {
                if (!genre.isBlank()) subjects.add(genre.strip());
            }
            metadata.setSubjects(subjects);
        }
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.absUrl("content");
    }
}
