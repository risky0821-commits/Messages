plugins { id("com.android.application") version "9.3.1" }
android {
    namespace = "org.fossify.sama.preview"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.fossify.sama.preview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}
dependencies { implementation("io.github.tribalfs:oneui-design:0.9.19+oneui8") }
