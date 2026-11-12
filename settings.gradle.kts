pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // repo1.maven.org / repo.maven.apache.org sering 429 dari sandbox — mirror dulu.
        maven("https://maven.aliyun.com/repository/central") { name = "aliyunCentral" }
        maven("https://maven.aliyun.com/repository/google") { name = "aliyunGoogle" }
        maven("https://repo.maven.apache.org/maven2") { name = "MavenCentralApache" }
        google()
        mavenCentral()
        maven("https://jitpack.io") { name = "jitpack" } // libsu 6.0.0
    }
}
rootProject.name = "ThorAgentMobile"
include(":app")
