package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BetwixtedbutterflyComTest {

    private static final String DOMAIN = "betwixtedbutterfly.com";
    private static final String NOVEL_URL = "https://betwixtedbutterfly.com/translations/dpb/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasEachChapterOnceInOrder() throws Exception {
        // The chapter tabs link two chapters twice; each chapter should be downloaded once.
        List<Chapter> chapters = betwixtedbutterfly_com.parseChapterList(novelPage());

        assertEquals(168, chapters.size());
        assertEquals(168, new HashSet<>(chapters.stream().map(chapter -> chapter.chapterURL).toList()).size());
        assertEquals("https://betwixtedbutterfly.com/translations/dpb1", chapters.getFirst().chapterURL);
        assertEquals("https://betwixtedbutterfly.com/translations/death-progress-bar-200/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyKeepsTheTextWithoutTitleAndNavigation() throws Exception {
        Element body = betwixtedbutterfly_com.parseChapterBody(page(DOMAIN, "chapter.html", "https://betwixtedbutterfly.com/translations/dpb1/"));
        assertNotNull(body);

        withBlacklistApplied(new betwixtedbutterfly_com(), body);
        assertEquals(84, body.select("p").size());
        assertTrue(body.select("hr, h2, h3, nav").isEmpty());
        // Line 1 was the chapter title heading
        assertTrue(body.text().startsWith("Line 2."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(betwixtedbutterfly_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = betwixtedbutterfly_com.parseMetadata(novelPage());

        assertEquals("Death Progress Bar (生存進度條)", metadata.getTitle());
        assertFalse(metadata.getDescription().isBlank(), "the description is the text block without the Author line");
        assertFalse(metadata.getDescription().contains("Author"));
        assertEquals(6, metadata.getSubjects().size());
        assertEquals("https://betwixtedbutterfly.com/translations/wp-content/uploads/2019/01/4b2c98f5gy1fsrb1yq2ipj205k07sjs6.jpg",
                betwixtedbutterfly_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(betwixtedbutterfly_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(betwixtedbutterfly_com.USER_AGENT).get();
        List<Chapter> chapters = betwixtedbutterfly_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(betwixtedbutterfly_com.USER_AGENT).get();
        assertNotNull(betwixtedbutterfly_com.parseChapterBody(chapterPage));
    }
}
