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

class AsianhobbyistComTest {

    private static final String DOMAIN = "asianhobbyist.com";
    private static final String NOVEL_URL = "https://www.asianhobbyist.com/series/black-demon-king/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListIsTheReleaseList() throws Exception {
        // The site's releases start at chapter 381; the URL prefix changed over time (knm-, bdk-, ...).
        List<Chapter> chapters = asianhobbyist_com.parseChapterList(novelPage());

        assertEquals(277, chapters.size());
        assertEquals("https://www.asianhobbyist.com/kuro-no-maou/knm-381/", chapters.getFirst().chapterURL);
        assertEquals("https://www.asianhobbyist.com/kuro-no-maou/bdk-616/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAds() throws Exception {
        Element body = asianhobbyist_com.parseChapterBody(page(DOMAIN, "chapter.html", "https://www.asianhobbyist.com/kuro-no-maou/knm-381/"));
        assertNotNull(body);

        withBlacklistApplied(new asianhobbyist_com(), body);
        assertTrue(body.select(".code-block").isEmpty(), "ad slot left in the text");
        assertEquals(285, body.select("p").size());
        assertEquals(1, body.select("blockquote").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(asianhobbyist_com.parseChapterBody(page(DOMAIN, "novel.html", "https://www.asianhobbyist.com/")));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = asianhobbyist_com.parseMetadata(novelPage());

        assertEquals("Black Demon King", metadata.getTitle());
        assertFalse(metadata.getDescription().isBlank());
        // The cover is lazy loaded: src holds a placeholder image, data-src the real one
        String cover = asianhobbyist_com.parseCoverUrl(novelPage());
        assertTrue(cover.startsWith("https://"), cover);
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(asianhobbyist_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(asianhobbyist_com.USER_AGENT).get();
        List<Chapter> chapters = asianhobbyist_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(asianhobbyist_com.USER_AGENT).get();
        assertNotNull(asianhobbyist_com.parseChapterBody(chapterPage));
    }
}
