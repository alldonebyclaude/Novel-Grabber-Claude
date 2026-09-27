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

class LightnovelstranslationsComTest {

    private static final String DOMAIN = "lightnovelstranslations.com";
    private static final String NOVEL_URL = "https://lightnovelstranslations.com/novel/a-harem-in-the-fantasy-world-dungeon/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasEveryChapterOnce() throws Exception {
        // The table of contents lists one volume 1 chapter again under volume 2; the page says "797 Chapters".
        List<Chapter> chapters = lightnovelstranslations_com.parseChapterList(novelPage());

        assertEquals(797, chapters.size());
        assertEquals("Isekai Dorei Harem Volume 1 Prologue Part 1", chapters.getFirst().name, "without the list number");
        assertEquals(NOVEL_URL + "isekai-dorei-harem-volume-1-prologue-part-1/", chapters.getFirst().chapterURL);
        assertEquals("Volume 13 Chapter 5 Part 8", chapters.getLast().name);
    }

    @Test
    void lockedChaptersAreLeftOut() {
        Document page = Jsoup.parse("""
                <div class="novel_list_chapter_content"><ul>
                <li class="chapter-item chapter-1 unlock"><span>1:</span><a href="/novel/x/one/" title="One">One</a></li>
                <li class="chapter-item chapter-2 lock"><span>2:</span><a href="/novel/x/two/" title="Two">Two</a></li>
                </ul></div>""", NOVEL_URL);

        assertEquals(List.of("One"), lightnovelstranslations_com.parseChapterList(page).stream().map(c -> c.name).toList());
    }

    @Test
    void chapterBodyWithoutTitleOrCredits() throws Exception {
        Element body = lightnovelstranslations_com.parseChapterBody(
                page(DOMAIN, "chapter-1.html", NOVEL_URL + "isekai-dorei-harem-volume-1-prologue-part-1/"));
        assertNotNull(body);

        withBlacklistApplied(new lightnovelstranslations_com(), body);
        assertTrue(body.select("h2, #textbox, div.row").isEmpty(), "title, notices or buttons left in the text");
        assertFalse(body.text().toLowerCase().contains("translat"), "the translator credit is left in the text");
        assertEquals(32, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(lightnovelstranslations_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = lightnovelstranslations_com.parseMetadata(novelPage());

        assertEquals("Harem in the Fantasy World Dungeon", metadata.getTitle());
        assertEquals("蘇我捨恥 (Shachi Sogano)", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the About text");
        assertEquals(List.of("Action", "Adventure", "Comedy", "Different World (Isekai)", "Ecchi", "Fantasy", "Harem",
                "LitRPG", "Romance", "Slice of Life", "Sexual Content"), metadata.getSubjects());
        assertEquals("https://lightnovelstranslations.com/wp-content/uploads/2020/02/Isekai-Dorei-Harem-Vol-1-Cover-207x300.jpg",
                lightnovelstranslations_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(lightnovelstranslations_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(lightnovelstranslations_com.USER_AGENT).get();
        List<Chapter> chapters = lightnovelstranslations_com.parseChapterList(novelPage);
        assertTrue(chapters.size() >= 797);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(lightnovelstranslations_com.USER_AGENT).get();
        assertNotNull(lightnovelstranslations_com.parseChapterBody(chapterPage));
    }
}
