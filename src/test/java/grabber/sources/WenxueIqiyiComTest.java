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

class WenxueIqiyiComTest {

    private static final String DOMAIN = "wenxue.iqiyi.com";
    private static final String BOOK_URL = "https://wenxue.iqiyi.com/book/detail-11k4akm8i41.html";
    private static final String CATALOG_URL = "https://wenxue.iqiyi.com/book/catalog-11k4akm8i41-1.html";

    private static Document bookPage() throws Exception {
        return page(DOMAIN, "book.html", BOOK_URL);
    }

    private static Document catalog() throws Exception {
        return page(DOMAIN, "catalog.html", CATALOG_URL);
    }

    @Test
    void theBookPageLinksTheWholeCatalog() throws Exception {
        assertEquals(CATALOG_URL, wenxue_iqiyi_com.parseCatalogUrl(bookPage()));
    }

    @Test
    void chapterListHasTheFreeChaptersOnly() throws Exception {
        // 9 free chapters; the other 57 are VIP chapters, marked with a diamond.
        List<Chapter> chapters = wenxue_iqiyi_com.parseChapterList(catalog());

        assertEquals(9, chapters.size());
        assertEquals("楔子", chapters.getFirst().name);
        assertEquals("https://wenxue.iqiyi.com/book/reader-11k4akm8i41-1oei99hrz87.html", chapters.getFirst().chapterURL);
        assertEquals(57, wenxue_iqiyi_com.countVipChapters(catalog()));
    }

    @Test
    void aCatalogOnOnePageHasNoNextPage() throws Exception {
        assertNull(wenxue_iqiyi_com.parseNextPageUrl(catalog()));
        Document paged = Jsoup.parse("<div class=\"mod-page\"><a href=\"/book/catalog-x-1.html\">上一页</a>"
                + "<a href=\"/book/catalog-x-3.html\">下一页</a></div>", "https://wenxue.iqiyi.com/book/catalog-x-2.html");
        assertEquals("https://wenxue.iqiyi.com/book/catalog-x-3.html", wenxue_iqiyi_com.parseNextPageUrl(paged));
    }

    @Test
    void chapterBodyKeepsTheParagraphs() throws Exception {
        Element body = wenxue_iqiyi_com.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://wenxue.iqiyi.com/book/reader-11k4akm8i41-1oei99hrz87.html"));
        assertNotNull(body);

        withBlacklistApplied(new wenxue_iqiyi_com(), body);
        assertEquals(10, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(wenxue_iqiyi_com.parseChapterBody(catalog()));
    }

    @Test
    void metadataIsTheBooksOwnNotARecommendedBooks() throws Exception {
        NovelMetadata metadata = wenxue_iqiyi_com.parseMetadata(bookPage());

        assertEquals("大生意人", metadata.getTitle());
        assertEquals("赵之羽", metadata.getAuthor(), "the side bar lists other books' authors too");
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the briefing");
        assertEquals("https://pic8.iqiyipic.com/image/20250529/c1/fd/bk_3939937831827139_r_601_m1.jpg",
                wenxue_iqiyi_com.parseCoverUrl(bookPage()));
    }

    @Test
    void isMarkedAsAPaywallSite() {
        assertTrue(new wenxue_iqiyi_com().isPaywallSite());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wenxue_iqiyi_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesCatalogAndFirstFreeChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document catalog = Jsoup.connect(CATALOG_URL).userAgent(wenxue_iqiyi_com.USER_AGENT).get();
        List<Chapter> chapters = wenxue_iqiyi_com.parseChapterList(catalog);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(wenxue_iqiyi_com.USER_AGENT).get();
        assertNotNull(wenxue_iqiyi_com.parseChapterBody(chapterPage));
    }
}
