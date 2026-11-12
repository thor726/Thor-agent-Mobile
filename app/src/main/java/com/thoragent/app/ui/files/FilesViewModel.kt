package com.thoragent.app.ui.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileRow(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val path: String, // path java.io untuk exec; DocumentFile untuk SAF
    val documentUri: Uri? = null
)

data class FilesUiState(
    val allFilesGranted: Boolean = false,
    val safUri: Uri? = null,
    val cwd: String = "/sdcard",
    val dirTitle: String = "/sdcard",
    val entries: List<FileRow> = emptyList(),
    val error: String? = null,
    val preview: Pair<String, String>? = null // nama -> isi
)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val rt = ThorRuntime.runtime(app)

    private val _ui = MutableStateFlow(FilesUiState())
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()

    val cwdSetting = rt.settings.cwd.stateIn(viewModelScope, SharingStarted.Eagerly, "/sdcard")

    fun refreshStatus() {
        viewModelScope.launch {
            val granted = if (android.os.Build.VERSION.SDK_INT >= 30)
                Environment.isExternalStorageManager() else true
            _ui.value = _ui.value.copy(allFilesGranted = granted, cwd = cwdSetting.value)
            browse()
        }
    }

    /**
     * Intent ke layar All-Files-Access untuk PAKET INI.
     * PERBAIKAN: pakai packageName runtime — dulu hardcode
     * "com.thoragent.app.debug" (paket build debug), jadi di APK release
     * layar izin terbuka untuk paket yang salah dan status izin tak
     * pernah berubah meski user sudah menekan izinkan.
     */
    fun requestAllFilesIntent(): Intent? =
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${getApplication<Application>().packageName}")
            )
        } else null

    /** Ambil izin persist untuk SAF tree URI. */
    fun attachSafTree(uri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            rt.settings.setSafTreeUri(uri.toString())
            _ui.value = _ui.value.copy(safUri = uri)
            browseSaf(uri)
        }
    }

    /** Set CWD untuk terminal/shell tool dari direktori yang sedang dibuka. */
    fun setCurrentDirAsCwd() {
        viewModelScope.launch {
            rt.settings.setCwd(_ui.value.dirTitle)
            _ui.value = _ui.value.copy(cwd = _ui.value.dirTitle)
        }
    }

    /** Naik satu tingkat. */
    fun up() {
        val cur = _ui.value.dirTitle
        if (cur == "/sdcard" || cur == "/") {
            browseRoot()
            return
        }
        val parent = File(cur).parent ?: "/sdcard"
        browseDir(parent)
    }

    fun browseRoot() {
        val all = listOf(
            FileRow("Al penyimpanan internal", true, 0, "/sdcard"),
            FileRow("Download", true, 0, "/sdcard/Download"),
            FileRow("DCIM", true, 0, "/sdcard/DCIM"),
            FileRow("Documents", true, 0, "/sdcard/Documents"),
            FileRow("Android/data", true, 0, "/sdcard/Android/data")
        )
        _ui.value = _ui.value.copy(dirTitle = "/", entries = all)
    }

    fun browseDir(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(path)
            if (!dir.isDirectory) {
                _ui.value = _ui.value.copy(error = "tidak bisa membuka $path")
                return@launch
            }
            val list = dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.take(500)
                ?.map {
                    FileRow(it.name, it.isDirectory, it.length(), it.absolutePath)
                } ?: emptyList()
            withContext(Dispatchers.Main) {
                _ui.value = _ui.value.copy(dirTitle = dir.absolutePath, entries = list, error = if (list.isEmpty()) "kosong / tidak ada izin" else null)
            }
        }
    }

    fun browseSaf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromTreeUri(getApplication(), uri)
                if (doc == null) {
                    withContext(Dispatchers.Main) { _ui.value = _ui.value.copy(error = "SAF tree tidak valid") }
                    return@launch
                }
                val list = doc.listFiles()
                    .sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
                    .take(500)
                    .map {
                        FileRow(
                            name = it.name ?: "(tanpa nama)",
                            isDir = it.isDirectory,
                            size = it.length(),
                            path = uri.toString() + "/" + (it.name ?: ""),
                            documentUri = it.uri
                        )
                    }
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(dirTitle = "${doc.name ?: "SAF"} (SAF)", entries = list)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _ui.value = _ui.value.copy(error = "SAF gagal: ${e.message}") }
            }
        }
    }

    fun browse() {
        val cur = _ui.value.dirTitle
        if (_ui.value.safUri != null && cur.contains("SAF")) {
            browseSaf(_ui.value.safUri!!)
        } else {
            browseDir(cur.ifBlank { "/sdcard" })
        }
    }

    /** Buka baris: masuk direktori / pratinjau berkas teks. */
    fun open(row: FileRow) {
        if (row.isDir) {
            if (row.documentUri != null) browseSafDir(row)
            else browseDir(row.path)
        } else {
            previewText(row)
        }
    }

    private fun browseSafDir(row: FileRow) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromSingleUri(getApplication(), row.documentUri!!)
                val list = doc?.listFiles()
                    ?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
                    ?.take(500)
                    ?.map {
                        FileRow(it.name ?: "(tanpa nama)", it.isDirectory, it.length(), it.uri.toString(), it.uri)
                    } ?: emptyList()
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(dirTitle = "${row.name} (SAF)", entries = list)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _ui.value = _ui.value.copy(error = "SAF gagal: ${e.message}") }
            }
        }
    }

    private fun previewText(row: FileRow) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text: String = if (row.documentUri != null) {
                    getApplication<Application>().contentResolver.openInputStream(row.documentUri)?.use {
                        it.bufferedReader().readText()
                    } ?: throw IllegalStateException("stream null")
                } else {
                    File(row.path).takeIf { it.length() < 2_000_000 }?.readText() ?: throw IllegalStateException("terlalu besar")
                }
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(preview = row.name to text.take(60_000))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _ui.value = _ui.value.copy(error = "gagal membaca: ${e.message}") }
            }
        }
    }

    fun closePreview() {
        _ui.value = _ui.value.copy(preview = null)
    }
}
