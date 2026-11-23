package set;

import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/**
 * set.cleanroom: applied after CleanroomGradle in a Cleanroom mod's build. It takes the Cleanroom version from the
 * set's platform (as the project property cleanroom_version, which CleanroomGradle builds read) and makes every
 * archive reproducible. Further shared settings move here as each project's build.gradle gives them up.
 */
public class SetCleanroomPlugin implements Plugin<Project> {
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
    }
}
