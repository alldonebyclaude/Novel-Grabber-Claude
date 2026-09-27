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

class NcodeSyosetuComTest {

    private static final String DOMAIN = "ncode.syosetu.com";
    private static final String NOVEL_URL = "https://ncode.syosetu.com/n9669bk/";
    private static final String SHORT_STORY_URL = "https://ncode.syosetu.com/n8744mt/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void tableOfContentsPageListsItsEpisodesInOrder() throws Exception {
        // 100 episodes per page
        List<Chapter> chapters = ncode_syosetu_com.parseChapterList(novelPage());

        assertEquals(100, chapters.size());
        assertEquals("プロローグ", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "1/", chapters.getFirst().chapterURL);
        assertEquals(NOVEL_URL + "100/", chapters.getLast().chapterURL);
    }

    @Test
    void tableOfContentsContinuesOnTheNextPage() throws Exception {
        assertEquals(NOVEL_URL + "?p=2", ncode_syosetu_com.parseNextPageUrl(novelPage()));

        Document lastPage = page(DOMAIN, "novel-page-3.html", NOVEL_URL + "?p=3");
        assertNull(ncode_syosetu_com.parseNextPageUrl(lastPage));
        List<Chapter> chapters = ncode_syosetu_com.parseChapterList(lastPage);
        assertEquals(86, chapters.size());
        assertEquals(NOVEL_URL + "201/", chapters.getFirst().chapterURL);
        assertEquals("エピローグ「プロローグ・ゼロ」", chapters.getLast().name);
        assertEquals(NOVEL_URL + "286/", chapters.getLast().chapterURL);
    }

    @Test
    void aShortStoryIsOneChapterOnTheNovelPage() throws Exception {
        // Short stories (短編) have no table of contents: the text is on the novel page itself.
        Document shortStory = page(DOMAIN, "short-story.html", SHORT_STORY_URL);

        List<Chapter> chapters = ncode_syosetu_com.parseChapterList(shortStory);
        assertEquals(1, chapters.size());
        assertEquals("転生したら気弱な公爵夫人だった。とりあえず、一発殴っていいかしら？", chapters.getFirst().name);
        assertEquals(SHORT_STORY_URL, chapters.getFirst().chapterURL);
        assertNull(ncode_syosetu_com.parseNextPageUrl(shortStory));

        Element body = ncode_syosetu_com.parseChapterBody(shortStory);
        assertNotNull(body);
        assertEquals(551, body.select("p").size(), "the text and the afterword");
    }

    @Test
    void episodeBodyKeepsTheText() throws Exception {
        Element body = ncode_syosetu_com.parseChapterBody(page(DOMAIN, "episode-1.html", NOVEL_URL + "1/"));
        assertNotNull(body);

        withBlacklistApplied(new ncode_syosetu_com(), body);
        assertEquals(275, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfATableOfContentsPageIsNull() throws Exception {
        assertNull(ncode_syosetu_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = ncode_syosetu_com.parseMetadata(novelPage());

        assertEquals("無職転生　- 異世界行ったら本気だす -", metadata.getTitle());
        assertEquals("理不尽な孫の手", metadata.getAuthor(), "without the 作者： label");
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the summary");
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(ncode_syosetu_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesTableOfContentsAndFirstEpisode() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(ncode_syosetu_com.USER_AGENT).get();
        List<Chapter> chapters = ncode_syosetu_com.parseChapterList(novelPage);
        assertEquals(100, chapters.size());

        Thread.sleep(1500);
        Document episodePage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(ncode_syosetu_com.USER_AGENT).get();
        assertNotNull(ncode_syosetu_com.parseChapterBody(episodePage));
    }
}
