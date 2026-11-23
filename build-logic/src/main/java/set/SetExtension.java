package set;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * The {@code set} extension of every project: a sibling's file as the set names it, a third-party file, a floor and
 * the platform. A sibling comes from the Store by its SHA-256, or from a stand-in folder or JAR the person gave;
 * every Store file handed out is re-hashed by setVerify before any compile.
 */
public class SetExtension {
    private final Project project;
    private final SetFile set;
    private final Map<File, String> verified = new LinkedHashMap<>();

    @Inject
    public SetExtension(Project project, SetFile set) {
        this.project = project;
        this.set = set;
    }

    /** The project's main artifact (mod). */
    public FileCollection jar(String name) {
        return jar(name, "mod");
    }

    /** One artifact of a sibling: from its stand-in when there is one, else from the Store at its SHA-256. */
    public FileCollection jar(String name, String artifact) {
        Map<String, Object> item = set.artifact(name, artifact);
        File standIn = set.standIns.get(name);
        if (standIn != null) {
            return project.files(fromStandIn(name, artifact, standIn, String.valueOf(item.get("glob"))));
        }
        return project.files(fromStore(name + " " + artifact, String.valueOf(item.get("sha256")),
                String.valueOf(item.get("file"))));
    }

    /** A third-party entry: module notation for a coordinate (published metadata keeps it), a Store file for a URL. */
    @SuppressWarnings("unchecked")
    public Object third(String name) {
        Object entry = set.table("third").get(name);
        if (!(entry instanceof Map)) {
            throw new GradleException("set " + set.shortRevision() + " has no third entry " + name);
        }
        Map<String, Object> third = (Map<String, Object>) entry;
        if (third.get("module") != null) {
            return String.valueOf(third.get("module"));
        }
        return project.files(fromStore(name, String.valueOf(third.get("sha256")), String.valueOf(third.get("file"))));
    }

    /** The consumer's own floor for a sibling: fails when the set's line is older than the version given. */
    public void since(String name, String version) {
        Object have = set.line(name).get("version");
        if (have != null && compare(String.valueOf(have), version) < 0) {
            throw new GradleException(project.getPath() + " needs " + name + " " + version + " or newer; set "
                    + set.shortRevision() + " names " + have);
        }
    }

    public Map<String, Object> platform() {
        return set.platform();
    }

    public String getRevision() {
        return set.revision;
    }

    Map<File, String> verified() {
        return verified;
    }

    private File fromStore(String what, String sha256, String file) {
        if (set.store == null) {
            throw new GradleException("No Store for " + what + ": set set.store in your Gradle properties "
                    + "(wb setup writes it), or run the build through Workbench");
        }
        File kept = new File(new File(new File(set.store, "files"), sha256), file);
        if (!kept.isFile()) {
            throw new GradleException("The Store has no " + file + " (" + what + ", " + sha256.substring(0, 12)
                    + "): run wb work sync");
        }
        verified.put(kept, sha256);
        return kept;
    }

    private File fromStandIn(String name, String artifact, File standIn, String glob) {
        if (standIn.isFile()) {
            project.getLogger().lifecycle(name + " " + artifact + " from " + standIn + " (stand-in)");
            return standIn;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        Path root = standIn.toPath();
        List<Path> matched = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(root.relativize(p)) || matcher.matches(
                            Path.of(root.relativize(p).toString().replace('\\', '/'))))
                    .forEach(matched::add);
        } catch (java.io.IOException e) {
            throw new GradleException("cannot search " + standIn + ": " + e.getMessage(), e);
        }
        if (matched.isEmpty()) {
            throw new GradleException(name + " " + artifact + ": nothing in the stand-in " + standIn + " matches "
                    + glob + ": run ./gradlew build in " + standIn);
        }
        if (matched.size() > 1) {
            throw new GradleException(name + " " + artifact + ": " + matched.size() + " files in the stand-in match "
                    + glob + ": " + matched.stream().map(Path::toString).collect(Collectors.joining(", ")));
        }
        File file = matched.get(0).toFile();
        long minutes = Math.max(0, (System.currentTimeMillis() - file.lastModified()) / 60000);
        project.getLogger().lifecycle(name + " " + artifact + " from " + file + " (stand-in, built " + minutes
                + " min ago)");
        return file;
    }

    static int compare(String a, String b) {
        String[] x = a.split("[.-]");
        String[] y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0";
            String q = i < y.length ? y[i] : "0";
            int c = p.matches("\\d+") && q.matches("\\d+") ? Long.compare(Long.parseLong(p), Long.parseLong(q))
                    : p.compareTo(q);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }
}
