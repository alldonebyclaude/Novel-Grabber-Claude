package grabber.formats;

import grabber.Chapter;
import grabber.Novel;
import grabber.NovelMetadata;
import grabber.sources.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChapterFilesTest {

    @TempDir
    Path saveLocation;

    @Test
    void paragraphsBecomeMarkdownParagraphsUnderTheTitle() {
        assertEquals("# Chapter 1\n\nFirst paragraph.\n\nSecond paragraph.\n",
                ChapterFiles.toMarkdown("Chapter 1", "<p>First paragraph.</p>\n<p>Second  paragraph.</p>", true));
    }

    @Test
    void lineBreaksSeparateParagraphs() {
        // Many sites put a whole chapter in one element and separate the paragraphs with <br>.
        assertEquals("One.\n\nTwo.\n\nThree.\n",
                ChapterFiles.toMarkdown("x", "<div>One.<br>Two.<br><br>Three.</div>", false));
    }

    @Test
    void emphasisIsKept() {
        assertEquals("He said *hello* and **left**.\n",
                ChapterFiles.toMarkdown("x", "<p>He said <em>hello</em> and <strong>left</strong>.</p>", false));
        assertEquals("*Italic* text and **bold** text.\n",
                ChapterFiles.toMarkdown("x", "<p><i> Italic </i>text and <b>bold </b>text.</p>", false));
        assertEquals("Nothing here.\n", ChapterFiles.toMarkdown("x", "<p>Nothing<em> </em>here.</p>", false));
    }

    @Test
    void sceneBreaksQuotesListsAndHeadings() {
        assertEquals("## Part two\n\nBefore.\n\n---\n\nAfter.\n\n> A letter.\n\n- First\n\n- Second\n",
                ChapterFiles.toMarkdown("x",
                        "<h2>Part two</h2><p>Before.</p><hr><p>After.</p><blockquote><p>A letter.</p></blockquote>"
                                + "<ul><li>First</li><li>Second</li></ul>", false));
    }

    @Test
    void markdownCharactersInTheTextAreEscaped() {
        assertEquals("2 \\* 3 = 6 and snake\\_case\n\n\\# not a heading\n\n\\> not a quote\n\n\\- not a list\n",
                ChapterFiles.toMarkdown("x",
                        "<p>2 * 3 = 6 and snake_case</p><p># not a heading</p><p>&gt; not a quote</p><p>- not a list</p>", false));
    }

    @Test
    void imagesAndScriptsAreLeftOut() {
        assertEquals("Text.\n", ChapterFiles.toMarkdown("x", "<p><img src=\"a.png\">Text.<script>x()</script></p>", false));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "7    | 4 | Chapter 7: The End?     | 0007 - Chapter 7 The End.md",
            "12   | 4 | Chương 12: Mở đầu       | 0012 - Chương 12 Mở đầu.md",
            "3    | 4 | 第三章 出发                 | 0003 - 第三章 出发.md",
            "5    | 4 | 'Trailing dots...'      | 0005 - Trailing dots.md",
            "2    | 4 | '   '                   | 0002 - Chapter.md",
            "12345| 4 | Long list               | 12345 - Long list.md",
    })
    void fileNamesKeepTheChapterTitle(int number, int width, String chapterName, String fileName) {
        // Only characters Windows does not allow in file names are removed; non-Latin titles stay readable.
        assertEquals(fileName, ChapterFiles.fileName(number, width, chapterName));
    }

    @Test
    void charactersWindowsDoesNotAllowAreRemoved() {
        assertEquals("0001 - abcdefghi.md", ChapterFiles.fileName(1, 4, "a/b\\c:d*e?f\"g<h>|i\u0007"));
    }

    @Test
    void longTitlesAreShortened() {
        String name = ChapterFiles.fileName(1, 4, "A".repeat(500));
        assertTrue(name.length() <= 160, name.length() + " characters");
        assertTrue(name.startsWith("0001 - AAA") && name.endsWith(".md"));
    }

    /** A source that serves one made-up paragraph per chapter, without the network. */
    private static Source sourceWithText() {
        return new Source() {
            public String getName() { return "Test"; }
            public String getUrl() { return "https://example.com/"; }
            public boolean canHeadless() { return false; }
            public List<Chapter> getChapterList() { return new ArrayList<>(); }
            public Element getChapterContent(Chapter chapter) {
                return Jsoup.parse("<div id=\"text\"><p>Text of " + chapter.name + ".</p></div>").selectFirst("#text");
            }
            public NovelMetadata getMetadata() { return new NovelMetadata(); }
            public List<String> getBlacklistedTags() { return new ArrayList<>(); }
        };
    }

    @Test
    void eachChapterIsWrittenAsSoonAsItIsDownloaded() throws Exception {
        // A download that is stopped or fails halfway keeps the chapters it already has.
        Novel novel = new Novel();
        novel.metadata = new NovelMetadata();
        novel.metadata.setTitle("Test Novel");
        novel.saveLocation = saveLocation.toString();
        novel.window = "gui";
        novel.source = sourceWithText();
        novel.blacklistedTags = new ArrayList<>();
        novel.chapterFiles = true;
        novel.chapterList = new ArrayList<>(List.of(
                new Chapter("Chapter 1", "https://example.com/novel/test/chapter/1"),
                new Chapter("Chapter 2", "https://example.com/novel/test/chapter/2")));
        Path folder = saveLocation.resolve("Test Novel (chapters)");

        novel.chapterList.getFirst().saveChapter(novel);

        assertEquals("# Chapter 1\n\nText of Chapter 1.\n", Files.readString(folder.resolve("0001 - Chapter 1.md"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(folder.resolve("0002 - Chapter 2.md")), "chapter 2 is not downloaded yet");
    }

    @Test
    void noFilesWhenChapterFilesAreOff() throws Exception {
        Novel novel = new Novel();
        novel.metadata = new NovelMetadata();
        novel.metadata.setTitle("Test Novel");
        novel.saveLocation = saveLocation.toString();
        novel.window = "gui";
        novel.source = sourceWithText();
        novel.blacklistedTags = new ArrayList<>();
        novel.chapterList = new ArrayList<>(List.of(new Chapter("Chapter 1", "https://example.com/novel/test/chapter/1")));

        novel.chapterList.getFirst().saveChapter(novel);

        assertFalse(Files.exists(saveLocation.resolve("Test Novel (chapters)")));
    }

    @Test
    void writesOneFilePerDownloadedChapterNumberedByItsPlaceInTheNovel() throws Exception {
        Novel novel = new Novel();
        novel.metadata = new NovelMetadata();
        novel.metadata.setTitle("Test: Novel");
        novel.saveLocation = saveLocation.toString();
        novel.window = "gui";
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Chapter chapter = new Chapter("Chapter " + i, "https://example.com/novel/test/chapter/" + i);
            chapter.chapterContent = "<p>Text of chapter " + i + ".</p>";
            chapter.status = 1;
            chapters.add(chapter);
        }
        novel.chapterList = chapters;
        // Only chapters 2 and 3 were downloaded this time
        novel.successfulChapters = new ArrayList<>(chapters.subList(1, 3));

        new ChapterFiles(novel).write();

        Path folder = saveLocation.resolve("Test Novel (chapters)");
        try (var files = Files.list(folder)) {
            assertEquals(List.of("0002 - Chapter 2.md", "0003 - Chapter 3.md"),
                    files.map(file -> file.getFileName().toString()).sorted().toList());
        }
        assertEquals("# Chapter 2\n\nText of chapter 2.\n",
                Files.readString(folder.resolve("0002 - Chapter 2.md"), StandardCharsets.UTF_8));
    }

    @Test
    void noSecondTitleWhenTheChapterAlreadyShowsIt() throws Exception {
        // With "display chapter title" on, the chapter content already starts with the title.
        assertEquals("# Chapter 1\n\nText.\n", ChapterFiles.toMarkdown("Chapter 1", "<h1>Chapter 1</h1><p>Text.</p>", false));
    }
}
