package com.shehzade7.filterlab

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    var original by mutableStateOf<Bitmap?>(null); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var thumbs by mutableStateOf<Map<Effect, Bitmap>>(emptyMap()); private set
    var effect by mutableStateOf(Effect.Original); private set
    var amount by mutableFloatStateOf(1f); private set
    var adjustments by mutableStateOf(Adjustments()); private set
    var extras by mutableStateOf(Extras()); private set
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    private var sourceUri: Uri? = null
    private var renderJob: Job? = null

    fun load(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadBitmap(ctx, uri, 1600) to loadBitmap(ctx, uri, 200) }.getOrNull()
            }
            if (loaded == null) {
                message = "Couldn't open that photo"
                return@launch
            }
            val (main, small) = loaded
            sourceUri = uri
            original = main
            preview = main
            effect = Effect.Original
            amount = 1f
            adjustments = Adjustments()
            extras = Extras()
            thumbs = emptyMap()
            thumbs = withContext(Dispatchers.Default) {
                Effect.entries.associateWith { Renderer.render(small, it, 1f, Adjustments()) }
            }
        }
    }

    fun selectEffect(e: Effect) { effect = e; render() }
    fun updateAmount(v: Float) { amount = v; render() }
    fun updateAdjustments(f: (Adjustments) -> Adjustments) { adjustments = f(adjustments); render() }
    fun updateExtras(f: (Extras) -> Extras) { extras = f(extras); render() }
    fun clearMessage() { message = null }

    fun reset() {
        effect = Effect.Original
        amount = 1f
        adjustments = Adjustments()
        extras = Extras()
        render()
    }

    private fun render() {
        val src = original ?: return
        val e = effect
        val a = amount
        val adj = adjustments
        val ex = extras
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            preview = withContext(Dispatchers.Default) { Renderer.render(src, e, a, adj, ex) }
        }
    }

    /** Re-renders at full quality and saves to Pictures/FilterLab. */
    fun save() {
        val uri = sourceUri ?: return
        val e = effect
        val a = amount
        val adj = adjustments
        val ex = extras
        saving = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching { saveToGallery(ctx, Renderer.render(loadBitmap(ctx, uri, 3000), e, a, adj, ex)) }
                    .getOrDefault(false)
            }
            saving = false
            message = if (ok) "Saved to Pictures/FilterLab" else "Couldn't save"
        }
    }
}
