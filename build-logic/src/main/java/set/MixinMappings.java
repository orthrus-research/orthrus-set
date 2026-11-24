package set;

import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * The member mappings CleanMix writes beside its refmap. CleanMix emits the mappings of shadowed and overwritten
 * members separately from the injection refmap; merged (-AmergeBehaviour=merge) into one tsrg file
 * (-AoutTsrgFile), they join the loader's mappings in the reobf task, so the production JAR renames both.
 */
final class MixinMappings {
    static final String PATH = "generated/mixin/mcp2srg.tsrg";

    private MixinMappings() {
    }

    /** Has compileJava write the merged mappings to build/generated/mixin/mcp2srg.tsrg, and returns that file. */
    static Provider<RegularFile> configure(Project project) {
        Provider<RegularFile> file = project.getLayout().getBuildDirectory().file(PATH);
        project.getTasks().named("compileJava", JavaCompile.class).configure(compile -> {
            compile.getOptions().getCompilerArgumentProviders().add(new Arguments(file));
            compile.doFirst(new MakeFolder(file));
        });
        return file;
    }

    /** CleanMix's two options, with the file as the compile's output. */
    static final class Arguments implements CommandLineArgumentProvider {
        private final Provider<RegularFile> file;

        Arguments(Provider<RegularFile> file) {
            this.file = file;
        }

        @OutputFile
        public Provider<RegularFile> getFile() {
            return file;
        }

        @Override
        public Iterable<String> asArguments() {
            return List.of("-AmergeBehaviour=merge", "-AoutTsrgFile=" + file.get().getAsFile().getAbsolutePath());
        }
    }

    /** CleanMix writes the file but does not make its folder. */
    static final class MakeFolder implements Action<Task> {
        private final Provider<RegularFile> file;

        MakeFolder(Provider<RegularFile> file) {
            this.file = file;
        }

        @Override
        public void execute(Task task) {
            file.get().getAsFile().getParentFile().mkdirs();
        }
    }
}
