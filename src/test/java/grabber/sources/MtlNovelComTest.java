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

class MtlNovelComTest {

    private static final String DOMAIN = "mtl-novel.com";
    private static final String NOVEL_URL = "https://mtl-novel.com/novel/universal-lord-a-random-buff-every-day/";
    private static final String LONG_URL =
            "https://mtl-novel.com/novel/the-world-descends-on-sky-island-god-level-talents-are-drawn-at-the-beginning/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsTheWholeChapterListFromTheNovelPage() throws Exception {
        List<Chapter> chapters = mtl_novel_com.parseChapterList(novelPage());

        assertEquals(55, chapters.size());
        assertEquals("#1 A Single Pull Creates a Miracle", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "chapter-1-a-single-pull-creates-a-miracle/", chapters.getFirst().chapterURL);
        assertEquals("#55 Talent Upgrade", chapters.getLast().name);
        assertEquals(NOVEL_URL + "chapter-55-talent-upgrade/", chapters.getLast().chapterURL);
    }

    @Test
    void readsEveryGroupOfALongListInOrder() throws Exception {
        // Long lists come in groups of 250 (<details>), all in the page. The fixture keeps a few links per group.
        List<Chapter> chapters = mtl_novel_com.parseChapterList(page(DOMAIN, "novel-long.html", LONG_URL));

        assertEquals(28, chapters.size());
        assertEquals(LONG_URL + "chapter-1-welcome-to-sky-island-paradise/", chapters.getFirst().chapterURL);
        assertEquals(LONG_URL + "chapter-959-the-pillar-of-origin-the-truth-at-the-end-of-the-long-river-of-time/",
                chapters.getLast().chapterURL);
        // Titles come from the site; the numbers in the URLs drift from the chapter numbers.
        assertTrue(chapters.getLast().name.startsWith("#963 "), chapters.getLast().name);
    }

    @Test
    void keepsTheSitesDuplicateLinkAndWarnsAboutIt() throws Exception {
        List<Chapter> chapters = mtl_novel_com.parseChapterList(page(DOMAIN, "novel-long.html", LONG_URL));
        String duplicate = LONG_URL + "chapter-403-the-third-godhead-heading-to-the-sea-of-light/";

        assertEquals(2, chapters.stream().filter(c -> c.chapterURL.equals(duplicate)).count());
        List<String> warnings = mtl_novel_com.checkChapterList(chapters);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains(duplicate), warnings.getFirst());
    }

    @Test
    void noWarningsForAListWithoutDuplicates() throws Exception {
        assertTrue(mtl_novel_com.checkChapterList(mtl_novel_com.parseChapterList(novelPage())).isEmpty());
    }

    @Test
    void chapterBodyIsTheParagraphBlock() throws Exception {
        Element body = mtl_novel_com.parseChapterBody(
                page(DOMAIN, "chapter-1.html", NOVEL_URL + "chapter-1-a-single-pull-creates-a-miracle/"));

        assertNotNull(body);
        withBlacklistApplied(new mtl_novel_com(), body);
        assertEquals(120, body.select("p").size());
        assertEquals("Line 1.", body.selectFirst("p").text());
        assertEquals("Line 120.", body.select("p").last().text());
    }

    @Test
    void novelPageHasNoChapterBody() throws Exception {
        assertNull(mtl_novel_com.parseChapterBody(novelPage()));
    }

    @Test
    void readsTitleAuthorDescriptionAndGenres() throws Exception {
        NovelMetadata metadata = mtl_novel_com.parseMetadata(novelPage());

        assertEquals("Universal Lord: A Random BUFF Every Day", metadata.getTitle());
        assertEquals("别再叫我椰羊", metadata.getAuthor());
        assertEquals("Line 1.", metadata.getDescription());
        assertEquals(List.of("Action", "Adventure", "Fan-Fiction", "Fantasy", "game", "Harem", "Mystery", "Sci-Fi", "Xuanhuan"),
                metadata.getSubjects());
    }

    @Test
    void coverIsTheNovelsOwnImageNotARelatedNovels() throws Exception {
        assertEquals("https://mtl-novel.com/wp-content/uploads/2026/09/universal-lord-a-random-buff-every-day-cover.jpg",
                mtl_novel_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(mtl_novel_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesListAndFirstChapter() throws Exception {
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(mtl_novel_com.USER_AGENT).get();
        List<Chapter> chapters = mtl_novel_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());
        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(mtl_novel_com.USER_AGENT).get();
        assertNotNull(mtl_novel_com.parseChapterBody(chapterPage));
    }
}
