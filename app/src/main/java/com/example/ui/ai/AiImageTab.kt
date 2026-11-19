package com.example.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.shimmerSkeleton
import com.example.ui.theme.vortexPrimaryGradient
import com.google.firebase.ai.type.AspectRatio

private data class AspectRatioChip(val ratio: AspectRatio, val label: String)

private val ASPECT_RATIO_OPTIONS = listOf(
    AspectRatioChip(AspectRatio.SQUARE_1x1, "1:1"),
    AspectRatioChip(AspectRatio.LANDSCAPE_16x9, "16:9"),
    AspectRatioChip(AspectRatio.PORTRAIT_9x16, "9:16"),
    AspectRatioChip(AspectRatio.LANDSCAPE_4x3, "4:3"),
    AspectRatioChip(AspectRatio.PORTRAIT_3x4, "3:4")
)

@Composable
fun AiImageTab(viewModel: AiStudioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalVortexColors.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadSourceBitmapFromUri(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Image Studio (gemini-nano-banana-2.1)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Create original artwork from text or pick a photo to transform and edit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.imagePrompt,
                        onValueChange = viewModel::onImagePromptChange,
                        label = {
                            Text(
                                if (state.sourceEditBitmap != null) {
                                    "Describe edits to apply to your photo"
                                } else {
                                    "Describe the image to create"
                                }
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = extra.glassBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_prompt_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ASPECT_RATIO_OPTIONS) { option ->
                            FilterChip(
                                selected = state.selectedAspectRatio == option.ratio,
                                onClick = { viewModel.selectImageAspectRatio(option.ratio) },
                                label = { Text(option.label) },
                                modifier = Modifier.testTag("aspect_ratio_${option.label}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_edit_image_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.sourceEditBitmap != null) "Change Photo" else "Edit Photo")
                        }

                        Button(
                            onClick = viewModel::generateOrEditImage,
                            enabled = state.imageState !is AsyncActionState.Loading,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(48.dp)
                                .testTag("generate_image_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(vortexPrimaryGradient()),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.sourceEditBitmap != null) "Edit Image" else "Generate",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    val sourceBmp = state.sourceEditBitmap
                    if (sourceBmp != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = sourceBmp.asImageBitmap(),
                                contentDescription = "Source photo to edit",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Source photo attached for editing",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::clearSourceEditBitmap) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove source photo")
                            }
                        }
                    }
                }
            }
        }

        item {
            when (val res = state.imageState) {
                AsyncActionState.Idle -> Unit
                AsyncActionState.Loading -> {
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Generating high-res image (may take 30–60s)…",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shimmerSkeleton(16.dp)
                            )
                        }
                    }
                }
                is AsyncActionState.Error -> {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is AsyncActionState.Success -> {
                    GlassCard(glowBorder = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                bitmap = res.data.asImageBitmap(),
                                contentDescription = "Generated AI image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            Button(
                                onClick = { viewModel.saveGeneratedImageToLibrary(res.data) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_generated_image_button")
                            ) {
                                Icon(Icons.Rounded.DownloadDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save to Vortex Library")
                            }
                            if (state.savedImageMessage != null) {
                                Text(
                                    text = state.savedImageMessage.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extra.success
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
