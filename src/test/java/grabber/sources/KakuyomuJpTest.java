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

class KakuyomuJpTest {

    private static final String DOMAIN = "kakuyomu.jp";
    private static final String NOVEL_URL = "https://kakuyomu.jp/works/1177354054935149052";

    // The novel page renders from its __NEXT_DATA__; the fixture keeps only that data.
    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListIsTheWholeTableOfContents() throws Exception {
        // The rendered page links only a few episodes; the full list is in the page data.
        List<Chapter> chapters = kakuyomu_jp.parseChapterList(novelPage());

        assertEquals(154, chapters.size());
        assertEquals("序", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "/episodes/1177354054935149092", chapters.getFirst().chapterURL);
        assertEquals("刑罰：カジット連山殲滅掃討　顛末　１", chapters.getLast().name);
    }

    @Test
    void chapterListOfAPageWithoutDataIsEmpty() {
        assertTrue(kakuyomu_jp.parseChapterList(Jsoup.parse("<html><body>Not found</body></html>")).isEmpty());
    }

    @Test
    void chapterBodyKeepsTheParagraphs() throws Exception {
        Element body = kakuyomu_jp.parseChapterBody(
                page(DOMAIN, "episode-1.html", NOVEL_URL + "/episodes/1177354054935149092"));
        assertNotNull(body);

        withBlacklistApplied(new kakuyomu_jp(), body);
        assertEquals(10, body.select("p").size(), "blank lines are kept as empty paragraphs");
        assertTrue(body.text().startsWith("Line 1."));
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(kakuyomu_jp.parseChapterBody(novelPage()));
    }

    @Test
    void metadataIsTheNovelsOwnNotARecommendedWorks() throws Exception {
        NovelMetadata metadata = kakuyomu_jp.parseMetadata(novelPage());

        assertEquals("勇者刑に処す　懲罰勇者９００４隊刑務記録", metadata.getTitle());
        assertEquals("ロケット商会", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 2."), "the introduction");
        assertEquals(List.of("戦記", "魔法", "勇者", "魔王"), metadata.getSubjects());
        assertEquals("https://cdn.kdkw.jp/cover_1000/322503/322503000770.webp", kakuyomu_jp.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(kakuyomu_jp.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstEpisode() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(kakuyomu_jp.USER_AGENT).get();
        List<Chapter> chapters = kakuyomu_jp.parseChapterList(novelPage);
        assertTrue(chapters.size() >= 154);

        Thread.sleep(1500);
        Document episodePage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(kakuyomu_jp.USER_AGENT).get();
        assertNotNull(kakuyomu_jp.parseChapterBody(episodePage));
    }
}
