package com.shehzade7.filterlab

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Pink = Color(0xFFFF6B8C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Color.Black)) {
                FilterLabScreen()
            }
        }
    }
}

@Composable
fun FilterLabScreen(vm: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::load)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::load)
    }
    val openGallery = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openCamera = {
        val uri = newCameraUri(context)
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(vm, openGallery, openCamera)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight
                when {
                    vm.original == null -> EmptyState(openGallery, openCamera)
                    wide -> Row(Modifier.fillMaxSize()) {
                        PhotoPreview(vm, Modifier.weight(1f).fillMaxHeight())
                        Controls(vm, wide = true, modifier = Modifier.width(380.dp).fillMaxHeight())
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        PhotoPreview(vm, Modifier.weight(1f).fillMaxWidth())
                        Controls(vm, wide = false, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(vm: EditorViewModel, onGallery: () -> Unit, onCamera: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FilterLab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onGallery) { Text("Gallery") }
        TextButton(onClick = onCamera) { Text("Camera") }
        Button(onClick = vm::save, enabled = vm.original != null && !vm.saving) {
            Text(if (vm.saving) "Saving…" else "Save")
        }
    }
}

@Composable
private fun EmptyState(onGallery: () -> Unit, onCamera: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", color = Pink, style = MaterialTheme.typography.displayMedium)
        Text("Pick a photo to start", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onGallery) { Text("Gallery") }
            OutlinedButton(onClick = onCamera) { Text("Camera") }
        }
    }
}

@Composable
private fun PhotoPreview(vm: EditorViewModel, modifier: Modifier) {
    var showOriginal by remember { mutableStateOf(false) }
    val bmp = if (showOriginal) vm.original else vm.preview
    Box(
        modifier
            .padding(12.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    showOriginal = true
                    tryAwaitRelease()
                    showOriginal = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            val img = remember(it) { it.asImageBitmap() }
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (showOriginal) {
            Text(
                "Original",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Controls(vm: EditorViewModel, wide: Boolean, modifier: Modifier) {
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vm.effect != Effect.Original) {
            LabeledSlider("Strength", vm.amount, 0f..1f, vm::updateAmount)
        }
        if (showAdjust) {
            val a = vm.adjustments
            LabeledSlider("Brightness", a.brightness, -0.3f..0.3f) { v -> vm.updateAdjustments { it.copy(brightness = v) } }
            LabeledSlider("Contrast", a.contrast, 0.5f..1.5f) { v -> vm.updateAdjustments { it.copy(contrast = v) } }
            LabeledSlider("Saturation", a.saturation, 0f..2f) { v -> vm.updateAdjustments { it.copy(saturation = v) } }
            LabeledSlider("Warmth", a.warmth, -1f..1f) { v -> vm.updateAdjustments { it.copy(warmth = v) } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = vm.extras.dateStamp,
                onClick = { vm.updateExtras { it.copy(dateStamp = !it.dateStamp) } },
                label = { Text("Date stamp") },
            )
            FilterChip(
                selected = vm.extras.frame,
                onClick = { vm.updateExtras { it.copy(frame = !it.frame) } },
                label = { Text("Frame") },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAdjust = !showAdjust }) { Text(if (showAdjust) "Hide adjust" else "Adjust") }
            Spacer(Modifier.weight(1f))
            Text("Hold photo to compare", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::reset) { Text("Reset") }
        }

        if (wide) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(Effect.entries) { e ->
                    EffectCell(e, vm.thumbs[e], vm.effect == e) { vm.selectEffect(e) }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                items(Effect.entries) { e ->
                    EffectCell(e, vm.thumbs[e], vm.effect == e) { vm.selectEffect(e) }
                }
            }
        }
    }
}

@Composable
private fun EffectCell(effect: Effect, thumb: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(shape)
                .background(Color.DarkGray)
                .border(2.dp, if (selected) Pink else Color.Transparent, shape)
        ) {
            thumb?.let {
                val img = remember(it) { it.asImageBitmap() }
                Image(img, contentDescription = effect.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(
            effect.label,
            color = if (selected) Pink else Color.LightGray,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
