package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * novellunar.com is a Next.js site. The novel page has no chapter list; every chapter page embeds the
 * full list in its React Server Components payload (the {@code self.__next_f.push(...)} scripts).
 * Chapter URLs use an index ({@code /chapter/<n>}) that is not the chapter number and has gaps,
 * so both the URLs and the titles are taken from that list.
 */
public class novellunar_com implements Source {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://novellunar.com";
    private static final Pattern SLUG = Pattern.compile("^https?://(?:www\\.)?novellunar\\.com/novel/([^/?#]+)");
    private static final String RSC_PUSH_PREFIX = "self.__next_f.push(";
    private static final Pattern CHAPTER_NUMBER = Pattern.compile("Chapter (\\d+)");
    private static final Pattern VOLUME_NUMBER = Pattern.compile("^Volume (\\d+)");
    private static final int MAX_LISTED = 5;

    private final String name = "Novellunar";
    private final String url = BASE_URL;
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public novellunar_com() {
    }

    public novellunar_com(Novel novel) {
        this.novel = novel;
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
        String slug = novelSlug(novel.novelLink);
        if (slug == null) {
            GrabberUtils.err(novel.window, "Not a novellunar.com novel link: " + novel.novelLink);
            return chapterList;
        }
        try {
            toc = fetch(BASE_URL + "/novel/" + slug);
            String firstChapterUrl = parseFirstChapterUrl(toc);
            if (firstChapterUrl == null) {
                GrabberUtils.err(novel.window, "Could not find the first chapter link. Correct novel link?");
                return chapterList;
            }
            chapterList = parseChapterList(fetch(firstChapterUrl));
            if (chapterList.isEmpty()) {
                GrabberUtils.err(novel.window, "Could not read the chapter list from " + firstChapterUrl);
            }
            for (String warning : checkChapterList(chapterList)) {
                GrabberUtils.info(novel.window, "Warning: " + warning);
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        Element chapterBody = null;
        try {
            chapterBody = parseChapterBody(fetch(chapter.chapterURL));
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterBody;
    }

    public NovelMetadata getMetadata() {
        if (toc == null) return new NovelMetadata();

        NovelMetadata metadata = parseMetadata(toc);
        String coverUrl = parseCoverUrl(toc);
        if (coverUrl != null) metadata.setBufferedCover(coverUrl);
        return metadata;
    }

    public List<String> getBlacklistedTags() {
        List<String> blacklistedTags = new ArrayList<>();
        // "NovelAI Studio" app banner between the navigation and the text
        blacklistedTags.add("a[href*=novelaistu]");
        blacklistedTags.add("img[src*=novelai-ads]");
        // Translator/editor credits, on their own or combined on one line, also in the middle of merged chapters
        blacklistedTags.add("p:matchesOwn((?i)^\\s*(translator|editor)\\s*:)");
        return blacklistedTags;
    }

    private Document fetch(String pageUrl) throws IOException {
        Map<String, String> cookies = novel.cookies != null ? novel.cookies : Collections.emptyMap();
        return Jsoup.connect(pageUrl).userAgent(USER_AGENT).cookies(cookies).get();
    }

    /**
     * Returns the novel slug from a novel or chapter URL, e.g. {@code swallowed-star-v1}, or null.
     */
    static String novelSlug(String link) {
        if (link == null) return null;
        Matcher matcher = SLUG.matcher(link.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Returns the absolute URL of the "Start Reading" link on the novel page, or null.
     */
    static String parseFirstChapterUrl(Document novelPage) {
        Element link = novelPage.selectFirst("a[href*=/chapter/]");
        return link == null ? null : link.attr("abs:href");
    }

    /**
     * Reads the chapter list embedded in a chapter page. Returns an empty list if the page has none
     * (the novel page, or a missing chapter).
     */
    static List<Chapter> parseChapterList(Document chapterPage) {
        List<Chapter> chapterList = new ArrayList<>();
        String slug = novelSlug(chapterPage.location());
        JSONArray entries = findJsonArray(readRscPayload(chapterPage), "\"chapters\":[");
        if (slug == null || entries == null) return chapterList;

        for (Object entry : entries) {
            JSONObject chapter = (JSONObject) entry;
            Object index = chapter.get("chapterNumber");
            Object title = chapter.get("title");
            if (index == null || title == null) continue;
            chapterList.add(new Chapter(title.toString(), BASE_URL + "/novel/" + slug + "/chapter/" + index));
        }
        return chapterList;
    }

    /**
     * Checks the site's chapter list for problems the source can't repair, as messages for the user: chapter numbers
     * the list skips, and volume labels that go back or skip ahead although the chapter numbers continue (on
     * Swallowed Star, chapters 21-40 are labelled Volume 4). Empty if the list looks continuous.
     */
    static List<String> checkChapterList(List<Chapter> chapterList) {
        List<String> skipped = new ArrayList<>();
        List<String> volumeJumps = new ArrayList<>();
        Integer previousNumber = null;
        Integer previousVolume = null;
        for (Chapter chapter : chapterList) {
            Integer number = firstNumber(CHAPTER_NUMBER, chapter.name);
            Integer volume = firstNumber(VOLUME_NUMBER, chapter.name);
            if (number != null && previousNumber != null && number > previousNumber + 1) {
                int from = previousNumber + 1;
                int to = number - 1;
                skipped.add(from == to ? String.valueOf(from) : from + "–" + to);
            }
            if (volume != null && previousVolume != null && (volume < previousVolume || volume > previousVolume + 1)) {
                volumeJumps.add("Volume " + previousVolume + " → " + volume + " at chapter " + (number != null ? number : chapter.name));
            }
            if (number != null) previousNumber = number;
            if (volume != null) previousVolume = volume;
        }
        List<String> warnings = new ArrayList<>();
        if (!skipped.isEmpty()) {
            warnings.add("The site's chapter list skips " + (skipped.size() == 1 && !skipped.getFirst().contains("–") ? "chapter " : "chapters ")
                    + listed(skipped) + "; they are not on the site.");
        }
        if (!volumeJumps.isEmpty()) {
            warnings.add("The site's volume labels jump (" + String.join(", ", volumeJumps.subList(0, Math.min(MAX_LISTED, volumeJumps.size())))
                    + (volumeJumps.size() > MAX_LISTED ? " and " + (volumeJumps.size() - MAX_LISTED) + " more" : "")
                    + "), so these chapters may not be in story order.");
        }
        return warnings;
    }

    private static Integer firstNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** "a", "a and b", "a, b and c", ... with at most MAX_LISTED items and "and N more" after that. */
    private static String listed(List<String> items) {
        if (items.size() > MAX_LISTED) {
            return String.join(", ", items.subList(0, MAX_LISTED)) + " and " + (items.size() - MAX_LISTED) + " more";
        }
        if (items.size() == 1) return items.getFirst();
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    /**
     * Returns the chapter text as a {@code <div>} of {@code <p>} elements, or null if the page has no
     * chapter text. The site answers missing indexes with HTTP 200 and a page without an {@code <article>}.
     * <p>
     * The site renders the text as {@code white-space: pre-wrap} spans: whitespace spans containing a newline
     * separate paragraphs, all other spans are pieces of the current paragraph.
     */
    static Element parseChapterBody(Document chapterPage) {
        Element text = chapterPage.selectFirst("article > div");
        if (text == null) return null;

        Element body = new Element("div");
        StringBuilder paragraph = new StringBuilder();
        for (Node node : text.childNodes()) {
            String content;
            if (node instanceof TextNode textNode) {
                content = textNode.getWholeText();
                if (content.isBlank() && content.contains("\n")) {
                    appendParagraph(body, paragraph);
                } else {
                    paragraph.append(textNode.outerHtml());
                }
            } else if (node instanceof Element element) {
                if (element.nameIs("br")) {
                    appendParagraph(body, paragraph);
                } else if (element.hasText() || !element.children().isEmpty()) {
                    paragraph.append(element.nameIs("span") ? element.html() : element.outerHtml());
                } else if (element.wholeText().contains("\n")) {
                    appendParagraph(body, paragraph);
                } else {
                    paragraph.append(' ');
                }
            }
        }
        appendParagraph(body, paragraph);
        return body;
    }

    private static void appendParagraph(Element body, StringBuilder paragraph) {
        String html = paragraph.toString().strip();
        if (!html.isEmpty()) {
            body.appendElement("p").html(html);
        }
        paragraph.setLength(0);
    }

    /**
     * Reads title, author, description and genres from the novel page. The cover is left to
     * {@link #parseCoverUrl(Document)}, because setting it on {@link NovelMetadata} downloads the image.
     * <p>
     * Deliberately returns an existing class instead of a nested record: the app instantiates every
     * {@code .class} file in {@code sources/grabber/sources/} as a Source, so a source must compile to one file.
     */
    static NovelMetadata parseMetadata(Document novelPage) {
        NovelMetadata metadata = new NovelMetadata();
        Element title = novelPage.selectFirst("h1.text-2xl");
        Element author = novelPage.selectFirst("a[href^=/author/]");
        Element description = novelPage.selectFirst("p.whitespace-pre-wrap");

        if (title != null) metadata.setTitle(title.text());
        if (author != null) metadata.setAuthor(author.text());
        if (description != null) metadata.setDescription(description.wholeText().strip());
        metadata.setSubjects(novelPage.select("a[href^=/tag/]").eachText());
        return metadata;
    }

    static String parseCoverUrl(Document novelPage) {
        Element cover = novelPage.selectFirst("meta[property=og:image]");
        return cover == null ? null : cover.absUrl("content");
    }

    /**
     * Joins the string chunks of all {@code self.__next_f.push([1, "..."])} scripts into the RSC payload.
     */
    private static String readRscPayload(Document page) {
        StringBuilder payload = new StringBuilder();
        JSONParser parser = new JSONParser();
        for (Element script : page.select("script")) {
            String data = script.data().strip();
            if (!data.startsWith(RSC_PUSH_PREFIX) || !data.endsWith(")")) continue;
            try {
                Object chunk = parser.parse(data.substring(RSC_PUSH_PREFIX.length(), data.length() - 1));
                if (chunk instanceof JSONArray array && array.size() > 1 && array.get(1) instanceof String s) {
                    payload.append(s);
                }
            } catch (ParseException e) {
                // Not a data chunk; the payload also carries module references we don't need.
            }
        }
        return payload.toString();
    }

    /**
     * Finds {@code marker} (which must end with '[') in {@code json} and parses the array that follows it.
     */
    private static JSONArray findJsonArray(String json, String marker) {
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length() - 1;

        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && --depth == 0) {
                try {
                    return (JSONArray) new JSONParser().parse(json.substring(start, i + 1));
                } catch (ParseException | ClassCastException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
