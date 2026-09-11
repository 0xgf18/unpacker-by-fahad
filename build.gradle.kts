plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "com.dpt"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    // keep bytecode portable across the JDK used to run Gradle
    // (JDK 17 on Windows, JDK 21 on Termux from termux-build-apps)
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

application {
    mainClass.set("com.dpt.unpack.MainKt")
}