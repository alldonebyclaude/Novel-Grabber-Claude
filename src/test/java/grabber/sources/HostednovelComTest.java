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

class HostednovelComTest {

    private static final String DOMAIN = "hostednovel.com";
    private static final String NOVEL_URL = "https://hostednovel.com/novel/after-being-bent-by-reader";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListPageListsItsChaptersInOrder() throws Exception {
        // The novel page shows 100 chapters at a time; the "start reading" button is not part of the list.
        List<Chapter> chapters = hostednovel_com.parseChapterList(novelPage());

        assertEquals(100, chapters.size());
        assertEquals("Chapter 1", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "/chapter-1", chapters.getFirst().chapterURL);
        // Longer chapters are split into parts, so 100 entries end partway through chapter 62
        assertEquals("Chapter 62-Pt.1", chapters.getLast().name);
        assertEquals(NOVEL_URL + "/chapter-62.1", chapters.getLast().chapterURL);
    }

    @Test
    void chapterListContinuesOnTheNextPage() throws Exception {
        assertEquals(NOVEL_URL + "?page=2", hostednovel_com.parseNextPageUrl(novelPage()));

        Document lastPage = page(DOMAIN, "novel-page-2.html", NOVEL_URL + "?page=2");
        assertNull(hostednovel_com.parseNextPageUrl(lastPage));
        List<Chapter> chapters = hostednovel_com.parseChapterList(lastPage);
        assertEquals(89, chapters.size());
        assertEquals("Chapter 62-Pt.2", chapters.getFirst().name);
        assertEquals("Chapter 106-Pt.2 (Grand Finale)", chapters.getLast().name);
        assertEquals(NOVEL_URL + "/chapter-106.2", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAdSlots() throws Exception {
        Element body = hostednovel_com.parseChapterBody(page(DOMAIN, "chapter-1.html", NOVEL_URL + "/chapter-1"));
        assertNotNull(body);

        withBlacklistApplied(new hostednovel_com(), body);
        assertEquals(92, body.select("p").size());
        assertTrue(body.select("[id^=ezoic]").isEmpty(), "ad slots left in the text");
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(hostednovel_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = hostednovel_com.parseMetadata(novelPage());

        assertEquals("After Being Bent by Reader (GL)", metadata.getTitle());
        assertEquals("Inverse Sign", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the synopsis");
        assertEquals(List.of("Girls' Love (GL)", "Romance", "City Life"), metadata.getSubjects());
        assertEquals("https://www.hostednovelcdn.com/covers/jpUDitlBpb5J4qlQKR3GvobV5JfNWRPdQGgb40su.jpg",
                hostednovel_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(hostednovel_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesBothListPagesAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(hostednovel_com.USER_AGENT).get();
        List<Chapter> chapters = hostednovel_com.parseChapterList(novelPage);
        assertEquals(100, chapters.size());

        Thread.sleep(1500);
        Document secondPage = Jsoup.connect(hostednovel_com.parseNextPageUrl(novelPage)).userAgent(hostednovel_com.USER_AGENT).get();
        assertFalse(hostednovel_com.parseChapterList(secondPage).isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(hostednovel_com.USER_AGENT).get();
        assertNotNull(hostednovel_com.parseChapterBody(chapterPage));
    }
}
