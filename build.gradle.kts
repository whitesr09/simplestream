buildscript {
    repositories {
        maven { url = uri("/root/maven/localMvnRepository") }
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

