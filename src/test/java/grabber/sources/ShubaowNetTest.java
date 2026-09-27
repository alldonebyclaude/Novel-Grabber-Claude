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

class ShubaowNetTest {

    private static final String DOMAIN = "shubaow.net";
    private static final String BOOK_URL = "https://www.shubaow.net/book/422.html";

    // The site's pages are GBK; the fixtures were converted to UTF-8.
    private static Document bookPage() throws Exception {
        return page(DOMAIN, "book.html", BOOK_URL);
    }

    @Test
    void chapterListIsTheFullDirectoryNotTheLatestChapterLink() throws Exception {
        // The fixture keeps the first 20 and last 5 of the book's 247 chapters.
        List<Chapter> chapters = shubaow_net.parseChapterList(bookPage());

        assertEquals(25, chapters.size());
        assertEquals("第1章 神武道惊鸿一瞥，一念桥逢魔遇仙", chapters.getFirst().name);
        assertEquals("https://www.shubaow.net/book/422/64946.html", chapters.getFirst().chapterURL);
        assertEquals("https://www.shubaow.net/book/422/65192.html", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyIsTheTextWithItsLineBreaks() throws Exception {
        Element body = shubaow_net.parseChapterBody(page(DOMAIN, "chapter-1.html", "https://www.shubaow.net/book/422/64946.html"));
        assertNotNull(body);

        withBlacklistApplied(new shubaow_net(), body);
        assertEquals(164, body.select("br").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(shubaow_net.parseChapterBody(bookPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = shubaow_net.parseMetadata(bookPage());

        assertEquals("天官赐福[新修版]", metadata.getTitle());
        assertEquals("墨香铜臭", metadata.getAuthor(), "without the 作者： label");
        assertTrue(metadata.getDescription().startsWith("Line 1."));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(shubaow_net.class);
    }

    @Test
    @Tag("live")
    void live_fetchesBookPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document bookPage = Jsoup.connect(BOOK_URL).userAgent(shubaow_net.USER_AGENT).timeout(60_000).get();
        List<Chapter> chapters = shubaow_net.parseChapterList(bookPage);
        assertTrue(chapters.size() >= 247);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(shubaow_net.USER_AGENT).timeout(60_000).get();
        assertNotNull(shubaow_net.parseChapterBody(chapterPage), "GBK decoded and text found");
    }
}
