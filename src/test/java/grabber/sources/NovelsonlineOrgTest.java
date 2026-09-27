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

class NovelsonlineOrgTest {

    private static final String DOMAIN = "novelsonline.org";
    private static final String NOVEL_URL = "https://novelsonline.org/in-a-different-world-with-a-smartphone-ln/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasEveryVolumeInReadingOrder() throws Exception {
        // One tab per volume; the page also has accordion toggles (#collapse-N) that are not chapters.
        List<Chapter> chapters = novelsonline_org.parseChapterList(novelPage());

        assertEquals(275, chapters.size());
        assertEquals(NOVEL_URL + "volume-1/chapter-pr", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "volume-30/chapter-aft", chapters.getLast().chapterURL);
        assertTrue(chapters.stream().allMatch(chapter -> chapter.chapterURL.startsWith(NOVEL_URL + "volume-")));
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAds() throws Exception {
        Element body = novelsonline_org.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "volume-1/chapter-pr"));
        assertNotNull(body);

        withBlacklistApplied(new novelsonline_org(), body);
        assertTrue(body.select(".ad1, noscript").isEmpty(), "ad slot left in the text");
        // Line 1 was a <noscript> "Your browser does not support JavaScript!" notice
        assertTrue(body.text().startsWith("Line 2."));
        assertEquals(8, body.select("p").size());
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(novelsonline_org.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = novelsonline_org.parseMetadata(novelPage());

        assertEquals("In a Different World with a Smartphone (LN)", metadata.getTitle());
        assertEquals("Fuyuhara Patora", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(10, metadata.getSubjects().size());
        assertEquals("https://novelsonline.org/uploads/posters/1613761461.jpg", novelsonline_org.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(novelsonline_org.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(novelsonline_org.USER_AGENT).get();
        List<Chapter> chapters = novelsonline_org.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(novelsonline_org.USER_AGENT).get();
        assertNotNull(novelsonline_org.parseChapterBody(chapterPage));
    }
}
