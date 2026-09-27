package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SnowycodexComTest {

    private static final String DOMAIN = "snowycodex.com";
    private static final String NOVEL_URL = "https://snowycodex.com/novels/tis/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListIsTheTableOfContentsInOrder() throws Exception {
        // Only links to this novel's chapters; the page also links the raw on jjwxc and the cover image.
        List<Chapter> chapters = snowycodex_com.parseChapterList(novelPage());

        assertEquals(106, chapters.size());
        assertEquals("Chapter 1: The Fortune-Teller at the School Gates", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "chapter-1/", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "chapter-106/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyWithoutTitleNavigationOrLikeButtons() throws Exception {
        Element body = snowycodex_com.parseChapterBody(page(DOMAIN, "chapter-1.html", NOVEL_URL + "chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new snowycodex_com(), body);
        assertTrue(body.select("h2, .sharedaddy").isEmpty(), "title or like buttons left in the text");
        assertTrue(body.select("a[href$=/novels/tis/]").isEmpty(), "the Table of Content / Next links are left");
        assertEquals(41 - 2, body.select("> p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() {
        assertNull(snowycodex_com.parseChapterBody(Jsoup.parse("<html><body><p>Not found</p></body></html>")));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = snowycodex_com.parseMetadata(novelPage());

        assertEquals("This Isn’t Scientific!", metadata.getTitle());
        assertEquals("Mo Jing", metadata.getAuthor());
        // The paragraphs between "Summary:" and the table of contents
        assertTrue(metadata.getDescription().startsWith("Line 5."), metadata.getDescription());
        assertTrue(metadata.getDescription().endsWith("Line 10.Line 11."), "stops at the separator");
        assertFalse(metadata.getDescription().contains("Chapter 1"), "the table of contents is not part of it");
        assertEquals("https://snowycodex.com/wp-content/uploads//2026/09/TIS-1.png", snowycodex_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(snowycodex_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(snowycodex_com.USER_AGENT).get();
        List<Chapter> chapters = snowycodex_com.parseChapterList(novelPage);
        assertTrue(chapters.size() >= 106);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(snowycodex_com.USER_AGENT).get();
        assertNotNull(snowycodex_com.parseChapterBody(chapterPage));
    }
}
