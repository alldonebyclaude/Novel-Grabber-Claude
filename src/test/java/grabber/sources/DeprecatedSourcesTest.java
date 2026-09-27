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

class DeprecatedSourcesTest {

    // Sites found gone on 2026-09-24 (no DNS, parked, auctioned, or taken over), moved to a new domain, or that
    // can't be supported any more.
    private static final Set<String> GONE = Set.of(
            "a_t_nu", "daonovel_com", "n78zaotl_com", "sofanovel_com",
            "booklat_com_ph", "boxnovel_com", "fastnovel_net", "liberspark_com", "novelcake_com", "novelhd_com",
            "novelsrock_com", "readlightnovel_org", "skynovel_org", "veratales_com", "wnmtl_org", "wuxiaworld_co",
            "zinnovel_com", "machine_translation_org", "novelgate_net", "wuxia_blog", "mtlnovels_com",
            "novelupdates_cc", "boxnovel_net", "blackbox_tl_com",
            "untamedalley_com", "zenithnovels_com", "wuxiaworldsite_co", "comrademao_com",
            "novelbuddy_com", "novelsonline_net", "truyenfull_vn", "webtruyen_com", "volarenovels_com", "ficfun_com",
            "moonquill_com", "ptwxz_com", "novelfun_net", "readwn_com",
            "jpmtl_com", "novelpoki_com", "wuxiaworld_online",
            // Scrambles its text against copying; the source's decoder works around that
            "chrysanthemumgarden_com",
            // Found gone on recheck, 2026-09-27
            "creativenovels_com", "n101novel_com",
            // Time out (checked 2026-09-24); the mtlnovel language sites are replaced by mtl_novel_com
            "biquge_se", "n9kqw_com", "shu111_com", "es_mtlnovel_com", "fr_mtlnovel_com", "id_mtlnovel_com");

    @Test
    void exactlyTheGoneSitesAreDeprecated() throws Exception {
        // Instantiate every source the way GrabberUtils.getSources() does.
        File dir = new File(Source.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File[] classes = new File(dir, "grabber/sources").listFiles((d, name) -> name.endsWith(".class"));
        assertNotNull(classes);

        Set<String> deprecated = new TreeSet<>();
        for (File file : classes) {
            String name = file.getName().replace(".class", "");
            if (name.equals("Source") || name.equals("example_com")) continue;
            Source source = (Source) Class.forName("grabber.sources." + name).getConstructor().newInstance();
            if (source.isDeprecated()) deprecated.add(name);
        }
        assertEquals(new TreeSet<>(GONE), deprecated);
    }

    @Test
    void downloadingFromADeprecatedSourceWarns() throws Exception {
        assertTrue(sourceMessages("https://boxnovel.com/novel/test/").contains("BoxNovel.com is deprecated"));
        assertFalse(sourceMessages("https://www.royalroad.com/fiction/1/test").contains("deprecated"));
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
