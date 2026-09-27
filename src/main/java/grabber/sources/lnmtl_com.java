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
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * lnmtl.com (machine translations). The novel page lists its volumes in a script; the chapters of each volume
 * come from {@code chapter?page=<n>&volumeId=<id>}, 50 per page.
 */
public class lnmtl_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://lnmtl.com";
    private static final long LIST_PAGE_DELAY_MS = 1000;
    private static final Pattern VOLUMES = Pattern.compile("lnmtl\\.volumes\\s*=\\s*(\\[.*?\\])\\s*;", Pattern.DOTALL);

    private final String name = "LNMTL";
    private final String url = BASE_URL + "/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public lnmtl_com() {
    }

    public lnmtl_com(Novel novel) {
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
            List<String> volumeIds = parseVolumeIds(toc);
            if (volumeIds.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not find the novel's volumes. Correct novel link?");
                return chapterList;
            }
            boolean firstRequest = true;
            for (String volumeId : volumeIds) {
                for (int page = 1; ; page++) {
                    if (!firstRequest) Thread.sleep(LIST_PAGE_DELAY_MS);
                    firstRequest = false;
                    String json = fetch(chapterListUrl(volumeId, page)).ignoreContentType(true).execute().body();
                    chapterList.addAll(parseChapterListPage(json));
                    if (!hasNextPage(json)) break;
                }
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
        return new ArrayList<>();
    }

    private Connection fetch(String pageUrl) {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies);
    }

    /**
     * Reads the ids of the novel's volumes from the {@code lnmtl.volumes = [...]} script, in order.
     */
    static List<String> parseVolumeIds(Document novelPage) {
        List<String> volumeIds = new ArrayList<>();
        for (Element script : novelPage.select("script:not([src])")) {
            Matcher volumes = VOLUMES.matcher(script.data());
            if (!volumes.find()) continue;
            try {
                for (Object volume : (JSONArray) new JSONParser().parse(volumes.group(1))) {
                    Object id = ((JSONObject) volume).get("id");
                    if (id != null) volumeIds.add(id.toString());
                }
            } catch (ParseException | ClassCastException e) {
                return new ArrayList<>();
            }
            break;
        }
        return volumeIds;
    }

    static String chapterListUrl(String volumeId, int page) {
        return BASE_URL + "/chapter?page=" + page + "&volumeId=" + volumeId;
    }

    private static JSONObject parseJson(String json) {
        try {
            return (JSONObject) new JSONParser().parse(json);
        } catch (ParseException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Reads one page of a volume's chapters. Returns an empty list for anything that is not the expected JSON.
     */
    static List<Chapter> parseChapterListPage(String json) {
        List<Chapter> chapterList = new ArrayList<>();
        JSONObject page = parseJson(json);
        if (page == null || !(page.get("data") instanceof JSONArray chapters)) return chapterList;
        for (Object entry : chapters) {
            if (!(entry instanceof JSONObject chapter)) continue;
            Object title = chapter.get("title");
            Object chapterUrl = chapter.get("site_url");
            if (title == null || chapterUrl == null) continue;
            chapterList.add(new Chapter(title.toString(), chapterUrl.toString()));
        }
        return chapterList;
    }

    static boolean hasNextPage(String json) {
        JSONObject page = parseJson(json);
        return page != null && page.get("next_page_url") != null;
    }

    /**
     * Returns the machine translated sentences as a {@code <div>} of {@code <p>} elements, or null if the page
     * has none. The page also shows each original sentence, which is left out. Dialogue ({@code <dq>}) is shown
     * in italics and {@code <sq>} in bold, so those become {@code <em>} and {@code <strong>}; the other
     * site-specific tags (terms, words) are unwrapped.
     */
    static Element parseChapterBody(Document chapterPage) {
        Elements sentences = chapterPage.select(".chapter-body sentence.translated");
        if (sentences.isEmpty()) return null;

        Element body = new Element("div");
        for (Element sentence : sentences) {
            Element paragraph = sentence.clone();
            paragraph.select("dq").tagName("em");
            paragraph.select("sq").tagName("strong");
            for (Element inline : paragraph.select("*")) {
                if (inline != paragraph && !inline.nameIs("em") && !inline.nameIs("strong")) inline.unwrap();
            }
            body.appendElement("p").html(paragraph.html());
        }
        return body;
    }

    /**
     * Reads title, author and description. The page no longer lists genres or tags. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("meta[property=og:title]");
        Element author = novelPage.selectFirst(".panel-body:contains(Authors) span");
        Element description = novelPage.selectFirst(".description");

        if (title != null) metadata.setTitle(title.attr("content").strip());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image:url]");
        return cover == null || cover.attr("content").isBlank() ? null : cover.attr("content");
    }
}
