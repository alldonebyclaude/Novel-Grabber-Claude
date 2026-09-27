package grabber;

import org.htmlunit.BrowserVersion;
import org.htmlunit.SilentCssErrorHandler;
import org.htmlunit.WebClient;
import org.htmlunit.javascript.SilentJavaScriptErrorListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import system.Config;

import java.time.Duration;
import java.util.Collections;

/**
 * Selenium driver handler
 */
public class Driver {
    public static String[] browserList = {"Chrome", "Firefox", "Edge", "IE", "Headless"};
    public WebDriver driver;
    public WebDriverWait wait;

    public Driver(String window) {
        this(window, null);
    }

    /**
     * @param browser the browser chosen for this download (-headless), or null to use the one from the settings
     */
    public Driver(String window, String browser) {
        driverSetup(window, chooseBrowser(browser, Config.getInstance().getBrowser()));
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    /**
     * The browser chosen for the download wins over the settings; with neither, HtmlUnit ("Headless"), which needs
     * no installed browser.
     */
    static String chooseBrowser(String novelBrowser, String settingsBrowser) {
        if (novelBrowser != null && !novelBrowser.isBlank()) return novelBrowser;
        if (settingsBrowser != null && !settingsBrowser.isBlank()) return settingsBrowser;
        return "Headless";
    }

    /**
     * Selenium driver creation for selected browser.
     */
    private void driverSetup(String window, String browser) {
        GrabberUtils.info(window, "Starting browser...");
        switch (browser) {
            case "Chrome":
                WebDriverManager.chromedriver().setup();
                ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
                chromeOptions.setExperimentalOption("useAutomationExtension", false);
                driver = new ChromeDriver(chromeOptions);
                break;
            case "Firefox":
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36 OPR/60.0.3255.170";
                firefoxOptions.addPreference("general.useragent.override", userAgent);
                firefoxOptions.addPreference("permissions.default.image", 2);
                driver = new FirefoxDriver();
                break;
            case "Edge":
                WebDriverManager.edgedriver().setup();
                driver = new EdgeDriver();
                break;
            case "IE":
                WebDriverManager.iedriver().setup();
                driver = new InternetExplorerDriver();
                break;
            case "Headless":
                // Run JavaScript like a browser, and like a browser keep going when one of the page's scripts
                // (often an ad) fails, instead of aborting the whole page
                driver = new HtmlUnitDriver(BrowserVersion.BEST_SUPPORTED, true) {
                    @Override
                    protected WebClient modifyWebClient(WebClient client) {
                        client.getOptions().setThrowExceptionOnScriptError(false);
                        // Broken ad scripts are not the user's problem; don't log every one of them as an error
                        client.setJavaScriptErrorListener(new SilentJavaScriptErrorListener());
                        // Likewise the site's markup mistakes (obsolete content types, odd iframes, ...)
                        client.setIncorrectnessListener((message, origin) -> { });
                        client.setCssErrorHandler(new SilentCssErrorHandler());
                        return client;
                    }
                };
                break;
        }
    }

    public void close() {
        driver.quit();
    }
}
