package system;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigChapterFilesTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            // chapterFiles, separateChapters (the old TXT-only option), chapter files on?
            "null,  null,  false",
            "true,  null,  true",
            "false, null,  false",
            // Someone who used "Separate chapters" keeps getting one file per chapter
            "null,  true,  true",
            "false, true,  true",
            "true,  false, true"})
    void theOldSeparateChaptersSettingTurnsOnChapterFiles(String chapterFiles, String separateChapters, boolean expected) {
        Properties saved = new Properties();
        if (chapterFiles != null) saved.setProperty("chapterFiles", chapterFiles);
        if (separateChapters != null) saved.setProperty("separateChapters", separateChapters);

        assertEquals(expected, Config.chapterFilesSetting(saved, false));
    }
}
