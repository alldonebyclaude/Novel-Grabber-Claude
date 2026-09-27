package grabber.sources;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Only proves the test setup works: every saved fixture can be loaded and parsed.
 */
class FixtureSmokeTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "novellunar/novel.html,            https://novellunar.com/novel/swallowed-star-v1",
            "novellunar/chapter-1.html,        https://novellunar.com/novel/swallowed-star-v1/chapter/1",
            "novellunar/chapter-2.html,        https://novellunar.com/novel/swallowed-star-v1/chapter/2",
            "novellunar/chapter-last.html,     https://novellunar.com/novel/swallowed-star-v1/chapter/1486",
            "novellunar/chapter-missing.html,  https://novellunar.com/novel/swallowed-star-v1/chapter/1487",
            "novellunar/chapter-gap.html,      https://novellunar.com/novel/swallowed-star-v1/chapter/100",
    })
    void fixtureParsesAndHasH1(String resource, String baseUrl) throws IOException, URISyntaxException {
        URL url = getClass().getClassLoader().getResource(resource);
        assertNotNull(url, "missing fixture " + resource);

        Document doc = Jsoup.parse(new File(url.toURI()), "UTF-8", baseUrl);

        assertFalse(doc.select("h1").isEmpty(), resource + " has no <h1>");
    }
}
