package set;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CleanMix's compatibility table comes out the same whatever order the compiler met the Mixins in. */
class SetCleanroomTest {
    @TempDir Path folder;

    @Test
    void theTableIsSortedTheSameWayFromAnyOrder() throws IOException {
        Path one = folder.resolve("one.json");
        Path two = folder.resolve("two.json");
        Files.write(one, "{\"b.Mixin\": \"0.8.0\", \"a.Mixin\": \"0.8.0\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(two, "{\n  \"a.Mixin\": \"0.8.0\",\n  \"b.Mixin\": \"0.8.0\"\n}".getBytes(StandardCharsets.UTF_8));
        SetCleanroomPlugin.sort(one.toFile());
        SetCleanroomPlugin.sort(two.toFile());
        String sorted = new String(Files.readAllBytes(one), StandardCharsets.UTF_8);
        assertEquals("{\n  \"a.Mixin\": \"0.8.0\",\n  \"b.Mixin\": \"0.8.0\"\n}", sorted);
        assertEquals(sorted, new String(Files.readAllBytes(two), StandardCharsets.UTF_8));
    }
}
