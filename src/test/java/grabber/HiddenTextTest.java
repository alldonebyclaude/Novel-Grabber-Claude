package grabber;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HiddenTextTest {

    private static Element chapter(String html) {
        return Jsoup.parse(html).selectFirst("#chapter");
    }

    @Test
    void removesElementsHiddenByClassRulesAndInlineStyles() {
        Element chapter = chapter("""
                <style>.a, .b { display: none } .c{visibility:hidden;color:red} .shown { color: blue }</style>
                <div id="chapter"><p>One.</p><p class="a">Hidden a.</p><span class="b x">Hidden b.</span>
                <p class="c">Hidden c.</p><p style="display:none">Hidden inline.</p><p class="shown">Two.</p></div>""");

        assertEquals(4, HiddenText.remove(chapter));
        assertEquals("One. Two.", chapter.text());
    }

    @Test
    void onlyFollowsSimpleClassRules() {
        // A rule like ".menu .item" hides things in a certain place; applying it to the chapter could remove real text.
        Element chapter = chapter("""
                <style>.menu .item { display: none } p.note { display: none }</style>
                <div id="chapter"><p class="item">Text.</p><p class="note">More text.</p></div>""");

        assertEquals(0, HiddenText.remove(chapter));
        assertEquals("Text. More text.", chapter.text());
    }

    @Test
    void neverRemovesTheChapterItself() {
        Element chapter = chapter("""
                <style>.wrapper { display: none }</style><div id="chapter" class="wrapper"><p>Text.</p></div>""");

        assertEquals(0, HiddenText.remove(chapter));
        assertEquals("Text.", chapter.text());
    }

    @Test
    void worksOnAChapterBuiltOutsideItsPage() {
        // Some sources build the chapter as a new element; only inline styles are known then.
        Element chapter = new Element("div");
        chapter.appendElement("p").text("Text.");
        chapter.appendElement("p").attr("style", "DISPLAY: NONE").text("Hidden.");

        assertEquals(1, HiddenText.remove(chapter));
        assertEquals("Text.", chapter.text());
    }
}
