# Novel-Grabber-Claude: notes for Claude

A Java 25 desktop and command-line tool that downloads web novels and exports them as EPUB, PDF or other formats.
It's a fork of Flameish/Novel-Grabber, which is no longer maintained. The work here was a one-time update made by Claude; the fork isn't maintained either (see the README). Anyone may continue it, and these notes are for whoever does.

## Environment
- Runs in WSL (Ubuntu). The repo lives at `~/src/Novel-Grabber`.
- Build tool: Maven 4 (`pom.xml`). It's installed at `/opt/apache-maven-4.0.0-rc-6` and is on `PATH`, so plain `mvn` works. The `.mvn/` directory marks the project root for Maven 4. The code targets Java 25 (`maven.compiler.release=25`), and the enforcer plugin fails the build on older JDKs or Maven versions before 4. Earlier Java versions are not supported, so Java 25 language features and APIs are fine.
- The installed JDK is OpenJDK 25 (Ubuntu 22.04). Don't change `pom.xml` to work around JDK issues without asking.
- okhttp 5 is declared as `okhttp-jvm` (the plain `okhttp` artifact is an empty Gradle stub), and the Kotlin BOM keeps the Kotlin stdlib versions pulled by okhttp, telegram-bot-api and Notify aligned.
- `curl` in WSL doesn't know Let's Encrypt's newer root certificates, so some sites fail with a certificate error in `curl` although they are fine. Check with `curl -k` before calling a site broken.
- There is no browser inside WSL. The app's built-in headless browser (HtmlUnit) needs none; Chrome only exists on the Windows side.

## Commands
- Build: `mvn -q -DskipTests package`
- Installable zip: `mvn package` builds `target/Novel-Grabber-Claude-<version>.zip` (jar, `sources/`, start scripts, README.txt)
- Offline unit tests: `mvn test` (must pass before any commit)
- Live smoke tests (hit the real site, run only on request): `mvn test -Dgroups=live`
- Single test class: `mvn test -Dtest=NovellunarComTest`

## Versioning
The version is `MAJOR.MINOR.PATCH`, in two places that must match: `<version>` in `pom.xml` and `versionNumber` in `src/main/java/system/init.java`. The zip name follows `pom.xml`.
- Bug fixes (a broken source repaired, a crash fixed, warnings cleaned up): raise the third digit, e.g. 3.11.0 → 3.11.1.
- New sources or features, and removing deprecated sources: raise the second digit and reset the third, e.g. 3.11.1 → 3.12.0.
- Changes that break existing installations (a newer Java requirement, an incompatible settings or library file format, removing a feature): raise the first digit and reset the others, e.g. 3.12.0 → 4.0.0.
- One version per release, not per commit. Ask before bumping if it's unclear which kind of change it is.

## How site sources work
- Each supported site is one class in `src/main/java/grabber/sources/` that implements `grabber.sources.Source`.
- The class name is the domain with dots replaced by underscores, e.g. `novellunar.com` becomes `novellunar_com`; a leading `www.` is ignored. `NovelBuilder` and `GrabberUtils.getSource(domain)` load it by that name through one shared class loader. A site that moves to a new domain needs a new class.
- At runtime, sources are loaded from `sources/grabber/sources/*.class` next to the jar (the zip puts them there). The app instantiates every `.class` file in that folder, so a source must compile to a single class file: no records, inner or anonymous classes (lambdas are fine).
- Follow `novellunar_com.java` (or `royalroad_com.java` for a small one): two constructors (no-arg and `Novel`), a `fetch()` helper using `novel.cookies` (null-safe) and a browser User-Agent, errors reported through `GrabberUtils.err(novel.window, ...)`.
- `getBlacklistedTags()` returns CSS selectors that are removed from chapter content. Put ads and credit lines there.
- Sites behind a bot check (Cloudflare "Just a moment...") need the app's browser: set `canHeadless = true`, load pages with `novel.headlessDriver` (create it with `new Driver(novel.window, novel.browser)`) and parse `getPageSource()` with jsoup. See `freewebnovel_com.java`.
- Options that apply to all sources belong in the app, not in a source: e.g. `HiddenText` (removes text the page hides with CSS, opt-in) and `ChapterFiles` (one Markdown file per chapter).

## Rules for new or changed sources
1. Keep networking and parsing separate. Put the parsing in package-private methods that take a jsoup `Document` (or `Element`, or the JSON string) and return plain data, such as `parseChapterList(Document)`, `parseChapterBody(Document)` and `parseMetadata(Document)`. The `Source` methods only fetch pages and delegate to these.
2. Write the tests first, against saved fixtures in `src/test/resources/<domain>/`, loaded with `SourceFixtures.page(domain, file, "<original url>")` so `abs:href` resolves. Each test class also checks `assertSingleClassFile`.
3. Fixtures are committed to a public repository, so they must not contain the story or personal data:
   - replace the chapter text with numbered "Line N." placeholders, keeping the markup;
   - remove comments, reviews, user names, author bios and avatars;
   - remove other copies of the text: meta descriptions, JSON-LD/SEO scripts, tooltips, embed titles;
   - trim huge lists (keep a few first and last entries);
   - check that no sentence of the original chapter is left anywhere in the file, and check every fixture of the site against the chapter, not only the chapter page: novel pages can embed the first chapter or its opening, and text can hide in attributes (`data-summary`, `title`, `alt`).
   Don't print chapter text or long descriptions while investigating a site; print structure, counts and titles.
   See "Gotchas" below for traps in the fixture checks.
4. Default tests must never touch the network. Put any live test under `@Tag("live")`.
5. Be polite to sites: no parallel requests, at least about 1 second between requests (also between chapter list pages inside a source), and a normal browser User-Agent.
6. Respect the site's protections. Don't work around scrambled or font-obfuscated text, paywalls, locked, "share to unlock" or password-protected chapters: skip what the reader can't open and tell the user, and use only the user's own login cookies. Loading pages in the app's browser is fine; disguising automation or solving challenges is not. Hidden anti-piracy text stays unless the user chooses to remove it.
7. Don't guess selectors, URLs or chapter numbers: check the page. A number in a chapter URL can be an index rather than the chapter number, so take titles from the site. A list with gaps, duplicates or out of order is the site's; report it rather than "fix" it (e.g. `novellunar_com.checkChapterList` warns about gaps and volume jumps).
8. Don't reformat unrelated files. Keep the diffs to the source being worked on, `pom.xml` and tests. Use LF line endings in code, forms and resources; they were normalized to LF on 2026-09-27 (`GrabberUtils.java`, `EPUB.java`, `editBlacklistedTags`, `editMetadata`, `about.txt` and `default.css` used to be CRLF). Exception: test fixtures in `src/test/resources/` keep the line endings the site served. Many sites send mixed CRLF/LF pages (checked 2026-09-27), so don't normalize fixtures.
9. GUI changes go into both `gui/GUI.java` (the generated `$$$setupUI$$$` code is checked in) and `gui/GUI.form`.
10. jsoup's `Elements.remove(...)`, `removeIf(...)` and `removeAll(...)` also remove the elements from the page. To filter a selection, copy it into a plain `List` first.
11. Many sites render from data embedded in the page rather than from HTML. Read that data instead of guessing at a partial HTML list: React Server Components (`self.__next_f.push(...)`, see `novellunar_com`, `woopread_com`), `__NEXT_DATA__` (`babelnovel_com`), `__APOLLO_STATE__` (`kakuyomu_jp`), `window.__remixContext` (`wattpad_com`) and `window.__DATA__` (`ranobes_net`). Fixtures of such pages keep only a trimmed copy of the data the source reads.

## Deprecating a source
- For sites that are gone, moved, or can't be supported any more: add `@Deprecated(forRemoval = true)` and a Javadoc `@deprecated` note with the reason and the date checked, and add the class to `DeprecatedSourcesTest`. The app warns when a deprecated source is used.
- Don't rename the source: saved logins are keyed by its name.
- Remove deprecated sources in a later release (second digit), not in the one that deprecates them.

## Sites the built-in browser can't load
- `@NeedsRealBrowser("<note>")` (annotation in `grabber`) marks sources whose site stops the built-in browser at its bot check or never finishes loading in it. The app shows the note and suggests Chrome; `NeedsRealBrowserTest` pins the list. Keep these sources for Chrome users; don't work around the bot check.
- `Driver.navigate(url)` gives up on a page after 90 seconds and starts a fresh browser; sources that use the browser must load pages through it, not `driver.navigate().to(...)`.

## Paywall sites
- Sites that sell their chapters are marked `@PaywallSite("<note for the user>")` (annotation in `grabber`, not in `grabber.sources`: the app loads every class in the sources folder as a site). The app shows the note when a download uses the source, and `PaywallSitesTest` pins which sources are marked.
- Their sources may only download what the user can open: free chapters, or bought ones with the user's own login. Skip locked chapters and tell the user; never try to unlock them.
- Currently marked: `booknet_com`, `jjwxc_net`, `wattpad_com`, `wenxue_iqiyi_com` and `babelnovel_com` (work, skip locked, VIP or paywalled chapters; BookNet and jjwxc download bought chapters with the user's own login; jjwxc's VIP support is untested), `dreame_com`, `goodnovel_com`, `moboreader_net`, `tapread_com` and `tapas_io`. The last five don't work with the rebuilt sites; rebuilding and testing bought-chapter download needs a paid account on each site, which wasn't available here, so they're left as they are until an owner with credentials can do it.

## Gotchas
- jsoup's `select(...)` includes the element itself: on a chapter body that is a `div`, `body.select("div")` is never empty. Check `children()` or use `div div`.
- Pages saved from HtmlUnit can wrap scripts in `//<![CDATA[ ... //]]>`; strip it before parsing the JSON.
- Pages served compressed come back as binary from plain `curl`; use `curl --compressed`.
- HtmlUnit must tolerate the site's script errors (`Driver` does); a probe without that fails on ad scripts, not on the site.
- A site can answer plain requests on its home page but challenge chapter pages, or the other way round: check a novel page and a chapter before deciding how to load it.
- Some novel pages show a whole chapter or its opening (inkitt), and some list pages repeat the synopsis in a second place or an attribute: check every fixture against the chapter and the synopsis.

## Remaining tasks (as of 2026-09-27)
Release (needs the owner):
- Version 4.0.0 is on master (pushed 2026-09-27, tag `v4.0.0`). It is a major version because it requires Java 25. Its 16 commits regroup the fork's history; the old history is kept in the local tag `backup/before-regroup`, which isn't pushed.
- Create the GitHub release for `v4.0.0` and attach `Novel-Grabber-Claude-4.0.0.zip` (built with `mvn package`); the GitHub CLI wasn't installed here.

Testing that couldn't be done here:
- Live tests (`mvn test -Dgroups=live`) ran on 2026-09-27: 37 of 38 pass. `novelsonline_org` timed out (its server answered with a Cloudflare 522 earlier that day); recheck it, and deprecate it if it stays down.
- Check the new GUI settings on Windows (chapter files and hidden-text checkboxes; "Separate chapters" is gone). The zip and start scripts were checked by the owner on Windows 11 and in WSL (2026-09-24).
- Try the `@NeedsRealBrowser` sites with Chrome as the browser.
- jjwxc's download of bought VIP chapters is untested (no login); its `jjwxcfont` detection is an assumption.

Sources:
- Rebuild the paywall sites that don't work (Dreame, GoodNovel, MoboReader, TapRead) and check Tapas, which was redesigned; free and bought chapters only. Not done here: testing bought-chapter download needs a paid account on each site, which wasn't available.
- Not attempted: `wuxiaworld_com` (the page is built entirely by JavaScript; sells chapters) and `n17k_com` (Alibaba Cloud JavaScript challenge on book pages; don't solve it).
- Assumed lock markers, only tested against made-up snippets: lightnovelstranslations' `.lock`, wordrain69's coin label.

Older open items:
- Password-protected chapters on chickengege: locked chapters are now detected (WordPress's `post-password-required` marker, confirmed live 2026-09-27) and skipped with a message pointing users at the existing "Edit Cookies" button to add their own `wp-postpass_` cookie. End-to-end unlocking with a real password is still untested (none available here).
- GUI form-bound fields that the IDE reports as unused.

Cleanup:
- Remove the deprecated sources (about 50) in the next minor release, 4.1.0.

## Workflow
- One branch per change, based on the latest branch, with small, working commits and clear messages. Don't push without asking; the remote is `origin` (`git@github.com:alldonebyclaude/Novel-Grabber-Claude.git`, https://github.com/alldonebyclaude/Novel-Grabber-Claude).
- When unsure about a selector, check the fixture HTML. Don't guess.
