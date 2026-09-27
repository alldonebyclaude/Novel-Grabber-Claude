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

class NovelbuddyMeTest {

    private static final String DOMAIN = "novelbuddy.me";
    private static final String NOVEL_URL = "https://novelbuddy.me/cultivation-online";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsTitleIdAndCacheVersionFromTheNovelPage() throws Exception {
        Document novelPage = novelPage();

        assertEquals("eDk1Rg8Q", novelbuddy_me.parseTitleId(novelPage));
        assertEquals("1790228813763", novelbuddy_me.parseCacheVersion(novelPage));
    }

    @Test
    void chapterListUrlPassesTheCacheVersion() {
        // Without the cache version the API returns an older list that misses the newest chapter.
        assertEquals("https://api.novelbuddy.me/titles/eDk1Rg8Q/chapters?cv=1790228813763",
                novelbuddy_me.chapterListUrl("eDk1Rg8Q", "1790228813763"));
        assertEquals("https://api.novelbuddy.me/titles/eDk1Rg8Q/chapters", novelbuddy_me.chapterListUrl("eDk1Rg8Q", null));
    }

    @Test
    void chapterListIsOldestFirstWithAbsoluteUrls() throws Exception {
        // The fixture keeps the API's three newest and two oldest entries; the API lists newest first.
        List<Chapter> chapters = novelbuddy_me.parseChapterList(text(DOMAIN, "chapters.json"));

        assertEquals(5, chapters.size());
        assertEquals("Chapter 1 - Cultivation Online", chapters.getFirst().name);
        assertEquals("https://novelbuddy.me/cultivation-online/chapter-1-cultivation-online", chapters.getFirst().chapterURL);
        assertTrue(chapters.getLast().name.startsWith("Chapter 2646"));
    }

    @Test
    void chapterListOfAnUnexpectedResponseIsEmpty() {
        assertTrue(novelbuddy_me.parseChapterList("{\"success\":false,\"message\":\"Not found\"}").isEmpty());
        assertTrue(novelbuddy_me.parseChapterList("<html>not json</html>").isEmpty());
    }

    @Test
    void chapterBodyKeepsTheTextWithoutAdsOrTitle() throws Exception {
        Element body = novelbuddy_me.parseChapterBody(
                page(DOMAIN, "chapter.html", NOVEL_URL + "/chapter-1-cultivation-online"));
        assertNotNull(body);

        withBlacklistApplied(new novelbuddy_me(), body);
        assertEquals(123, body.select("p").size());
        assertTrue(body.select("h3, div.my-4").isEmpty(), "title heading or ad slot left in the text");
        assertTrue(body.text().startsWith("Line 2."), "text should start after the title");
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(novelbuddy_me.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = novelbuddy_me.parseMetadata(novelPage());

        assertEquals("Cultivation Online", metadata.getTitle());
        assertEquals("MyLittleBrother", metadata.getAuthor(), "the same author listed twice");
        assertFalse(metadata.getDescription().isBlank());
        // The site lists four of the novel's genres twice
        assertEquals(List.of("Game", "Action", "Adventure", "Romance", "Harem", "Comedy"), metadata.getSubjects());
        assertEquals("https://rs.novelbuddy.me/covers/cultivation-online.png", novelbuddy_me.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(novelbuddy_me.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelListAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(novelbuddy_me.USER_AGENT).get();
        String listUrl = novelbuddy_me.chapterListUrl(novelbuddy_me.parseTitleId(novelPage), novelbuddy_me.parseCacheVersion(novelPage));

        Thread.sleep(1500);
        String json = Jsoup.connect(listUrl).userAgent(novelbuddy_me.USER_AGENT).ignoreContentType(true).execute().body();
        List<Chapter> chapters = novelbuddy_me.parseChapterList(json);
        assertTrue(chapters.size() > 2000);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(novelbuddy_me.USER_AGENT).get();
        assertNotNull(novelbuddy_me.parseChapterBody(chapterPage));
    }
}
