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

class BooknetComTest {

    private static final String DOMAIN = "booknet.com";
    private static final String NOVEL_URL = "https://booknet.com/es/book/casarse-a-ciegas-b472739";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasOnlyTheChaptersTheReaderCanOpen() throws Exception {
        // BookNet sells chapters. The page, fetched without a login, marks 28 of the 38 chapters as locked;
        // those are left out instead of downloaded as paywall pages.
        Document novelPage = novelPage();
        List<Chapter> chapters = booknet_com.parseChapterList(novelPage);

        assertEquals(10, chapters.size());
        assertEquals(28, booknet_com.countLockedChapters(novelPage));
        assertEquals("https://booknet.com/es/reader/casarse-a-ciegas-b472739?c=5132817", chapters.getFirst().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyIsTheReaderText() throws Exception {
        Element body = booknet_com.parseChapterBody(page(DOMAIN, "chapter.html", "https://booknet.com/es/reader/casarse-a-ciegas-b472739?c=5132817"));
        assertNotNull(body);

        withBlacklistApplied(new booknet_com(), body);
        assertEquals(33, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(booknet_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = booknet_com.parseMetadata(novelPage());

        assertEquals("Casarse a ciegas", metadata.getTitle());
        assertEquals("Anna Románova", metadata.getAuthor(), "without the role label");
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(List.of("Novela romántica", "traicion", "matrimonio por contrato", "segunda oportunidad venganza"),
                metadata.getSubjects());
        assertEquals("https://st.booknet.com/uploads/covers/220/1771413540_91.png", booknet_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(booknet_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(booknet_com.USER_AGENT).get();
        List<Chapter> chapters = booknet_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(booknet_com.USER_AGENT).get();
        assertNotNull(booknet_com.parseChapterBody(chapterPage));
    }
}
