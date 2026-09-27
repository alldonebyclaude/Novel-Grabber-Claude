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

class FanfiktionDeTest {

    private static final String DOMAIN = "fanfiktion.de";
    private static final String STORY_URL = "https://www.fanfiktion.de/s/5e84d98c00044a79d59d9af/1/Wonderland";

    // The story's first page is also its first chapter.
    private static Document storyPage() throws Exception {
        return page(DOMAIN, "story.html", STORY_URL);
    }

    @Test
    void chapterListComesFromTheChapterSelector() throws Exception {
        List<Chapter> chapters = fanfiktion_de.parseChapterList(storyPage());

        assertEquals(12, chapters.size());
        assertEquals("1. Vorwort", chapters.getFirst().name);
        assertEquals(STORY_URL, chapters.getFirst().chapterURL);
        assertEquals("12. Nachwort", chapters.getLast().name);
        assertEquals("https://www.fanfiktion.de/s/5e84d98c00044a79d59d9af/12/Wonderland", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheLines() throws Exception {
        Element body = fanfiktion_de.parseChapterBody(storyPage());
        assertNotNull(body);

        withBlacklistApplied(new fanfiktion_de(), body);
        assertEquals(84, body.select("br").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() {
        assertNull(fanfiktion_de.parseChapterBody(Jsoup.parse("<html><body><p>No story here.</p></body></html>")));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = fanfiktion_de.parseMetadata(storyPage());

        assertEquals("Wonderland", metadata.getTitle());
        assertEquals("IamRsDyRe", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank(), "the story's summary");
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(fanfiktion_de.class);
    }

    @Test
    @Tag("live")
    void live_fetchesStoryAndSecondChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document storyPage = Jsoup.connect(STORY_URL).userAgent(fanfiktion_de.USER_AGENT).get();
        List<Chapter> chapters = fanfiktion_de.parseChapterList(storyPage);
        assertTrue(chapters.size() > 1);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.get(1).chapterURL).userAgent(fanfiktion_de.USER_AGENT).get();
        assertNotNull(fanfiktion_de.parseChapterBody(chapterPage));
    }
}
