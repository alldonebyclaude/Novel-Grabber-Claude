package grabber.formats;

import grabber.Chapter;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the written package document directly: epublib's EpubReader treats the first page as the
 * cover page when a book has none, so reading the book back would hide a missing cover page.
 */
class EPUBCoverPageTest {

    @TempDir
    Path saveLocation;

    // A novel with one downloaded chapter and the placeholder cover NovelMetadata starts with.
    private Novel novel(String window) {
        NovelMetadata metadata = new NovelMetadata();
        metadata.setTitle("Test Novel");
        metadata.setAuthor("Test Author");

        Chapter chapter = new Chapter("Chapter 1", "https://example.com/novel/test/chapter/1");
        chapter.chapterContent = "<div><p>Chapter text.</p></div>";
        chapter.status = 1;
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(chapter);

        Novel novel = new Novel();
        novel.metadata = metadata;
        novel.chapterList = chapters;
        novel.window = window;
        novel.novelLink = "https://example.com/novel/test";
        novel.saveLocation = saveLocation.toString();
        return novel;
    }

    /** The written EPUB: its package document and a way to read the pages it lists. */
    private record Epub(Path file, Document opf) {
        static Epub write(Novel novel, Path saveLocation) throws IOException {
            new EPUB(novel).write();
            Path file = saveLocation.resolve(novel.filename);
            return new Epub(file, Jsoup.parse(read(file, "OEBPS/content.opf"), "", Parser.xmlParser()));
        }

        private static String read(Path file, String entry) throws IOException {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                return new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String read(String entry) throws IOException {
            return read(file, entry);
        }

        String hrefOfId(String id) {
            return opf.selectFirst("*|item[id=" + id + "]").attr("href");
        }

        String coverPageHref() {
            Element reference = opf.selectFirst("*|guide > *|reference[type=cover]");
            return reference == null ? null : reference.attr("href");
        }

        List<Element> spine() {
            return opf.select("*|spine > *|itemref");
        }
    }

    @Test
    void bookOpensOnACoverPageShowingTheCoverImage() throws Exception {
        Epub epub = Epub.write(novel("gui"), saveLocation);

        String coverPage = epub.coverPageHref();
        assertNotNull(coverPage, "no cover page in the guide");

        Element firstPage = epub.spine().getFirst();
        assertEquals(coverPage, epub.hrefOfId(firstPage.attr("idref")), "cover page is not the first page");
        assertNotEquals("no", firstPage.attr("linear"), "cover page is hidden from the reading order");

        String coverImage = epub.hrefOfId(epub.opf.selectFirst("*|meta[name=cover]").attr("content"));
        assertTrue(epub.read("OEBPS/" + coverPage).contains("src=\"" + coverImage + "\""),
                "cover page does not show the cover image");
    }

    @Test
    void updatingAnExistingBookKeepsASingleCoverPage() throws Exception {
        Epub.write(novel("gui"), saveLocation);
        Epub updated = Epub.write(novel("checker"), saveLocation);

        String coverPage = updated.coverPageHref();
        List<String> pages = updated.spine().stream().map(itemref -> updated.hrefOfId(itemref.attr("idref"))).toList();
        assertEquals(1, pages.stream().filter(page -> page.equals(coverPage)).count(), "pages: " + pages);
        assertEquals(coverPage, pages.getFirst());
    }
}
