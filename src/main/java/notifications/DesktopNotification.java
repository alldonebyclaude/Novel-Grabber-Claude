package notifications;

import dorkbox.notify.Notify;
import dorkbox.notify.Theme;
import grabber.GrabberUtils;
import grabber.Novel;
import kotlin.Unit;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;

public class DesktopNotification {

    public static void sendChapterReleaseNotification(Novel novel) {
        try {
            URI uri = new URI(novel.chapterList.get(novel.chapterList.size()-1).chapterURL);
            Notify.Companion.create()
                    .title(novel.metadata.getTitle())
                    .text(novel.chapterList.get(novel.chapterList.size()-1).name)
                    .theme(Theme.Companion.getDefaultDark())
                    .image(novel.metadata.getBufferedCover())
                    .hideAfter(5000)
                    .hideCloseButton()
                    .onClickAction(arg0 -> {
                        GrabberUtils.openWebpage(uri);
                        return Unit.INSTANCE;
                    })
                    .show();
        } catch (URISyntaxException e) {
            GrabberUtils.err(e.getMessage(), e);
        }
    }
    public static void sendDownloadFinishedNotification(Novel novel) {
        Image image = novel.metadata.getBufferedCover();
        Notify.Companion.create()
                .title(novel.metadata.getTitle())
                .text("Download finished!")
                .theme(Theme.Companion.getDefaultDark())
                .image(image)
                .hideAfter(5000)
                .hideCloseButton()
                .show();
    }
}
