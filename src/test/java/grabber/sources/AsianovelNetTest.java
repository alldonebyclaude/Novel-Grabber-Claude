package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AsianovelNetTest {

    private static final String DOMAIN = "asianovel.net";
    private static final String STORY_URL = "https://www.asianovel.net/story/3861/";

    // The site answers plain requests with a Cloudflare bot check; the fixtures were saved from the app's
    // headless browser.
    private static Document storyPage() throws Exception {
        return page(DOMAIN, "story.html", STORY_URL);
    }

    @Test
    void chapterListInOrder() throws Exception {
        List<Chapter> chapters = asianovel_net.parseChapterList(storyPage());

        assertEquals(35, chapters.size());
        assertEquals("Chapter 1: Tempting a Monk of the Buddha, You Brought This Upon Yourself", chapters.getFirst().name);
        assertEquals("https://www.asianovel.net/chapter/chapter-920648/", chapters.getFirst().chapterURL);
        assertEquals("https://www.asianovel.net/chapter/chapter-920682/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyWithoutAdSlots() throws Exception {
        Element body = asianovel_net.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://www.asianovel.net/chapter/chapter-920648/"));
        assertNotNull(body);

        withBlacklistApplied(new asianovel_net(), body);
        assertTrue(body.select("[class*=asian-ads]").isEmpty(), "ad slots left in the text");
        assertEquals(95, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(asianovel_net.parseChapterBody(storyPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = asianovel_net.parseMetadata(storyPage());

        // The site writes the apostrophe as "#*#"
        assertEquals("The Buddhist Sect's Black Sheep", metadata.getTitle());
        assertEquals("临十", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
        assertEquals(List.of("BL", "Historical", "Romance", "Yaoi"), metadata.getSubjects());
        assertEquals("https://www.asianovel.net/wp-content/uploads/2026/09/3861.png", asianovel_net.parseCoverUrl(storyPage()));
    }

    @Test
    void apostrophesInTitlesArePutBack() {
        assertEquals("The Sect's Heir", asianovel_net.apostrophes("The Sect#*#s Heir"));
        assertEquals("No change", asianovel_net.apostrophes("No change"));
    }

    @Test
    void usesTheAppsBrowser() {
        assertTrue(new asianovel_net().canHeadless());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(asianovel_net.class);
    }

    @Test
    void aBlankPageHasNoChapters() {
        assertTrue(asianovel_net.parseChapterList(Jsoup.parse("<html><body></body></html>")).isEmpty());
    }
}
