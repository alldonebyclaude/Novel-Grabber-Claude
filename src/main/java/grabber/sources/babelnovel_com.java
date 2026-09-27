package grabber.sources;

import grabber.Chapter;
import grabber.Driver;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.PaywallSite;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BabelNovel answers plain requests with a Cloudflare block, so every page is loaded in the app's browser. The
 * chapter list page ({@code /books/<slug>/chapters}) renders from its {@code __NEXT_DATA__}, which also has the
 * book's details. The list doesn't say which chapters are locked: a locked chapter's page shows an unlock panel
 * over a short preview, and such chapters are skipped.
 */
@PaywallSite("Locked chapters are skipped; free chapters, and chapters you bought with your own login, are downloaded.")
public class babelnovel_com implements Source {
    private static final Duration BOT_CHECK_WAIT = Duration.ofSeconds(30);
    private static final Pattern BOOK = Pattern.compile("(https?://[^/]*babelnovel\\.com/books/[^/?#]+)");

    private final String name = "BabelNovel";
    private final String url = "https://babelnovel.com/";
    private final boolean canHeadless = true;
    private Novel novel;
    private Document toc;

    public babelnovel_com(Novel novel) {
        this.novel = novel;
    }

    public babelnovel_com() {
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
        String listUrl = chapterListUrl(novel.novelLink);
        if (listUrl == null) {
            GrabberUtils.err(novel.window, "Could not find the book in the link. Correct novel link?");
            return chapterList;
        }
        try {
            toc = load(listUrl);
            chapterList = parseChapterList(toc);
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct novel link?");
            } else {
                GrabberUtils.info(novel.window, "Locked chapters are skipped. To download chapters you bought, "
                        + "add your BabelNovel login in the account settings.");
            }
        } catch (WebDriverException e) {
            GrabberUtils.err(novel.window, "Could not load the page in the browser: " + e.getMessage().split("\n")[0], e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        try {
            Document page = load(chapter.chapterURL);
            if (isLocked(page)) {
                GrabberUtils.err(novel.window, chapter.name + " is locked, so it is skipped.");
                return null;
            }
            return parseChapterBody(page);
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
        // The chapter title above the text
        blacklistedTags.add("h3[class*=chapter_title]");
        blacklistedTags.add("script");
        return blacklistedTags;
    }

    private Driver browser() {
        if (novel.headlessDriver == null) novel.headlessDriver = new Driver(novel.window, novel.browser);
        return novel.headlessDriver;
    }

    /** Loads a page in the browser. If the site shows its bot check first, waits for the browser to get past it. */
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

    /** The chapter list page for a link to the book or one of its chapters, or null. */
    static String chapterListUrl(String link) {
        Matcher book = BOOK.matcher(link);
        return book.find() ? book.group(1) + "/chapters" : null;
    }

    /** The page's cached data ({@code props.pageProps.cacheData} of its {@code __NEXT_DATA__}), or null. */
    private static JSONObject cacheData(Document page) {
        Element script = page.selectFirst("script#__NEXT_DATA__");
        if (script == null) return null;
        String data = script.data().strip();
        // The browser may wrap the script in CDATA markers
        if (data.startsWith("//<![CDATA[")) data = data.substring("//<![CDATA[".length());
        if (data.endsWith("//]]>")) data = data.substring(0, data.length() - "//]]>".length());
        try {
            JSONObject next = (JSONObject) new JSONParser().parse(data.strip());
            JSONObject props = (JSONObject) next.get("props");
            JSONObject pageProps = props == null ? null : (JSONObject) props.get("pageProps");
            return pageProps == null ? null : (JSONObject) pageProps.get("cacheData");
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    /** The cached entry whose key starts with {@code prefix}, e.g. {@code bookId-cold-showers}. */
    private static Object cached(Document page, String prefix) {
        JSONObject data = cacheData(page);
        if (data == null) return null;
        for (Object key : data.keySet()) {
            if (key.toString().startsWith(prefix)) return data.get(key);
        }
        return null;
    }

    private static String stringField(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value == null ? null : value.toString().strip();
    }

    /** Reads every chapter of the book from the chapter list page's data, in order. */
    static List<Chapter> parseChapterList(Document chapterListPage) {
        List<Chapter> chapterList = new ArrayList<>();
        if (!(cached(chapterListPage, "chapters-page-chapters-") instanceof JSONArray chapters)) return chapterList;
        String listUrl = chapterListPage.location().replaceAll("[?#].*$", "").replaceAll("/+$", "");
        for (Object item : chapters) {
            if (!(item instanceof JSONObject chapter)) continue;
            String name = stringField(chapter, "name");
            String slug = stringField(chapter, "canonicalName");
            if (name == null || slug == null) continue;
            chapterList.add(new Chapter(name, listUrl + "/" + slug));
        }
        return chapterList;
    }

    /** Whether the chapter page shows the unlock panel instead of the chapter. */
    static boolean isLocked(Document chapterPage) {
        return chapterPage.selectFirst("[class*=unlock-chapter_container]") != null;
    }

    /** Returns the chapter text, or null if the page has none or the chapter is locked (only a preview shows). */
    static Element parseChapterBody(Document chapterPage) {
        if (isLocked(chapterPage)) return null;
        return chapterPage.selectFirst("article[class*=chapter_container] > section");
    }

    /**
     * Reads title, author, synopsis and genres from the page's data. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document page) {
        NovelMetadata metadata = new NovelMetadata();
        if (!(cached(page, "bookId-") instanceof JSONObject book)) return metadata;

        String title = stringField(book, "name");
        String author = stringField(book, "authorName");
        String synopsis = stringField(book, "synopsis");
        if (title != null) metadata.setTitle(title);
        if (author != null) metadata.setAuthor(author);
        if (synopsis != null) metadata.setDescription(synopsis);
        if (book.get("genres") instanceof JSONArray genres) {
            List<String> subjects = new ArrayList<>();
            for (Object genre : genres) {
                String genreName = genre instanceof JSONObject object ? stringField(object, "name") : null;
                if (genreName != null) subjects.add(genreName);
            }
            metadata.setSubjects(subjects);
        }
        return metadata;
    }

    static String parseCoverUrl(Document page) {
        return cached(page, "bookId-") instanceof JSONObject book ? stringField(book, "cover") : null;
    }
}
