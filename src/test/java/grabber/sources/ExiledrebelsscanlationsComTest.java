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

class ExiledrebelsscanlationsComTest {

    private static final String DOMAIN = "exiledrebelsscanlations.com";
    private static final String NOVEL_URL = "https://exiledrebelsscanlations.com/novels/a-glimpse-of-the-muscular-man/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListInOrder() throws Exception {
        List<Chapter> chapters = exiledrebelsscanlations_com.parseChapterList(novelPage());

        assertEquals(7, chapters.size());
        assertEquals("https://exiledrebelsscanlations.com/muscular-man-chapter-1/", chapters.getFirst().chapterURL);
        assertEquals("https://exiledrebelsscanlations.com/muscular-man-chapter-7/", chapters.getLast().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = exiledrebelsscanlations_com.parseChapterBody(
                page(DOMAIN, "chapter.html", "https://exiledrebelsscanlations.com/muscular-man-chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new exiledrebelsscanlations_com(), body);
        assertEquals(69, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(exiledrebelsscanlations_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        // og:description is empty; the summary sits between "SUMMARY:" and "CHAPTERS:" on the page, the author in
        // a "by ..." heading.
        NovelMetadata metadata = exiledrebelsscanlations_com.parseMetadata(novelPage());

        assertEquals("A Glimpse of the Muscular Man", metadata.getTitle());
        assertEquals("Starry Sand", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertFalse(metadata.getDescription().contains("SUMMARY") || metadata.getDescription().contains("CHAPTERS"));
        assertTrue(exiledrebelsscanlations_com.parseCoverUrl(novelPage()).startsWith("https://"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(exiledrebelsscanlations_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(exiledrebelsscanlations_com.USER_AGENT).get();
        List<Chapter> chapters = exiledrebelsscanlations_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(exiledrebelsscanlations_com.USER_AGENT).get();
        assertNotNull(exiledrebelsscanlations_com.parseChapterBody(chapterPage));
    }
}
