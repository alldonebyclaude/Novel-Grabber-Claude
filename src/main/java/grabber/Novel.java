package grabber;

import bots.telegram.DownloadTask;
import grabber.formats.ChapterFiles;
import grabber.formats.EPUB;
import grabber.formats.PDF;
import grabber.formats.Text;
import grabber.sources.Source;
import notifications.DesktopNotification;
import org.jsoup.nodes.Document;
import system.Config;
import system.init;
import java.awt.image.BufferedImage;
import java.util.*;

public class Novel {
    public Source source;
    public Driver headlessDriver;
    public Document tableOfContent;
    public Map<String, String> cookies = new HashMap<>();
    public List<Chapter> chapterList;
    public List<Chapter> successfulChapters;
    public List<Chapter> failedChapters;
    public NovelMetadata metadata;
    public List<String> blacklistedTags;
    public HashMap<String, BufferedImage> images = new HashMap<>();
    public DownloadTask downloadTask;
    public boolean killTask;
    public boolean reGrab = false;
    public boolean getImages = false;
    public boolean displayChapterTitle = false;
    public boolean noDescription = false;
    public boolean chapterFiles = false; // also write each chapter as a Markdown file (-chapterFiles)
    public boolean removeHiddenText = false; // remove text the site hides with CSS (-removeHiddenText)
    public String bookNameSuffix = ""; // added to the book's file name, e.g. for a book of a stopped download
    public boolean reverseOrder = false;
    public boolean useHeadless = false;
    public boolean headlessGUI = false;
    public boolean useAccount = false;
    public boolean autoDetectContainer = false;
    public int waitTime = 0;
    public int firstChapter;
    public int lastChapter;
    public int wordCount = 0;
    public String saveLocation;
    public String window;
    public String browser;
    public String novelLink;
    public String nextChapterBtn = "NOT_SET";
    public String nextChapterURL;
    public String filename;

    /**
     * Main novel download handling object.
     * Fetches and stores novel information.
     * Downloads chapters and creates EPUB.
     */
    public Novel() { }

    /**
     * Builder methods to create a novel object from various download settings.
     */
    public static NovelBuilder builder() {
        return new NovelBuilder();
    }

    /**
     * Modifies an existing Novel object without creating a new one.
     */
    public static NovelBuilder modifier(Novel novel) {
        return new NovelBuilder(novel);
    }

    /**
     * Fetches metadata, blacklisted tags, chapter list.
     */
    public void check() {
        if(source != null) {
            // Reset chapter numbering
            Chapter.chapterCounter = 0;
            if(useAccount) {
                getLoginCookies();
            }
            chapterList = source.getChapterList();
            // Are created in GUI for manual
            if(!window.equals("manual")) {
                blacklistedTags = source.getBlacklistedTags();
                metadata = source.getMetadata();
            }
        } else {
            GrabberUtils.err(window, "No source!");
        }
    }

    public void getLoginCookies() {
        GrabberUtils.info("Fetching cookies");
        cookies = Accounts.getInstance().getCookiesForDomain(source.getName());
    }

    /**
     * Downloads chapters from list.
     * @throws InterruptedException on stopped grabbing.
     */
    public void downloadChapters() throws InterruptedException{
        GrabberUtils.info(window,"Starting download...");
        // Preparation
        if(init.gui != null) {
            init.gui.setMaxProgress(window, lastChapter-firstChapter+1);
        }
        if(reGrab) {
            wordCount = 0;
            for(Chapter chapter: chapterList) chapter.status = 0; // Reset download status of chapters
        }
        if(reverseOrder) Collections.reverse(chapterList);
        // Download handling
        try {
            for(int i = firstChapter-1; i < lastChapter; i++) { // -1 since chapter numbers start at 1
                // replace with actual interrupted
                if(killTask) {
                    throw new InterruptedException("Download stopped.");
                }
                chapterList.get(i).saveChapter(this);
                if(init.gui != null) {
                    init.gui.updateProgress(window);
                }
                if (this.downloadTask != null) {
                    this.downloadTask.updateProgress(i, this.lastChapter);
                }
                GrabberUtils.sleep(waitTime);
            }
        } catch (InterruptedException | RuntimeException e) {
            // Stopped or failed halfway: keep what was downloaded, then let the caller handle it as before
            savePartialBook(lastChapter - firstChapter + 1);
            throw e;
        }
        reGrab = true;
    }

    /**
     * Follows the chapters via a "next chapter button".
     */
    public void processChaptersToChapters(String firstChapterURL,
                                          String lastChapterURL,
                                          String nextChapterBtn,
                                          String chapterNumberString) throws InterruptedException {
        GrabberUtils.info(window, "Connecting...");
        init.gui.setMaxProgress(window, 9001);

        nextChapterURL = firstChapterURL;
        this.nextChapterBtn = nextChapterBtn;
        int chapterNumber = 1;
        if(chapterNumberString != null && !chapterNumberString.isEmpty()) chapterNumber = Integer.parseInt(chapterNumberString);
        // Reset chapter numbering
        Chapter.chapterCounter = 0;
        chapterList = new ArrayList<>();
        images = new HashMap<>();
        try {
            followChapters(lastChapterURL, chapterNumber);
        } catch (InterruptedException | RuntimeException e) {
            // Stopped or failed halfway: keep what was downloaded, then let the caller handle it as before
            savePartialBook(null);
            throw e;
        }
    }

    private void followChapters(String lastChapterURL, int chapterNumber) throws InterruptedException {
        while (true) {
            // replace with actual interrupted
            if(killTask) {
                throw new InterruptedException("Download stopped.");
            }
            Chapter currentChapter = new Chapter("Chapter " + chapterNumber++, nextChapterURL);
            chapterList.add(currentChapter);
            currentChapter.saveChapter(this);

            init.gui.updateProgress(window);

            // Reached final chapter
            if (nextChapterURL.equals(lastChapterURL) || (nextChapterURL + "/").equals(lastChapterURL)) {
                GrabberUtils.sleep(waitTime);
                this.nextChapterBtn = "NOT_SET";
                currentChapter = new Chapter("Chapter " + chapterNumber++, nextChapterURL);
                chapterList.add(currentChapter);
                currentChapter.saveChapter(this);
                init.gui.updateProgress(window);
                break;
            }
            GrabberUtils.sleep(waitTime);
        }
    }

    public void retry() throws InterruptedException {
        GrabberUtils.info(window,"Retrying failed chapters...");

        if(init.gui != null) {
            init.gui.setMaxProgress(window, failedChapters.size());
        }

        for (int i = 0; i < failedChapters.size(); i++) {
            Chapter chapter = failedChapters.get(i);
            chapter.saveChapter(this);
            if(chapter.status == 1) {
                successfulChapters.add(chapter);
            }
            if(init.gui != null) {
                init.gui.updateProgress(window);
            }
            if (this.downloadTask != null) {
                this.downloadTask.updateProgress(i, this.lastChapter);
            }
            // replace with actual interrupted
            if(killTask) {
                throw new InterruptedException("Download stopped.");
            }
            GrabberUtils.sleep(waitTime);
        }
        failedChapters.removeIf(chapter -> chapter.status != 2);
        // Show failed chapter window again if using gui
        if(init.gui != null && !failedChapters.isEmpty()) {
            init.gui.showFailedChapters(this);
        } else {
            // Output EPUB if at least one chapter was downloaded
            if(!successfulChapters.isEmpty()) {
                writeBookFiles();
            }
        }
    }

    /**
     * Prints potential failed chapters.
     * Reverses the chapter order for next grabbing. (If grabbing was stopped with this option selected)
     * Closes headless driver if used.
     * Writes EPUB
     */
    public void output() {
        // Print finishing information
        GrabberUtils.info(window,"Download finished.");

        // Reverse chapter order if needed for potential re-grabbing
        if(reverseOrder) Collections.reverse(chapterList);

        // Print failed chapters
        successfulChapters = new ArrayList<>();
        failedChapters = new ArrayList<>();
        for(Chapter chapter: chapterList) {
            if(chapter.status == 1) { // 0 = not downloaded, 1 = successfully downloaded, 2 = failed download
                successfulChapters.add(chapter);
            }
            if(chapter.status == 2) {
                failedChapters.add(chapter);
            }
        }

        for (Chapter chapter: failedChapters) {
            GrabberUtils.err(window, "Failed to download: " + chapter.name);
        }

        // Set driver to null. Closed() driver cant be reopened
        if(headlessDriver != null) {
            headlessDriver.close();
            headlessDriver = null; // close() != null --> checks to start a new browser are against null
        }
        // Show failed chapter window if using gui
        if(init.gui != null && !failedChapters.isEmpty()) {
            init.gui.showFailedChapters(this);
        } else {
            // Output EPUB if at least one chapter was downloaded
            if(!successfulChapters.isEmpty()) {
                // Make interface
                writeBookFiles();
                if (init.gui != null && Config.getInstance().isShowNovelFinishedNotification()) {
                    DesktopNotification.sendDownloadFinishedNotification(this);
                }
            }
        }
    }

    /**
     * Writes a book of the chapters downloaded so far, for a download that was stopped or failed halfway. Its file
     * name says so, e.g. "(stopped after 37 of 100 chapters)", so it never replaces a complete book. Not done for the
     * library checker, which updates existing books in place.
     *
     * @param planned the number of chapters the download was for, or null if unknown (chapter-to-chapter mode)
     */
    private void savePartialBook(Integer planned) {
        if ("checker".equals(window)) return;
        // Same order as output() writes the book in
        if (reverseOrder) Collections.reverse(chapterList);
        successfulChapters = new ArrayList<>();
        failedChapters = new ArrayList<>();
        for (Chapter chapter : chapterList) {
            if (chapter.status == 1) successfulChapters.add(chapter);
            if (chapter.status == 2) failedChapters.add(chapter);
        }
        if (successfulChapters.isEmpty()) return;

        bookNameSuffix = " (stopped after " + successfulChapters.size() + (planned != null ? " of " + planned : "") + " chapters)";
        try {
            writeBookFiles();
            GrabberUtils.info(window, "Download stopped: saved the " + successfulChapters.size() + " chapters downloaded so far.");
        } catch (RuntimeException e) {
            GrabberUtils.err(window, "Could not save the chapters downloaded so far. " + e.getMessage(), e);
        } finally {
            bookNameSuffix = "";
        }
    }

    /**
     * Writes the book in the selected output format. Chosen chapter files are already written, one per chapter.
     */
    private void writeBookFiles() {
        switch (Config.getInstance().getOutputFormat()) {
            case 0 -> new EPUB(this).write();
            case 1 -> new Text(this).write();
            case 2 -> new PDF(this).write();
            default -> GrabberUtils.err(window, "Unknown output format " + Config.getInstance().getOutputFormat());
        }
        // The chapter files were written during the download, one per chapter
        if (ChapterFiles.isEnabled(this)) {
            GrabberUtils.info(window, "Chapter files: " + new ChapterFiles(this).folder());
        }
    }
}
