plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "com.nureal.ide"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
}

kotlin {
    // JDK 24 (o que roda o Gradle aqui) ainda nao e suportado como TARGET pelo
    // compilador Kotlin ("falling back to JVM_22"), o que deixava compileJava
    // (auto-adicionado pelo plugin kotlin("jvm")) e compileKotlin em versoes
    // de bytecode inconsistentes. Fixa os dois no MESMO toolchain (JDK 21, ja
    // instalado nesta maquina) em vez de depender do JAVA_HOME de quem builda.
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
}

compose.desktop {
    application {
        mainClass = "com.nureal.ide.chatui.SmokeTestKt"
    }
}
