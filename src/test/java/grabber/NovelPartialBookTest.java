package grabber;

import grabber.sources.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class NovelPartialBookTest {

    @TempDir
    Path saveLocation;

    /** A source that serves made-up text and lets a test act while a chapter is being fetched. */
    private static Source source(Consumer<Chapter> whileFetching) {
        return new Source() {
            public String getName() { return "Test"; }
            public String getUrl() { return "https://example.com/"; }
            public boolean canHeadless() { return false; }
            public List<Chapter> getChapterList() { return new ArrayList<>(); }
            public Element getChapterContent(Chapter chapter) {
                whileFetching.accept(chapter);
                return Jsoup.parse("<div id=\"text\"><p>Text of " + chapter.name + ".</p></div>").selectFirst("#text");
            }
            public NovelMetadata getMetadata() { return new NovelMetadata(); }
            public List<String> getBlacklistedTags() { return new ArrayList<>(); }
        };
    }

    private Novel novel(String window, Consumer<Chapter> whileFetching) {
        Novel novel = new Novel();
        novel.metadata = new NovelMetadata();
        novel.metadata.setTitle("Test Novel");
        novel.metadata.setAuthor("Test Author");
        novel.window = window;
        novel.novelLink = "https://example.com/novel/test";
        novel.saveLocation = saveLocation.toString();
        novel.blacklistedTags = new ArrayList<>();
        novel.source = source(whileFetching);
        novel.chapterList = new ArrayList<>();
        for (int i = 1; i <= 3; i++) novel.chapterList.add(new Chapter("Chapter " + i, "https://example.com/novel/test/chapter/" + i));
        novel.firstChapter = 1;
        novel.lastChapter = 3;
        return novel;
    }

    private List<String> books() throws Exception {
        try (var files = Files.list(saveLocation)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".epub")).toList();
        }
    }

    @Test
    void aStoppedDownloadSavesTheChaptersItHas() throws Exception {
        Novel novel = novel("gui", null);
        novel.source = source(chapter -> novel.killTask = true); // the user presses stop during chapter 1

        assertThrows(InterruptedException.class, novel::downloadChapters, "callers still learn that it was stopped");

        List<String> books = books();
        assertEquals(1, books.size(), books.toString());
        assertTrue(books.getFirst().contains("(stopped after 1 of 3 chapters)"), books.getFirst());
    }

    @Test
    void aDownloadThatFailsHalfwaySavesTheChaptersItHas() throws Exception {
        Novel novel = novel("gui", chapter -> {
            if (chapter.name.equals("Chapter 2")) throw new IllegalStateException("site went down");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, novel::downloadChapters);
        assertEquals("site went down", failure.getMessage());

        List<String> books = books();
        assertEquals(1, books.size(), books.toString());
        assertTrue(books.getFirst().contains("(stopped after 1 of 3 chapters)"), books.getFirst());
    }

    @Test
    void theLibraryCheckerDoesNotWritePartialBooks() throws Exception {
        // It updates existing EPUB files in place; a second, partial file would confuse it.
        Novel novel = novel("checker", null);
        novel.source = source(chapter -> novel.killTask = true);

        assertThrows(InterruptedException.class, novel::downloadChapters);
        assertTrue(books().isEmpty());
    }
}
