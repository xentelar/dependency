package dependency


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings

import java.nio.file.Paths
import java.util.regex.Matcher

class DependencyPlugin implements Plugin<Settings> {

    private static final EXTENSION_NAME = 'gitDependencies'

    private Settings settings
    private SettingsExtension props
    private Credential credential
    private final Dependencies dependencies = new Dependencies()
    private final GroovyShell shell = new GroovyShell()
    private final Set<String> projectsWithExtension = new HashSet<>()

    /**
     *
     * @param settings
     */
    void apply(Settings settings) {
        this.settings = settings
        this.props = new SettingsExtension(settings, { url, closure ->
            GitDependency dep = buildDependency(settings, null, url, closure)
            dependencies.add(null, dep)
        })
        this.credential = new Credential(settings.gradle.gradleUserHomeDir)

        // Adding configuration method to collect plugin settings
        settings.metaClass."$EXTENSION_NAME" = props.&apply

        settings.gradle.settingsEvaluated {
            resolveDependenciesRecursively(settings.projectDescriptorRegistry.allProjects)
            cleanup()
        }

        // Registering special extension that will ignore our fake extension (which was already
        // handled as part of `Settings` evaluation process above)
        settings.gradle.beforeProject { Project project ->
            // Only adding `empty` extension for the projects where we successfully handled
            // the extension during settings evaluation.
            if (projectsWithExtension.contains(project.name)) {
                project.extensions.create(EXTENSION_NAME, EmptyExtension)
            }
        }

        // Adding projects dependencies for each project
        settings.gradle.afterProject { Project project ->
            dependencies.get(project.name).each { GitDependency dep ->
                project.dependencies.add(dep.configName, project.project(dep.projectName))
            }
        }
    }

    /**
     * Building git dependencies list by invoking extension method for each project
     *
     * @param projects
     */
    void resolveDependenciesRecursively(Set<ProjectDescriptor> projects) {
        Set<ProjectDescriptor> newlyAddedProjects = new HashSet<>()
        projects.each { ProjectDescriptor project ->
            Log.debug "Resolve Project: '${project.name}'"
            Script script = parseBuildScript(project.buildFile)

            if (script != null) {
                projectsWithExtension.add(project.name)

                // Defining extra properties to be available in extension script
                script.metaClass.project = project
                script.metaClass.gradle = settings.gradle

                // Adding method to handle configuration names dynamically
                script.metaClass.methodMissing { methodName, args ->
                    //Log.debug "Build Dependency: '${methodName}' at Args: '${args}'"
                    if (args.length > 1) {
                        if (args[1] instanceof Closure) {
                            GitDependency dep = buildDependency(script, methodName, args)
                            dependencies.add(project.name, dep)
                        }
                    } else {
                        GitDependency dep = buildDependency(script, methodName, args)
                        dependencies.add(project.name, dep)
                    }
                }

                // Calling (fake) extension method
                script."$EXTENSION_NAME"()

                // Registering new projects
                dependencies.get(project.name).each { GitDependency dep ->
                    if (!settings.findProject(dep.projectName)) {
                        settings.include(dep.projectName)
                        ProjectDescriptor depProject = settings.project(dep.projectName)
                        depProject.projectDir = dep.projectDir
                        newlyAddedProjects.add(depProject)
                    }
                }
            }
        }

        // If we have new projects we should process them too
        if (newlyAddedProjects) resolveDependenciesRecursively(newlyAddedProjects)
    }

    /**
     * The extension to appear as regular DSL and cannot be used Gradle's native
     * extensions mechanism because project extensions are handled after
     * `Settings` was configured so we won't be able to add dynamic projects anymore.
     * Instead we are going to parse projects build scripts manually during settings
     * configuration phase. When manually parsing scripts we cannot run regular DSL, like
     * `android { ... }`, but we can run methods defined like `def android() {...}`.
     * So we have to use a hack: transform DSL call to a method reference directly in sources.
     *
     * @param buildFile
     * @return
     */
    private Script parseBuildScript(File buildFile) {
        if (buildFile == null || !buildFile.exists()) return null

        // Reading original script
        GroovyCodeSource orig = new GroovyCodeSource(buildFile, 'UTF-8')

        // Checking if we have our extension defined
        Matcher matcher = orig.scriptText =~ /(^|\n)([\s\t\n\r]*)($EXTENSION_NAME)([\s\t\n\r]*\{)/
        if (!matcher.find()) return null

        // Replacing extension with method declaration
        String fixedScript = matcher.replaceFirst('$1$2def $3()$4')

        // Parsing fixed script
        String codeBase = buildFile.toURI().toString()
        GroovyCodeSource fixedSource = new GroovyCodeSource(fixedScript, orig.name, codeBase)
        //Log.info "Groovy Code: '${fixedSource.scriptText}'"
        Script script = shell.parse(fixedSource)

        // Returning parsed script if it contains expected method
        if (script.metaClass.respondsTo(script, EXTENSION_NAME)) {
            return script
        } else {
            return null
        }
    }

    /**
     * Handling calls like `implementation 'git url', { dependency config closure }`
     *
     * @param owner
     * @param configName
     * @param args
     * @return
     */
    private GitDependency buildDependency(Object owner, String configName, def args) {
        String url
        Closure closure = null

        if (args != null && args.length > 0 && args[0] instanceof String) {
            url = args[0]
        } else {
            throw new IllegalArgumentException('Git dependency: Missing git repo URL')
        }

        if (args.length > 1) {
            if (args[1] instanceof Closure) {
                closure = args[1]
            } else {
                throw new IllegalArgumentException("Git dependency: Config closure is invalid")
            }
        }

        return buildDependency(owner, configName, url, closure)
    }

    /**
     * Passing `builder` as delegate and `owner` as owner, to have access to extra
     * properties from extension scripts
     *
     * @param owner
     * @param configName
     * @param url
     * @param closure
     * @return
     */
    private GitDependency buildDependency(Object owner, String configName,
                                          String url, Closure closure) {
        final GitDependency.Builder builder = new GitDependency.Builder(configName, url)
        if (closure != null) {
            closure.rehydrate(builder, builder, builder).call()
        }

        return new GitDependency(props, credential, builder)
    }

    /**
     * Cleaning up unused directories.
     * Deleting dirs if they're not declared as dependency and don't contain local changes
     */
    private void cleanup() {
        if (props.cleanup) {
            final File libsDir = props.dir

            dirLoop:
            for (File dir in libsDir.listFiles()) {
                if (!dir.isDirectory()) continue

                for (GitDependency dep in dependencies.all()) {
                    if (dir == dep.dir) continue dirLoop
                }

                String relativePath = Paths.get(libsDir.absolutePath)
                        .relativize(Paths.get(dir.absolutePath))
                        .toString()

                if (GitPrompt.hasLocalChangesInDir(dir)) {
                    Log.warn "Skipping directory deletion for $relativePath"
                } else {
                    Log.warn "Deleting unknown directory $relativePath"
                    dir.deleteDir()
                }
            }

            // Removing libs directory if it's empty
            if (!libsDir.list()) libsDir.deleteDir()
        }

        if (props.cleanupIdeaModules) {
            IdeaPrompt.cleanModules(settings.rootDir, dependencies)
        }
    }
}
