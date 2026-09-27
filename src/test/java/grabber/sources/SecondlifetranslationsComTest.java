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

class SecondlifetranslationsComTest {

    private static final String DOMAIN = "secondlifetranslations.com";
    private static final String NOVEL_URL = "https://secondlifetranslations.com/novel/sit/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListInOrder() throws Exception {
        List<Chapter> chapters = secondlifetranslations_com.parseChapterList(novelPage());

        assertEquals(34, chapters.size());
        assertEquals("Chapter 1: A Little Fool", chapters.getFirst().name);
        assertEquals("https://secondlifetranslations.com/sit/sit-chapter-1/", chapters.getFirst().chapterURL);
        assertEquals("https://secondlifetranslations.com/sit/sit-chapter-34/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyWithoutAdsOrTheSitesDisclaimer() throws Exception {
        Element body = secondlifetranslations_com.parseChapterBody(
                page(DOMAIN, "chapter-1.html", "https://secondlifetranslations.com/sit/sit-chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new secondlifetranslations_com(), body);
        // A visible notice about where the translation is published sits in the middle of the text
        assertTrue(body.select(".jmbl-disclaimer, .jmbl, .code-block, .sharedaddy").isEmpty(),
                "notice, markers, ads or like buttons left in the text");
        assertFalse(body.text().toLowerCase().contains("support"), "the notice's text is left");
        assertEquals(85, body.select("> p").size());
        assertEquals(1, body.select("> ul").size(), "the translator's notes stay");
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(secondlifetranslations_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = secondlifetranslations_com.parseMetadata(novelPage());

        assertEquals("Stepping into the Trap: The Unbridled Boss Yields Everytime", metadata.getTitle());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the synopsis");
        assertEquals(List.of("Drama", "Modern", "Romance"), metadata.getSubjects());
        assertEquals("https://secondlifetranslations.com/wp-content/uploads/2026/07/p2ob4d2fe75193625f6bd459bbe2356bb6dtplv-resize_225_300.jpg",
                secondlifetranslations_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(secondlifetranslations_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(secondlifetranslations_com.USER_AGENT).get();
        List<Chapter> chapters = secondlifetranslations_com.parseChapterList(novelPage);
        assertTrue(chapters.size() >= 34);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL)
                .userAgent(secondlifetranslations_com.USER_AGENT).get();
        assertNotNull(secondlifetranslations_com.parseChapterBody(chapterPage));
    }
}
