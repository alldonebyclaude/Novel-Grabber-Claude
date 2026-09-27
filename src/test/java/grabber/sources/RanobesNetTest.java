package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RanobesNetTest {

    private static final String DOMAIN = "ranobes.net";
    private static final String NOVEL_URL = "https://ranobes.net/novels/1207214-make-the-immortal-sect-great-again.html";
    private static final String TOC_URL = "https://ranobes.net/chapters/1207214/";

    // The table of contents renders from window.__DATA__; the fixtures keep only its chapter list fields.
    private static Document tocPage(int page) throws Exception {
        return page == 1 ? page(DOMAIN, "toc.html", TOC_URL) : page(DOMAIN, "toc-page-" + page + ".html", TOC_URL + "page/" + page + "/");
    }

    @Test
    void tableOfContentsUrlsComeFromTheNovelsId() {
        assertEquals(TOC_URL, ranobes_net.tocUrl(NOVEL_URL));
        assertEquals(TOC_URL + "page/17/", ranobes_net.tocPageUrl(TOC_URL, 17));
        assertNull(ranobes_net.tocUrl("https://ranobes.net/"));
    }

    @Test
    void eachTableOfContentsPageListsItsChaptersNewestFirst() throws Exception {
        // 418 chapters, 25 per page, on 17 pages
        assertEquals(17, ranobes_net.parsePageCount(tocPage(1)));

        List<Chapter> newest = ranobes_net.parseChapterListPage(tocPage(1));
        assertEquals(25, newest.size());
        assertEquals("Chapter 417: Reverse Hexagram", newest.getFirst().name);
        assertEquals("https://ranobes.net/make-the-immortal-sect-great-again-1207214/3277667.html", newest.getFirst().chapterURL);

        List<Chapter> oldest = ranobes_net.parseChapterListPage(tocPage(17));
        assertEquals(18, oldest.size());
        assertEquals("Chapter 1: There Is a Traitor Among Us!", oldest.getLast().name);
    }

    @Test
    void theWholeListIsOldestFirst() throws Exception {
        List<List<Chapter>> pages = new ArrayList<>();
        pages.add(ranobes_net.parseChapterListPage(tocPage(1)));
        pages.add(ranobes_net.parseChapterListPage(tocPage(17)));

        List<Chapter> chapters = ranobes_net.oldestFirst(pages);
        assertEquals(25 + 18, chapters.size());
        assertEquals("Chapter 1: There Is a Traitor Among Us!", chapters.getFirst().name);
        assertEquals("Chapter 417: Reverse Hexagram", chapters.getLast().name);
    }

    @Test
    void aPageWithoutDataHasNoChapters() {
        Document empty = Jsoup.parse("<html><body>Not found</body></html>");
        assertTrue(ranobes_net.parseChapterListPage(empty).isEmpty());
        assertEquals(0, ranobes_net.parsePageCount(empty));
    }

    @Test
    void chapterBodyWithoutAdSlots() throws Exception {
        Element body = ranobes_net.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://ranobes.net/make-the-immortal-sect-great-again-1207214/3255709.html"));
        assertNotNull(body);

        withBlacklistApplied(new ranobes_net(), body);
        assertTrue(body.select("script, .free-support-top, [id^=bg-ssp]").isEmpty(), "ad slots left in the text");
        assertEquals(131, body.select("p").size());
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(ranobes_net.parseChapterBody(tocPage(1)));
    }

    @Test
    void metadata() throws Exception {
        Document novelPage = page(DOMAIN, "novel.html", NOVEL_URL);
        NovelMetadata metadata = ranobes_net.parseMetadata(novelPage);

        assertEquals("Make the Immortal Sect Great Again", metadata.getTitle());
        assertEquals("鹤守月满池", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line "), "the description");
        // The novel's genres, not the site menu's
        assertEquals(List.of("Xianxia", "Action", "Mature", "Fantasy", "Adventure", "Drama", "Historical", "Mystery", "Romance"),
                metadata.getSubjects());
        assertEquals("https://ranobes.net/uploads/posts/2026-08/1788204563_make-the-immortal-sect-great-again.webp",
                ranobes_net.parseCoverUrl(novelPage));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(ranobes_net.class);
    }

    @Test
    @Tag("live")
    void live_fetchesTableOfContentsAndOldestChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document first = Jsoup.connect(TOC_URL).userAgent(ranobes_net.USER_AGENT).get();
        int pages = ranobes_net.parsePageCount(first);
        assertTrue(pages >= 17);

        Thread.sleep(1500);
        Document last = Jsoup.connect(ranobes_net.tocPageUrl(TOC_URL, pages)).userAgent(ranobes_net.USER_AGENT).get();
        List<Chapter> oldest = ranobes_net.parseChapterListPage(last);
        assertFalse(oldest.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(oldest.getLast().chapterURL).userAgent(ranobes_net.USER_AGENT).get();
        assertNotNull(ranobes_net.parseChapterBody(chapterPage));
    }
}
