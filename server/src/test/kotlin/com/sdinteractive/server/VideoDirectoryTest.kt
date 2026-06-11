package com.sdinteractive.server

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoDirectoryTest {
    @Test
    fun `default video directory falls back to workspace root when server runs from module directory`() {
        val workspace = createTempDirectory("sdinteractive-workspace")
        val serverDir = workspace.resolve("server").createDirectories()
        val rootVideo = workspace.resolve("video").createDirectories()

        assertEquals(
            rootVideo.toAbsolutePath().normalize(),
            resolveVideoDirectory(
                configured = null,
                envVideoDir = null,
                workingDirectory = serverDir
            )
        )
    }

    @Test
    fun `configured video directory still wins over auto discovery`() {
        val workspace = createTempDirectory("sdinteractive-workspace")
        val serverDir = workspace.resolve("server").createDirectories()
        val configuredVideo = workspace.resolve("custom-video").createDirectories()
        workspace.resolve("video").createDirectories()

        assertEquals(
            configuredVideo.toAbsolutePath().normalize(),
            resolveVideoDirectory(
                configured = configuredVideo.toString(),
                envVideoDir = null,
                workingDirectory = serverDir
            )
        )
    }
}
