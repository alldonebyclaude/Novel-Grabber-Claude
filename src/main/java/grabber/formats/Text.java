package grabber.formats;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.Jsoup;
import system.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Text {
    private Novel novel;
    private final NovelMetadata novelMetadata;

    public Text(Novel novel) {
        this.novel = novel;
        this.novelMetadata = novel.metadata;
    }

    public void write() {
        String filename = setFilename();
        GrabberUtils.createDir(novel.saveLocation);
        GrabberUtils.info(novel.window,"Writing TXT...");
        // One file per chapter is the "chapter files" option, which works for every output format
        filename += ".txt";
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(novel.saveLocation + "/" + filename), StandardCharsets.UTF_8))) {
            for(Chapter chapter: novel.successfulChapters) {
                // Preserve line breaks
                writer.write(Jsoup.parse(chapter.chapterContent).wholeText());
            }
            novel.filename = filename;
            GrabberUtils.info(novel.window, "Output: " + novel.saveLocation + "/" + filename);
        } catch (UnsupportedEncodingException | FileNotFoundException e) {
            GrabberUtils.err(novel.window, e.getMessage(), e);
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not write file. "+e.getMessage(), e);
        }
    }

    private String setFilename() {
        String filename = "Unknown";
        switch (Config.getInstance().getFilenameFormat()) {
            case 0:
                filename = novelMetadata.getAuthor() + " - " + novelMetadata.getTitle();
                break;
            case 1:
                filename = novelMetadata.getTitle() + " - " + novelMetadata.getAuthor();
                break;
            case 2:
                filename = novelMetadata.getTitle();
                break;
            case 3:
                String template = Config.getInstance().getNovelFileNameTemplate();
                filename = template
                        .replace("%t",novelMetadata.getTitle())
                        .replace("%a", novelMetadata.getAuthor())
                        .replace("%fc", String.valueOf(novel.firstChapter))
                        .replace("%lc", String.valueOf(novel.lastChapter));

                break;
        }
        if(novel.window.equals("checker")) filename =
                novel.firstChapter + "-"+ novel.lastChapter+"-"+filename.replaceAll(" ","-");
        return (filename + novel.bookNameSuffix).replaceAll("[\\\\/:*?\"<>|]", "");
    }

}
