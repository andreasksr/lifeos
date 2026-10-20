// Reines Kotlin/JVM – keine Android-Abhängigkeit. Läuft auf jeder JVM und ist schnell testbar.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    testImplementation(libs.junit)
}
