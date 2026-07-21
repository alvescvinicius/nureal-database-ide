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

    // Classes do app principal (core.ai.*, ChatWindowPreferences, SqlEditorPane,
    // SqlFormatter etc.) — o jar "original" (thin, sem shading) que "mvn package"
    // gera ao lado do fat jar. PRECISA rodar "mvn package" no modulo raiz antes
    // de buildar este modulo (ou depois de qualquer mudanca no codigo Java que
    // este modulo reusa) — as duas builds nao estao encadeadas automaticamente.
    implementation(files("../target/original-nureal-database-ide.jar"))

    // So o que os TIPOS reusados acima expoem nas suas assinaturas (RSyntaxTextArea
    // no styleAsReadOnlySql, FlatLaf.isLafDark()) — mesmas versoes do pom.xml.
    // "autocomplete" e necessaria mesmo sem usar autocomplete aqui: carregar a
    // classe SqlEditorPane (so pra chamar o static styleAsReadOnlySql) exige
    // resolver CompletionProvider, que aparece no construtor dela.
    implementation("com.fifesoft:rsyntaxtextarea:3.5.1")
    implementation("com.fifesoft:autocomplete:3.3.1")
    implementation("com.formdev:flatlaf:3.5.4")
}

compose.desktop {
    application {
        // Harness descartavel (Agent fake, sem MainWindow/provider real) so
        // pra validar o port visualmente via "./gradlew run" — ver Harness.kt.
        mainClass = "com.nureal.ide.ui.ai.compose.HarnessKt"
    }
}
