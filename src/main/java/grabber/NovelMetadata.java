package grabber;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NovelMetadata {
    private String title = "Unknown";
    private String author = "Unknown";
    private String description = "";
    private String coverFormat = "png";
    private String coverName = "cover";
    private List<String> subjects = new ArrayList<>();
    private BufferedImage bufferedCover;

    public NovelMetadata() {
        try {
            bufferedCover = ImageIO.read(this.getClass().getResource("/images/cover_placeholder.png"));
        } catch (IOException e) {
            GrabberUtils.err(e.getMessage(), e);
        }
    }
    /**
     * Writes BufferedImage cover to file.
     */
    public void saveCover(String destDir) {
        // Save cover
        GrabberUtils.createDir(destDir);
        File outputfile = new File(destDir + coverName + "." + coverFormat);
        try {
            // cover name + file extension
            ImageIO.write(getBufferedCover(), getCoverFormat(), outputfile);
        } catch (IOException e) {
            GrabberUtils.err("Could not save cover.", e);
        }
    }

    public String getTitle() {
        return title;
    }
    public String getAuthor() {
        return author;
    }
    public String getDescription() {
        return description;
    }
    public String getCoverFormat() {
        return coverFormat;
    }
    public String getCoverName() {
        return coverName;
    }
    public List<String> getSubjects() {
        return subjects;
    }
    public BufferedImage getBufferedCover() {
        return bufferedCover;
    }

    public void setTitle(String title) {
        this.title = title.isEmpty() ? "Unknown": title;
    }
    public void setAuthor(String author) {
        this.author = author.isEmpty() ? "Unknown": author;
    }
    public void setDescription(String description) {
        this.description = description.isEmpty() ? "": description;
    }
    public void setCoverFormat(String coverFormat) {
        this.coverFormat = coverFormat;
    }
    public void setCoverName(String coverName) {
        this.coverName = coverName;
    }
    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }
    public void setBufferedCover(String coverURL) {
        if(coverURL != null && !coverURL.isEmpty()) {
            bufferedCover = GrabberUtils.getImage(coverURL);
            String coverName = GrabberUtils.getFilenameFromUrl(coverURL);
            coverFormat = writableFormat(coverName == null ? null : GrabberUtils.getFileExtension(coverName));
            if(bufferedCover == null) {
                GrabberUtils.err("Could not decode cover image " + coverURL + ", using placeholder cover.");
                try {
                    bufferedCover = ImageIO.read(this.getClass().getResource("/images/cover_placeholder.png"));
                    coverFormat = "png";
                } catch (IOException e) {
                    GrabberUtils.err(e.getMessage(), e);
                }
            } else if (coverFormat.equals("jpg") || coverFormat.equals("jpeg")) {
                bufferedCover = withoutAlpha(bufferedCover);
            }
        }
    }
    /**
     * Returns the format the cover is stored in. The cover is written with {@code ImageIO.write}, which can read
     * more formats than it can write (e.g. WebP), so anything without an ImageIO writer is stored as JPEG.
     * Covers without an extension keep the old PNG default.
     */
    static String writableFormat(String extension) {
        if (extension == null) return "png";
        if (!ImageIO.getImageWritersBySuffix(extension).hasNext()) return "jpg";
        return extension;
    }

    /**
     * The JPEG writer refuses images with an alpha channel, so transparent covers are flattened onto white.
     */
    static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    public void setBufferedCover(BufferedImage coverImage, String coverFormat) {
        this.bufferedCover = coverImage;
        this.coverFormat = coverFormat;
    }
}
