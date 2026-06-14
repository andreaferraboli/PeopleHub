package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File

/**
 * A person's avatar: their photo loaded from internal storage via Coil, or a gold-bordered
 * monogram of their [initials] when no photo is set.
 *
 * @param shape defaults to a sharp-edged "fine art" rounded square; pass a circular shape for list
 * rows where a circle reads better.
 */
@Composable
fun PersonAvatar(
    initials: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    val borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoPath.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = (size.value * 0.34f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            )
        }
    }
}
