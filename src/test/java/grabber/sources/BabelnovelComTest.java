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

class BabelnovelComTest {

    private static final String DOMAIN = "babelnovel.com";
    private static final String BOOK_URL = "https://babelnovel.com/books/cold-showers";

    // The site answers plain requests with a Cloudflare block; the fixtures were saved from the app's headless
    // browser. The chapter list page renders from __NEXT_DATA__; its fixture keeps only the data the source reads.
    private static Document chapterListPage() throws Exception {
        return page(DOMAIN, "chapters.html", BOOK_URL + "/chapters");
    }

    @Test
    void theChapterListIsOnItsOwnPage() {
        assertEquals(BOOK_URL + "/chapters", babelnovel_com.chapterListUrl(BOOK_URL));
        assertEquals(BOOK_URL + "/chapters", babelnovel_com.chapterListUrl(BOOK_URL + "/chapters/c1"));
        assertNull(babelnovel_com.chapterListUrl("https://babelnovel.com/"));
    }

    @Test
    void chapterListHasEveryChapter() throws Exception {
        // The list doesn't say which chapters are locked; that shows on the chapter page.
        List<Chapter> chapters = babelnovel_com.parseChapterList(chapterListPage());

        assertEquals(52, chapters.size());
        assertEquals("C1", chapters.getFirst().name);
        assertEquals(BOOK_URL + "/chapters/c1", chapters.getFirst().chapterURL);
        assertEquals(BOOK_URL + "/chapters/c52", chapters.getLast().chapterURL);
    }

    @Test
    void aFreeChapterHasItsText() throws Exception {
        Document page = page(DOMAIN, "chapter-free.html", BOOK_URL + "/chapters/c1");
        assertFalse(babelnovel_com.isLocked(page));

        Element body = babelnovel_com.parseChapterBody(page);
        assertNotNull(body);
        withBlacklistApplied(new babelnovel_com(), body);
        assertTrue(body.select("h3").isEmpty(), "the chapter title is left in the text");
        assertEquals(165, body.select("p").size());
    }

    @Test
    void aLockedChapterIsNotDownloadedAsItsPreview() throws Exception {
        // A locked chapter shows a short preview under the unlock panel; that is not the chapter.
        Document page = page(DOMAIN, "chapter-locked.html", BOOK_URL + "/chapters/c52");

        assertTrue(babelnovel_com.isLocked(page));
        assertNull(babelnovel_com.parseChapterBody(page));
    }

    @Test
    void metadataComesFromThePageData() throws Exception {
        NovelMetadata metadata = babelnovel_com.parseMetadata(chapterListPage());

        assertEquals("Cold Showers", metadata.getTitle());
        assertEquals("Symplyayisha", metadata.getAuthor());
        assertEquals("Line 1.", metadata.getDescription());
        assertEquals(List.of("Billionaire"), metadata.getSubjects());
        assertEquals("https://img.babelchain.org/book_images/Cold_Showers.jpg", babelnovel_com.parseCoverUrl(chapterListPage()));
    }

    @Test
    void aPageWithoutDataHasNoChapters() {
        assertTrue(babelnovel_com.parseChapterList(Jsoup.parse("<html><body></body></html>")).isEmpty());
    }

    @Test
    void isMarkedAsAPaywallSiteAndUsesTheAppsBrowser() {
        assertTrue(new babelnovel_com().isPaywallSite());
        assertTrue(new babelnovel_com().canHeadless());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(babelnovel_com.class);
    }
}
