import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Standard javac API, avoiding ZipFS close/canonical-path failures in this Windows sandbox. */
public final class CompileBridge {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8);
        List<File> classpath=new ArrayList<>(), sources=new ArrayList<>();
        for(String line:Files.readAllLines(Paths.get(args[0]))) classpath.add(new File(line));
        for(String line:Files.readAllLines(Paths.get(args[1]))) sources.add(new File(line));
        manager.setLocation(StandardLocation.CLASS_PATH,classpath);
        manager.setLocation(StandardLocation.CLASS_OUTPUT,Arrays.asList(new File(args[2])));
        boolean success=compiler.getTask(null,manager,null,
                Arrays.asList("-encoding","UTF-8","-source","8","-target","8"),null,
                manager.getJavaFileObjectsFromFiles(sources)).call();
        // All diagnostics and generation have completed. Process exit closes read-only archive
        // handles without ZipFS canonicalizing their restricted Windows ancestor directories.
        System.exit(success?0:1);
    }
}
