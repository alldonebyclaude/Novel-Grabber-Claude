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

class LnmtlComTest {

    private static final String DOMAIN = "lnmtl.com";
    private static final String NOVEL_URL = "https://lnmtl.com/novel/above-the-skies";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsTheVolumesFromTheNovelPage() throws Exception {
        List<String> volumeIds = lnmtl_com.parseVolumeIds(novelPage());

        assertEquals(22, volumeIds.size());
        assertEquals("5538", volumeIds.getFirst());
        assertEquals("https://lnmtl.com/chapter?page=2&volumeId=5538", lnmtl_com.chapterListUrl("5538", 2));
    }

    @Test
    void chapterListPageAndItsSuccessor() throws Exception {
        // The endpoint returns 50 chapters per page and a next_page_url until the volume's last page.
        String json = text(DOMAIN, "chapters-volume.json");
        List<Chapter> chapters = lnmtl_com.parseChapterListPage(json);

        assertEquals(50, chapters.size());
        assertEquals("https://lnmtl.com/chapter/above-the-skies-book-1-chapter-1", chapters.getFirst().chapterURL);
        assertFalse(chapters.getFirst().name.isBlank());
        assertTrue(lnmtl_com.hasNextPage(json));
        assertFalse(lnmtl_com.hasNextPage("{\"data\":[],\"next_page_url\":null}"));
    }

    @Test
    void chapterListPageOfAnUnexpectedResponseIsEmpty() {
        assertTrue(lnmtl_com.parseChapterListPage("<html>not json</html>").isEmpty());
        assertFalse(lnmtl_com.hasNextPage("<html>not json</html>"));
    }

    @Test
    void chapterBodyHasOneParagraphPerTranslatedSentence() throws Exception {
        // The page shows each sentence twice: machine translated and original. Dialogue (<dq>) is shown in
        // italics and <sq> in bold; keep that instead of flattening the sentences to plain text.
        Element body = lnmtl_com.parseChapterBody(page(DOMAIN, "chapter.html", "https://lnmtl.com/chapter/above-the-skies-book-1-chapter-1"));
        assertNotNull(body);

        withBlacklistApplied(new lnmtl_com(), body);
        assertEquals(90, body.select("p").size());
        assertEquals(64, body.select("p em").size());
        assertEquals(1, body.select("p strong").size());
        assertTrue(body.select("sentence, dq, sq, t, w").isEmpty(), "site-specific tags left in the text");
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(lnmtl_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = lnmtl_com.parseMetadata(novelPage());

        assertEquals("Above the Skies", metadata.getTitle());
        assertEquals("Watching Chess", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertTrue(metadata.getSubjects().isEmpty(), "the page no longer lists genres or tags");
        assertEquals("https://lnmtl.com/upload/novel/304.jpg", lnmtl_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(lnmtl_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelFirstListPageAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(lnmtl_com.USER_AGENT).get();
        String listUrl = lnmtl_com.chapterListUrl(lnmtl_com.parseVolumeIds(novelPage).getFirst(), 1);

        Thread.sleep(1500);
        String json = Jsoup.connect(listUrl).userAgent(lnmtl_com.USER_AGENT).ignoreContentType(true).execute().body();
        List<Chapter> chapters = lnmtl_com.parseChapterListPage(json);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(lnmtl_com.USER_AGENT).get();
        assertNotNull(lnmtl_com.parseChapterBody(chapterPage));
    }
}
