package com.minicdesign.buildlogic

import com.github.tomakehurst.wiremock.WireMockServer
import java.util.concurrent.ConcurrentHashMap

object WiremockRegistry {
    private val servers = ConcurrentHashMap<String, WireMockServer>()

    fun register(projectPath: String, server: WireMockServer) {
        servers[projectPath]?.stop()
        servers[projectPath] = server
    }

    fun stop(projectPath: String) {
        servers.remove(projectPath)?.stop()
    }

    fun stopAll() {
        servers.values.forEach { it.stop() }
        servers.clear()
    }
}
