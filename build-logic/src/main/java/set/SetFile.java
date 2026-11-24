package set;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The set a build resolves: set.json at one revision, where the Store keeps the files it names, and the stand-ins
 * that replace a sibling's files with a folder or JAR of the person's own.
 *
 * <p>There are two answers only. -Pset.dir names a folder holding set.json (a job's export, or a plain clone of the
 * Set). Otherwise the nearest .set file above the build names a revision, read from -Pset.home/&lt;revision&gt;.
 */
final class SetFile {
    final File dir;
    final String revision;
    final Map<String, Object> data;
    final File store;
    final File work;            // the folder holding the .set this build took its revision from, if any
    final Map<String, File> standIns = new LinkedHashMap<>();
    String consumer;            // this build's project name: -Pset.project, else [project] name in its project.toml

    private SetFile(File dir, String revision, Map<String, Object> data, File store, File work) {
        this.dir = dir;
        this.revision = revision;
        this.data = data;
        this.store = store;
        this.work = work;
    }

    @SuppressWarnings("unchecked")
    static SetFile locate(File settingsDir, Map<String, String> properties) {
        String given = properties.get("set.dir");
        File work = null;
        File dir;
        String revision;
        if (given != null) {
            dir = new File(given);
            revision = "(" + dir.getName() + ")";
        } else {
            File mark = null;
            for (File d = settingsDir.getAbsoluteFile(); d != null && mark == null; d = d.getParentFile()) {
                File f = new File(d, ".set");
                if (f.isFile()) {
                    mark = f;
                }
            }
            String home = properties.get("set.home");
            if (mark == null || home == null) {
                throw new IllegalStateException("No set for this folder: run wb work sync, or pass "
                        + "-Pset.dir=<clone of the Set>");
            }
            Map<String, Object> mine = (Map<String, Object>) Json.parse(read(mark.toPath()));
            revision = String.valueOf(mine.get("set"));
            dir = new File(home, revision);
            work = mark.getParentFile();
        }
        File json = new File(dir, "set.json");
        if (!json.isFile()) {
            throw new IllegalStateException("No set.json at " + json + ": run wb work sync");
        }
        Map<String, Object> data = (Map<String, Object>) Json.parse(read(json.toPath()));
        if (!Long.valueOf(1).equals(data.get("schema"))) {
            throw new IllegalStateException(json + " is not schema 1");
        }
        String store = properties.get("set.store");
        SetFile set = new SetFile(dir, revision, data, store == null ? null : new File(store), work);
        set.findStandIns(settingsDir, properties);
        set.consumer = properties.containsKey("set.project") ? properties.get("set.project") : projectName(settingsDir);
        return set;
    }

    /** The name in [project] of the project.toml beside the settings file, or null without one. */
    static String projectName(File settingsDir) {
        File manifest = new File(settingsDir, "project.toml");
        if (!manifest.isFile()) {
            return null;
        }
        boolean inProject = false;
        for (String line : read(manifest.toPath()).split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                inProject = trimmed.equals("[project]");
            } else if (inProject) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^name\\s*=\\s*\"([^\"]+)\"").matcher(trimmed);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private void findStandIns(File settingsDir, Map<String, String> properties) {
        for (Map.Entry<String, String> e : properties.entrySet()) {
            if (e.getKey().startsWith("set.with.")) {
                standIns.put(e.getKey().substring("set.with.".length()), new File(e.getValue()));
            }
        }
        if (work == null) {
            return;
        }
        // A Work: every member folder beside this build that is named after a project with a line stands in for it.
        File self = settingsDir.getAbsoluteFile();
        while (self.getParentFile() != null && !self.getParentFile().equals(work.getAbsoluteFile())) {
            self = self.getParentFile();
        }
        File[] members = work.listFiles(File::isDirectory);
        if (members == null) {
            return;
        }
        for (File member : members) {
            for (String name : projects().keySet()) {
                if (member.getName().equalsIgnoreCase(name) && !member.equals(self) && !standIns.containsKey(name)) {
                    standIns.put(name, member);
                }
            }
        }
    }

    static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> table(String key) {
        Object value = data.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    Map<String, Object> projects() {
        return table("projects");
    }

    Map<String, Object> platform() {
        return table("platform");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> line(String project) {
        Object line = projects().get(project);
        if (!(line instanceof Map)) {
            throw new IllegalStateException("set " + shortRevision() + " has no line for " + project
                    + " (lines: " + projects().keySet() + ")");
        }
        return (Map<String, Object>) line;
    }

    /**
     * One artifact of a sibling as this build takes it: a hold for this consumer (project:artifact@consumer), else a
     * hold for everyone (project:artifact), else the line's. A held item keeps the line's glob for stand-ins and
     * names its hold.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> artifact(String project, String artifact) {
        Map<String, Object> lineItem = null;
        Object lineValue = projects().get(project);
        if (lineValue instanceof Map) {
            Object item = ((Map<String, Object>) lineValue).getOrDefault("artifacts", Collections.emptyMap());
            Object found = item instanceof Map ? ((Map<String, Object>) item).get(artifact) : null;
            lineItem = found instanceof Map ? (Map<String, Object>) found : null;
        }
        String[] keys = consumer == null ? new String[] {project + ":" + artifact}
                : new String[] {project + ":" + artifact + "@" + consumer, project + ":" + artifact};
        for (String key : keys) {
            Object hold = table("holds").get(key);
            if (!(hold instanceof Map)) {
                continue;
            }
            Object held = ((Map<String, Object>) hold).get("artifacts");
            Object item = held instanceof Map ? ((Map<String, Object>) held).get(artifact) : null;
            if (item instanceof Map) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) item);
                if (lineItem != null && lineItem.get("glob") != null) {
                    merged.putIfAbsent("glob", lineItem.get("glob"));
                }
                merged.put("hold", key);
                return merged;
            }
        }
        if (lineItem == null) {
            Map<String, Object> artifacts = (Map<String, Object>) line(project).getOrDefault("artifacts", Collections.emptyMap());
            throw new IllegalStateException(project + "'s line names no artifact " + artifact
                    + " (it has: " + artifacts.keySet() + ")");
        }
        return lineItem;
    }

    String shortRevision() {
        return revision.length() > 7 && !revision.startsWith("(") ? revision.substring(0, 7) : revision;
    }
}
