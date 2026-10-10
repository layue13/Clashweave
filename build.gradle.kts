
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
