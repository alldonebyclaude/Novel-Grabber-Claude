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

class MoonquillnovelsComTest {

    private static final String DOMAIN = "moonquillnovels.com";
    private static final String BOOK_URL = "https://moonquillnovels.com/book/the-ballad-of-omega";

    private static Document bookPage() throws Exception {
        return page(DOMAIN, "book.html", BOOK_URL);
    }

    @Test
    void chapterListIsTheTableOfContentsTab() throws Exception {
        List<Chapter> chapters = moonquillnovels_com.parseChapterList(bookPage());

        assertEquals(37, chapters.size(), "the page says 37 Chapters");
        assertEquals("1- His World", chapters.getFirst().name);
        assertEquals(BOOK_URL + "/chapter/37864/1-his-world", chapters.getFirst().chapterURL);
        assertEquals("37- The Tower II", chapters.getLast().name);
    }

    @Test
    void chapterBodyKeepsTheText() throws Exception {
        Element body = moonquillnovels_com.parseChapterBody(
                page(DOMAIN, "chapter-1.html", BOOK_URL + "/chapter/37864/1-his-world"));
        assertNotNull(body);

        withBlacklistApplied(new moonquillnovels_com(), body);
        assertEquals(93, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(moonquillnovels_com.parseChapterBody(bookPage()));
    }

    @Test
    void metadataIsTheBooksOwnNotARecommendedBooks() throws Exception {
        NovelMetadata metadata = moonquillnovels_com.parseMetadata(bookPage());

        assertEquals("The Ballad of Omega", metadata.getTitle());
        assertEquals("Feragon42", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the synopsis");
        // Recommended books further down the page have genre badges too
        assertEquals(List.of("Mystery", "Adventure", "Fantasy", "Psychological", "Slice of life"), metadata.getSubjects());
        assertEquals("https://st.moonquillnovels.com/cover/1951-0", moonquillnovels_com.parseCoverUrl(bookPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(moonquillnovels_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesBookPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document bookPage = Jsoup.connect(BOOK_URL).userAgent(moonquillnovels_com.USER_AGENT).get();
        List<Chapter> chapters = moonquillnovels_com.parseChapterList(bookPage);
        assertTrue(chapters.size() >= 37);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(moonquillnovels_com.USER_AGENT).get();
        assertNotNull(moonquillnovels_com.parseChapterBody(chapterPage));
    }
}
