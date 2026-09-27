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

class ZhenhunxiaoshuoComTest {

    private static final String DOMAIN = "zhenhunxiaoshuo.com";
    private static final String BOOK_URL = "https://www.zhenhunxiaoshuo.com/yanhui/";

    private static Document bookPage() throws Exception {
        return page(DOMAIN, "book.html", BOOK_URL);
    }

    @Test
    void chapterListInOrder() throws Exception {
        List<Chapter> chapters = zhenhunxiaoshuo_com.parseChapterList(bookPage());

        assertEquals(24, chapters.size());
        assertEquals("第一章", chapters.getFirst().name);
        assertEquals("https://www.zhenhunxiaoshuo.com/355587.html", chapters.getFirst().chapterURL);
        assertEquals("https://www.zhenhunxiaoshuo.com/355631.html", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheParagraphs() throws Exception {
        Element body = zhenhunxiaoshuo_com.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://www.zhenhunxiaoshuo.com/355587.html"));
        assertNotNull(body);

        withBlacklistApplied(new zhenhunxiaoshuo_com(), body);
        assertEquals(87, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(zhenhunxiaoshuo_com.parseChapterBody(bookPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = zhenhunxiaoshuo_com.parseMetadata(bookPage());

        assertEquals("烟灰", metadata.getTitle());
        assertTrue(metadata.getDescription().startsWith("Line 1."));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(zhenhunxiaoshuo_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesBookPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document bookPage = Jsoup.connect(BOOK_URL).userAgent(zhenhunxiaoshuo_com.USER_AGENT).get();
        List<Chapter> chapters = zhenhunxiaoshuo_com.parseChapterList(bookPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(zhenhunxiaoshuo_com.USER_AGENT).get();
        assertNotNull(zhenhunxiaoshuo_com.parseChapterBody(chapterPage));
    }
}
