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

class WuxiaCityTest {

    private static final String DOMAIN = "wuxia.city";
    private static final String NOVEL_URL = "https://wuxia.city/book/a-knight-who-eternally-regresses";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListComesFromTheNovelPageOldestFirst() throws Exception {
        // The novel page lists all chapters, newest first; the separate chapter tab the old source used is gone.
        List<Chapter> chapters = wuxia_city.parseChapterList(novelPage());

        assertEquals(958, chapters.size());
        assertEquals("Chapter 0: Prologue", chapters.getFirst().name, "the chapter number badge is not part of the name");
        assertEquals(NOVEL_URL + "/8A7x43nD", chapters.getFirst().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = wuxia_city.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "/8aXPAEN2"));
        assertNotNull(body);

        withBlacklistApplied(new wuxia_city(), body);
        assertEquals(486, body.select("br").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(wuxia_city.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = wuxia_city.parseMetadata(novelPage());

        assertEquals("A Knight Who Eternally Regresses", metadata.getTitle());
        assertEquals("Soul Pung", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(5, metadata.getSubjects().size());
        assertTrue(wuxia_city.parseCoverUrl(novelPage()).startsWith("https://"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wuxia_city.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(wuxia_city.USER_AGENT).get();
        List<Chapter> chapters = wuxia_city.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(wuxia_city.USER_AGENT).get();
        assertNotNull(wuxia_city.parseChapterBody(chapterPage));
    }
}
