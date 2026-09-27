package grabber.sources;

import grabber.Chapter;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NovellunarComTest {

    private static final String NOVEL_URL = "https://novellunar.com/novel/swallowed-star-v1";

    /** Loads a saved fixture as if it had been fetched from {@code url}. */
    private static Document fixture(String name, String url) throws IOException, URISyntaxException {
        URL resource = NovellunarComTest.class.getClassLoader().getResource("novellunar/" + name);
        assertNotNull(resource, "missing fixture " + name);
        return Jsoup.parse(new File(resource.toURI()), "UTF-8", url);
    }

    private static Document chapterPage(String name, int index) throws IOException, URISyntaxException {
        return fixture(name, NOVEL_URL + "/chapter/" + index);
    }

    /** Applies the source's blacklist the same way Chapter.removeUnwantedTags does. */
    private static Element withBlacklistApplied(Element body) {
        for (String selector : new novellunar_com().getBlacklistedTags()) {
            body.select(selector).remove();
        }
        return body;
    }

    @Nested
    class ChapterList {

        @Test
        void readsEveryEntryFromTheEmbeddedDataInSiteOrder() throws Exception {
            List<Chapter> chapters = novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1));

            assertEquals(1081, chapters.size());
            assertEquals("Volume 1 - Chapter 1-2", chapters.get(0).name);
            assertEquals(NOVEL_URL + "/chapter/1", chapters.get(0).chapterURL);
            assertEquals(NOVEL_URL + "/chapter/1486", chapters.get(chapters.size() - 1).chapterURL);
        }

        @Test
        void titlesComeFromTheSiteNotFromTheIndex() throws Exception {
            List<Chapter> chapters = novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1));

            Chapter last = chapters.get(chapters.size() - 1);
            assertTrue(last.name.startsWith("Chapter 1486 - "), last.name);
        }

        @Test
        void skipsIndexesTheSiteDoesNotHave() throws Exception {
            Set<String> urls = novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1)).stream()
                    .map(c -> c.chapterURL)
                    .collect(Collectors.toSet());

            // The site's list jumps from 75 to 479 and also lacks 988 and 997.
            for (int missing : new int[]{76, 100, 478, 988, 997, 1487}) {
                assertFalse(urls.contains(NOVEL_URL + "/chapter/" + missing), "index " + missing);
            }
            for (int present : new int[]{75, 479, 987, 989, 1484, 1485}) {
                assertTrue(urls.contains(NOVEL_URL + "/chapter/" + present), "index " + present);
            }
        }

        @Test
        void usesSingularChapterPath() throws Exception {
            List<Chapter> chapters = novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1));

            assertTrue(chapters.stream().allMatch(c -> c.chapterURL.startsWith(NOVEL_URL + "/chapter/")));
        }

        @Test
        void isTheSameFromAnyChapterPage() throws Exception {
            List<Chapter> fromFirst = novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1));
            List<Chapter> fromLast = novellunar_com.parseChapterList(chapterPage("chapter-last.html", 1486));

            assertEquals(
                    fromFirst.stream().map(c -> c.chapterURL).collect(Collectors.toList()),
                    fromLast.stream().map(c -> c.chapterURL).collect(Collectors.toList()));
        }

        @Test
        void novelPageDoesNotCarryTheList() throws Exception {
            assertTrue(novellunar_com.parseChapterList(fixture("novel.html", NOVEL_URL)).isEmpty());
        }

        @Test
        void firstChapterLinkComesFromTheNovelPage() throws Exception {
            assertEquals(NOVEL_URL + "/chapter/1", novellunar_com.parseFirstChapterUrl(fixture("novel.html", NOVEL_URL)));
        }
    }

    @Nested
    class ChapterBody {

        @Test
        void rebuildsParagraphsFromTheSpanLayout() throws Exception {
            Element body = novellunar_com.parseChapterBody(chapterPage("chapter-2.html", 2));

            assertNotNull(body);
            assertEquals(76, body.select("p").size());
            assertTrue(body.select("span").isEmpty(), "spans should be flattened into paragraphs");
            assertEquals("Lorem ipsum paragraph 1.", body.selectFirst("p").text());
        }

        @Test
        void keepsInTextChapterHeadings() throws Exception {
            Element body = withBlacklistApplied(novellunar_com.parseChapterBody(chapterPage("chapter-1.html", 1)));

            List<String> headings = body.select("p").eachText().stream()
                    .filter(t -> t.matches("Chapter \\d+:.*"))
                    .collect(Collectors.toList());
            assertEquals(2, headings.size(), headings.toString());
        }

        @Test
        void blacklistRemovesSeparateCreditLines() throws Exception {
            Element body = withBlacklistApplied(novellunar_com.parseChapterBody(chapterPage("chapter-1.html", 1)));

            assertFalse(body.text().contains("Translator"));
            assertFalse(body.text().contains("Editor"));
            assertEquals(215 - 4, body.select("p").size());
        }

        @Test
        void blacklistRemovesCombinedCreditLine() throws Exception {
            Element body = withBlacklistApplied(novellunar_com.parseChapterBody(chapterPage("chapter-last.html", 1486)));

            assertFalse(body.text().contains("Translator"));
            assertEquals(147 - 1, body.select("p").size());
        }

        @Test
        void excludesTheAdBanner() throws Exception {
            Element body = withBlacklistApplied(novellunar_com.parseChapterBody(chapterPage("chapter-1.html", 1)));

            assertTrue(body.select("a, img").isEmpty());
            assertFalse(body.text().contains("NovelAI"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"chapter-missing.html", "chapter-gap.html"})
        void returnsNullForIndexesTheSiteDoesNotHave(String fixture) throws Exception {
            // The site answers these with HTTP 200 and a page without an <article>.
            assertNull(novellunar_com.parseChapterBody(chapterPage(fixture, 1487)));
        }
    }

    @Nested
    class Metadata {

        @Test
        void readsTitleAuthorAndDescription() throws Exception {
            NovelMetadata metadata = novellunar_com.parseMetadata(fixture("novel.html", NOVEL_URL));

            assertEquals("Swallowed Star", metadata.getTitle());
            assertEquals("我吃西红柿, Wo Chi Xi Hong Shi, I Eat Tomatoes", metadata.getAuthor());
            assertFalse(metadata.getDescription().isBlank());
        }

        @Test
        void readsGenresAndCover() throws Exception {
            Document novelPage = fixture("novel.html", NOVEL_URL);

            assertEquals(List.of("Sci-Fi", "Martial Arts", "Adventure", "Fantasy", "Action", "Xuanhuan"),
                    novellunar_com.parseMetadata(novelPage).getSubjects());
            assertEquals("https://img.novellunar.com/swallowed-star-v1.webp", novellunar_com.parseCoverUrl(novelPage));
        }
    }

    @Nested
    class Urls {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://novellunar.com/novel/swallowed-star-v1",
                "https://novellunar.com/novel/swallowed-star-v1/",
                "https://novellunar.com/novel/swallowed-star-v1/chapter/3",
        })
        void findsSlugFromNovelOrChapterUrl(String url) {
            assertEquals("swallowed-star-v1", novellunar_com.novelSlug(url));
        }

        @Test
        void keepsIdSuffixInSlug() {
            assertEquals("the-mech-touch-v5_69bfbb4adb75926344002907",
                    novellunar_com.novelSlug("https://novellunar.com/novel/the-mech-touch-v5_69bfbb4adb75926344002907"));
        }

        @Test
        void rejectsUrlsWithoutANovelSlug() {
            assertNull(novellunar_com.novelSlug("https://novellunar.com/ranking"));
        }
    }

    @Test
    void warnsAboutChaptersAndVolumesTheSiteHasOutOfOrderOrMissing() throws Exception {
        // Swallowed Star on the site: chapters 21-40 are labelled Volume 4 and 41-73 Volume 5, although the
        // numbers continue, and after chapter 75 the list jumps to 479. The source can't repair that; it says so.
        List<String> warnings = novellunar_com.checkChapterList(novellunar_com.parseChapterList(chapterPage("chapter-1.html", 1)));

        assertEquals(List.of(
                "The site's chapter list skips chapters 76–478, 988 and 997; they are not on the site.",
                "The site's volume labels jump (Volume 2 → 1 at chapter 3, Volume 1 → 4 at chapter 21, "
                        + "Volume 5 → 10 at chapter 74), so these chapters may not be in story order."),
                warnings);
    }

    @Test
    void noWarningsForAContinuousList() {
        List<Chapter> chapters = List.of(
                new Chapter("Volume 1 - Chapter 1: Start", "https://novellunar.com/novel/x/chapter/1"),
                new Chapter("Volume 1 - Chapter 2: Middle", "https://novellunar.com/novel/x/chapter/2"),
                new Chapter("Volume 2 - Chapter 3: End", "https://novellunar.com/novel/x/chapter/3"),
                new Chapter("Chapter 4", "https://novellunar.com/novel/x/chapter/4"));

        assertTrue(novellunar_com.checkChapterList(chapters).isEmpty());
    }

    @Test
    void compilesToASingleClassFile() throws Exception {
        // GrabberUtils.getSources() instantiates every .class file in sources/grabber/sources/ as a Source,
        // so nested classes (records, anonymous classes) would break the whole source list.
        File classFile = new File(novellunar_com.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File[] nested = new File(classFile, "grabber/sources").listFiles((dir, name) -> name.startsWith("novellunar_com$"));
        assertNotNull(nested);
        assertEquals(0, nested.length, "nested classes: " + List.of(nested));
    }

    @Test
    @Tag("live")
    void live_fetchesListAndFirstChapter() throws Exception {
        // Two requests, one at a time, with a pause in between.
        Document chapterPage = Jsoup.connect(NOVEL_URL + "/chapter/1").userAgent(novellunar_com.USER_AGENT).get();
        List<Chapter> chapters = novellunar_com.parseChapterList(chapterPage);
        assertFalse(chapters.isEmpty());
        assertNotNull(novellunar_com.parseChapterBody(chapterPage));

        Thread.sleep(1500);
        Document novelPage = Jsoup.connect(NOVEL_URL).userAgent(novellunar_com.USER_AGENT).get();
        assertEquals("Swallowed Star", novellunar_com.parseMetadata(novelPage).getTitle());
    }
}
