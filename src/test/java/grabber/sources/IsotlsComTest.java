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

class IsotlsComTest {

    private static final String DOMAIN = "isotls.com";
    private static final String NOVEL_URL = "https://www.isotls.com/novel/638928e9623f27041eaff63f/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListUsesTheChapterTitles() throws Exception {
        List<Chapter> chapters = isotls_com.parseChapterList(novelPage());

        assertEquals(13, chapters.size());
        assertEquals("Chapter 1 - It's Much Different From What I Imagined (思っていたのと大分違う)", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "chapter/1", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "chapter/13", chapters.getLast().chapterURL);
    }

    @Test
    void chapterListIgnoresTheSitesUpdateWidget() throws Exception {
        // The page also links the latest chapters of other novels.
        assertTrue(isotls_com.parseChapterList(novelPage()).stream()
                .allMatch(chapter -> chapter.chapterURL.startsWith(NOVEL_URL)));
    }

    @Test
    void chapterBodyIsTheTextWithoutPageFurniture() throws Exception {
        // The article also holds the title, a notice, navigation, an ad slot and a footer.
        Element body = isotls_com.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "chapter/1/"));
        assertNotNull(body);

        withBlacklistApplied(new isotls_com(), body);
        assertEquals(58, body.select("p").size());
        assertEquals(3, body.select("blockquote").size());
        assertEquals(2, body.select("ol li").size(), "translator footnotes are part of the text");
        assertTrue(body.select("header, nav, footer, aside, #ad-container, .alert").isEmpty());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(isotls_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = isotls_com.parseMetadata(novelPage());

        assertEquals("I Want to Fall in Love After Getting Reincarnated as the Heroine on the Verge of a Bad Ending, "
                + "but My OP Older Brother Won't Leave Me Alone!?", metadata.getTitle());
        assertEquals("琴子 (Kotoko)", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertFalse(metadata.getDescription().startsWith("Author:"), "the author line is not part of the description");
        assertEquals("https://casper.isotls.com/content/images/size/w360/2022/12/unknown--3-.png", isotls_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(isotls_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(isotls_com.USER_AGENT).get();
        List<Chapter> chapters = isotls_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(isotls_com.USER_AGENT).get();
        assertNotNull(isotls_com.parseChapterBody(chapterPage));
    }
}
