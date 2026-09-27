package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class HoneyfeedFmTest {

    private static final String DOMAIN = "honeyfeed.fm";
    private static final String NOVEL_URL = "https://www.honeyfeed.fm/novels/33615";

    // The site answers plain requests with a Cloudflare bot check; the fixtures were saved from the app's
    // headless browser.
    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void theChapterListIsOnItsOwnPage() {
        assertEquals(NOVEL_URL + "/chapters", honeyfeed_fm.chapterListUrl(NOVEL_URL));
        assertEquals(NOVEL_URL + "/chapters", honeyfeed_fm.chapterListUrl(NOVEL_URL + "/"));
        assertEquals(NOVEL_URL + "/chapters", honeyfeed_fm.chapterListUrl(NOVEL_URL + "/chapters"));
    }

    @Test
    void chapterListInOrder() throws Exception {
        List<Chapter> chapters = honeyfeed_fm.parseChapterList(page(DOMAIN, "chapters.html", NOVEL_URL + "/chapters"));

        assertEquals(5, chapters.size());
        assertEquals("Chapter 1 :The Start of Summer Break", chapters.getFirst().name);
        assertEquals("https://www.honeyfeed.fm/chapters/246632", chapters.getFirst().chapterURL);
        assertEquals("https://www.honeyfeed.fm/chapters/252526", chapters.getLast().chapterURL);
    }

    @Test
    void chapterBodyHasAllOfTheChaptersPages() throws Exception {
        // The site splits a chapter's text into page blocks on the same page.
        Element body = honeyfeed_fm.parseChapterBody(page(DOMAIN, "chapter-1.html", "https://www.honeyfeed.fm/chapters/246632"));
        assertNotNull(body);

        withBlacklistApplied(new honeyfeed_fm(), body);
        assertEquals(4, body.select("[id^=page-]").size());
        // The first page starts with a line of loose text before its paragraphs ("Line 46." in the fixture)
        assertTrue(body.text().startsWith("Line 46. Line 1."), body.text().substring(0, 20));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(honeyfeed_fm.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = honeyfeed_fm.parseMetadata(novelPage());

        assertEquals("NARAKU-NO-INK : AUGUST IN A VIAL", metadata.getTitle());
        assertEquals("NARAKU-NO-INK", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the synopsis");
        // The genres are shown twice (for small and large screens)
        assertEquals(List.of("Drama", "Fantasy", "Magic", "Romance", "Seinen"), metadata.getSubjects());
        assertEquals("https://honeyfeed-novel-images.s3.amazonaws.com/uploads/novel_image/file/34303/cover_f3e68438-54bd-48fa-a498-9705f9a1b0ad.jpg",
                honeyfeed_fm.parseCoverUrl(novelPage()));
    }

    @Test
    void usesTheAppsBrowser() {
        assertTrue(new honeyfeed_fm().canHeadless());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(honeyfeed_fm.class);
    }

    @Test
    void aBlankPageHasNoChapters() {
        assertTrue(honeyfeed_fm.parseChapterList(Jsoup.parse("<html><body></body></html>")).isEmpty());
    }
}
