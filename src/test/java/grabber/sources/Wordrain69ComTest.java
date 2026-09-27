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

class Wordrain69ComTest {

    private static final String DOMAIN = "wordrain69.com";
    private static final String NOVEL_URL = "https://wordrain69.com/manga/blackened-villain-rescue-system/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListComesFromTheNovelsAjaxUrl() {
        assertEquals(NOVEL_URL + "ajax/chapters/", wordrain69_com.chapterListUrl(NOVEL_URL));
        assertEquals(NOVEL_URL + "ajax/chapters/", wordrain69_com.chapterListUrl(NOVEL_URL.substring(0, NOVEL_URL.length() - 1)));
    }

    @Test
    void chapterListIsOldestFirst() throws Exception {
        // The site lists the newest chapter first. The fixture keeps the 5 newest and 5 oldest of 202 chapters.
        List<Chapter> chapters = wordrain69_com.parseChapterList(page(DOMAIN, "chapters.html", NOVEL_URL + "ajax/chapters/"));

        assertEquals(10, chapters.size());
        assertEquals("Chapter 1 - First World", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "chapter-1/", chapters.getFirst().chapterURL);
        assertEquals("Chapter 202 - END", chapters.getLast().name);
        assertEquals(0, wordrain69_com.countLockedChapters(page(DOMAIN, "chapters.html", NOVEL_URL + "ajax/chapters/")));
    }

    @Test
    void chaptersThatCostCoinsAreLeftOut() {
        // Made up after the site's markup: free chapters are marked "free-chap" and "coin free".
        Document list = Jsoup.parse("""
                <ul class="main version-chap">
                <li class="wp-manga-chapter premium"><span class="coin">5</span> <a href="%1$schapter-3/">Chapter 3</a></li>
                <li class="wp-manga-chapter"><span class="coin">5</span> <a href="%1$schapter-2/">Chapter 2</a></li>
                <li class="wp-manga-chapter free-chap"><span class="coin free">Free</span> <a href="%1$schapter-1/">Chapter 1</a></li>
                </ul>""".formatted(NOVEL_URL), NOVEL_URL + "ajax/chapters/");

        assertEquals(List.of("Chapter 1"), wordrain69_com.parseChapterList(list).stream().map(c -> c.name).toList());
        assertEquals(2, wordrain69_com.countLockedChapters(list));
    }

    @Test
    void chapterBodyKeepsTheParagraphs() throws Exception {
        Element body = wordrain69_com.parseChapterBody(page(DOMAIN, "chapter-1.html", NOVEL_URL + "chapter-1/"));
        assertNotNull(body);

        withBlacklistApplied(new wordrain69_com(), body);
        assertEquals(44, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(wordrain69_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = wordrain69_com.parseMetadata(novelPage());

        assertEquals("Villain Rescue System", metadata.getTitle());
        assertEquals("Miao", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
        assertEquals(List.of("Completed", "Drama", "Fantasy", "Romance", "Transmigration"), metadata.getSubjects());
        assertEquals("https://wordrain69.com/storage/2022/07/133049535916587138-193x278.jpg",
                wordrain69_com.parseCoverUrl(novelPage()), "the lazy-loaded image, not its placeholder");
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(wordrain69_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesChapterListAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document list = Jsoup.connect(wordrain69_com.chapterListUrl(NOVEL_URL)).userAgent(wordrain69_com.USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest").post();
        List<Chapter> chapters = wordrain69_com.parseChapterList(list);
        assertTrue(chapters.size() >= 202);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(wordrain69_com.USER_AGENT).get();
        assertNotNull(wordrain69_com.parseChapterBody(chapterPage));
    }
}
