package com.thoragent.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "thor_settings")

/** Pengaturan aplikasi (DataStore Preferences). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val cwd = stringPreferencesKey("cwd")
        val confirmDangerous = booleanPreferencesKey("confirm_dangerous")
        val safTreeUri = stringPreferencesKey("saf_tree_uri")
        val useRoot = booleanPreferencesKey("use_root")
        val foregroundService = booleanPreferencesKey("foreground_service")
        // v0.3.0 — sinkron fitur repo sumber
        val sessionModel = stringPreferencesKey("session_model")
        val goal = stringPreferencesKey("standing_goal")
        val yolo = booleanPreferencesKey("yolo_mode")
        val reasoningLevel = stringPreferencesKey("reasoning_level")
        // v0.4.0 — UI/UX interaktif + dukungan penuh semua slash command
        val timestamps = booleanPreferencesKey("ui_timestamps")
        val focusMode = booleanPreferencesKey("ui_focus_mode")
        val verboseMode = stringPreferencesKey("ui_verbose_mode")
        val statusbar = booleanPreferencesKey("ui_statusbar")
        val battery = booleanPreferencesKey("ui_battery")
        val skin = stringPreferencesKey("ui_skin")
        val personality = stringPreferencesKey("agent_personality")
        val voice = booleanPreferencesKey("voice_tts")
        val pet = stringPreferencesKey("pet_name")
        val homeSession = longPreferencesKey("home_session")
        val subgoals = stringPreferencesKey("subgoals")
        val paused = booleanPreferencesKey("paused_global")
    }

    val cwd: Flow<String> = context.dataStore.data.map { it[Keys.cwd] ?: "/sdcard" }
    val confirmDangerous: Flow<Boolean> = context.dataStore.data.map { it[Keys.confirmDangerous] ?: true }
    val safTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.safTreeUri] }

    /** Gunakan root hanya bila eksekusi filesystem butuh (bawaan: ya bila tersedia). */
    val useRoot: Flow<Boolean> = context.dataStore.data.map { it[Keys.useRoot] ?: true }
    val foregroundService: Flow<Boolean> = context.dataStore.data.map { it[Keys.foregroundService] ?: false }

    /** Model khusus sesi (override per sesi — meniru /model session-scoped repo sumber). */
    val sessionModel: Flow<String> = context.dataStore.data.map { it[Keys.sessionModel] ?: "" }

    /** Tujuan berdiri /goal repo sumber (goal_command.py). */
    val goal: Flow<String> = context.dataStore.data.map { it[Keys.goal] ?: "" }

    /** YOLO mode — lewati semua persetujuan perintah berbahaya (config yolo repo). */
    val yolo: Flow<Boolean> = context.dataStore.data.map { it[Keys.yolo] ?: false }

    /** Level reasoning (--reasoning repo sumber): none|minimal|low|medium|high. */
    val reasoningLevel: Flow<String> = context.dataStore.data.map { it[Keys.reasoningLevel] ?: "" }

    // ==== v0.4.0: preferensi UI/UX (agar terasa hidup & nyaman) ====

    /** Tampilkan timestamp [HH:MM] pada gelembung pesan (/timestamps). */
    val timestamps: Flow<Boolean> = context.dataStore.data.map { it[Keys.timestamps] ?: true }

    /** Focus view — hanya tampilkan prompt & balasan (/focus). */
    val focusMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.focusMode] ?: false }

    /** Tampilan progres tool (/verbose): off|new|all. */
    val verboseMode: Flow<String> = context.dataStore.data.map { it[Keys.verboseMode] ?: "new" }

    /** Status bar mini di bawah top bar (/statusbar). */
    val statusbar: Flow<Boolean> = context.dataStore.data.map { it[Keys.statusbar] ?: true }

    /** Indikator baterai di status bar (/battery). */
    val battery: Flow<Boolean> = context.dataStore.data.map { it[Keys.battery] ?: false }

    /** Skin/tema (/skin): thor|midnight|ember|mono. */
    val skin: Flow<String> = context.dataStore.data.map { it[Keys.skin] ?: "thor" }

    /** Personality agent (/personality): default|precise|ramah|guru|senior|singkat. */
    val personality: Flow<String> = context.dataStore.data.map { it[Keys.personality] ?: "default" }

    /** Mode suara — TTS on-device membacakan balasan (/voice). */
    val voice: Flow<Boolean> = context.dataStore.data.map { it[Keys.voice] ?: false }

    /** Nama pet maskot ("" = nonaktif) — /pet & /hatch. */
    val pet: Flow<String> = context.dataStore.data.map { it[Keys.pet] ?: "" }

    /** Sesi “rumah” yang dibuka saat aplikasi jalan (/sethome). */
    val homeSession: Flow<Long> = context.dataStore.data.map { it[Keys.homeSession] ?: -1L }

    /** Subgoal tambahan pada goal aktif (/subgoal) — dipisah baris. */
    val subgoals: Flow<String> = context.dataStore.data.map { it[Keys.subgoals] ?: "" }

    /** Jeda global pekerjaan baru (/pause) — emergency stop. */
    val paused: Flow<Boolean> = context.dataStore.data.map { it[Keys.paused] ?: false }

    suspend fun setCwd(v: String) = context.dataStore.edit { it[Keys.cwd] = v }
    suspend fun setConfirmDangerous(v: Boolean) = context.dataStore.edit { it[Keys.confirmDangerous] = v }
    suspend fun setSafTreeUri(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(Keys.safTreeUri) else it[Keys.safTreeUri] = v
    }
    suspend fun setUseRoot(v: Boolean) = context.dataStore.edit { it[Keys.useRoot] = v }
    suspend fun setForegroundService(v: Boolean) = context.dataStore.edit { it[Keys.foregroundService] = v }

    suspend fun setSessionModel(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.sessionModel) else it[Keys.sessionModel] = v
    }

    suspend fun setGoal(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.goal) else it[Keys.goal] = v
    }

    suspend fun setYolo(v: Boolean) = context.dataStore.edit { it[Keys.yolo] = v }

    suspend fun setReasoningLevel(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.reasoningLevel) else it[Keys.reasoningLevel] = v
    }

    suspend fun setTimestamps(v: Boolean) = context.dataStore.edit { it[Keys.timestamps] = v }
    suspend fun setFocusMode(v: Boolean) = context.dataStore.edit { it[Keys.focusMode] = v }
    suspend fun setVerboseMode(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.verboseMode) else it[Keys.verboseMode] = v
    }
    suspend fun setStatusbar(v: Boolean) = context.dataStore.edit { it[Keys.statusbar] = v }
    suspend fun setBattery(v: Boolean) = context.dataStore.edit { it[Keys.battery] = v }
    suspend fun setSkin(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.skin) else it[Keys.skin] = v
    }
    suspend fun setPersonality(v: String) = context.dataStore.edit {
        if (v.isBlank() || v == "default") it.remove(Keys.personality) else it[Keys.personality] = v
    }
    suspend fun setVoice(v: Boolean) = context.dataStore.edit { it[Keys.voice] = v }
    suspend fun setPet(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.pet) else it[Keys.pet] = v
    }
    suspend fun setHomeSession(v: Long) = context.dataStore.edit {
        if (v < 0) it.remove(Keys.homeSession) else it[Keys.homeSession] = v
    }
    suspend fun setSubgoals(v: String) = context.dataStore.edit {
        if (v.isBlank()) it.remove(Keys.subgoals) else it[Keys.subgoals] = v
    }
    suspend fun setPaused(v: Boolean) = context.dataStore.edit { it[Keys.paused] = v }

    companion object {
        const val REPO_URL = "https://github.com/dwisetyabudi15581/Thor-agent.git"
    }
}
