# ![alt text](https://i.imgur.com/LrV2tLe.png) Novel-Grabber
Novel-Grabber is a GUI based web scraper that can download and convert chapters into EPUB from various supported web novel sites or from any other site manually.

> **This fork is not maintained.** It is a one-time update of [Flameish/Novel-Grabber](https://github.com/Flameish/Novel-Grabber),
> which is no longer developed, and there are no plans to keep it up to date. Sites change often, so sources will break
> again over time.
>
> All changes in this update were made by Claude, an AI assistant from Anthropic. Anyone is welcome to continue the work:
> fork it, or open an issue if you would like to help, and the owner will add people who want to keep it going to the
> project.

The sources were checked against the sites as they were in September 2026 and have tests.

## Features
- Over 100 supported [sites](src/main/java/grabber/sources)
- A manual mode for unsupported sites
- Blacklist HTML tags to remove unwanted content
- A library to keep track of new releases for your favorite novels
- Login support via cookies
- CLI
- A cover page at the start of the EPUB
- Chapter files: each chapter can also be saved as its own Markdown file, e.g. to translate a novel chapter by chapter.
  Each file is written as soon as its chapter is downloaded.
- Stopped or failed downloads keep what they have: the chapters downloaded so far are saved as a book named
  "(stopped after N of M chapters)".
- Optional removal of text a site hides from its readers with CSS, such as anti-piracy notices (off by default)
- A built-in browser for sites behind a bot check: no browser needs to be installed.
  A page that doesn't finish loading within 90 seconds is given up on instead of hanging the download.
- Notices when a download starts, if the site sells its chapters, needs a real browser, or is deprecated
  (see [Notes on sites](#notes-on-sites))

## How to use
### Running the program
Unzip `Novel-Grabber-<version>.zip` into a folder you can write to (not `C:\Program Files`: settings are saved next to the jar) and start it:

* Windows: double-click `Novel-Grabber.bat`
* Linux / macOS: `./novel-grabber.sh`

The `README.txt` inside the zip explains installing and updating. To build the zip yourself, see [Building](#building).

### Automatic novel downloading
1. Enter the link of the novel
2. Click on 'Check' to fetch metadata and chapter list
3. Choose from various download options / manually adjust metadata
4. Select chapter range and grab chapters

### Chapter files
Tick "Also save chapters as Markdown files" in the settings, or use `-chapterFiles` on the command line. Next to the book, the chapters are
saved as Markdown files in a `<title> (chapters)` folder: `0001 - <chapter name>.md`, `0002 - ...`. The number is the
chapter's place in the novel, so downloading chapters 50 to 60 later adds `0050` to `0060` to the same folder.

### Removing hidden text
Some sites hide text such as anti-piracy notices inside the chapter with CSS; readers don't see it, but it would show
up in the book. Tick "Remove hidden anti-piracy text" in the settings, or use `-removeHiddenText`, to leave it out. It is off by
default.

### Manual novel downloading

#### Using a table of content page
1. Enter the URL to the novel's table of contents page and retrieve all links on the site.
2. Remove all unwanted links from the selection window.
3. Input the chapter container the site uses. I strongly suggest using the "auto detect" function, it generally works well and enables grabbing from different sites.
Or you can [specify a CSS selector manually](https://github.com/Flameish/Novel-Grabber/issues/62#issuecomment-730305855).
4. You can remove unwanted content from the chapter via the "blacklisted tags" window (flag icon at the top). Don't forget to set title, author, cover etc.

#### Using Chapter-To-Chapter navigation
1. Input the URL of your starting point and ending point chapter. (inclusive)
2. Input the selector for the "Next-Chapter" button. You want to select the `<a>` tag of it. (via css selector)

### CLI
**Use these arguments with the main `Novel-Grabber.jar` from inside the `bin` folder!**

[] = optional parameters
{} = arguments for parameter

| Parameter | Arguments | Description |
| :--- | :---: | :---|
-gui / `none` | | Starts the Graphical User Interface.
-link | {novel_URL} | URL to the novel's table of contents page. Starts download.
[-wait] | {milliseconds} | Time between each chapter grab.
[-headless] | {headless/chrome/firefox/edge/IE} | Load the pages in a browser, which runs the site's JavaScript. `headless` is the built-in browser and needs nothing installed; the others need that browser installed.
[-chapters] | {all}, {5 27}, {12 last}	| Specify which chapters to download.
[-path] | {directory_path} | Output directory for the EPUB.
[-login] | | Log in on website with saved account. -account is not needed if an account for this domain was added previously.
[-noDesc] | | Don't create a description page.
[-getImages] | | Grab images from chapter body as well.
[-displayTitle]| | Write the chapter title at the top of each chapter text.
[-invertOrder] | | Invert the chapter order.
[-chapterFiles] | | Also save each chapter as its own Markdown file (`0001 - <chapter>.md`) in a `<title> (chapters)` folder, e.g. for translating.
[-removeHiddenText] | | Remove text the site hides from its readers with CSS, such as anti-piracy notices. Off by default.
-help | | Shows the help page.

Example:

	java -jar Novel-Grabber.jar -link http://host.com/novel/ -chapters 15 last -getImages

### Telegram bot

To host your own Telegram bot you need to add the line `telegramApiToken=your_token ` to your `config.ini`
or on the GUI via the Telegram Bot settings tab.

By default the bot will read the output for `/info` from a `info.txt` file inside the telegram folder which you can adjust.

To start the bot you need to use the `-telegramBot` parameter:

	java -jar Novel-Grabber.jar -telegramBot

## Notes on sites
When a download starts, the app shows a notice if one of these applies to the site.

**Paywall sites** sell their chapters. Their sources only download what you can read: free chapters, and chapters you
bought when you add your own login for the site in the account settings. Locked chapters are skipped and the app says
how many. Working: BookNet, jjwxc, Wattpad, iQIYI Literature and BabelNovel (jjwxc's download of bought VIP chapters is
untested). Dreame, GoodNovel, MoboReader and TapRead don't work with their redesigned sites yet, and Tapas hasn't been
checked against its redesigned site; they will be rebuilt.

**Sites that need a real browser:** some sites stop the built-in browser at their bot check, or their pages never
finish loading in it: foxaholic, foxteller, Webnovel, Quotev, AlphaPolis, VipNovel, FanFiction.net, LightNovelPub,
LightNovelWorld, NovelFull, NovelHall, NovelUpdates and Scribble Hub. They may work with Chrome chosen as the browser in
the settings (`-headless chrome` on the command line); this is untested.

**Deprecated sites** are gone, have moved or can't be supported any more, and will be removed in a later release.
Where a site moved, use its new source instead:

| Old site | New site |
| :--- | :--- |
| moonquill.com | moonquillnovels.com |
| ptwxz.com | piaotia.com |
| readwn.com | wuxiabox.com |
| novelbuddy.com | novelbuddy.me |
| novelsonline.net | novelsonline.org |
| truyenfull.vn | truyenfull.live |
| webtruyen.com | truyencom.com |
| volarenovels.com | wuxiaworld.com |

Text that a site scrambles with its own font, and chapters behind a password or "share to unlock", are not supported.

## Screenshots
![Automatic Tab](https://i.imgur.com/xBUdkmL.png)

![Manual Tab](https://i.imgur.com/jDm2ABW.png)

![Library Tab](https://i.imgur.com/8OUQe9E.png)

## Disclaimer & Warning
Most sites prohibit the scraping of their content. Use at your own risk.
Please use with appropriate wait times. Downloaded chapters are for private use only.

## Requirements
* Java 25 or newer, e.g. from [Adoptium](https://adoptium.net/). The start scripts check the version.
* No browser is needed: sites behind a bot check are loaded in the built-in browser.
* Optional: Chrome, Firefox or Edge, for the sites listed under [sites that need a real browser](#notes-on-sites).

## Building
Building needs a JDK 25 and [Maven 4](https://maven.apache.org/); the build stops with an error on older versions.

    mvn package                  # runs the tests and builds target/Novel-Grabber-<version>.zip
    mvn -DskipTests package      # without the tests
    mvn test -Dgroups=live       # tests that visit the real sites (slow, only when needed)

The normal tests don't use the network: each source is tested against saved pages in `src/test/resources`, with the
story text replaced by placeholders.

## Credits & Libraries
Novel Grabber was built in Java with:

* [jsoup](https://www.jsoup.org/)
* [json-simple](https://code.google.com/archive/p/json-simple/)
* [epublib](https://github.com/psiegman/epublib)
* [webdrivermanager](https://github.com/bonigarcia/webdrivermanager)
* [selenium ](https://selenium.dev/)
* [HtmlUnit](https://www.htmlunit.org/)
* [Simple Java Mail ](https://github.com/bbottema/simple-java-mail/)
* [Readability4J](https://github.com/dankito/Readability4J)
* [Notify](https://github.com/dorkbox/Notify)
* [icons8](https://icons8.com)
* [FlatLaf](https://www.formdev.com/flatlaf/)
* [Java Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api/)
