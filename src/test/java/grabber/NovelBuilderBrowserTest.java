package grabber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NovelBuilderBrowserTest {

    @ParameterizedTest
    @CsvSource({
            "headless, Headless",
            "chrome,   Chrome",
            "Chrome,   Chrome",
            "firefox,  Firefox",
            "edge,     Edge",
            "ie,       IE"})
    void theHeadlessOptionNamesABrowser(String argument, String browser) {
        assertEquals(browser, NovelBuilder.browserFromCli(argument));
    }

    @Test
    void operaIsRejectedWithAClearMessage() {
        // It used to be accepted, but the app can't start Opera, so the download failed without a browser.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> NovelBuilder.browserFromCli("opera"));
        assertTrue(error.getMessage().contains("Opera is not supported"), error.getMessage());
        assertTrue(error.getMessage().contains("headless, chrome, firefox, edge or ie"), error.getMessage());
    }

    @Test
    void anUnknownBrowserIsRejectedInsteadOfIgnored() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> NovelBuilder.browserFromCli("safari"));
        assertTrue(error.getMessage().contains("safari"), error.getMessage());
    }
}
