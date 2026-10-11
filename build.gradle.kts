
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}

// Client-only camera hook; the loading plugin and transformer resolve no client types on a server.
tasks.withType<Jar>().configureEach {
    if (name == "jar") manifest.attributes("FMLCorePlugin" to "com.layue13.clashweave.coremod.CameraLoadingPlugin", "FMLCorePluginContainsFMLMod" to "true")
}
tasks.withType<JavaExec>().configureEach {
    if (name == "runClient") jvmArgs("-Dfml.coreMods.load=com.layue13.clashweave.coremod.CameraLoadingPlugin")
}
