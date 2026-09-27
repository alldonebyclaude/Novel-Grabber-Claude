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

class InkittComTest {

    private static final String DOMAIN = "inkitt.com";
    private static final String STORY_URL = "https://www.inkitt.com/stories/1720715";

    // The site answers plain requests with a Cloudflare bot check; the fixtures were saved from the app's
    // headless browser.
    private static Document storyPage() throws Exception {
        return page(DOMAIN, "story.html", STORY_URL);
    }

    @Test
    void chapterListUsesTheChapterTitlesWithoutTheirNumbers() throws Exception {
        List<Chapter> chapters = inkitt_com.parseChapterList(storyPage());

        assertEquals(16, chapters.size());
        assertEquals("Chapter 1", chapters.getFirst().name);
        assertEquals(STORY_URL + "/chapters/1", chapters.getFirst().chapterURL);
        assertEquals(STORY_URL + "/chapters/16", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = inkitt_com.parseChapterBody(page(DOMAIN, "chapter-1.html", STORY_URL + "/chapters/1"));
        assertNotNull(body);

        withBlacklistApplied(new inkitt_com(), body);
        assertEquals(207, body.select("p").size());
        assertTrue(body.text().startsWith("Line "));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() {
        // (The story page itself shows the first chapter, so it has a chapter body.)
        assertNull(inkitt_com.parseChapterBody(Jsoup.parse("<html><body><div class=\"story-title\">x</div></body></html>")));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = inkitt_com.parseMetadata(storyPage());

        assertEquals("[Moving to Galatea] What We Never Healed", metadata.getTitle());
        assertEquals("Ava Reed", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
        assertEquals("https://cdn-gcs.inkitt.com/vertical_storycovers/ipad_b00b828931c645f2eccdf3396cb15498.jpg",
                inkitt_com.parseCoverUrl(storyPage()), "the cover from the background image");
    }

    @Test
    void coverFallsBackToTheSharingImage() {
        Document page = Jsoup.parse("<meta property=\"og:image\" content=\"https://cdn-gcs.inkitt.com/storycovers/x.jpg\">");
        assertEquals("https://cdn-gcs.inkitt.com/storycovers/x.jpg", inkitt_com.parseCoverUrl(page));
    }

    @Test
    void usesTheAppsBrowser() {
        assertTrue(new inkitt_com().canHeadless());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(inkitt_com.class);
    }
}
