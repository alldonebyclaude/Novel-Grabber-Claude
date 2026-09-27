package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
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
 * FanFiktion.de. A story's first page is its first chapter; the chapter selector ({@code #kA}) lists all chapters by
 * number, and a chapter's URL is the story URL with that number: {@code /s/<id>/<number>/<title>}.
 */
public class fanfiktion_de implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    /** A story URL split around its chapter number. */
    private static final Pattern STORY_URL = Pattern.compile("^(.*/s/[^/]+/)\\d+(/.*)$");

    private final String name = "FanFiktion";
    private final String url = "https://fanfiktion.de";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public fanfiktion_de(Novel novel) {
        this.novel = novel;
    }

    public fanfiktion_de() {
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
                GrabberUtils.err(novel.window, "Could not find any chapters. Correct story link?");
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
        return toc == null ? new NovelMetadata() : parseMetadata(toc);
    }

    public List<String> getBlacklistedTags() {
        return new ArrayList<>();
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Reads the chapter selector of a story page. The chapter URLs are the story's canonical URL with the chapter
     * number replaced. Empty if the page has no selector or canonical link.
     */
    static List<Chapter> parseChapterList(Document storyPage) {
        List<Chapter> chapterList = new ArrayList<>();
        Element canonical = storyPage.selectFirst("link[rel=canonical]");
        Matcher storyUrl = STORY_URL.matcher(canonical == null ? "" : canonical.attr("abs:href"));
        if (!storyUrl.matches()) return chapterList;
        for (Element option : storyPage.select("#kA option[value]")) {
            String number = option.val().strip();
            if (number.isEmpty()) continue;
            chapterList.add(new Chapter(option.text(), storyUrl.group(1) + number + storyUrl.group(2)));
        }
        return chapterList;
    }

    /**
     * Returns the chapter text, or null if the page has none.
     */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".user-formatted-inner");
    }

    /**
     * Reads title, author and the story's summary. The site shows no cover.
     */
    static NovelMetadata parseMetadata(Document storyPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = storyPage.selectFirst(".huge-font");
        Element author = storyPage.selectFirst("a.no-wrap");
        Element summary = storyPage.selectFirst("#story-summary-inline");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (summary != null) metadata.setDescription(summary.text());
        return metadata;
    }
}
