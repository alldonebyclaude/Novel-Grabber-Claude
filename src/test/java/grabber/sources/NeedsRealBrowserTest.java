package grabber.sources;

import grabber.Novel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class NeedsRealBrowserTest {

    // Checked 2026-09-24 with the app's built-in browser: the first ones stop at the site's bot check, the last four
    // load the home page but never finish loading novel or chapter pages. Kept for people who choose Chrome.
    private static final Set<String> NEED_A_REAL_BROWSER = Set.of(
            "foxaholic_com", "foxteller_com", "webnovel_com", "quotev_com", "alphapolis_co_jp", "vipnovel_com",
            "fanfiction_net", "lightnovelpub_com", "lightnovelworld_com",
            "novelfull_com", "novelhall_com", "novelupdates_com", "scribblehub_com");

    @Test
    void exactlyTheSitesTheBuiltInBrowserCantLoadAreMarked() throws Exception {
        File dir = new File(Source.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File[] classes = new File(dir, "grabber/sources").listFiles((d, name) -> name.endsWith(".class"));
        assertNotNull(classes);

        Set<String> marked = new TreeSet<>();
        for (File file : classes) {
            String name = file.getName().replace(".class", "");
            if (name.equals("Source") || name.equals("example_com")) continue;
            Source source = (Source) Class.forName("grabber.sources." + name).getConstructor().newInstance();
            if (source.needsRealBrowser()) marked.add(name);
        }
        assertEquals(new TreeSet<>(NEED_A_REAL_BROWSER), marked);
    }

    @Test
    void downloadingFromSuchASiteSaysSoAndMentionsChrome() throws Exception {
        String messages = sourceMessages("https://www.webnovel.com/book/12345");
        assertTrue(messages.contains("bot check blocks the built-in browser"), messages);
        assertTrue(messages.contains("Chrome"), messages);
        assertFalse(sourceMessages("https://novellunar.com/novel/test").contains("built-in browser"));
    }

    private static String sourceMessages(String link) throws Exception {
        PrintStream stdout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Novel.builder().novelLink(link).setSource(link);
        } finally {
            System.setOut(stdout);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
