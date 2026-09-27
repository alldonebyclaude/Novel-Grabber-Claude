package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PiaotiaComTest {

    private static final String DOMAIN = "piaotia.com";
    private static final String BOOK_URL = "https://www.piaotia.com/bookinfo/2/2072.html";
    private static final String INDEX_URL = "https://www.piaotia.com/html/2/2072/index.html";

    // The site's pages are GBK; the fixtures were converted to UTF-8.

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.piaotia.com/bookinfo/2/2072.html",
            "https://www.piaotia.com/html/2/2072/",
            "https://www.piaotia.com/html/2/2072/index.html",
            "https://www.piaotia.com/html/2/2072/896896.html",
            "https://www.ptwxz.com/bookinfo/2/2072.html"})
    void bookAndIndexPagesComeFromAnyLinkToTheBook(String link) {
        assertEquals(BOOK_URL, piaotia_com.bookPageUrl(link));
        assertEquals(INDEX_URL, piaotia_com.indexUrl(link));
    }

    @Test
    void linksThatAreNotABookHaveNoPages() {
        assertNull(piaotia_com.bookPageUrl("https://www.piaotia.com/"));
        assertNull(piaotia_com.indexUrl("https://www.piaotia.com/"));
    }

    @Test
    void chapterListIsTheIndexInOrder() throws Exception {
        // The fixture keeps the first 30 and last 10 of the novel's 4258 chapters.
        List<Chapter> chapters = piaotia_com.parseChapterList(page(DOMAIN, "index.html", INDEX_URL));

        assertEquals(40, chapters.size());
        assertEquals("https://www.piaotia.com/html/2/2072/896896.html", chapters.getFirst().chapterURL);
        assertEquals("https://www.piaotia.com/html/2/2072/13115787.html", chapters.getLast().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyIsTheTextBetweenTheNavigationBars() throws Exception {
        // The text is loose in <body>, between the top navigation and the bottom links.
        Element body = piaotia_com.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://www.piaotia.com/html/2/2072/896896.html"));
        assertNotNull(body);

        withBlacklistApplied(new piaotia_com(), body);
        assertTrue(body.text().startsWith("Line 1."), body.text().substring(0, 20));
        assertTrue(body.text().endsWith("Line 41."));
        assertTrue(body.children().stream().allMatch(child -> child.normalName().equals("br")),
                "only lines and line breaks, no title or page parts");
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(piaotia_com.parseChapterBody(page(DOMAIN, "index.html", INDEX_URL)));
    }

    @Test
    void metadata() throws Exception {
        Document bookPage = page(DOMAIN, "book.html", BOOK_URL);
        NovelMetadata metadata = piaotia_com.parseMetadata(bookPage);

        assertEquals("混沌剑神", metadata.getTitle());
        assertEquals("心星逍遥", metadata.getAuthor(), "without the 作 者： label");
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the introduction, without its label");
        assertEquals("https://www.piaotia.com/files/article/image/2/2072/2072s.jpg", piaotia_com.parseCoverUrl(bookPage));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(piaotia_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesIndexAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document index = Jsoup.connect(INDEX_URL).userAgent(piaotia_com.USER_AGENT).get();
        List<Chapter> chapters = piaotia_com.parseChapterList(index);
        assertTrue(chapters.size() >= 4258);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(piaotia_com.USER_AGENT).get();
        assertNotNull(piaotia_com.parseChapterBody(chapterPage), "GBK decoded and text found");
    }
}
