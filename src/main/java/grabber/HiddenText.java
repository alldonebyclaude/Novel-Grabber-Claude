package grabber;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes text a page hides from its readers with CSS, such as the anti-piracy notices some sites put into every
 * chapter. The site's readers never see it, but e-readers don't apply the site's styles and would show it.
 * <p>
 * Used for all sources, but only when chosen (setting "Remove hidden anti-piracy text", {@code -removeHiddenText});
 * by default chapters are saved as the site sends them.
 */
public final class HiddenText {
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^}]*)}");
    private static final Pattern HIDING = Pattern.compile("display\\s*:\\s*none|visibility\\s*:\\s*hidden", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIMPLE_CLASS = Pattern.compile("\\s*\\.([\\w-]+)\\s*");

    private HiddenText() {
    }

    /**
     * Removes the chapter's elements that its page hides: elements with a class that a {@code <style>} rule hides
     * ({@code .x { display: none }}) and elements with a hiding inline style. Only simple class rules are followed;
     * a rule like {@code .menu .item} hides something in a certain place and could otherwise remove real text.
     *
     * @return the number of elements removed
     */
    public static int remove(Element chapter) {
        Set<String> hiddenClasses = hiddenClasses(chapter.ownerDocument());
        List<Element> hidden = new ArrayList<>();
        for (Element element : chapter.getAllElements()) {
            if (element == chapter) continue;
            boolean hiddenByClass = element.classNames().stream().anyMatch(hiddenClasses::contains);
            if (hiddenByClass || HIDING.matcher(element.attr("style")).find()) hidden.add(element);
        }
        int removed = 0;
        for (Element element : hidden) {
            // Skip elements already removed together with a hidden ancestor
            if (isInside(element, chapter)) {
                element.remove();
                removed++;
            }
        }
        return removed;
    }

    private static boolean isInside(Element element, Element chapter) {
        for (Element parent = element.parent(); parent != null; parent = parent.parent()) {
            if (parent == chapter) return true;
        }
        return false;
    }

    private static Set<String> hiddenClasses(Document page) {
        Set<String> classes = new HashSet<>();
        if (page == null) return classes;
        for (Element style : page.select("style")) {
            Matcher rule = RULE.matcher(style.data());
            while (rule.find()) {
                if (!HIDING.matcher(rule.group(2)).find()) continue;
                for (String selector : rule.group(1).split(",")) {
                    Matcher simpleClass = SIMPLE_CLASS.matcher(selector);
                    if (simpleClass.matches()) classes.add(simpleClass.group(1));
                }
            }
        }
        return classes;
    }
}
