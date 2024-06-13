import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ITJumboTraceSuite {

    private static String JumboTrace = "JumboTrace";
    private static String Examples = "examples";
    private static String MainFolderFromJumbo = "../../../../";
    private static String MainFolderFromExamples = "../../../";
    private static String InstrumentationPlugin =  "InstrumentationPlugin/target/classes";
    private static String FileLogger = "Logging/target/classes";
    private static List<String> CommandTemplate = List.of(
            "javac",
            "-processorpath",
            "PluginPath",
            "-g",
            "-Xplugin:MyPlugin",
            "-cp",
            "./:LoggerPath",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");


    void compileJumboTrace() throws IOException {
        copyJumboTrace();

        Map<String, String> dirWithError = new HashMap<>();

        jumboTraceDirectories()
                .forEach(dir -> {
                    List<File> javaFiles = Arrays.stream(dir
                                    .listFiles())
                            .filter(f -> f.getName().endsWith(".java"))
                            .toList();
                    if (javaFiles.size() == 1) {
                        List<String> command = compileCommandJumbo(javaFiles);
                        ProcessBuilder builder = new ProcessBuilder("javac", "--version")
                                .command(command)
                                .directory(dir);
                        try {
                            Process process = builder.start();
                            String output = readOutput(process.getErrorStream());
                            System.out.println(dir.getName());
                            assertThat(output).isEmpty();
                            dirWithError.put(dir.getName(), output);
                        } catch (IOException e) {
                            fail();
                        }
                    }
                });

        assertThat(dirWithError.entrySet())
                .allMatch(p -> p.getValue().isEmpty());
    }

    @Test
    void compileBoid(){
        Map<String, String> dirWithError = new HashMap<>();

        examplesDirectories()
                .forEach(dir -> {
                    List<File> javaFiles = Arrays.stream(dir
                                    .listFiles())
                            .filter(f -> f.getName().endsWith(".java"))
                            .toList();
                    if (javaFiles.size() == 1) {
                        List<String> command = compileCommandExamples(javaFiles);
                        ProcessBuilder builder = new ProcessBuilder("javac", "--version")
                                .command(command)
                                .directory(dir);
                        try {
                            Process process = builder.start();
                            String output = readOutput(process.getErrorStream());
                            System.out.println(dir.getName());
                            assertThat(output).isEmpty();
                            dirWithError.put(dir.getName(), output);
                        } catch (IOException e) {
                            fail();
                        }
                    }
                });

        assertThat(dirWithError.entrySet())
                .allMatch(p -> p.getValue().isEmpty());
    }

    // we suppose we are in JumboTrace/examples/{example}
    private static List<String> compileCommandJumbo(List<File> javaFiles) {
        List<String> command = new ArrayList<>(CommandTemplate);
        command.set(2, command.get(2).replace("PluginPath", MainFolderFromJumbo +InstrumentationPlugin));
        command.set(6, command.get(6).replace("LoggerPath", MainFolderFromJumbo +FileLogger));
        command.add(String.join(
                " ",
                javaFiles.stream().map(File::getName).toList()));

        return command;
    }

    private static List<String> compileCommandExamples(List<File> javaFiles) {
        List<String> command = new ArrayList<>(CommandTemplate);
        command.set(2, command.get(2).replace("PluginPath", MainFolderFromExamples+InstrumentationPlugin));
        command.set(6, command.get(6).replace("LoggerPath", MainFolderFromExamples+FileLogger));
        command.add(String.join(
                " ",
                javaFiles.stream().map(File::getName).toList()));

        return command;
    }

    private static Stream<File> jumboTraceDirectories() {
        Path examples = Path.of(JumboTrace, Examples);

        return Stream.of(examples.toFile().listFiles())
                .filter(File::isDirectory);
    }

    private static Stream<File> examplesDirectories() {
        Path examples = Path.of(Examples);

        return Stream.of(examples.toFile().listFiles())
                .filter(File::isDirectory);
    }

    private static void copyJumboTrace() throws IOException {
        Path jumboPath = Paths.get(JumboTrace);
        if (!Files.exists(jumboPath)) {
            new ProcessBuilder("./initDirectory.sh")
                    .start();
        }
    }

    private static String readOutput(InputStream inputStream) {
        InputStreamReader reader = new InputStreamReader(inputStream);
        BufferedReader buffered = new BufferedReader(reader);

        return buffered.lines().collect(Collectors.joining("\n"));
    }
}
