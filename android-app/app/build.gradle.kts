import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "me.zayedbinhasan.android_app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "me.zayedbinhasan.android_app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            java.srcDir(file("build/generated/proto/java"))
        }
    }
}

val protoDir = rootProject.projectDir.parentFile.resolve("proto")
val outDirProvider = layout.buildDirectory.dir("generated/proto/java")

val generateProtoJava by tasks.registering(Exec::class) {
    inputs.dir(protoDir)
    outputs.dir(outDirProvider)

    doFirst {
        val protoFiles = protoDir
            .listFiles { file -> file.isFile && file.extension == "proto" }
            ?.sortedBy { it.name }
            .orEmpty()

        if (protoFiles.isEmpty()) {
            throw GradleException("No .proto files found under ${protoDir.absolutePath}")
        }

        val outDir = outDirProvider.get().asFile
        outDir.mkdirs()

        executable = "protoc"
        args("--proto_path=${protoDir.absolutePath}")
        args("--java_out=${outDir.absolutePath}")
        args(protoFiles.map { it.absolutePath })
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateProtoJava)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.protobuf.java)
    implementation(libs.javax.annotation.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

sqldelight {
    databases {
        register("Database") {
            packageName.set("me.zayedbinhasan.data")
        }
    }
}