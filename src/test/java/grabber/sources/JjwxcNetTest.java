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

class JjwxcNetTest {

    private static final String DOMAIN = "jjwxc.net";
    private static final String NOVEL_URL = "https://www.jjwxc.net/onebook.php?novelid=10035712";

    // The site's pages are GB18030; the fixtures were converted to UTF-8.
    private static Document novelPage() throws Exception {
        return page(DOMAIN, "novel.html", NOVEL_URL);
    }

    @Test
    void chapterListHasTheFreeChaptersOnly() throws Exception {
        // 22 free chapters are followed by 411 VIP chapters, which open a purchase dialog instead of a link.
        List<Chapter> chapters = jjwxc_net.parseChapterList(novelPage());

        assertEquals(22, chapters.size());
        assertEquals("求生第一步：去沙滩整点儿蛤蜊", chapters.getFirst().name);
        assertEquals(NOVEL_URL + "&chapterid=1", chapters.getFirst().chapterURL, "https, not the page's http links");
        assertEquals(NOVEL_URL + "&chapterid=22", chapters.getLast().chapterURL);
        assertEquals(411, jjwxc_net.countVipChapters(novelPage()));
    }

    @Test
    void withTheUsersLoginTheVipChaptersAreListedInPlace() throws Exception {
        // Chapters the user bought can be read with their own login cookies; the rest come back without text.
        List<Chapter> chapters = jjwxc_net.parseChapterList(novelPage(), true);

        assertEquals(22 + 411, chapters.size());
        assertEquals(NOVEL_URL + "&chapterid=22", chapters.get(21).chapterURL);
        assertEquals("https://my.jjwxc.net/onebook_vip.php?novelid=10035712&chapterid=23", chapters.get(22).chapterURL);
        assertFalse(chapters.get(22).name.isBlank());
        assertTrue(chapters.stream().noneMatch(chapter -> chapter.chapterURL.contains("buynovel")), "buy buttons are not chapters");
    }

    @Test
    void textScrambledWithTheSitesFontIsRecognised() {
        // VIP text can be drawn with a font that maps the characters to other ones; it's not worked around.
        Document scrambled = Jsoup.parse("""
                <style>@font-face{font-family:jjwxcfont_00a3b;src:url(//static.jjwxc.net/tmp/fonts/jjwxcfont_00a3b.woff2)}</style>
                <div class="noveltext" style="font-family:jjwxcfont_00a3b"><div id="paragraph_comment_content">Line 1.</div></div>""");
        Document plain = Jsoup.parse("<div class=\"noveltext\"><div id=\"paragraph_comment_content\">Line 1.</div></div>");

        assertTrue(jjwxc_net.isFontScrambled(scrambled));
        assertFalse(jjwxc_net.isFontScrambled(plain));
    }

    @Test
    void chapterBodyIsTheTextOnly() throws Exception {
        Element body = jjwxc_net.parseChapterBody(page(DOMAIN, "chapter-1.html", NOVEL_URL + "&chapterid=1"));
        assertNotNull(body);

        withBlacklistApplied(new jjwxc_net(), body);
        assertEquals(230, body.select("br").size());
        assertTrue(body.text().startsWith("Line 1."));
        assertTrue(body.select("#report_box, #note_danmu_wrapper, script").isEmpty(), "page controls left in the text");
    }

    @Test
    void chapterBodyOfAPageWithoutTextIsNull() throws Exception {
        // e.g. a VIP chapter opened without having bought it
        assertNull(jjwxc_net.parseChapterBody(novelPage()));
    }

    @Test
    void metadata() throws Exception {
        NovelMetadata metadata = jjwxc_net.parseMetadata(novelPage());

        assertEquals("海岛求生：生活玩家种田囤货", metadata.getTitle());
        assertEquals("绯夷", metadata.getAuthor());
        assertTrue(metadata.getDescription().startsWith("Line 1."), "the novel's introduction");
        assertEquals(List.of("原创", "纯爱", "幻想未来", "剧情"), metadata.getSubjects());
        assertEquals("https://i9-static.jjwxc.net/novelimage.php?novelid=10035712&coverid=156&ver=ff36901ea3f4fd26ce12bcd826e25f4e",
                jjwxc_net.parseCoverUrl(novelPage()));
    }

    @Test
    void isMarkedAsAPaywallSite() {
        assertTrue(new jjwxc_net().isPaywallSite());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        assertSingleClassFile(jjwxc_net.class);
    }

    @Test
    @Tag("live")
    void live_fetchesNovelPageAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(jjwxc_net.USER_AGENT).get();
        List<Chapter> chapters = jjwxc_net.parseChapterList(novelPage);
        assertFalse(chapters.isEmpty());
        assertEquals("海岛求生：生活玩家种田囤货", jjwxc_net.parseMetadata(novelPage).getTitle(), "GB18030 decoded");

        Thread.sleep(1500);
        Document chapterPage = Jsoup.connect(chapters.getFirst().chapterURL).userAgent(jjwxc_net.USER_AGENT).get();
        assertNotNull(jjwxc_net.parseChapterBody(chapterPage));
    }
}
