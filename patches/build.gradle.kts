group = "app.dualvot.yandex.addon"

patches {
    about {
        name = "Dual VoT Yandex Add-on"
        description = "Development-only Voice Over Translation (Yandex) add-on compatible with Morphe Patches API v1"
        source = "https://github.com/sashade8-ship-it/dual-vot-yandex-addon"
        author = "Dual VoT contributors; preserves MarcaDian, Jav1x, and anddea credits"
        contact = "https://github.com/sashade8-ship-it/dual-vot-yandex-addon/issues"
        website = "https://github.com/sashade8-ship-it/dual-vot-yandex-addon"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.register("patchListGeneratorClasspath")

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)

    patchListGeneratorClasspath(libs.gson)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath.get()
        mainClass.set("app.morphe.util.PatchListGeneratorKt")

        // The library generator has a historical semantic-release note baked
        // into its JSON.  This independent add-on deliberately has no release
        // automation, so normalize only that generated notice as part of the
        // generation task rather than leaving stale release instructions.
        doLast {
            val patchList = rootProject.file("patches-list.json")
            val legacyNote = "Do NOT manually edit this file. This file is automatically updated when semantic release (release.yml) runs. Manually editing this file can break your releases and break third party tools that use this file."
            val developmentNote = "Do NOT manually edit this file. Regenerate it with ./gradlew generatePatchesList before validating a development artifact."
            if (patchList.isFile) {
                patchList.writeText(patchList.readText().replace(legacyNote, developmentNote))
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
