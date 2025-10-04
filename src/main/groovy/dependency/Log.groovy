package dependency

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class Log {

    private static final Logger logger = Logging.getLogger("git-dependencies-plugin")

    /**
     *
     */
    private Log() {}

    /**
     *
     * @param msg
     */
    static void info(String msg) {
        logger.lifecycle("Git dependency: $msg")
    }

    /**
     *
     * @param msg
     */
    static void warn(String msg) {
        logger.warn("Git dependency: $msg")
    }

    /**
     *
     * @param msg
     */
    static void debug(String msg) {
        logger.debug("Git dependency: $msg")
    }
}
