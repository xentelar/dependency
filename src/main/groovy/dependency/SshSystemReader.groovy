package dependency

import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.UserConfigFile
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.StringUtils
import org.eclipse.jgit.util.SystemReader

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

import groovy.lang.Delegate

// Based on https://github.com/ajoberstar/grgit/blob/1a1c6b7/grgit-core/src/main/groovy/org/ajoberstar/grgit/auth/GrgitSystemReader.java
class SshSystemReader extends SystemReader {

    /**
     *
     */
    @Delegate
    private final SystemReader delegate

    private final String gitSsh

    private volatile String hostname

    private SshSystemReader(SystemReader delegate, String gitSsh) {
        this.delegate = delegate
        this.gitSsh = gitSsh
    }

    /**
     *
     * @param variable
     *            system variable to read
     * @return
     */
    @Override
    String getenv(String variable) {
        String value = delegate.getenv(variable)
        if ('GIT_SSH' == variable && value == null) {
            return gitSsh
        } else {
            return value
        }
    }

    /**
     *
     */
    static void install() {
        final SystemReader current = instance
        if (current instanceof SshSystemReader) return

        final String gitSsh = findExecutable("ssh") ?: findExecutable("plink")
        instance = new SshSystemReader(current, gitSsh)
    }

    /**
     *
     * @param exe
     * @return
     */
    private static String findExecutable(String exe) {
        final String path = System.getenv('PATH') ?: ''
        final String pathExt = System.getenv('PATHEXT') ?: ''
        final Pattern splitter = Pattern.compile(Pattern.quote(File.pathSeparator))

        final String[] extensions = splitter.split(pathExt)
        final List<String> withExt =
                extensions.length == 0 ? [exe] : extensions.collect { ext -> exe + ext }

        return splitter.split(path).findResult {it ->
            final Path dir = Paths.get(it)
            withExt.findResult {
                final Path exePath = dir.resolve(it)
                Files.isExecutable(exePath) ? exePath.toAbsolutePath().toString() : null
            }
        }
    }

    /**
     *
     * @return
     */
    @Override
    String getHostname() {
        if (hostname == null) {
            try {
                InetAddress localMachine = InetAddress.getLocalHost()
                hostname = localMachine.getCanonicalHostName()
            } catch (UnknownHostException ignored) {
                hostname = "localhost"
            }
            assert hostname != null
        }
        return hostname
    };

    /**
     *
     * @param key
     *            of the system property to read
     * @return
     */
    @Override
    String getProperty(String key) {
        return System.getProperty(key)
    }

    /**
     *
     * @param parent
     *            a config with values not found directly in the returned config
     * @param fs
     *            the file system abstraction which will be necessary to perform
     *            certain file system operations.
     * @return
     */
    @Override
    FileBasedConfig openUserConfig(Config parent, FS fs) {
        File homeFile = new File(fs.userHome(), ".gitconfig");
        Path xdgPath = getXdgConfigDirectory(fs);
        if (xdgPath != null) {
            Path configPath = xdgPath.resolve("git")
                    .resolve(Constants.CONFIG);
            return new UserConfigFile(parent, homeFile, configPath.toFile(),
                    fs);
        }
        return new FileBasedConfig(parent, homeFile, fs);
    }

    /**
     *
     * @param parent
     *            a config with values not found directly in the returned
     *            config. Null is a reasonable value here.
     * @param fs
     *            the file system abstraction which will be necessary to perform
     *            certain file system operations.
     * @return
     */
    @Override
    FileBasedConfig openSystemConfig(Config parent, FS fs) {
        if (StringUtils
                .isEmptyOrNull(getenv(Constants.GIT_CONFIG_NOSYSTEM_KEY))) {
            File configFile = fs.getGitSystemConfig()
            if (configFile != null) {
                return new FileBasedConfig(parent, configFile, fs)
            }
        }
        return new FileBasedConfig(parent, null, fs) {
            @Override
            void load() {
                // empty, do not load
            }

            @Override
            boolean isOutdated() {
                // regular class would bomb here
                return false
            }
        }
    }

    /**
     *
     * @param parent
     *            a config with values not found directly in the returned config
     * @param fs
     *            the file system abstraction which will be necessary to perform
     *            certain file system operations.
     * @return
     */
    @Override
    FileBasedConfig openJGitConfig(Config parent, FS fs) {
        Path xdgPath = getXdgConfigDirectory(fs);
        if (xdgPath != null) {
            Path configPath = xdgPath.resolve("jgit")
                    .resolve(Constants.CONFIG)
            return new FileBasedConfig(parent, configPath.toFile(), fs)
        }
        return new FileBasedConfig(parent,
                new File(fs.userHome(), ".jgitconfig"), fs)
    }

    /**
     *
     * @return
     */
    @Override
    @Deprecated(since = "7.1")
    long getCurrentTime() {
        return System.currentTimeMillis()
    }

    /**
     *
     * @param when
     *            a system timestamp
     * @return
     */
    @Override
    @Deprecated(since = "7.1")
    int getTimezone(long when) {
        return TimeZone.getDefault().getOffset(when) / (60 * 1000)
    }
}
