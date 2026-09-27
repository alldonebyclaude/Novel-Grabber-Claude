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

class DummynovelsComTest {

    private static final String DOMAIN = "dummynovels.com";
    private static final String NOVEL_URL = "https://dummynovels.com/novel/a-healing-painter-matched-with-a-human-weapon/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasTheTranslatedChapters() throws Exception {
        // An ongoing translation with seven chapters so far; the accordion's heading is not a chapter.
        List<Chapter> chapters = dummynovels_com.parseChapterList(novelPage());

        assertEquals(7, chapters.size());
        assertEquals(NOVEL_URL + "chapter-1", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "chapter-7", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = dummynovels_com.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new dummynovels_com(), body);
        assertEquals(95, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(dummynovels_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = dummynovels_com.parseMetadata(novelPage());

        assertEquals("A Healing Painter Matched with a Human Weapon", metadata.getTitle());
        assertEquals("灰剑如羽", metadata.getAuthor(), "without the Author: label");
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(4, metadata.getSubjects().size());
        assertTrue(dummynovels_com.parseCoverUrl(novelPage()).startsWith("https://dummynovels.com/wp-content/uploads/"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(dummynovels_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(dummynovels_com.USER_AGENT).get();
        List<Chapter> chapters = dummynovels_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(dummynovels_com.USER_AGENT).get();
        assertNotNull(dummynovels_com.parseChapterBody(chapterPage));
    }
}
