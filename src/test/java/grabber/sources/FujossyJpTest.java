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

class FujossyJpTest {

    private static final String DOMAIN = "fujossy.jp";
    private static final String NOVEL_URL = "https://fujossy.jp/books/24584";

    @Test
    void theBookIdComesFromTheLink() {
        assertEquals("24584", fujossy_jp.parseBookId(NOVEL_URL));
        assertEquals("24584", fujossy_jp.parseBookId(NOVEL_URL + "/stories/505579"));
        assertNull(fujossy_jp.parseBookId("https://fujossy.jp/users/someone"));
        assertEquals("https://fujossy.jp/api/books/24584.json", fujossy_jp.bookApiUrl("24584"));
    }

    @Test
    void chapterListComesFromTheBookApi() throws Exception {
        // The fixture keeps the first three and the last two of the book's 41 chapters.
        List<Chapter> chapters = fujossy_jp.parseChapterList(text(DOMAIN, "book.json"), "24584");

        assertEquals(5, chapters.size());
        assertEquals(NOVEL_URL + "/stories/505579", chapters.getFirst().chapterURL);
        assertEquals("Dom/Subユニバースの世界観", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "/stories/606968", chapters.getLast().chapterURL);
    }

    @Test
    void chapterListOfAnUnexpectedResponseIsEmpty() {
        assertTrue(fujossy_jp.parseChapterList("{\"error\":\"not found\"}", "1").isEmpty());
        assertTrue(fujossy_jp.parseChapterList("<html>not json</html>", "1").isEmpty());
    }

    @Test
    void chapterBodyTurnsLineBreaksIntoBrTags() throws Exception {
        // The site writes the text with plain line breaks, which an EPUB would join into one line.
        Element body = fujossy_jp.parseChapterBody(Jsoup.parse("<div class=\"story__body\">One.\nTwo.\n\nThree.</div>"));
        assertEquals(3, body.select("br").size());
        assertEquals("One. Two. Three.", body.text());

        Element saved = fujossy_jp.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "/stories/505579"));
        assertNotNull(saved);
        assertTrue(saved.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(fujossy_jp.parseChapterBody(page(DOMAIN, "novel.html", NOVEL_URL)));
    }

    @Test
    void metadataComesFromTheBookApi() throws Exception {
        String json = text(DOMAIN, "book.json");
        NovelMetadata metadata = fujossy_jp.parseMetadata(json);

        assertEquals("箱庭の蝶が見る夢は", metadata.getTitle());
        assertEquals("小此木雪花", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(List.of("R18", "Dom/Subユニバース", "D/Sユニバース", "溺愛", "S攻め"), metadata.getSubjects().subList(0, 5));
        assertTrue(fujossy_jp.parseCoverUrl(json).startsWith("https://fujossy-production.s3.amazonaws.com/uploads/book/cover/24584/"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(fujossy_jp.class);
    }

    @Test
    @Tag("live")
    void live_fetchesBookAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        String json = Jsoup.connect(fujossy_jp.bookApiUrl("24584")).userAgent(fujossy_jp.USER_AGENT).ignoreContentType(true).execute().body();
        List<Chapter> chapters = fujossy_jp.parseChapterList(json, "24584");
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(fujossy_jp.USER_AGENT).get();
        assertNotNull(fujossy_jp.parseChapterBody(chapterPage));
    }
}
