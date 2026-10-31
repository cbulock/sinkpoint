import java.util.Properties

// Use source history, never a workflow run number: APK and AAB builds run in
// different repositories and must give the same source commit the same version.
val baseline = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
fun gitVersionOutput(vararg arguments: String): String {
    val output = providers.exec {
        workingDir(rootProject.projectDir)
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }
    check(output.result.get().exitValue == 0) {
        "Cannot determine app version: git ${arguments.joinToString(" ")} failed. " +
            "Build from a Git checkout with full history (actions/checkout fetch-depth: 0)."
    }
    return output.standardOutput.asText.get().trim()
}

check(gitVersionOutput("rev-parse", "--is-shallow-repository") == "false") {
    "Automatic app versioning requires full Git history. Run git fetch --unshallow " +
        "or configure actions/checkout with fetch-depth: 0."
}
val baseCommit = baseline.getProperty("baseCommit")
    ?: error("Missing baseCommit in version.properties")
gitVersionOutput("merge-base", "--is-ancestor", baseCommit, "HEAD")
val commitsSinceBaseline = gitVersionOutput("rev-list", "--count", "$baseCommit..HEAD").toLong()
val baseName = baseline.getProperty("versionName") ?: error("Missing versionName")
check(baseName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "versionName in version.properties must be major.minor.patch"
}
val nameParts = baseName.split('.')
val calculatedVersionCode = baseline.getProperty("versionCode").toLong() + commitsSinceBaseline
check(calculatedVersionCode in 1..2_100_000_000L) { "Calculated versionCode is outside Android's supported range" }
val calculatedVersionName = "${nameParts[0]}.${nameParts[1]}.${nameParts[2].toLong() + commitsSinceBaseline}"
extra["appVersionCode"] = calculatedVersionCode.toInt()
extra["appVersionName"] = calculatedVersionName

tasks.register("printAppVersion") {
    group = "help"
    description = "Print the source-derived version shared by APK and AAB builds."
    doLast {
        println("versionName=$calculatedVersionName")
        println("versionCode=$calculatedVersionCode")
    }
}
