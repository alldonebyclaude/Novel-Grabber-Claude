package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static grabber.sources.SourceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RoyalroadComTest {

    private static final String DOMAIN = "royalroad.com";
    private static final String NOVEL_URL = "https://www.royalroad.com/fiction/104434/the-elf-who-would-become-a-dragon-story-complete";

    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasEachChapterOnce() throws Exception {
        // Each row links the chapter twice (title and date); the old selector listed every chapter twice.
        List<Chapter> chapters = royalroad_com.parseChapterList(novelPage());

        assertEquals(160, chapters.size());
        assertEquals(160, new HashSet<>(chapters.stream().map(chapter -> chapter.chapterURL).toList()).size());
        assertTrue(chapters.getFirst().chapterURL.startsWith(NOVEL_URL + "/chapter/"));
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.name.isBlank()));
    }

    @Test
    void chapterBodyKeepsTheHiddenNoticeUnlessTheUserRemovesHiddenText() throws Exception {
        // Royal Road hides an anti-piracy notice in each chapter with a display:none rule on a random class; in the
        // fixture it is a <span> between the paragraphs. It is only removed with the "remove hidden text" option.
        String hiddenClass = "cjA3YWU3ZGMwZWUyZjQ5ZDc4OGU3ZmJhOTJkNmE3NGEy";
        Element body = royalroad_com.parseChapterBody(page(DOMAIN, "chapter.html", NOVEL_URL + "/chapter/2034542/x"));
        assertNotNull(body);
        withBlacklistApplied(new royalroad_com(), body);

        assertEquals(1, body.getElementsByClass(hiddenClass).size());
        assertEquals(153, body.select("p").size());
        assertTrue(body.text().startsWith("Line 1."));

        assertEquals(1, grabber.HiddenText.remove(body));
        assertTrue(body.getElementsByClass(hiddenClass).isEmpty());
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        assertNull(royalroad_com.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = royalroad_com.parseMetadata(novelPage());

        assertEquals("The Elf Who Would Become A Dragon [Story Complete; Copyedits in Progress]", metadata.getTitle());
        assertEquals("ljamberfantasy", metadata.getAuthor());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(17, metadata.getSubjects().size());
        assertTrue(royalroad_com.parseCoverUrl(novelPage()).startsWith("https://www.royalroadcdn.com/public/covers-large/104434-"));
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(royalroad_com.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(royalroad_com.USER_AGENT).get();
        List<Chapter> chapters = royalroad_com.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(royalroad_com.USER_AGENT).get();
        assertNotNull(royalroad_com.parseChapterBody(chapterPage));
    }
}
