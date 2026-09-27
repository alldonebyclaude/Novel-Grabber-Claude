package grabber.formats;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import system.Config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes each downloaded chapter to its own Markdown file, e.g. to translate a novel chapter by chapter:
 * {@code <save location>/<title> (chapters)/0001 - <chapter name>.md}.
 * <p>
 * Files are numbered by the chapter's place in the novel, so downloading chapters 50 to 60 later adds
 * {@code 0050 ...} to {@code 0060 ...} to the same folder. Each file is written as soon as its chapter is downloaded.
 */
public class ChapterFiles {
    private static final int MAX_NAME_LENGTH = 150;
    private static final Pattern NOT_ALLOWED_IN_FILE_NAMES = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    /** Text at the start of a paragraph that Markdown would read as a heading, quote or list. */
    private static final Pattern BLOCK_MARKER = Pattern.compile("^(#{1,6}\\s|>|[-+]\\s|\\d+\\.\\s)");

    private final Novel novel;

    public ChapterFiles(Novel novel) {
        this.novel = novel;
    }

    /** Whether chapter files are chosen for this download: the setting or the -chapterFiles flag. */
    public static boolean isEnabled(Novel novel) {
        return novel.chapterFiles || Config.getInstance().isChapterFiles();
    }

    /** The folder the chapter files go to: {@code <save location>/<title> (chapters)}. */
    public Path folder() {
        String title = novel.metadata == null ? "" : safeName(novel.metadata.getTitle());
        return Path.of(novel.saveLocation, (title.isEmpty() ? "Novel" : title) + " (chapters)");
    }

    /**
     * Writes one downloaded chapter. Called right after each chapter is downloaded, so a download that is stopped
     * or fails halfway keeps the chapters it already has.
     */
    public void writeChapter(Chapter chapter) {
        int number = novel.chapterList == null ? 0 : novel.chapterList.indexOf(chapter) + 1;
        if (number == 0 && novel.successfulChapters != null) number = novel.successfulChapters.indexOf(chapter) + 1;
        if (number == 0) number = 1;
        int width = Math.max(4, String.valueOf(novel.chapterList == null ? 0 : novel.chapterList.size()).length());
        // With "display chapter title" the chapter content already starts with the title
        String markdown = toMarkdown(chapter.name, chapter.chapterContent, !novel.displayChapterTitle);
        try {
            Path folder = folder();
            Files.createDirectories(folder);
            try (BufferedWriter writer = Files.newBufferedWriter(folder.resolve(fileName(number, width, chapter.name)),
                    StandardCharsets.UTF_8)) {
                writer.write(markdown);
            }
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not write the chapter file for " + chapter.name + ". " + e.getMessage(), e);
        }
    }

    /**
     * Writes all downloaded chapters at once (the download itself writes each chapter as it comes in).
     */
    public void write() {
        for (Chapter chapter : novel.successfulChapters) {
            writeChapter(chapter);
        }
        GrabberUtils.info(novel.window, "Chapter files: " + folder());
    }

    /**
     * The file name for a chapter, e.g. {@code 0007 - Chapter 7 The End.md}. Keeps the title's letters in any
     * script and removes only characters Windows does not allow in file names.
     */
    static String fileName(int number, int width, String chapterName) {
        String name = safeName(chapterName);
        return String.format("%0" + width + "d", number) + " - " + (name.isEmpty() ? "Chapter" : name) + ".md";
    }

    private static String safeName(String name) {
        String safe = NOT_ALLOWED_IN_FILE_NAMES.matcher(Normalizer.normalize(name, Normalizer.Form.NFC)).replaceAll("");
        safe = safe.replaceAll("\\s+", " ").strip();
        if (safe.codePointCount(0, safe.length()) > MAX_NAME_LENGTH) {
            safe = safe.substring(0, safe.offsetByCodePoints(0, MAX_NAME_LENGTH));
        }
        // Windows drops trailing dots and spaces
        return safe.replaceAll("[. ]+$", "");
    }

    /**
     * Converts a chapter's HTML to Markdown: one paragraph per block element or line break, emphasis as
     * {@code *...*} and {@code **...**}, headings, scene breaks ({@code ---}), quotes and list items. Images,
     * scripts and links' targets are left out.
     *
     * @param addTitle start with the chapter name as a heading
     */
    static String toMarkdown(String title, String chapterHtml, boolean addTitle) {
        MarkdownWriter writer = new MarkdownWriter();
        if (addTitle) writer.blocks.add("# " + escapeInline(title.strip()));
        for (Node node : Jsoup.parseBodyFragment(chapterHtml).body().childNodes()) {
            writer.write(node);
        }
        writer.endParagraph();
        return writer.blocks.isEmpty() ? "" : String.join("\n\n", writer.blocks) + "\n";
    }

    private static String escapeInline(String text) {
        return text.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
    }

    private static final class MarkdownWriter {
        final List<String> blocks = new ArrayList<>();
        private final StringBuilder paragraph = new StringBuilder();
        private int headingLevel;
        private int quoteDepth;
        private boolean listItem;

        void write(Node node) {
            if (node instanceof TextNode text) {
                paragraph.append(escapeInline(text.getWholeText().replaceAll("[\\s\\u00A0]+", " ")));
            } else if (node instanceof Element element) {
                write(element);
            }
        }

        private void write(Element element) {
            switch (element.normalName()) {
                case "br" -> endParagraph();
                case "hr" -> {
                    endParagraph();
                    blocks.add("---");
                }
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    endParagraph();
                    headingLevel = element.normalName().charAt(1) - '0';
                    writeChildren(element);
                    endParagraph();
                    headingLevel = 0;
                }
                case "blockquote" -> {
                    endParagraph();
                    quoteDepth++;
                    writeChildren(element);
                    endParagraph();
                    quoteDepth--;
                }
                case "li" -> {
                    endParagraph();
                    listItem = true;
                    writeChildren(element);
                    endParagraph();
                    listItem = false;
                }
                case "p", "div", "section", "article", "center", "pre", "table", "tr", "ul", "ol", "dl", "dt", "dd" -> {
                    endParagraph();
                    writeChildren(element);
                    endParagraph();
                }
                case "em", "i", "cite" -> emphasise(element, "*");
                case "strong", "b" -> emphasise(element, "**");
                case "img", "script", "style", "noscript", "iframe" -> {
                }
                default -> writeChildren(element);
            }
        }

        private void writeChildren(Element element) {
            for (Node child : element.childNodes()) {
                write(child);
            }
        }

        /** Wraps the element's text in markers, keeping surrounding spaces outside them so Markdown still sees them. */
        private void emphasise(Element element, String marker) {
            int start = paragraph.length();
            writeChildren(element);
            String inner = paragraph.substring(start);
            paragraph.setLength(start);
            String text = inner.strip();
            if (text.isEmpty()) {
                paragraph.append(inner.isEmpty() ? "" : " ");
                return;
            }
            if (Character.isWhitespace(inner.charAt(0))) paragraph.append(' ');
            paragraph.append(marker).append(text).append(marker);
            if (Character.isWhitespace(inner.charAt(inner.length() - 1))) paragraph.append(' ');
        }

        void endParagraph() {
            String text = paragraph.toString().replaceAll(" {2,}", " ").strip();
            paragraph.setLength(0);
            if (text.isEmpty()) return;

            if (headingLevel > 0) {
                text = "#".repeat(headingLevel) + " " + text;
            } else if (BLOCK_MARKER.matcher(text).find()) {
                text = "\\" + text;
            }
            if (listItem) {
                text = "- " + text;
                listItem = false;
            }
            blocks.add("> ".repeat(quoteDepth) + text);
        }
    }
}
