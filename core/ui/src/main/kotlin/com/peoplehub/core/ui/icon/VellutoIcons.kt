package com.peoplehub.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline icon set of the Velluto design system: 24dp stroke glyphs with round caps and joins.
 *
 * Every glyph is transcribed from Lucide (lucide-static v1.49.0, https://lucide.dev), released
 * under the ISC license; the license text ships in `assets/licenses/LICENSE-lucide.txt`. The vectors
 * are stroked in black so `Icon` tints them with the current content color, and each one is built
 * lazily on first use.
 */
object VellutoIcons {
    /** Lucide `activity`. */
    val Activity: ImageVector by lazy { lucideIcon("Activity", ActivityPaths) }

    /** Lucide `archive`. */
    val Archive: ImageVector by lazy { lucideIcon("Archive", ArchivePaths) }

    /** Lucide `arrow-left`. */
    val ArrowLeft: ImageVector by lazy { lucideIcon("ArrowLeft", ArrowLeftPaths) }

    /** Lucide `arrow-up-down`. */
    val ArrowUpDown: ImageVector by lazy { lucideIcon("ArrowUpDown", ArrowUpDownPaths) }

    /** Lucide `arrow-up-right`. */
    val ArrowUpRight: ImageVector by lazy { lucideIcon("ArrowUpRight", ArrowUpRightPaths) }

    /** Lucide `bell`. */
    val Bell: ImageVector by lazy { lucideIcon("Bell", BellPaths) }

    /** Lucide `bell-off`. */
    val BellOff: ImageVector by lazy { lucideIcon("BellOff", BellOffPaths) }

    /** Lucide `bell-ring`. */
    val BellRing: ImageVector by lazy { lucideIcon("BellRing", BellRingPaths) }

    /** Lucide `cake`. */
    val Cake: ImageVector by lazy { lucideIcon("Cake", CakePaths) }

    /** Lucide `calendar`. */
    val Calendar: ImageVector by lazy { lucideIcon("Calendar", CalendarPaths) }

    /** Lucide `calendar-days`. */
    val CalendarDays: ImageVector by lazy { lucideIcon("CalendarDays", CalendarDaysPaths) }

    /** Lucide `calendar-plus`. */
    val CalendarPlus: ImageVector by lazy { lucideIcon("CalendarPlus", CalendarPlusPaths) }

    /** Lucide `calendar-range`. */
    val CalendarRange: ImageVector by lazy { lucideIcon("CalendarRange", CalendarRangePaths) }

    /** Lucide `calendar-x`. */
    val CalendarX: ImageVector by lazy { lucideIcon("CalendarX", CalendarXPaths) }

    /** Lucide `camera`. */
    val Camera: ImageVector by lazy { lucideIcon("Camera", CameraPaths) }

    /** Lucide `check`. */
    val Check: ImageVector by lazy { lucideIcon("Check", CheckPaths) }

    /** Lucide `chevron-down`. */
    val ChevronDown: ImageVector by lazy { lucideIcon("ChevronDown", ChevronDownPaths) }

    /** Lucide `chevron-left`. */
    val ChevronLeft: ImageVector by lazy { lucideIcon("ChevronLeft", ChevronLeftPaths) }

    /** Lucide `chevron-right`. */
    val ChevronRight: ImageVector by lazy { lucideIcon("ChevronRight", ChevronRightPaths) }

    /** Lucide `chevron-up`. */
    val ChevronUp: ImageVector by lazy { lucideIcon("ChevronUp", ChevronUpPaths) }

    /** Lucide `circle-alert`. */
    val CircleAlert: ImageVector by lazy { lucideIcon("CircleAlert", CircleAlertPaths) }

    /** Lucide `circle-check`. */
    val CircleCheck: ImageVector by lazy { lucideIcon("CircleCheck", CircleCheckPaths) }

    /** Lucide `circle-help`. */
    val CircleHelp: ImageVector by lazy { lucideIcon("CircleHelp", CircleHelpPaths) }

    /** Lucide `circle-minus`. */
    val CircleMinus: ImageVector by lazy { lucideIcon("CircleMinus", CircleMinusPaths) }

    /** Lucide `clock`. */
    val Clock: ImageVector by lazy { lucideIcon("Clock", ClockPaths) }

    /** Lucide `crop`. */
    val Crop: ImageVector by lazy { lucideIcon("Crop", CropPaths) }

    /** Lucide `download`. */
    val Download: ImageVector by lazy { lucideIcon("Download", DownloadPaths) }

    /** Lucide `eye-off`. */
    val EyeOff: ImageVector by lazy { lucideIcon("EyeOff", EyeOffPaths) }

    /** Lucide `list-filter`. */
    val Filter: ImageVector by lazy { lucideIcon("Filter", FilterPaths) }

    /** Lucide `flask-conical`. */
    val Flask: ImageVector by lazy { lucideIcon("Flask", FlaskPaths) }

    /** Lucide `flower`. */
    val Flower: ImageVector by lazy { lucideIcon("Flower", FlowerPaths) }

    /** Lucide `gift`. */
    val Gift: ImageVector by lazy { lucideIcon("Gift", GiftPaths) }

    /** Lucide `globe`. */
    val Globe: ImageVector by lazy { lucideIcon("Globe", GlobePaths) }

    /** Lucide `heart`. */
    val Heart: ImageVector by lazy { lucideIcon("Heart", HeartPaths) }

    /** Lucide `house`. */
    val House: ImageVector by lazy { lucideIcon("House", HousePaths) }

    /** Lucide `image`. */
    val Image: ImageVector by lazy { lucideIcon("Image", ImagePaths) }

    /** Lucide `image-plus`. */
    val ImagePlus: ImageVector by lazy { lucideIcon("ImagePlus", ImagePlusPaths) }

    /** Lucide `inbox`. */
    val Inbox: ImageVector by lazy { lucideIcon("Inbox", InboxPaths) }

    /** Lucide `info`. */
    val Info: ImageVector by lazy { lucideIcon("Info", InfoPaths) }

    /** Lucide `list`. */
    val List: ImageVector by lazy { lucideIcon("List", ListPaths) }

    /** Lucide `list-checks`. */
    val ListChecks: ImageVector by lazy { lucideIcon("ListChecks", ListChecksPaths) }

    /** Lucide `lock`. */
    val Lock: ImageVector by lazy { lucideIcon("Lock", LockPaths) }

    /** Lucide `map-pin`. */
    val MapPin: ImageVector by lazy { lucideIcon("MapPin", MapPinPaths) }

    /** Lucide `minus`. */
    val Minus: ImageVector by lazy { lucideIcon("Minus", MinusPaths) }

    /** Lucide `ellipsis-vertical`. */
    val MoreVertical: ImageVector by lazy { lucideIcon("MoreVertical", MoreVerticalPaths) }

    /** Lucide `party-popper`. */
    val PartyPopper: ImageVector by lazy { lucideIcon("PartyPopper", PartyPopperPaths) }

    /** Lucide `pencil`. */
    val Pencil: ImageVector by lazy { lucideIcon("Pencil", PencilPaths) }

    /** Lucide `pin`. */
    val Pin: ImageVector by lazy { lucideIcon("Pin", PinPaths) }

    /** Lucide `pin-off`. */
    val PinOff: ImageVector by lazy { lucideIcon("PinOff", PinOffPaths) }

    /** Lucide `plus`. */
    val Plus: ImageVector by lazy { lucideIcon("Plus", PlusPaths) }

    /** Lucide `refresh-cw`. */
    val RefreshCw: ImageVector by lazy { lucideIcon("RefreshCw", RefreshCwPaths) }

    /** Lucide `search`. */
    val Search: ImageVector by lazy { lucideIcon("Search", SearchPaths) }

    /** Lucide `settings`. */
    val Settings: ImageVector by lazy { lucideIcon("Settings", SettingsPaths) }

    /** Lucide `share-2`. */
    val Share: ImageVector by lazy { lucideIcon("Share", SharePaths) }

    /** Lucide `sparkle`. */
    val Sparkle: ImageVector by lazy { lucideIcon("Sparkle", SparklePaths) }

    /** Lucide `sparkles`. */
    val Sparkles: ImageVector by lazy { lucideIcon("Sparkles", SparklesPaths) }

    /** Lucide `sprout`. */
    val Sprout: ImageVector by lazy { lucideIcon("Sprout", SproutPaths) }

    /** Lucide `star`. */
    val Star: ImageVector by lazy { lucideIcon("Star", StarPaths) }

    /** Lucide `trash-2`. */
    val Trash2: ImageVector by lazy { lucideIcon("Trash2", Trash2Paths) }

    /** Lucide `triangle-alert`. */
    val TriangleAlert: ImageVector by lazy { lucideIcon("TriangleAlert", TriangleAlertPaths) }

    /** Lucide `upload`. */
    val Upload: ImageVector by lazy { lucideIcon("Upload", UploadPaths) }

    /** Lucide `user-plus`. */
    val UserPlus: ImageVector by lazy { lucideIcon("UserPlus", UserPlusPaths) }

    /** Lucide `users`. */
    val Users: ImageVector by lazy { lucideIcon("Users", UsersPaths) }

    /** Lucide `x`. */
    val X: ImageVector by lazy { lucideIcon("X", XPaths) }
}

private const val ICON_SIZE = 24f
private const val STROKE_WIDTH = 1.6f
private val StrokeBrush = SolidColor(Color.Black)

/** Builds a stroke-only [ImageVector] with one path per Lucide SVG element in [paths]. */
private fun lucideIcon(
    name: String,
    paths: List<String>,
): ImageVector {
    val builder =
        ImageVector.Builder(
            name = "VellutoIcons.$name",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = ICON_SIZE,
            viewportHeight = ICON_SIZE,
        )
    paths.forEach { pathData ->
        builder.addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = StrokeBrush,
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private val ActivityPaths =
    listOf(
        "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 " +
            "8.36A2 2 0 0 1 4.49 12H2",
    )

private val ArchivePaths =
    listOf(
        "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
        "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
        "M10 12h4",
    )

private val ArrowLeftPaths =
    listOf(
        "m12 19-7-7 7-7",
        "M19 12H5",
    )

private val ArrowUpDownPaths =
    listOf(
        "m21 16-4 4-4-4",
        "M17 20V4",
        "m3 8 4-4 4 4",
        "M7 4v16",
    )

private val ArrowUpRightPaths =
    listOf(
        "M7 7h10v10",
        "M7 17 17 7",
    )

private val BellPaths =
    listOf(
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 " +
            "4.499-1.411 5.956-2.738 7.326",
    )

private val BellOffPaths =
    listOf(
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M17 17H4a1 1 0 0 1-.74-1.673C4.59 13.956 6 12.499 6 8a6 6 0 0 1 .258-1.742",
        "m2 2 20 20",
        "M8.668 3.01A6 6 0 0 1 18 8c0 2.687.77 4.653 1.707 6.05",
    )

private val BellRingPaths =
    listOf(
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M22 8c0-2.3-.8-4.3-2-6",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 " +
            "4.499-1.411 5.956-2.738 7.326",
        "M4 2C2.8 3.7 2 5.7 2 8",
    )

private val CakePaths =
    listOf(
        "M20 21v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8",
        "M4 16s.5-1 2-1 2.5 2 4 2 2.5-2 4-2 2.5 2 4 2 2-1 2-1",
        "M2 21h20",
        "M7 8v3",
        "M12 8v3",
        "M17 8v3",
        "M7 4h.01",
        "M12 4h.01",
        "M17 4h.01",
    )

private val CalendarPaths =
    listOf(
        "M8 2v3",
        "M16 2v3",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M3 9h18",
    )

private val CalendarDaysPaths =
    listOf(
        "M8 2v3",
        "M16 2v3",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M3 9h18",
        "M8 13h.01",
        "M12 13h.01",
        "M16 13h.01",
        "M8 17h.01",
        "M12 17h.01",
        "M16 17h.01",
    )

private val CalendarPlusPaths =
    listOf(
        "M16 18h6",
        "M16 2v3",
        "M19 15v6",
        "M21 11.5V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h8.3",
        "M3 9h18",
        "M8 2v3",
    )

private val CalendarRangePaths =
    listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M16 2v3",
        "M3 9h18",
        "M8 2v3",
        "M17 13h-6",
        "M13 17H7",
        "M7 13h.01",
        "M17 17h.01",
    )

private val CalendarXPaths =
    listOf(
        "M8 2v3",
        "M16 2v3",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M3 9h18",
        "m14 13-4 4",
        "m10 13 4 4",
    )

private val CameraPaths =
    listOf(
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 " +
            "1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
        "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    )

private val CheckPaths = listOf("M20 6 9 17l-5-5")

private val ChevronDownPaths = listOf("m6 9 6 6 6-6")

private val ChevronLeftPaths = listOf("m15 18-6-6 6-6")

private val ChevronRightPaths = listOf("m9 18 6-6-6-6")

private val ChevronUpPaths = listOf("m18 15-6-6-6 6")

private val CircleAlertPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 8L12 12",
        "M12 16L12.01 16",
    )

private val CircleCheckPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "m16 9-5.5 5.5L8 12",
    )

private val CircleHelpPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",
        "M12 17h.01",
    )

private val CircleMinusPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M8 12h8",
    )

private val ClockPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 6v6l4 2",
    )

private val CropPaths =
    listOf(
        "M6 2v14a2 2 0 0 0 2 2h14",
        "M18 22V8a2 2 0 0 0-2-2H2",
    )

private val DownloadPaths =
    listOf(
        "M12 15V3",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
    )

private val EyeOffPaths =
    listOf(
        "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
        "M14.084 14.158a3 3 0 0 1-4.242-4.242",
        "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
        "m2 2 20 20",
    )

private val FilterPaths =
    listOf(
        "M2 5h20",
        "M6 12h12",
        "M9 19h6",
    )

private val FlaskPaths =
    listOf(
        "M14 2v6a2 2 0 0 0 .245.96l5.51 10.08A2 2 0 0 1 18 22H6a2 2 0 0 1-1.755-2.96l5.51-10.08A2 2 0 0 0 10 " +
            "8V2",
        "M6.453 15h11.094",
        "M8.5 2h7",
    )

private val FlowerPaths =
    listOf(
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5a4.5 4.5 0 1 1 4.5 4.5 4.5 4.5 0 1 1-4.5 4.5",
        "M12 7.5V9",
        "M7.5 12H9",
        "M16.5 12H15",
        "M12 16.5V15",
        "m8 8 1.88 1.88",
        "M14.12 9.88 16 8",
        "m8 16 1.88-1.88",
        "M14.12 14.12 16 16",
    )

private val GiftPaths =
    listOf(
        "M12 7v14",
        "M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8",
        "M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5",
        "M4 7h16a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z",
    )

private val GlobePaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
    )

private val HeartPaths =
    listOf(
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5" +
            "l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
    )

private val HousePaths =
    listOf(
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2" +
            "z",
    )

private val ImagePaths =
    listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
    )

private val ImagePlusPaths =
    listOf(
        "M16 5h6",
        "M19 2v6",
        "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
    )

private val InboxPaths =
    listOf(
        "M22 12L16 12L14 15L10 15L8 12L2 12",
        "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 " +
            "0-1.79 1.11z",
    )

private val InfoPaths =
    listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 16v-4",
        "M12 8h.01",
    )

private val ListPaths =
    listOf(
        "M3 5h.01",
        "M3 12h.01",
        "M3 19h.01",
        "M8 5h13",
        "M8 12h13",
        "M8 19h13",
    )

private val ListChecksPaths =
    listOf(
        "M13 5h8",
        "M13 12h8",
        "M13 19h8",
        "m3 17 2 2 4-4",
        "m3 7 2 2 4-4",
    )

private val LockPaths =
    listOf(
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )

private val MapPinPaths =
    listOf(
        "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
        "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    )

private val MinusPaths = listOf("M5 12h14")

private val MoreVerticalPaths =
    listOf(
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
    )

private val PartyPopperPaths =
    listOf(
        "M5.8 11.3 2 22l10.7-3.79",
        "M4 3h.01",
        "M22 8h.01",
        "M15 2h.01",
        "M22 20h.01",
        "m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10",
        "m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17",
        "m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7",
        "M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z",
    )

private val PencilPaths =
    listOf(
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622" +
            "l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4",
    )

private val PinPaths =
    listOf(
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 " +
            "0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 " +
            "1z",
    )

private val PinOffPaths =
    listOf(
        "M12 17v5",
        "M15 9.34V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H7.89",
        "m2 2 20 20",
        "M9 9v1.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h11",
    )

private val PlusPaths =
    listOf(
        "M5 12h14",
        "M12 5v14",
    )

private val RefreshCwPaths =
    listOf(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )

private val SearchPaths =
    listOf(
        "m21 21-4.34-4.34",
        "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z",
    )

private val SettingsPaths =
    listOf(
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 " +
            "2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 " +
            "2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 " +
            "6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    )

private val SharePaths =
    listOf(
        "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M8.59 13.51L15.42 17.49",
        "M15.41 6.51L8.59 10.49",
    )

private val SparklePaths =
    listOf(
        "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966" +
            "l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 " +
            "0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
    )

private val SparklesPaths =
    listOf(
        "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966" +
            "l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 " +
            "0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
        "M20 2v4",
        "M22 4h-4",
        "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
    )

private val SproutPaths =
    listOf(
        "M14 9.536V7a4 4 0 0 1 4-4h1.5a.5.5 0 0 1 .5.5V5a4 4 0 0 1-4 4 4 4 0 0 0-4 4c0 2 1 3 1 5a5 5 0 0 1-1 " +
            "3",
        "M4 9a5 5 0 0 1 8 4 5 5 0 0 1-8-4",
        "M5 21h14",
    )

private val StarPaths =
    listOf(
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 " +
            ".294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428" +
            "a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879" +
            "L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
    )

private val Trash2Paths =
    listOf(
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )

private val TriangleAlertPaths =
    listOf(
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    )

private val UploadPaths =
    listOf(
        "M12 3v12",
        "m17 8-5-5-5 5",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
    )

private val UserPlusPaths =
    listOf(
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
        "M19 8L19 14",
        "M22 11L16 11",
    )

private val UsersPaths =
    listOf(
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M16 3.128a4 4 0 0 1 0 7.744",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
    )

private val XPaths =
    listOf(
        "M18 6 6 18",
        "m6 6 12 12",
    )
