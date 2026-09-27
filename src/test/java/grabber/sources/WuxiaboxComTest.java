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

class WuxiaboxComTest {

    private static final String DOMAIN = "wuxiabox.com";
    private static final String NOVEL_URL = "https://www.wuxiabox.com/novel/a-hundredfold-training-system-instantly-upgrades-999.html";
    private static final String PAGE_2_URL =
            "https://www.wuxiabox.com/e/extend/fy.php?page=1&wjm=a-hundredfold-training-system-instantly-upgrades-999";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void theNovelPageListsTheFirstHundredChapters() throws Exception {
        List<Chapter> chapters = wuxiabox_com.parseChapterList(novelPage());

        assertEquals(100, chapters.size());
        assertEquals("Chapter 1: A system that can become stronger at any time!", chapters.getFirst().name, "without the number in front");
        assertEquals("https://www.wuxiabox.com/novel/a-hundredfold-training-system-instantly-upgrades-999_1.html",
                chapters.getFirst().chapterURL);
        assertEquals(PAGE_2_URL, wuxiabox_com.parseNextPageUrl(novelPage()));
    }

    @Test
    void theNextPagesContinueTheList() throws Exception {
        Document page2 = page(DOMAIN, "chapters-page-2.html", PAGE_2_URL);
        List<Chapter> chapters = wuxiabox_com.parseChapterList(page2);

        assertEquals(100, chapters.size());
        assertEquals("https://www.wuxiabox.com/novel/a-hundredfold-training-system-instantly-upgrades-999_101.html",
                chapters.getFirst().chapterURL);
        assertEquals("https://www.wuxiabox.com/e/extend/fy.php?page=2&wjm=a-hundredfold-training-system-instantly-upgrades-999",
                wuxiabox_com.parseNextPageUrl(page2));
    }

    @Test
    void theLastPageHasNoNextPage() {
        Document last = Jsoup.parse("<ul class=\"pagination\"><li><a href=\"/e/extend/fy.php?page=6&wjm=x\">&lt;</a></li>"
                + "<li class=\"active\"><a>8</a></li></ul>", PAGE_2_URL);
        assertNull(wuxiabox_com.parseNextPageUrl(last));
    }

    @Test
    void chapterBodyWithoutAdsOrScripts() throws Exception {
        Element body = wuxiabox_com.parseChapterBody(page(DOMAIN, "chapter-1.html",
                "https://www.wuxiabox.com/novel/a-hundredfold-training-system-instantly-upgrades-999_1.html"));
        assertNotNull(body);

        withBlacklistApplied(new wuxiabox_com(), body);
        assertTrue(body.select("div div, script").isEmpty(), "ad slots left in the text");
        assertEquals(48, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(wuxiabox_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadataIsTheNovelsOwnNotARecommendedNovels() throws Exception {
        NovelMetadata metadata = wuxiabox_com.parseMetadata(novelPage());

        assertEquals("A Hundredfold Training System Instantly Upgrades 999", metadata.getTitle());
        assertEquals("Coming for Koi", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
        assertEquals(List.of("Action", "Adventure", "Fantasy", "Xuanhuan"), metadata.getSubjects());
        assertEquals("https://www.wuxiabox.com/d/file/coverb/A-Hundred-Times-Training-System-Instantly-Upgrades-999-213x300.jpg",
                wuxiabox_com.parseCoverUrl(novelPage()), "the lazy-loaded image, not its placeholder");
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wuxiabox_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(wuxiabox_com.USER_AGENT).get();
        List<Chapter> chapters = wuxiabox_com.parseChapterList(novelPage);
        assertEquals(100, chapters.size());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(wuxiabox_com.USER_AGENT).get();
        assertNotNull(wuxiabox_com.parseChapterBody(chapterPage));
    }
}
