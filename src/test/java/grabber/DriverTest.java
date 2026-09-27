package grabber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.TimeoutException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void aPageThatNeverFinishesLoadingFailsWithinTheLimitAndTheBrowserStillWorks() throws Exception {
        // A local server that answers "/ok" and leaves every other request hanging, like a site stuck on a bot check.
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            List<Socket> hanging = new ArrayList<>();
            Thread serverThread = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        byte[] request = new byte[2048];
                        int read = socket.getInputStream().read(request);
                        String firstLine = new String(request, 0, Math.max(read, 0), StandardCharsets.ISO_8859_1);
                        if (firstLine.startsWith("GET /ok")) {
                            try (OutputStream out = socket.getOutputStream()) {
                                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n"
                                        + "<html><head><title>fine</title></head><body>ok</body></html>").getBytes(StandardCharsets.ISO_8859_1));
                            }
                        } else {
                            hanging.add(socket);
                        }
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            String base = "http://127.0.0.1:" + server.getLocalPort();

            Driver browser = new Driver("checker", "Headless");
            try {
                long start = System.nanoTime();
                TimeoutException failure = assertThrows(TimeoutException.class,
                        () -> browser.navigate(base + "/hangs", Duration.ofSeconds(2)));
                assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 20, "gave up in time");
                assertTrue(failure.getMessage().contains(base + "/hangs"), failure.getMessage());

                browser.navigate(base + "/ok", Duration.ofSeconds(20));
                assertEquals("fine", browser.driver.getTitle(), "a fresh browser takes over after the stuck one");
            } finally {
                browser.close();
                for (Socket socket : hanging) socket.close();
            }
        }
    }

    @Test
    void aBrowserTheAppCantStartGivesAClearError() {
        // e.g. "Opera" saved in the settings by an older version
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new Driver("checker", "Opera"));
        assertTrue(error.getMessage().contains("Opera"), error.getMessage());
        assertTrue(error.getMessage().contains("settings"), error.getMessage());
    }

    @Test
    void aMissingPageFromTheBrowserParsesAsAnEmptyPage() {
        // Selenium may answer null for the page source, the current URL or an element's innerHTML
        assertEquals("", Driver.parse(null, null).body().html());
        assertEquals("https://example.com/a", Driver.parse("<a href='/a'>x</a>", "https://example.com/b").expectFirst("a").absUrl("href"));
        assertEquals("", Driver.parse("<a href='/a'>x</a>", null).expectFirst("a").absUrl("href"));
    }
}
