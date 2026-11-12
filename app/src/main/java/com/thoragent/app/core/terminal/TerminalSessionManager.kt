package com.thoragent.app.core.terminal

import com.thoragent.app.core.root.RootManager
import com.topjohnwu.superuser.Shell

/** Pengelola sesi terminal (multi-sesi, mode root/non-root). */
class TerminalSessionManager(private val rootManager: RootManager) {

    private val sessions = linkedMapOf<Long, TerminalSession>()
    private val shells = mutableListOf<Shell>()

    @Synchronized
    fun create(root: Boolean, initialCwd: String): TerminalSession? {
        val shell = rootManager.buildShell(root) ?: return null
        shells.add(shell)
        val s = TerminalSession(TerminalSession.nextId(), root, shell, initialCwd)
        sessions[s.id] = s
        return s
    }

    @Synchronized
    fun get(id: Long): TerminalSession? = sessions[id]

    @Synchronized
    fun all(): List<TerminalSession> = sessions.values.toList()

    @Synchronized
    fun close(id: Long) {
        sessions.remove(id)?.close()
    }

    @Synchronized
    fun shutdown() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        shells.clear()
    }
}
