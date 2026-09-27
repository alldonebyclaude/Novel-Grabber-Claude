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

class ReadnovelfullComTest {

    private static final String DOMAIN = "readnovelfull.com";
    private static final String NOVEL_URL = "https://readnovelfull.com/advent-of-the-three-calamities.html";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsTheNovelIdForTheChapterList() throws Exception {
        assertEquals("2454", readnovelfull_com.parseNovelId(novelPage()));
        assertEquals("https://readnovelfull.com/ajax/chapter-archive?novelId=2454", readnovelfull_com.chapterListUrl("2454"));
    }

    @Test
    void chapterListHasEveryChapterInOrder() throws Exception {
        List<Chapter> chapters = readnovelfull_com.parseChapterList(
                page(DOMAIN, "chapters.html", readnovelfull_com.chapterListUrl("2454")));

        assertEquals(876, chapters.size());
        assertEquals("https://readnovelfull.com/advent-of-the-three-calamities/chapter-prologue-1.html", chapters.getFirst().chapterURL);
        assertEquals("https://readnovelfull.com/advent-of-the-three-calamities/chapter-876-restless-3.html", chapters.getLast().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = readnovelfull_com.parseChapterBody(
                page(DOMAIN, "chapter.html", "https://readnovelfull.com/advent-of-the-three-calamities/chapter-prologue-1.html"));
        assertNotNull(body);

        withBlacklistApplied(new readnovelfull_com(), body);
        assertEquals(155, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(readnovelfull_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = readnovelfull_com.parseMetadata(novelPage());

        assertEquals("Advent of the Three Calamities", metadata.getTitle());
        assertEquals("Entrail_JI", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(5, metadata.getSubjects().size());
        assertTrue(readnovelfull_com.parseCoverUrl(novelPage()).startsWith("https://img.readnovelfull.com/thumb/"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(readnovelfull_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelListAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(readnovelfull_com.USER_AGENT).get();
        String listUrl = readnovelfull_com.chapterListUrl(readnovelfull_com.parseNovelId(novelPage));

        Thread.sleep(1500);
        List<Chapter> chapters = readnovelfull_com.parseChapterList(Jsoup.connect(listUrl).userAgent(readnovelfull_com.USER_AGENT).get());
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(readnovelfull_com.USER_AGENT).get();
        assertNotNull(readnovelfull_com.parseChapterBody(chapterPage));
    }
}
