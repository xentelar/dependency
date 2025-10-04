
1. [What is Dependency](#what-is-Dependency)
2. [How it works](#how-it-works)
3. [Setup](#setup)
4. [Usage](#usage)
5. [Supported parameters](#supported-parameters)
6. [Git Credentials](#git-credentials)
7. [Examples](#examples)

### What is Dependency ###

When you declare a dependency on a library, Gradle looks for the library’s binaries in a binary repository,
such as JCenter or Maven Central, and downloads the binaries for use in the build, but some time you have 
situations when you need use the source code like a dependency from a git repository or local path.

Gradle plugin Dependency allow you to instead have Gradle automatically check out the source for the library
from Git and build the binaries locally on your machine, rather than manually downloading them.

### How it works ###

1. Provide git repository URL and other optional details in `build.gradle` file.
2. The plugin clones the git repository to default path `libs/[name]` at specified commit, tag or branch. Default path can be changed.
3. The git repository cloned will be included as sub-project and defined as dependency of project.
4. The dependencies are resolved recursively, i.e. your git dependency can have other git dependencies.
5. If several projects have dependencies with same name then all other details (url, commit, etc)
   should be completely the same, otherwise build process will fail.
6. The plugin automatically updates repository if `commit` doesn't much local commit. If there're any
   uncommited changes in local repo then build process will fail until you manually resolve conflicts.
7. Removed dependencies will be automatically cleaned from `libs` directory.

### Setup ###

In `settings.gradle` file add the following lines:

* Using Gradle 9.1.x

```
plugins {
    id 'com.xentelar.dependency' version '0.1.0'
}
```

* Warning: Older Gradle version aren't supported

```
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.xentelar:dependency:0.1.0'
    }
}

apply plugin: 'com.xentelar.dependency'
```

Optionally you can provide settings next in `settings.gradle`:

```
gitDependencies {
    dir 'libs' // Directory in which to store git repositories, 'libs' by default
    cleanup true // Whether to cleanup unused dirs inside 'libs' dir, true by default
    defaultAuthGroup 'group name' // Default auth group to be used for all repos. See `Credentials` section below.
}
```

### Usage ###

**Warning: the plugin works with Groovy scripts only, no Kotlin DSL support is available yet.**

In `build.gradle` add the following configuration example:

```
gitDependencies {
    implementation 'https://example.com/repository1.git', {
        name 'DependencyName1'
        commit '12345678abcdefgh'
    }
    
    api 'https://example.com/repository2.git', {
        name 'DependencyName2'
        tag '0.1.0'
    }
}
```
Note that using `master` or any other branch name as git commit is not recommended,
use explicit commit or tag instead.


You can also specify git repos in `settings.gradle` similar as it is done in `build.gradle`
but use `fetch` instead of configuration name:

```
gitDependencies {
    fetch 'https://example.com/repository.git', {
        dir "$rootDir/gradle/scripts"
        tag 'v1.2.3'
    }
}
```

Such repositories will be downloaded but not added as dependencies.
This can be useful, for example, if you want to pre-fetch build scripts.

### Supported parameters ###

| Parameter       | Description |
| --------------- | ----------- |
| name            | Dependency name. Will be used as gradle project name and as repo directory name. If the name is not set then it will be taken from url. |
| commit          | Git commit id of any length, tag name or full branch name. For example `e628b205`, `v1.2.3`, `origin/master`. |
| tag             | Same as `commit`, see above. |
| branch          | Same as `commit`, see above. |
| username        | Username to access repository. See `Credentials` section below. |
| password        | Password to access repository. See `Credentials` section below. |
| authGroup       | Group name used when looking for credentials. See `Credentials` section below. |
| dir             | Directory for cloned repository. Used to override default directory as defined in `settings.gradle`. |
| projectPath     | Path within repository which should be added as gradle project. By default repo's root directory is added as project. |
| keepUpdated     | Whether to update this repository automatically or not. Default is `true`. |

### Git Credentials ###

If git repo is using SSH url (starts with `git@`) then the plugin will automatically try to use
local SSH key. But you need to ensure your SSH key is correctly setup, see instructions for:
- [Gitlab](https://docs.gitlab.com/user/ssh/)
- [GitHub](https://help.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh)
- [Bitbucket](https://confluence.atlassian.com/bitbucket/ssh-keys-935365775.html)
- [ASW CodeCommit](https://docs.aws.amazon.com/codecommit/latest/userguide/setting-up-ssh-unixes.html)

If git repo is using HTTPS url then there are two options how you can define credentials:

* Using `username` and `password` options directly in `build.gradle`, for example, if you run Gradle
  with a system property like this:
    ```
    ./gradlew build -Dmy.username.property=someName -Dmy.password.property=somePassword
    ```
    ```
    def myUserNameValue = System.getProperty("my.username.property")
    def myPasswordValue = System.getProperty("my.password.property")
    ```
    ```
    gitDependencies {
        implementation 'https://example.com/repository.git', {
            username myUserNameValue
            password myPasswordValue
        }
    }
    ```

* Using `authGroup` option and providing credentials as specified below:
  If `authGroup` is provided then the plugin will search for `git.[authGroup].username` and
  `git.[authGroup].password` params in:

    * command line arguments, e.g: `-Dgit.[authGroup].username=someName -Dgit.[authGroup].password=somePassword`
    * gradle.properties, e.g: 
      ```
      git.[authGroup].username=someName 
      git.[authGroup].password=somePassword
      ```
    * local.properties
    * ~/.gradle/gradle.properties
    * environment variables, in uppercase and with `_` instead of `.`, e.g. `GIT_GITHUB_USERNAME`

  If `defaultAuthGroup` is provided in `settings.gradle` then it will be used for all repos
  unless `authGroup` is explicitly set.

### Examples ###

```
gitDependencies {
    implementation 'git@github.com/xentelar/finite-state-machine'

    api 'https://github.com/xentelar/easy-rules-core.git', {
        name 'rules-core'
        tag '4.4.0'
        projectPath '/library'
    }
}
```
