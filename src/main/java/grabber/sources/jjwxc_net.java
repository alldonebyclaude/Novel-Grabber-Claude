package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.PaywallSite;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The novel page lists every chapter; the first ones are free and the rest are VIP chapters that must be bought.
 * VIP chapters open a purchase dialog instead of a link; their address is in the link's {@code rel}. They are
 * downloaded only with the user's own login, and only those the user bought come back with text. Text the site
 * scrambles with its own font is not worked around.
 */
@PaywallSite("Without your jjwxc login only the free chapters are downloaded. With it, the VIP chapters you bought "
        + "should be downloaded too (untested: we had no jjwxc login to try it); add your login in the account settings.")
public class jjwxc_net implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final String name = "jjwxc";
    private final String url = "https://www.jjwxc.net/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public jjwxc_net(Novel novel) {
        this.novel = novel;
    }

    public jjwxc_net() {
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
            boolean loggedIn = novel.cookies != null && !novel.cookies.isEmpty();
            chapterList = parseChapterList(toc, loggedIn);
            int vip = countVipChapters(toc);
            if (vip > 0 && loggedIn) {
                GrabberUtils.info(novel.window, vip + " chapters of this novel are VIP chapters. Those you bought are "
                        + "downloaded; the others are skipped.");
            } else if (vip > 0) {
                GrabberUtils.info(novel.window, vip + " chapters of this novel are VIP chapters, so they are left out. "
                        + "To download chapters you bought, add your jjwxc login in the account settings.");
            }
            if (chapterList.isEmpty() && vip == 0) {
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
            Document page = fetch(chapter.chapterURL);
            if (isFontScrambled(page)) {
                GrabberUtils.err(novel.window, chapter.name + ": the site scrambles this chapter's text with its own "
                        + "font, so it is skipped.");
                return null;
            }
            chapterBody = parseChapterBody(page);
            if (chapterBody == null && chapter.chapterURL.contains("onebook_vip.php")) {
                GrabberUtils.err(novel.window, chapter.name + " is a VIP chapter you haven't bought (or your login "
                        + "expired), so it is skipped.");
            }
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

    /** The site sends GB18030; jsoup reads the charset from the page. */
    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /** Reads the free chapters, in order. VIP chapters open a purchase dialog (onclick) and are left out. */
    static List<Chapter> parseChapterList(Document novelPage) {
        return parseChapterList(novelPage, false);
    }

    /**
     * Reads the chapters in order: the free ones and, if {@code includeVip}, the VIP ones in their place. A VIP
     * chapter's address is in its link's {@code rel}; the buy button next to it is not a chapter.
     */
    static List<Chapter> parseChapterList(Document novelPage, boolean includeVip) {
        List<Chapter> chapterList = new ArrayList<>();
        for (Element row : novelPage.select("#oneboolt tbody tr")) {
            Element free = row.selectFirst("a[itemprop=url][href]:not([onclick])");
            Element vip = includeVip ? row.selectFirst("a[itemprop=url][onclick][rel*=onebook_vip.php]") : null;
            Element link = free != null ? free : vip;
            if (link == null || link.text().isBlank()) continue;
            String chapterUrl = link == free ? link.attr("abs:href") : link.attr("abs:rel");
            // The page links over http
            chapterList.add(new Chapter(link.text(), chapterUrl.replaceFirst("^http:", "https:")));
        }
        return chapterList;
    }

    /** The number of VIP chapters, which have to be bought. */
    static int countVipChapters(Document novelPage) {
        return novelPage.select("#oneboolt tbody tr:has(a[itemprop=url][onclick])").size();
    }

    /**
     * Whether the page draws the text with one of the site's scrambling fonts ({@code jjwxcfont_...}), which map
     * the characters to other ones. Such text is not worked around.
     */
    static boolean isFontScrambled(Document chapterPage) {
        if (chapterPage.select("style").stream().anyMatch(style -> style.data().contains("jjwxcfont"))) return true;
        return !chapterPage.select(".noveltext[style*=jjwxcfont], .noveltext [style*=jjwxcfont], "
                + ".noveltext[class*=jjwxcfont], .noveltext [class*=jjwxcfont]").isEmpty();
    }

    /** Returns the chapter text without the page controls around it, or null if the page has none. */
    static Element parseChapterBody(Document chapterPage) {
        return chapterPage.selectFirst(".noveltext #paragraph_comment_content");
    }

    /**
     * Reads title, author, introduction and genres. The cover is left to {@link #parseCoverUrl(Document)},
     * because setting it on {@link NovelMetadata} downloads the image.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst(".sptd .bigtext span");
        Element author = novelPage.selectFirst(".sptd h2 span");
        Element description = novelPage.selectFirst("#novelintro");
        Element genre = novelPage.selectFirst("span[itemprop=genre]");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.text());
        // One line such as "原创-纯爱-幻想未来-剧情"
        if (genre != null) metadata.setSubjects(Arrays.stream(genre.text().split("-")).map(String::strip)
                .filter(subject -> !subject.isEmpty()).toList());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst(".noveldefaultimage");
        return cover == null ? null : cover.absUrl("src");
    }
}
