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

class WoopreadComTest {

    private static final String DOMAIN = "woopread.com";
    private static final String SERIES_URL = "https://woopread.com/series/the-star-painting-genius";

    // The series page shows only a few chapters; the whole list is in its React Server Components payload
    // (self.__next_f.push scripts). The fixture keeps only the chapter list of that payload.
    private static Document seriesPage() throws Exception {
        return page(DOMAIN, "series.html", SERIES_URL);
    }

    @Test
    void chapterListIsTheWholeListFromThePageData() throws Exception {
        List<Chapter> chapters = woopread_com.parseChapterList(seriesPage());

        assertEquals(121, chapters.size());
        assertEquals("Chapter 1: The Shadow and the Swirl", chapters.getFirst().name);
        assertEquals(SERIES_URL + "/chapter-1-the-shadow-and-the-swirl", chapters.getFirst().chapterURL);
        assertEquals("Chapter 121: The Creation of the Rice Ball (1)", chapters.getLast().name);
        assertEquals(SERIES_URL + "/chapter-121-the-creation-of-the-rice-ball-1", chapters.getLast().chapterURL);
    }

    @Test
    void chapterListOfAPageWithoutDataIsEmpty() {
        assertTrue(woopread_com.parseChapterList(Jsoup.parse("<html><body>Not found</body></html>")).isEmpty());
        assertTrue(woopread_com.parseChapterList(Jsoup.parse(
                "<script>self.__next_f.push([1,\"5:{\\\"chapters\\\":[{\\\"broken\"])</script>")).isEmpty());
    }

    @Test
    void chapterBodyIsTheParagraphsWithoutTheirButtons() throws Exception {
        // The page streams the chapter into a hidden placeholder that its script then shows; that is not hidden text.
        // The fixture keeps the first 20 and last 5 of the chapter's 343 paragraphs.
        Element body = woopread_com.parseChapterBody(page(DOMAIN, "chapter-1.html",
                SERIES_URL + "/chapter-1-the-shadow-and-the-swirl"));
        assertNotNull(body);

        withBlacklistApplied(new woopread_com(), body);
        assertEquals(25, body.select("p").size());
        assertTrue(body.select("button").isEmpty(), "the paragraph buttons are left in the text");
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(woopread_com.parseChapterBody(seriesPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = woopread_com.parseMetadata(seriesPage());

        assertEquals("The Star-Painting Genius", metadata.getTitle());
        assertEquals("가화만사성™", metadata.getAuthor());
        assertEquals("Line 1.", metadata.getDescription(), "the tagline");
        assertEquals(List.of("Comedy", "Drama", "Slice of Life"), metadata.getSubjects());
        assertEquals("https://imgcdn.woopread.com/wp-content/uploads/2026/06/cover/FNoweePP8AA-1780422352.webp",
                woopread_com.parseCoverUrl(seriesPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(woopread_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesSeriesAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document seriesPage = Jsoup.connect(SERIES_URL).userAgent(woopread_com.USER_AGENT).get();
        List<Chapter> chapters = woopread_com.parseChapterList(seriesPage);
        assertTrue(chapters.size() >= 121);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(woopread_com.USER_AGENT).get();
        assertNotNull(woopread_com.parseChapterBody(chapterPage));
    }
}
