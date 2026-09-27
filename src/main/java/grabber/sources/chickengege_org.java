package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chicken Gege. State of the site, checked 2026-09-24 (not fixed yet, not deprecated):
 * <ul>
 *   <li>Novel pages ({@code /novels/<slug>/}) no longer list chapters, so {@code #novelList} finds nothing. Some
 *   novels are summary-only or licensed (chapters taken down). The chapters' own "TOC" link
 *   ({@code /novels/<abbreviation>/}) answered HTTP 521 (origin server down).</li>
 *   <li>Chapters live under {@code /translations/<abbreviation>/<abbreviation>-<n>/};
 *   {@code /translations/<abbreviation>/} redirects to chapter 1. Every chapter page has a chapter dropdown,
 *   {@code select[name=chapterList]}, whose options hold all chapter URLs in order (117 for "symx"), so one
 *   request gives the full list; walking the "Next" links is not needed.</li>
 *   <li>The chapters checked (first and latest of "symx") are password-protected WordPress posts: instead of the
 *   text the page shows a note and a form posting to {@code wp-login.php?action=postpass}. The source must not
 *   try to get around that.</li>
 *   <li>{@code article div.entry-content} now selects the block with that password form and the author box, not
 *   chapter text. The outer {@code article} carries WordPress's own {@code post-password-required} class whenever
 *   a chapter is still locked (confirmed against the live "symx" chapter 1 page, 2026-09-27); {@link
 *   #getChapterContent} checks for it and skips the chapter instead of saving the password form as its text.</li>
 * </ul>
 * Users with a chapter password can enter it once in a browser at the site's password page, then add the
 * {@code wp-postpass_<hash>} cookie WordPress sets to this novel's cookies with the "Edit Cookies" button: the
 * same cookie jar the source already sends with every request. End-to-end unlocking is untested here for lack of
 * a password; only the locked-chapter detection above was checked against the live site.
 */
public class chickengege_org implements Source {
    private final String name = "Chicken Gege";
    private final String url = "https://www.chickengege.org/";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public chickengege_org(Novel novel) {
        this.novel = novel;
    }

    public chickengege_org() {
    }

    public String getName() {
        return name;
    }

    public boolean canHeadless() {
        return canHeadless;
    }

    public String toString() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public List<Chapter> getChapterList() {
        List<Chapter> chapterList = new ArrayList<>();
        try {
            toc = Jsoup.connect(novel.novelLink).cookies(novel.cookies).get();
            Elements chapterLinks = toc.select("#novelList a");
            for (Element chapterLink : chapterLinks) {
                chapterList.add(new Chapter(chapterLink.text(), chapterLink.attr("abs:href")));
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        } catch (NullPointerException e) {
            GrabberUtils.err(novel.window, "Could not find expected selectors. Correct novel link?", e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        Element chapterBody = null;
        try {
            Document doc = Jsoup.connect(chapter.chapterURL).cookies(novel.cookies).get();
            if (!doc.select("article.post-password-required").isEmpty()) {
                GrabberUtils.err(novel.window, "\"" + chapter.name + "\" is password-protected and was skipped. "
                        + "Enter the chapter password once in a browser, then add the wp-postpass_ cookie it sets "
                        + "to this novel's cookies to unlock it.");
                return null;
            }
            chapterBody = doc.select("article div.entry-content").first();
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterBody;
    }

    public NovelMetadata getMetadata() {
        NovelMetadata metadata = new NovelMetadata();

        if (toc != null) {
            metadata.setTitle(toc.select("meta[property=og:title]").
                    attr("content")
                    .replace(" | Chicken Gege", "")
            );
            metadata.setDescription(toc.select("meta[property=og:description]").attr("content"));
            metadata.setBufferedCover(toc.select(".novelist-cover-image").attr("abs:src"));
        }

        return metadata;
    }

    public List<String> getBlacklistedTags() {
        List<String> blacklistedTags = new ArrayList<>();
        blacklistedTags.add(".meta-comments");
        blacklistedTags.add(".m-a-box");
        blacklistedTags.add(".m-a-box");
        return blacklistedTags;
    }

}
