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

class WuxiaworldSiteTest {

    private static final String DOMAIN = "wuxiaworld.site";
    private static final String NOVEL_URL = "https://wuxiaworld.site/novel/complete-martial-arts-attributes-spp/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListComesFromTheNovelsAjaxUrl() {
        assertEquals(NOVEL_URL + "ajax/chapters/", wuxiaworld_site.chapterListUrl(NOVEL_URL));
    }

    @Test
    void chapterListIsOldestFirst() throws Exception {
        // The site lists the newest chapter first. The fixture keeps the 5 newest and 5 oldest of 3866 entries;
        // newer chapters' URLs lack the "chapter-" prefix.
        List<Chapter> chapters = wuxiaworld_site.parseChapterList(page(DOMAIN, "chapters.html", NOVEL_URL + "ajax/chapters/"));

        assertEquals(10, chapters.size());
        assertEquals("Chapter 1", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "chapter-1/", chapters.getFirst().chapterURL);
        assertEquals("Chapter 3865", chapters.getLast().name);
        assertEquals(NOVEL_URL + "3865/", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyWithoutTitleOrCredits() throws Exception {
        Element body = wuxiaworld_site.parseChapterBody(page(DOMAIN, "chapter-1.html", NOVEL_URL + "chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new wuxiaworld_site(), body);
        assertTrue(body.select("h3").isEmpty(), "the chapter title is left in the text");
        assertFalse(body.text().toLowerCase().contains("translator:"), "the translator and editor credit is left");
        assertEquals(81 - 1, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(wuxiaworld_site.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = wuxiaworld_site.parseMetadata(novelPage());

        assertEquals("Complete Martial Arts Attributes", metadata.getTitle());
        assertEquals("Don't Enter The Jianghu", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
        assertEquals(List.of("Action", "Adventure", "Fantasy"), metadata.getSubjects());
        assertEquals("https://wuxiaworld.site/wp-content/uploads/2021/04/complete-martial-arts-attributes.jpg",
                wuxiaworld_site.parseCoverUrl(novelPage()), "the lazy-loaded image, not its placeholder");
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wuxiaworld_site.class);
    }

    @Test
    @Tag("live")
    void live_fetchesChapterListAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document list = Jsoup.connect(wuxiaworld_site.chapterListUrl(NOVEL_URL)).userAgent(wuxiaworld_site.USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest").post();
        List<Chapter> chapters = wuxiaworld_site.parseChapterList(list);
        assertTrue(chapters.size() >= 3865);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(wuxiaworld_site.USER_AGENT).get();
        assertNotNull(wuxiaworld_site.parseChapterBody(chapterPage));
    }
}
