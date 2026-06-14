package com.peoplehub.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Disciplined, architectural shapes: crisp small radii for structural elements, a slightly softer
 * radius for cards. Pill shapes (tags) are applied locally with `CircleShape` where needed.
 */
internal val PeopleHubShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
