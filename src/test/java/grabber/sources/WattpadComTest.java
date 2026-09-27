package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WattpadComTest {

    private static final String DOMAIN = "wattpad.com";
    private static final String STORY_URL = "https://www.wattpad.com/story/90623050";

    // The story page renders from window.__remixContext; the fixture keeps only the story's data.
    private static Document storyPage() throws Exception {
        return page(DOMAIN, "story.html", STORY_URL);
    }

    @Test
    void chapterListHasTheFreePartsOnly() throws Exception {
        // A paid story: 11 of its 37 parts are free, the other 26 are paywalled.
        List<Chapter> chapters = wattpad_com.parseChapterList(storyPage());

        assertEquals(11, chapters.size());
        assertEquals("Bite Me and Love Bites Coming to Bookstores!!!", chapters.getFirst().name);
        assertEquals("https://www.wattpad.com/1535194515-bite-me-and-love-bites-coming-to-bookstores",
                chapters.getFirst().chapterURL);
        assertEquals(26, wattpad_com.countLockedParts(storyPage()));
    }

    @Test
    void chapterListOfAPageWithoutStoryDataIsEmpty() {
        assertTrue(wattpad_com.parseChapterList(Jsoup.parse("<html><body>Not found</body></html>")).isEmpty());
        assertEquals(0, wattpad_com.countLockedParts(Jsoup.parse("<script>window.__remixContext = {nope</script>")));
    }

    @ParameterizedTest
    @CsvSource({
            "https://www.wattpad.com/348867055-bite-me-chapter-1, 348867055",
            "https://wattpad.com/348867055-bite-me-chapter-1, 348867055",
            "https://www.wattpad.com/348867055, 348867055"})
    void partIdComesFromThePartUrl(String partUrl, String partId) {
        assertEquals(partId, wattpad_com.partId(partUrl));
    }

    @Test
    void theTextIsFetchedFromTheUrlThePartsApiGives() {
        String json = "{\"text_url\":{\"text\":\"https://www.wattpad.com/apiv2/?m=storytext&id=348867055&page=\","
                + "\"refresh_token\":\"x\"}}";
        assertEquals("https://www.wattpad.com/apiv2/?m=storytext&id=348867055&page=", wattpad_com.parseTextUrl(json));
        assertNull(wattpad_com.parseTextUrl("{\"error_type\":\"NotFound\"}"));
        assertNull(wattpad_com.parseTextUrl("not json"));
    }

    @Test
    void chapterBodyIsThePartsParagraphs() throws Exception {
        Element body = wattpad_com.parseChapterBody(text(DOMAIN, "part-text.html"));
        assertNotNull(body);

        withBlacklistApplied(new wattpad_com(), body);
        assertEquals(68, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAnEmptyResponseIsNull() {
        assertNull(wattpad_com.parseChapterBody(""));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = wattpad_com.parseMetadata(storyPage());

        assertEquals("Bite Me", metadata.getTitle());
        assertEquals("alicia", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."));
        assertEquals(25, metadata.getSubjects().size());
        assertEquals("alpha", metadata.getSubjects().getFirst());
        assertEquals("https://img.wattpad.com/cover/90623050-256-k846633.jpg", wattpad_com.parseCoverUrl(storyPage()));
    }

    @Test
    void isMarkedAsAPaywallSite() {
        assertTrue(new wattpad_com().isPaywallSite());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wattpad_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesStoryAndFirstFreePart() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document storyPage = Jsoup.connect(STORY_URL).userAgent(wattpad_com.USER_AGENT).get();
        List<Chapter> chapters = wattpad_com.parseChapterList(storyPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        String json = Jsoup.connect("https://www.wattpad.com/v4/parts/" + wattpad_com.partId(chapters.getFirst().chapterURL)
                + "?fields=text_url").userAgent(wattpad_com.USER_AGENT).ignoreContentType(true).execute().body();
        String textUrl = wattpad_com.parseTextUrl(json);
        assertNotNull(textUrl);

        Thread.sleep(1500);
        String text = Jsoup.connect(textUrl).userAgent(wattpad_com.USER_AGENT).ignoreContentType(true).execute().body();
        assertNotNull(wattpad_com.parseChapterBody(text));
    }
}
