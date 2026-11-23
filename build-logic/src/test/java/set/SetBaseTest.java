package set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * set.base in a real build: a sibling's JAR from the Store, verified before compiling, a changed byte refused with
 * the fix; a stand-in folder in its place, one file only; and the platform's JDK major required.
 */
class SetBaseTest {
    @TempDir Path base;
    Path project;
    Path setDir;
    Path store;
    String digest;

    static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() throws IOException {
        project = base.resolve("consumer");
        setDir = base.resolve("set");
        store = base.resolve("store");
        byte[] jar = jar("lib.txt");
        digest = SetVerify.sha256(writeBytes(base.resolve("lib-1.jar"), jar).toFile());
        writeBytes(store.resolve("files").resolve(digest).resolve("lib-1.jar"), jar);
        writeSet(Runtime.version().feature());
        write(project.resolve("settings.gradle"), "plugins { id 'set.base' }\nrootProject.name = 'consumer'\n");
        write(project.resolve("build.gradle"), String.join("\n",
                "plugins { id 'java' }",
                "dependencies { compileOnly set.jar('lib') }",
                "tasks.register('where') { def f = set.jar('lib'); doLast { println 'lib=' + f.singleFile.name } }",
                ""));
        write(project.resolve("src/main/java/demo/A.java"), "package demo; class A {}\n");
    }

    static byte[] jar(String entry) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entry));
            zip.write(entry.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    static Path writeBytes(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.write(path, bytes);
    }

    void writeSet(int java) throws IOException {
        write(setDir.resolve("set.json"), "{\"schema\": 1, \"projects\": {\"lib\": {\"branch\": \"main\", \"commit\": \""
                + "a".repeat(40) + "\", \"artifacts\": {\"mod\": {\"sha256\": \"" + digest + "\", \"file\": \"lib-1.jar\","
                + " \"glob\": \"build/libs/lib-*.jar\", \"check\": \"build\"}}}}, \"third\": {}, \"holds\": {},"
                + " \"platform\": {\"java\": " + java + "}, \"doctor\": {}}\n");
    }

    GradleRunner runner(String... args) {
        String[] all = new String[args.length + 3];
        all[0] = "-Pset.dir=" + setDir;
        all[1] = "-Pset.store=" + store;
        all[2] = "--stacktrace";
        System.arraycopy(args, 0, all, 3, args.length);
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(all);
    }

    @Test
    void aStoreFileIsVerifiedBeforeCompilingAndAChangedByteIsRefused() throws IOException {
        BuildResult ok = runner("compileJava", "where").build();
        assertTrue(ok.getOutput().contains("lib=lib-1.jar"), ok.getOutput());
        assertTrue(ok.getOutput().contains("set (set)"), ok.getOutput());
        writeBytes(store.resolve("files").resolve(digest).resolve("lib-1.jar"), jar("changed.txt"));
        BuildResult refused = runner("compileJava", "--rerun-tasks").buildAndFail();
        assertTrue(refused.getOutput().contains("the Store was changed outside Workbench"), refused.getOutput());
    }

    @Test
    void aStandInFolderStandsInWithOneFileOnly() throws IOException {
        Path standIn = base.resolve("lib");
        writeBytes(standIn.resolve("build/libs/lib-2.jar"), new byte[] {1});
        BuildResult ok = runner("where", "-Pset.with.lib=" + standIn).build();
        assertTrue(ok.getOutput().contains("lib=lib-2.jar"), ok.getOutput());
        assertTrue(ok.getOutput().contains("(stand-in, built"), ok.getOutput());
        writeBytes(standIn.resolve("build/libs/lib-3.jar"), new byte[] {2});
        BuildResult two = runner("where", "-Pset.with.lib=" + standIn).buildAndFail();
        assertTrue(two.getOutput().contains("2 files in the stand-in match"), two.getOutput());
    }

    @Test
    void anotherJdkMajorIsRefused() throws IOException {
        writeSet(8);
        BuildResult refused = runner("help").buildAndFail();
        assertTrue(refused.getOutput().contains("Run Gradle on JDK 8"), refused.getOutput());
    }

    @Test
    void versionsCompareByNumber() {
        assertEquals(-1, Integer.signum(SetExtension.compare("0.5.0-dev", "0.10.0")));
        assertEquals(0, SetExtension.compare("1.2", "1.2.0"));
    }
}
