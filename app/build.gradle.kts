import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("androidx.baselineprofile") }
val nextTvSigningFile = rootProject.file(".signing/signing.properties")
val nextTvSigning = Properties().apply { if(nextTvSigningFile.exists())nextTvSigningFile.inputStream().use { load(it) } }
val nextTvVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val baseVersionName = nextTvVersion.getProperty("versionName")
require(baseVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "versionName 必须使用 主版本.次版本.修订号 格式。"
}
val releaseNumber = nextTvVersion.getProperty("releaseNumber").toInt()
// Each releaseNumber owns a 100,000-code block. Alpha builds use +1..+99,998 and
// the formal build reserves +99,999, so a formal APK always upgrades its own Alpha,
// while the next releaseNumber lifts Alpha above the previously published formal APK.
val versionCodeRange = 100_000
val formalVersionOffset = versionCodeRange - 1
require(releaseNumber in 1..((2_100_000_000 - formalVersionOffset) / versionCodeRange))
val alphaBuild = providers.gradleProperty("nextTvAlphaBuild").orNull?.let {
    val buildNumber = it.toIntOrNull()
    require(buildNumber != null && buildNumber in 1 until (versionCodeRange - 1)) {
        "nextTvAlphaBuild 必须在 1 到 ${versionCodeRange - 2} 之间。"
    }
    buildNumber
}
// Optional channel prefix for non-main builds, e.g. dev -> "dev-1.4.1-Alpha.7".
// The prefix is part of versionName so the settings screen shows the branch too.
val nextTvChannel = providers.gradleProperty("nextTvChannel").orNull?.trim()?.lowercase().orEmpty()
require(nextTvChannel.isEmpty() || nextTvChannel.matches(Regex("[a-z0-9][a-z0-9-]{0,31}"))) {
    "nextTvChannel 只能包含小写字母、数字和连字符，最长 32 个字符。"
}
val nextTvChannelPrefix = if (nextTvChannel.isEmpty()) "" else "$nextTvChannel-"
val nextTvVersionName = nextTvChannelPrefix + (alphaBuild?.let { "$baseVersionName-Alpha.$it" } ?: baseVersionName)
val nextTvVersionCode = alphaBuild?.let { releaseNumber * versionCodeRange + it }
    ?: (releaseNumber * versionCodeRange + formalVersionOffset)
val verifyReleaseVersion by tasks.registering {
    group = "verification"
    description = "Checks that the candidate uses the next formal release number."
    inputs.file(rootProject.file("version.properties"))
    doLast {
        val previous = nextTvVersion.getProperty("lastPublishedReleaseNumber").toInt()
        check(previous in 0 until releaseNumber && releaseNumber == previous + 1) {
            "releaseNumber 必须恰好比上次正式发布的 lastPublishedReleaseNumber 大 1。"
        }
    }
}
tasks.register("packageNextTvRelease") {
    group = "build"
    description = "Verifies the candidate version and packages the release APK."
    dependsOn(verifyReleaseVersion, "assembleRelease")
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    mustRunAfter(verifyReleaseVersion)
}
android {
 signingConfigs {
  create("Next TV") {
   if(nextTvSigningFile.exists()) {
    storeFile = rootProject.file(nextTvSigning.getProperty("storeFile"))
    storePassword = nextTvSigning.getProperty("storePassword")
    keyAlias = nextTvSigning.getProperty("keyAlias")
    keyPassword = nextTvSigning.getProperty("keyPassword")
    storeType = "PKCS12"
   }
  }
 }
 namespace = "com.nexttv"
 compileSdk = 35
 defaultConfig { testInstrumentationRunner = providers.gradleProperty("perfRunner").orNull ?: "com.nexttv.AuditFixInstrumentation"; applicationId = "com.nexttv"; minSdk = 23; targetSdk = 35; versionCode = nextTvVersionCode; versionName = nextTvVersionName }
 compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 buildTypes {
  getByName("debug") { if(nextTvSigningFile.exists())signingConfig=signingConfigs.getByName("Next TV") }
  getByName("release") {
   isMinifyEnabled = true
   isShrinkResources = true
   if(nextTvSigningFile.exists())signingConfig = signingConfigs.getByName("Next TV")
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
 }
}
baselineProfile { automaticGenerationDuringBuild = false }
dependencies {
 add("baselineProfile",project(":baselineprofile"))
 implementation("androidx.tv:tv-material:1.1.0")
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
 implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
 implementation("com.google.zxing:core:3.5.3")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 implementation(platform("androidx.compose:compose-bom:2026.02.00"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.media3:media3-exoplayer:1.9.4")
 implementation("androidx.media3:media3-exoplayer-hls:1.9.4")
 implementation("androidx.media3:media3-ui-compose:1.9.4")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
