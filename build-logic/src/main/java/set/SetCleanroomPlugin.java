package set;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * set.cleanroom: applied after CleanroomGradle in a Cleanroom mod's build. It takes the Cleanroom version from the
 * set's platform (as the project property cleanroom_version, which CleanroomGradle builds read), makes every archive
 * reproducible, and sorts CleanMix's compatibility table after each compile. CleanMix writes that table from a hash
 * map filled in the order javac meets the Mixins, which follows the file system's listing, so the same commit would
 * otherwise pack different bytes on Windows and Linux.
 */
public class SetCleanroomPlugin implements Plugin<Project> {
    static final String COMPATIBILITY = "cleanmix_version_compatibility.json";

    @Override
    @SuppressWarnings("unchecked")
    public void apply(Project project) {
        SetExtension set = project.getExtensions().findByType(SetExtension.class);
        if (set == null) {
            throw new GradleException("set.cleanroom needs set.base in settings.gradle");
        }
        Object cleanroom = set.platform().get("cleanroom");
        if (cleanroom instanceof Map) {
            Object version = ((Map<String, Object>) cleanroom).get("version");
            project.getExtensions().getExtraProperties().set("cleanroom_version", String.valueOf(version));
        }
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(archive -> {
            archive.setPreserveFileTimestamps(false);
            archive.setReproducibleFileOrder(true);
        });
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> compile.doLast(new SortTable()));
    }

    /** Rewrites the compatibility table in a compile's output with its keys in order, if the compile wrote one. */
    static final class SortTable implements org.gradle.api.Action<Task> {
        @Override
        public void execute(Task task) {
            File table = new File(((JavaCompile) task).getDestinationDirectory().get().getAsFile(), COMPATIBILITY);
            if (table.isFile()) {
                sort(table);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void sort(File table) {
        try {
            Object parsed = Json.parse(new String(Files.readAllBytes(table.toPath()), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map)) {
                return;
            }
            StringBuilder out = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) parsed).entrySet()) {
                out.append(first ? "" : ",\n").append("  ").append(quote(e.getKey())).append(": ")
                        .append(quote(String.valueOf(e.getValue())));
                first = false;
            }
            Files.write(table.toPath(), out.append("\n}").toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GradleException("cannot sort " + table + ": " + e.getMessage(), e);
        }
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
