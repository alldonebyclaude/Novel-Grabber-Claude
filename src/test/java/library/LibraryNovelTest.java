package library;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibraryNovelTest {

    // An entry as written by getAsJSONObject(), minus the optional keys.
    private static final String REQUIRED_KEYS = """
            "novelUrl": "https://example.com/novel/test",
            "saveLocation": "/tmp/novels",
            "title": "Test Novel",
            "coverFormat": "png",
            "lastChapterName": "Chapter 10",
            "newestChapterName": "Chapter 12",
            "autoDownloadEnabled": false,
            "sendEmailNotification": false,
            "sendDesktopNotification": false,
            "sendAttachmentEnabled": false,
            "updateLast": false,
            "lastChapter": 10,
            "newestChapter": 12,
            "threshold": 5""";

    // Parse from text like Library does, so numbers come back as Long.
    private static JSONObject parse(String json) throws Exception {
        return (JSONObject) new JSONParser().parse(json);
    }

    @Test
    void waitTimeIsReadFromItsOwnKey() throws Exception {
        LibraryNovel novel = new LibraryNovel(parse("{" + REQUIRED_KEYS + ", \"waitTime\": 1500}"));

        assertEquals(1500, novel.getWaitTime());
        assertEquals(5, novel.getThreshold());
    }

    @Test
    void missingWaitTimeDefaultsToZero() throws Exception {
        LibraryNovel novel = new LibraryNovel(parse("{" + REQUIRED_KEYS + "}"));

        assertEquals(0, novel.getWaitTime());
    }

    @Test
    void waitTimeSurvivesSaveAndLoad() throws Exception {
        LibraryNovel novel = new LibraryNovel(parse("{" + REQUIRED_KEYS + "}"));
        novel.setWaitTime(2500);

        LibraryNovel reloaded = new LibraryNovel(parse(novel.getAsJSONObject().toJSONString()));

        assertEquals(2500, reloaded.getWaitTime());
        assertEquals(5, reloaded.getThreshold());
    }
}
