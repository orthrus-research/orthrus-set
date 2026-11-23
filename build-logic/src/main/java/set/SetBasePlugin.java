package set;

import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * set.base: the settings plugin every project's settings.gradle applies. It reads the set the build resolves, checks
 * the JDK against the set's platform, and gives every project an extension named {@code set} (set.jar, set.third,
 * set.since, set.platform) and a task setVerify, which every compile depends on and which re-hashes each Store file
 * the build was handed against set.json.
 */
public class SetBasePlugin implements Plugin<Settings> {
    private static final Logger LOG = Logging.getLogger(SetBasePlugin.class);

    @Override
    public void apply(Settings settings) {
        Map<String, String> properties = new LinkedHashMap<>();
        settings.getProviders().gradlePropertiesPrefixedBy("set.").get().forEach(properties::put);
        SetFile set;
        try {
            set = SetFile.locate(settings.getSettingsDir(), properties);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        checkJdk(set, "true".equals(properties.get("set.exact")));
        StringBuilder said = new StringBuilder("set " + set.shortRevision());
        set.standIns.forEach((name, file) -> said.append("; ").append(name).append(" from ").append(file)
                .append(" (stand-in)"));
        LOG.lifecycle(said.toString());
        settings.getExtensions().add("set", new SetSettings(set));
        settings.getGradle().beforeProject(project -> {
            SetExtension extension = project.getExtensions().create("set", SetExtension.class, project, set);
            var verify = project.getTasks().register("setVerify", SetVerify.class, task -> {
                task.setDescription("Re-hashes each file this project took from the Store against set.json.");
                task.getFiles().set(project.provider(extension::verified));
            });
            project.getTasks().withType(JavaCompile.class).configureEach(compile -> compile.dependsOn(verify));
        });
    }

    @SuppressWarnings("unchecked")
    private static void checkJdk(SetFile set, boolean exact) {
        Object java = set.platform().get("java");
        if (java == null) {
            return;
        }
        int major = Runtime.version().feature();
        if (major != ((Number) java).intValue()) {
            throw new GradleException("Run Gradle on JDK " + java + ", as set " + set.shortRevision()
                    + "'s platform says; this is JDK " + major + ".");
        }
        Object jdks = set.platform().get("jdk");
        Object entry = jdks instanceof Map ? ((Map<String, Object>) jdks).get(String.valueOf(java)) : null;
        String build = entry instanceof Map ? String.valueOf(((Map<String, Object>) entry).get("build")) : null;
        String running = System.getProperty("java.runtime.version", "");
        if (build != null && !running.equals(build) && !running.startsWith(build + "-")) {
            String text = "set " + set.shortRevision() + " builds on JDK " + build + "; this is " + running;
            if (exact) {
                throw new GradleException(text + " (-Pset.exact=true asks for that build exactly).");
            }
            LOG.lifecycle(text);
        }
    }

    /** What settings.gradle sees as {@code set}: the revision and the platform, for the plugin versions. */
    public static class SetSettings {
        private final SetFile set;

        SetSettings(SetFile set) {
            this.set = set;
        }

        public String getRevision() {
            return set.revision;
        }

        public Map<String, Object> getPlatform() {
            return set.platform();
        }
    }
}
