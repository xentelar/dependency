package dependency

import org.gradle.api.initialization.Settings

class SettingsExtension {

    private final Closure dependencyAction
    final File rootDir

    File dir
    String defaultAuthGroup
    boolean cleanup
    boolean cleanupIdeaModules

    /**
     *
     * @param settings
     * @param dependencyAction
     */
    SettingsExtension(Settings settings, Closure dependencyAction) {
        this.dependencyAction = dependencyAction
        this.rootDir = settings.rootDir
        // Setting default values:
        this.dir = new File(settings.rootDir, 'libs')
        this.defaultAuthGroup = null
        this.cleanup = true
        this.cleanupIdeaModules = true
    }

    /**
     *
     * @param closure
     */
    void apply(Closure closure) {
        final Collector collector = new Collector()
        closure.rehydrate(collector, collector, collector).call()
    }

    /**
     *
     */
    @SuppressWarnings("unused")
    class Collector {
        void dir(def dir) {
            if (!dir) throw new NullPointerException("Git dependency: 'dir' cannot be null")
            SettingsExtension.this.dir = dir as File
        }

        void defaultAuthGroup(String authGroup) {
            SettingsExtension.this.defaultAuthGroup = authGroup
        }

        void cleanup(boolean cleanup) {
            SettingsExtension.this.cleanup = cleanup
        }

        void cleanupIdeaModules(boolean cleanup) {
            SettingsExtension.this.cleanupIdeaModules = cleanup
        }

        void fetch(String url, Closure closure = null) {
            dependencyAction(url, closure)
        }
    }
}
