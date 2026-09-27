package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TruyenfullLiveTest {

    private static final String DOMAIN = "truyenfull.live";
    private static final String NOVEL_URL = "https://truyenfull.live/ba-ngan-met-tren-may-lang-nhi-doa/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsTheStoryIdForTheChapterList() throws Exception {
        assertEquals("45202", truyenfull_live.parseStoryId(novelPage()));
        assertEquals("https://truyenfull.live/ajax.php?type=chapter_option&data=45202",
                truyenfull_live.chapterListUrl("45202"));
    }

    @ParameterizedTest
    @ValueSource(strings = {NOVEL_URL, "https://truyenfull.live/ba-ngan-met-tren-may-lang-nhi-doa"})
    void chapterListHasEveryChapterInOrder(String novelUrl) throws Exception {
        // The novel page shows 50 chapters per page; the chapter-jump list has all of them.
        List<Chapter> chapters = truyenfull_live.parseChapterList(
                page(DOMAIN, "chapters.html", truyenfull_live.chapterListUrl("45202")), novelUrl);

        assertEquals(148, chapters.size());
        assertEquals(NOVEL_URL + "chuong-1/", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "chuong-148/", chapters.getLast().chapterURL);
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAds() throws Exception {
        Element body = truyenfull_live.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "chuong-1/"));
        assertNotNull(body);

        withBlacklistApplied(new truyenfull_live(), body);
        assertTrue(body.select("#ads-chapter-top, .ads-responsive").isEmpty(), "ad slot left in the text");
        assertEquals(81, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(truyenfull_live.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = truyenfull_live.parseMetadata(novelPage());

        assertEquals("Ba Ngàn Mét Trên Mây - Lang Nhĩ Đóa", metadata.getTitle());
        assertEquals("Lang Nhĩ Đóa", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(3, metadata.getSubjects().size(), "only the novel's genres, not the site's genre menu");
        assertEquals("https://lh3.googleusercontent.com/d/1EeOSFmKP26bThv9TXjmQcWGZ-DJ0mDZD",
                truyenfull_live.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(truyenfull_live.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelListAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(truyenfull_live.USER_AGENT).get();
        String listUrl = truyenfull_live.chapterListUrl(truyenfull_live.parseStoryId(novelPage));

        Thread.sleep(1500);
        List<Chapter> chapters = truyenfull_live.parseChapterList(
                Jsoup.connect(listUrl).userAgent(truyenfull_live.USER_AGENT).get(), NOVEL_URL);
        assertTrue(chapters.size() >= 148);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(truyenfull_live.USER_AGENT).get();
        assertNotNull(truyenfull_live.parseChapterBody(chapterPage));
    }
}
