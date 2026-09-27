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

class ReLibraryComTest {

    private static final String DOMAIN = "re-library.com";
    private static final String NOVEL_URL = "https://re-library.com/translations/destiny-unchain-online/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasTheVolumesThenTheForumChapters() throws Exception {
        // 18 volumes, then an in-story "Forums" section; 13 early-access chapters are left out.
        List<Chapter> chapters = re_library_com.parseChapterList(novelPage());

        assertEquals(408 - 13, chapters.size());
        assertEquals("Prologue 1 - Guild Ranking Competition", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "volume-1/prologue-1-guild-ranking-competition/", chapters.getFirst().chapterURL);
        assertTrue(chapters.getLast().chapterURL.startsWith(NOVEL_URL + "forums/"), chapters.getLast().chapterURL);
    }

    @Test
    void earlyAccessChaptersAreLeftOutAndCounted() throws Exception {
        // They show "(Unlocks on <date>)" and can't be read yet.
        List<Chapter> chapters = re_library_com.parseChapterList(novelPage());

        assertEquals(13, re_library_com.countLockedChapters(novelPage()));
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.contains("Unlocks on")));
        assertTrue(chapters.stream().noneMatch(chapter ->
                chapter.chapterURL.equals(NOVEL_URL + "volume-17/chapter-339-commotion-in-the-sacred-city-1/")));
    }

    @Test
    void chapterBodyKeepsTheTextAndTheInStoryChatWithoutNavigationCreditsOrAds() throws Exception {
        Element body = re_library_com.parseChapterBody(
                page(DOMAIN, "chapter-1.html", NOVEL_URL + "volume-1/prologue-1-guild-ranking-competition/"));
        assertNotNull(body);

        withBlacklistApplied(new re_library_com(), body);
        assertTrue(body.select(".PageLink, table, .code-block, .sharedaddy, .post-views, .wpulike").isEmpty(),
                "navigation, credits, ads or buttons left in the text");
        assertEquals(2, body.select(".live-comment").size(), "the story's chat logs are part of the text");
        assertEquals(74 + 8, body.select("p").size());
        // "Line 1." in the fixture is the comments button, which is removed
        assertTrue(body.text().startsWith("Line 2."), body.text().substring(0, 20));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() {
        assertNull(re_library_com.parseChapterBody(Jsoup.parse("<html><body><p>Not found</p></body></html>")));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = re_library_com.parseMetadata(novelPage());

        assertEquals("Destiny Unchain Online", metadata.getTitle());
        assertEquals("Resn", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the synopsis");
        assertEquals(List.of("Action", "Adventure", "Fantasy", "Gender Bender", "Sci-fi"), metadata.getSubjects());
        assertEquals("https://re-library.com/wp-content/uploads/2023/04/Destiny-Unchain-Online-Resized.jpg",
                re_library_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(re_library_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(re_library_com.USER_AGENT).get();
        List<Chapter> chapters = re_library_com.parseChapterList(novelPage);
        assertTrue(chapters.size() >= 395);

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(re_library_com.USER_AGENT).get();
        assertNotNull(re_library_com.parseChapterBody(chapterPage));
    }
}
