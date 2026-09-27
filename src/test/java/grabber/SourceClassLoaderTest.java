package grabber;

import grabber.sources.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceClassLoaderTest {

    @Test
    void theSourcesFolderGetsOneClassLoaderForAllLookups() throws Exception {
        // Before, every source lookup created a new class loader that was never closed.
        assertSame(GrabberUtils.sourceClassLoader(), GrabberUtils.sourceClassLoader());
    }

    @Test
    void theSharedClassLoaderFindsTheSources() throws Exception {
        Class<?> source = GrabberUtils.sourceClassLoader().loadClass("grabber.sources.novellunar_com");

        assertTrue(Source.class.isAssignableFrom(source));
    }
}
