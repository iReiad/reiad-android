/* The app is two modules, and the split is not taste.

   `core` is plain Kotlin: the API models, the block parser, the
   storage keys and the sync arithmetic. None of it imports
   Android, so all of it compiles and tests on a JVM with no SDK,
   which is where the risky half of this port lives. `app` is
   Compose and everything that needs a handset.

   ANDROID.md called for one module until build times argued
   otherwise. This is a better argument than build times: the
   sync engine and the body parser are the two pieces that have
   to be right, and this way they are provable anywhere. */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "reiad-android"
include(":core")
