package grabber;

import org.htmlunit.BrowserVersion;
import org.htmlunit.SilentCssErrorHandler;
import org.htmlunit.WebClient;
import org.htmlunit.javascript.SilentJavaScriptErrorListener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Selenium driver handler
 */
public class Driver {
    public static String[] browserList = {"Chrome", "Firefox", "Edge", "IE", "Headless"};
    /** How long a page may take to load before {@link #navigate(String)} gives up on it. */
    static final Duration PAGE_LOAD_LIMIT = Duration.ofSeconds(90);
    public WebDriver driver;
    public WebDriverWait wait;
    private final String window;
    private final String browser;

    public Driver(String window) {
        this(window, null);
    }

    /**
     * @param browser the browser chosen for this download (-headless), or null to use the one from the settings
     */
    public Driver(String window, String browser) {
        this.window = window;
        this.browser = chooseBrowser(browser, Config.getInstance().getBrowser());
        start();
    }

    private void start() {
        driverSetup(window, browser);
        driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_LIMIT);
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    /**
     * Opens a page, giving up after {@link #PAGE_LOAD_LIMIT}. Some sites keep the built-in browser busy forever
     * (a bot check it can't pass, a script that never ends); then the stuck browser is dropped, a fresh one takes
     * its place for the next page, and a {@link TimeoutException} says which page didn't load.
     */
    public void navigate(String pageUrl) {
        navigate(pageUrl, PAGE_LOAD_LIMIT);
    }

    void navigate(String pageUrl, Duration limit) {
        ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "page-load");
            thread.setDaemon(true);
            return thread;
        });
        WebDriver current = driver;
        Future<?> loading = loader.submit(() -> current.navigate().to(pageUrl));
        try {
            loading.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            loading.cancel(true);
            // Closing the stuck browser can block as well, so it's done on the side
            Thread closer = new Thread(() -> {
                try {
                    current.quit();
                } catch (RuntimeException ignored) {
                    // It's being dropped anyway
                }
            }, "close-stuck-browser");
            closer.setDaemon(true);
            closer.start();
            start();
            throw new TimeoutException("The page didn't finish loading within " + limit.toSeconds() + " seconds: "
                    + pageUrl + ". The site may be blocking the app's browser; choosing Chrome in the settings may help.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            loading.cancel(true);
            Thread.currentThread().interrupt();
            throw new TimeoutException("Stopped while loading " + pageUrl);
        } finally {
            loader.shutdownNow();
        }
    }

    /** The page the browser shows, parsed with jsoup; links resolve against the page's own address. */
    public Document pageDocument() {
        return pageDocument(driver.getCurrentUrl());
    }

    /** The page the browser shows, parsed with jsoup; links resolve against {@code baseUrl}. */
    public Document pageDocument(String baseUrl) {
        return parse(driver.getPageSource(), baseUrl);
    }

    /** The HTML inside a browser element, parsed with jsoup. */
    public static Document innerHtml(WebElement element, String baseUrl) {
        return parse(element.getAttribute("innerHTML"), baseUrl);
    }

    /** Selenium may answer null (no page, no such property); that parses as an empty page. */
    static Document parse(String html, String baseUrl) {
        return Jsoup.parse(html == null ? "" : html, baseUrl == null ? "" : baseUrl);
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
            default:
                throw new IllegalArgumentException("The browser \"" + browser + "\" can't be used. Choose Headless, "
                        + "Chrome, Firefox, Edge or IE in the settings (or with -headless).");
        }
    }

    public void close() {
        driver.quit();
    }
}
