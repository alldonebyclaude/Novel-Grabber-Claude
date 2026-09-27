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

class PaywallSitesTest {

    // Sites that sell their chapters. BookNet's, jjwxc's, Wattpad's, iQIYI's and BabelNovel's sources skip locked chapters; Dreame's, GoodNovel's,
    // MoboReader's and TapRead's don't work with the current sites and will be rebuilt, for free and bought chapters
    // only, once all sources have tests.
    private static final Set<String> PAYWALL_SITES = Set.of("booknet_com", "dreame_com", "goodnovel_com", "jjwxc_net",
            "moboreader_net", "tapread_com", "wattpad_com", "wenxue_iqiyi_com", "babelnovel_com",
            "tapas_io");

    @Test
    void exactlyThePaywallSitesAreMarked() throws Exception {
        File dir = new File(Source.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File[] classes = new File(dir, "grabber/sources").listFiles((d, name) -> name.endsWith(".class"));
        assertNotNull(classes);

        Set<String> marked = new TreeSet<>();
        for (File file : classes) {
            String name = file.getName().replace(".class", "");
            if (name.equals("Source") || name.equals("example_com")) continue;
            Source source = (Source) Class.forName("grabber.sources." + name).getConstructor().newInstance();
            if (source.isPaywallSite()) marked.add(name);
        }
        assertEquals(new TreeSet<>(PAYWALL_SITES), marked);
    }

    @Test
    void downloadingFromAPaywallSiteShowsItsNote() throws Exception {
        String messages = sourceMessages("https://www.dreame.com/story/1244749568-the-silver-wolf");
        assertTrue(messages.contains("Dreame is a paywall site"), messages);
        assertFalse(sourceMessages("https://novellunar.com/novel/test").contains("paywall"));
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
