package hugo.weaving.plugin

import com.android.build.api.transform.*
import com.android.utils.Pair
import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.JavaCompile

import static com.android.build.api.transform.Status.*

/**
 * Created by williamwebb on 2/16/16.
 */
@CompileStatic
class HugoTransform extends Transform {

    private final Project project
    private final Map<Pair<String, String>, JavaCompile> javaCompileTasks = new HashMap<>()
    private final boolean enabled;
    public HugoTransform(Project project, boolean enabled) {
        this.project = project
        this.enabled = enabled;
    }

    /**
     * We need to set this later because the classpath is not fully calculated until the last
     * possible moment when the java compile task runs. While a Transform currently doesn't have any
     * variant information, we can guess the variant based off the input path.
     */
    public void putJavaCompileTask(String flavorName, String buildTypeName, JavaCompile javaCompileTask) {
        javaCompileTasks.put(Pair.of(flavorName, buildTypeName), javaCompileTask)
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        boolean debug = context.path.toLowerCase().endsWith("debug");
        if(!enabled || !debug) return;

        inputs.each { TransformInput input ->
            def outputDir = outputProvider.getContentLocation("hugo", outputTypes, scopes, Format.DIRECTORY)
            JavaCompile javaCompile = javaCompileTasks.get(Pair.of("", "debug"))

            input.directoryInputs.each { DirectoryInput directoryInput ->

                String inPath;
                if (isIncremental) {
                    FileCollection changed = project.files()
                    directoryInput.changedFiles.each { File file, Status status ->
                        if (status == ADDED || status == CHANGED) {
                            changed += project.files(file.parent);
                        }
                    }
                    inPath = changed.asPath
                } else {
                    inPath = javaCompile.destinationDir.toString()
                }

                def exec = new HugoExec(project)
                exec.inpath = inPath
                exec.aspectpath = javaCompile.classpath.asPath
                exec.destinationpath = outputDir
                exec.classpath = javaCompile.classpath.asPath
                exec.bootclasspath = getBootClassPath(javaCompile).asPath
                exec.exec()
            }
        }
    }

    private FileCollection getBootClassPath(JavaCompile javaCompile) {
        def bootClasspath = javaCompile.options.bootClasspath
        if (bootClasspath) {
            return project.files(bootClasspath.tokenize(File.pathSeparator))
        } else {
            // If this is null it means the javaCompile task didn't need to run, however, we still
            // need to run but can't without the bootClasspath. Just fail and ask the user to rebuild.
            throw new ProjectConfigurationException("Unable to obtain the bootClasspath. This may happen if your javaCompile tasks didn't run but retrolambda did. You must rebuild your project or otherwise force javaCompile to run.", null)
        }
    }

    @Override
    public String getName() {
        return "hugo"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES)
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return Collections.singleton(QualifiedContent.Scope.PROJECT)
    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return Collections.singleton(QualifiedContent.Scope.PROJECT)
    }

    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.<String, Object> builder()
                .put("enabled", enabled) // project.hugo.enabled)
                .build();
    }

    @Override
    public boolean isIncremental() {
        return true
    }
}