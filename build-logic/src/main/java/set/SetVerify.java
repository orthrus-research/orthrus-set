package set;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Re-hashes every file the project took from the Store against the SHA-256 set.json gives it. */
@DisableCachingByDefault(because = "it reads files to prove their bytes; nothing to cache")
public abstract class SetVerify extends DefaultTask {
    @Internal
    public abstract MapProperty<File, String> getFiles();

    @TaskAction
    public void verify() {
        for (Map.Entry<File, String> entry : getFiles().get().entrySet()) {
            String actual = sha256(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                throw new GradleException(entry.getKey() + " is " + actual + ", not the " + entry.getValue()
                        + " the set names: the Store was changed outside Workbench; run wb work sync, and if it "
                        + "stays, wb run store.verify");
            }
        }
    }

    static String sha256(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new GradleException("cannot hash " + file + ": " + e.getMessage(), e);
        }
    }
}
