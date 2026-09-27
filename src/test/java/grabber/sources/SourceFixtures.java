package grabber.sources;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the saved pages in src/test/resources/&lt;domain&gt;/. Chapter pages there have their text replaced by
 * "Line N." placeholders; the markup around it is unchanged.
 */
final class SourceFixtures {
    private SourceFixtures() {
    }

    private static File file(String domain, String name) throws Exception {
        URL resource = SourceFixtures.class.getClassLoader().getResource(domain + "/" + name);
        assertNotNull(resource, "missing fixture " + domain + "/" + name);
        return new File(resource.toURI());
    }

    /** Loads a saved page as if it had been fetched from {@code url}, so {@code abs:href} resolves. */
    static Document page(String domain, String name, String url) throws Exception {
        return Jsoup.parse(file(domain, name), "UTF-8", url);
    }

    static String text(String domain, String name) throws Exception {
        return Files.readString(file(domain, name).toPath(), StandardCharsets.UTF_8);
    }

    /** Applies a source's blacklist the same way Chapter.removeUnwantedTags does. */
    static Element withBlacklistApplied(Source source, Element body) {
        for (String selector : source.getBlacklistedTags()) {
            body.select(selector).remove();
        }
        return body;
    }

    /**
     * GrabberUtils.getSources() instantiates every .class file in sources/grabber/sources/ as a Source,
     * so a source must not compile to nested classes (records, inner or anonymous classes). Lambdas are fine.
     */
    static void assertSingleClassFile(Class<? extends Source> source) throws Exception {
        File classes = new File(source.getProtectionDomain().getCodeSource().getLocation().toURI());
        File[] nested = new File(classes, "grabber/sources")
                .listFiles((dir, name) -> name.startsWith(source.getSimpleName() + "$"));
        assertNotNull(nested);
        assertEquals(0, nested.length, "nested classes: " + List.of(nested));
    }
}
