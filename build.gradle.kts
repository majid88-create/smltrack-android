plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // KSP versi harus cocok dengan Kotlin 1.9.24 (format: <versi-kotlin>-<versi-ksp>)
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
