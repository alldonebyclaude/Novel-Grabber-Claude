package grabber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            // -headless chrome on the command line wins over the saved setting
            "Chrome,   Firefox,  Chrome",
            // otherwise the browser chosen in the settings
            "null,     Firefox,  Firefox",
            "'',       Edge,     Edge",
            // no browser chosen anywhere: HtmlUnit, which needs no installed browser (before: no driver at all)
            "null,     '',       Headless",
            "null,     null,     Headless",
    })
    void choosesTheBrowser(String novelBrowser, String settingsBrowser, String expected) {
        assertEquals(expected, Driver.chooseBrowser(novelBrowser, settingsBrowser));
    }
}
