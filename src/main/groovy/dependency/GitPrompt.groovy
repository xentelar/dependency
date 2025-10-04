package dependency

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.gradle.api.GradleException

class GitPrompt {

    private GitPrompt() {}

    /**
     * @param dep
     */
    static void init(GitDependency dep) {
        // For SSH to work with JGit 5.8+ we have to manually set the SSH factory because default
        // ServiceLoader mechanism does not seem to work with Gradle plugins.
        SshSessionFactory.instance = new JschConfigSessionFactory()

        // Jsch does not really work well so we'd better rely on system "ssh" command.
        // To do so we'll manually try to find "ssh" in local PATH and set it as "GIT_SSH" for Jgit.
        SshSystemReader.install()

        final Git git = openGit(dep)

        if (git != null) {
            final String remoteUrl = remoteUrl(git)

            if (remoteUrl == null || remoteUrl != dep.url) {
                throw new GradleException("Git cannot update from ${remoteUrl} to ${dep.url}.\n" +
                        "Delete directory '${dep.dir}' and try again.")
            }

            final String targetCommit = dep.commit

            if (dep.keepUpdated && !isLocalCommit(git, targetCommit)) {
                final String localCommit = head(git).substring(0, 7)
                Log.warn "Local version '${localCommit}' is not equal to target " +
                        "'${targetCommit}' for '${dep.dir}'"

                if (hasLocalChanges(git)) {
                    throw new GradleException("Git repo cannot be updated to '${targetCommit}', " +
                            "'${dep.dir}' contains local changes.\n" +
                            "Commit or revert all changes manually.")
                } else {
                    Log.warn "Updating to version '${targetCommit}' for '${dep.dir}'"
                    update(git, dep)
                }
            }
        } else {
            clone(dep)
        }
    }

    /**
     *
     * @param dir
     * @return
     */
    static boolean hasLocalChangesInDir(File dir) {
        try {
            return hasLocalChanges(Git.open(dir))
        } catch (RepositoryNotFoundException ignored) {
            return false
        }
    }

    /**
     *
     * @param git
     * @param dep
     */
    static void update(Git git, GitDependency dep) {
        final long start = System.currentTimeMillis()
        Log.info "Update started '${dep.url}' at version '${dep.commit}'"

        git.fetch()
                .setTagOpt(TagOpt.FETCH_TAGS)
                .setCredentialsProvider(creds(dep))
                .call()

        git.checkout().setName(dep.commit).call()

        final long spent = System.currentTimeMillis() - start
        Log.info "Update finished ($spent ms)"
    }

    /**
     *
     * @param dep
     */
    static void clone(GitDependency dep) {
        final long start = System.currentTimeMillis()
        Log.info "Clone started '${dep.url}' at version '${dep.commit}'"

        final Git git = Git.cloneRepository()
                .setDirectory(dep.dir)
                .setURI(dep.url)
                .setRemote('origin')
        //.setNoCheckout(true)
                .setCredentialsProvider(creds(dep))
                .call()

        git.checkout().setName(dep.commit).call()

        final long spent = System.currentTimeMillis() - start
        Log.info "Clone finished ($spent ms)"
    }

    /**
     *
     * @param dep
     * @return
     */
    private static Git openGit(GitDependency dep) {
        try {
            return Git.open(dep.dir)
        } catch (RepositoryNotFoundException ignored) {
            return null
        }
    }

    /**
     *
     * @param git
     * @return
     */
    private static String remoteUrl(Git git) {
        return git.repository.config.getString('remote', Constants.DEFAULT_REMOTE_NAME, 'url')
    }

    /**
     *
     * @param git
     * @return
     */
    private static String head(Git git) {
        return ObjectId.toString(git.repository.resolve('HEAD'))
    }

    /**
     *
     * @param git
     * @param targetId
     * @return
     */
    private static boolean isLocalCommit(Git git, String targetId) {
        // Checking if local commit is equal to (starts with) requested one.
        final String headId = head(git)
        if (headId.startsWith(targetId)) return true

        // If not then we should check if there is a tag with given name pointing to current head.
        final Ref tag = git.repository.refDatabase.exactRef(Constants.R_TAGS + targetId)
        // Annotated tags need extra effort
        final Ref peeledTag = tag == null ? null : git.repository.refDatabase.peel(tag)
        final tagObjectId = peeledTag?.peeledObjectId ?: tag?.objectId

        final String tagId = ObjectId.toString(tagObjectId)
        return tagId == headId
    }

    /**
     *
     * @param git
     * @return
     */
    private static boolean hasLocalChanges(Git git) {
        final Status status = git.status().call()
        final List<String> changes = new ArrayList<>()
        changes.addAll(status.added)
        changes.addAll(status.changed)
        changes.addAll(status.removed)
        changes.addAll(status.untracked)
        changes.addAll(status.modified)
        changes.addAll(status.missing)
        return !changes.isEmpty()
    }

    /**
     *
     * @param dep
     * @return
     */
    private static CredentialsProvider creds(GitDependency dep) {
        return dep.needsAuth
                ? new UsernamePasswordCredentialsProvider(dep.username, dep.password) : null
    }
}
