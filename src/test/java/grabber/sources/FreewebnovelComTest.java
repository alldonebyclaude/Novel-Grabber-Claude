package grabber.sources;

import grabber.Chapter;
import grabber.Driver;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class FreewebnovelComTest {

    private static final String DOMAIN = "freewebnovel.com";
    private static final String NOVEL_URL = "https://freewebnovel.com/novel/the-prodigies-war";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    private static Document chapterPage() throws Exception {
        return page(DOMAIN, "chapter.html", NOVEL_URL + "/chapter-1");
    }

    @Test
    void chapterListComesFromTheReadersChapterListRequest() throws Exception {
        // The fixture keeps the first three and the last two of the 3240 chapters the site returned.
        List<Chapter> chapters = freewebnovel_com.parseChapterList(text(DOMAIN, "chapterlist.json"));

        assertEquals(5, chapters.size());
        assertEquals("CH.1: Youngster Lin Xun", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "/chapter-1", chapters.getFirst().chapterURL);
        assertEquals("CH.3240 Finale", chapters.getLast().name);
        assertEquals(NOVEL_URL + "/chapter-3240", chapters.getLast().chapterURL);
    }

    @Test
    void chapterListOfAnUnexpectedResponseIsEmpty() {
        assertTrue(freewebnovel_com.parseChapterList("{\"error\":\"invalid request\"}").isEmpty());
        assertTrue(freewebnovel_com.parseChapterList("<html><title>Just a moment...</title></html>").isEmpty());
    }

    @Test
    void theChapterPageSaysHowToRequestTheList() throws Exception {
        assertEquals(Map.of("aid", "3168", "acode", "the-prodigies-war", "cid", "1"),
                freewebnovel_com.parseChapterListRequest(chapterPage()));
        assertTrue(freewebnovel_com.parseChapterListRequest(novelPage()).isEmpty());
    }

    @Test
    void novelPageListsOnlyTheFirstFortyChapters() throws Exception {
        // Its page selector sends every page back to the first one; this list is the fallback when the browser
        // cannot run the chapter list request.
        List<Chapter> chapters = freewebnovel_com.parseFirstChapters(novelPage());

        assertEquals(40, chapters.size());
        assertEquals(NOVEL_URL + "/chapter-1", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "/chapter-40", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAdSlots() throws Exception {
        Element body = freewebnovel_com.parseChapterBody(chapterPage());
        assertNotNull(body);

        withBlacklistApplied(new freewebnovel_com(), body);
        assertEquals(64, body.select("p").size());
        assertTrue(body.select(".reader-ad-skip, script").isEmpty());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(freewebnovel_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = freewebnovel_com.parseMetadata(novelPage());

        assertEquals("The Prodigies War", metadata.getTitle());
        assertEquals("Xiao Jinyu", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(List.of("Action", "Adventure", "Fantasy", "Martial arts", "Supernatural", "Xuanhuan"), metadata.getSubjects());
        assertEquals("https://freewebnovel.com/files/article/image/3/3168/3168s.jpg", freewebnovel_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(freewebnovel_com.class);
    }

    @Test
    @Tag("live")
    void live_readsTheListAndFirstChapterThroughTheHeadlessBrowser() {
        // The site answers plain requests with a bot check, so this goes through the app's headless browser:
        // the novel page, chapter 1 (with the chapter list request) and chapter 2.
        Novel novel = new Novel();
        novel.novelLink = NOVEL_URL;
        novel.window = "test";
        novel.browser = "Headless";
        freewebnovel_com source = new freewebnovel_com(novel);
        try {
            List<Chapter> chapters = source.getChapterList();
            assertTrue(chapters.size() > 3000, chapters.size() + " chapters");
            assertNotNull(source.getChapterContent(chapters.get(1)));
            assertEquals("The Prodigies War", source.getMetadata().getTitle());
        } finally {
            Driver driver = novel.headlessDriver;
            if (driver != null) driver.close();
        }
    }
}
