package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TruyencomComTest {

    private static final String DOMAIN = "truyencom.com";
    private static final String NOVEL_URL = "https://truyencom.com/con-duong-ba-chu.66/";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void readsStoryIdAndAliasForTheChapterList() throws Exception {
        Document novelPage = novelPage();

        assertEquals("66", truyencom_com.parseStoryId(novelPage));
        assertEquals("con-duong-ba-chu", truyencom_com.parseStoryAlias(novelPage));
        assertEquals("https://truyencom.com/api/chapters/66/2/50", truyencom_com.chapterListUrl("66", 2));
    }

    @Test
    void chapterListPageHasFiftyChaptersInOrder() throws Exception {
        List<Chapter> chapters = truyencom_com.parseChapterList(text(DOMAIN, "chapters-1.json"), "con-duong-ba-chu");

        assertEquals(truyencom_com.CHAPTERS_PER_PAGE, chapters.size());
        assertEquals("https://truyencom.com/con-duong-ba-chu/chuong-1.html", chapters.getFirst().chapterURL);
        assertEquals("https://truyencom.com/con-duong-ba-chu/chuong-50.html", chapters.getLast().chapterURL);
        assertTrue(chapters.getFirst().name.startsWith("Chương 1"));
    }

    @Test
    void chapterListOfAnUnexpectedResponseIsEmpty() {
        assertTrue(truyencom_com.parseChapterList("{\"storyID\":66,\"items\":[]}", "x").isEmpty());
        assertTrue(truyencom_com.parseChapterList("<html>not json</html>", "x").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Chương 1                | chuong-1",
            "Chương 12               | chuong-12",
            "Quyển 2 - Chương 10     | quyen-2-chuong-10",
            "Đoạn Kết!               | doan-ket",
            "Chương 5 (Hạ)           | chuong-5-ha",
    })
    void chapterSlugsFollowTheSitesStr2url(String name, String slug) {
        // Port of str2url() in the site's main.js, which builds the chapter links from the chapter names.
        assertEquals(slug, truyencom_com.str2url(name));
    }

    @Test
    void chapterBodyKeepsTheLines() throws Exception {
        Element body = truyencom_com.parseChapterBody(
                page(DOMAIN, "chapter.html", "https://truyencom.com/con-duong-ba-chu/chuong-1.html"));
        assertNotNull(body);

        withBlacklistApplied(new truyencom_com(), body);
        assertTrue(body.text().startsWith("Line 1."));
        assertEquals(57, body.select("br").size());
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(truyencom_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = truyencom_com.parseMetadata(novelPage());

        assertEquals("Con Đường Bá Chủ", metadata.getTitle());
        assertEquals("Akay Hau", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(3, metadata.getSubjects().size(), "only the novel's genres, not the site's genre menu");
        assertEquals("https://cdn.truyencom.com/medias/covers/0/66-con-duong-ba-chu_cover_large.jpg",
                truyencom_com.parseCoverUrl(novelPage()));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(truyencom_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelFirstListPageAndFirstChapter() throws Exception {
        // Three requests, one at a time, with pauses in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(truyencom_com.USER_AGENT).get();
        String listUrl = truyencom_com.chapterListUrl(truyencom_com.parseStoryId(novelPage), 1);

        Thread.sleep(1500);
        String json = Jsoup.connect(listUrl).userAgent(truyencom_com.USER_AGENT).ignoreContentType(true).execute().body();
        List<Chapter> chapters = truyencom_com.parseChapterList(json, truyencom_com.parseStoryAlias(novelPage));
        assertEquals(truyencom_com.CHAPTERS_PER_PAGE, chapters.size());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(truyencom_com.USER_AGENT).get();
        assertNotNull(truyencom_com.parseChapterBody(chapterPage));
    }
}
