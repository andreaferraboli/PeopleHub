package com.peoplehub.importguide

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GroupedRows
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.IconTile
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.io.readTextRobust

private val JSON_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

/** A documented field in an import format. */
private data class FieldDoc(val name: String, val type: String, val required: Boolean, val description: String)

private fun readText(context: Context, uri: Uri): String? = readTextRobust(context, uri)

/** In-app reference documenting every import format, with one-tap import + a result banner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportGuideScreen(
    onBack: () -> Unit,
    viewModel: ImportGuideViewModel = hiltViewModel(),
) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

    val csvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readText(context, uri)?.let(viewModel::importBirthdaysCsv)
        }
    val personLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readText(context, uri)?.let(viewModel::importPersonJson)
        }
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) pendingBackupJson = readText(context, uri)
        }

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = stringResource(R.string.import_guide_title),
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.xxs),
                        )
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.gutter, vertical = spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(VellutoShapes.Pill),
                        color = c.accent,
                        trackColor = c.track,
                    )
                }

                outcome?.let { result ->
                    ResultBanner(result = result, onDismiss = viewModel::onOutcomeShown)
                }

                Text(
                    text = stringResource(R.string.ig_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                    modifier = Modifier.padding(horizontal = spacing.xxs).riseIn(0),
                )

                FormatSection(
                    title = stringResource(R.string.ig_csv_title),
                    icon = VellutoIcons.Cake,
                    fileType = ".csv",
                    description = stringResource(R.string.ig_csv_desc),
                    fields = csvFields(),
                    example = CSV_EXAMPLE,
                    importLabel = stringResource(R.string.ig_csv_import),
                    onImport = { csvLauncher.launch(CSV_TYPES) },
                    modifier = Modifier.riseIn(1),
                )

                FormatSection(
                    title = stringResource(R.string.ig_person_title),
                    icon = VellutoIcons.UserPlus,
                    fileType = ".json",
                    description = stringResource(R.string.ig_person_desc),
                    fields = personFields(),
                    example = PERSON_EXAMPLE,
                    importLabel = stringResource(R.string.ig_person_import),
                    onImport = { personLauncher.launch(JSON_TYPES) },
                    modifier = Modifier.riseIn(2),
                )

                FormatSection(
                    title = stringResource(R.string.ig_backup_title),
                    icon = VellutoIcons.Archive,
                    fileType = ".json",
                    description = stringResource(R.string.ig_backup_desc),
                    fields = backupFields(),
                    example = BACKUP_EXAMPLE,
                    importLabel = stringResource(R.string.ig_backup_import),
                    onImport = { backupLauncher.launch(JSON_TYPES) },
                    modifier = Modifier.riseIn(3),
                )
            }
        }
    }

    val backupJson = pendingBackupJson
    if (backupJson != null) {
        BackupStrategyDialog(
            onMerge = {
                viewModel.importBackup(backupJson, ImportStrategy.MERGE)
                pendingBackupJson = null
            },
            onReplace = {
                viewModel.importBackup(backupJson, ImportStrategy.REPLACE)
                pendingBackupJson = null
            },
            onDismiss = { pendingBackupJson = null },
        )
    }
}

/** The import result: a soft success or error panel with its status icon and a dismiss action. */
@Composable
private fun ResultBanner(result: ImportOutcome, onDismiss: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val tint = if (result.success) c.ok else c.err
    val container = if (result.success) c.okSoft else c.errSoft
    val edge = if (result.success) c.line else c.errEdge
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(container, VellutoShapes.Inner)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, edge)
                .padding(start = spacing.l, top = spacing.xxs, bottom = spacing.xxs, end = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (result.success) VellutoIcons.CircleCheck else VellutoIcons.CircleAlert,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            modifier = Modifier.weight(1f).padding(vertical = spacing.m),
        )
        TextAction(label = stringResource(R.string.ig_result_dismiss), onClick = onDismiss, color = tint)
    }
}

/** One import format: icon tile + serif title + file-type chip, description, fields, example, import. */
@Composable
private fun FormatSection(
    title: String,
    icon: ImageVector,
    fileType: String,
    description: String,
    fields: List<FieldDoc>,
    example: String,
    importLabel: String,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
        Column(modifier = Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.ml)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                IconTile(icon)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                CategoryChip(label = fileType)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.text2)

            CapsLabel(text = stringResource(R.string.ig_fields), modifier = Modifier.padding(top = spacing.xs))
            GroupedRows {
                fields.forEachIndexed { index, field ->
                    if (index > 0) Hairline(modifier = Modifier.padding(horizontal = spacing.ml))
                    FieldRow(field)
                }
            }

            CapsLabel(text = stringResource(R.string.ig_example), modifier = Modifier.padding(top = spacing.xs))
            CodeBlock(example)

            VellutoButton(
                text = importLabel,
                onClick = onImport,
                icon = VellutoIcons.Upload,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
            )
        }
    }
}

/** A documented field: monospace name in accent and its type/requirement, then the description. */
@Composable
private fun FieldRow(field: FieldDoc) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val requiredLabel = if (field.required) stringResource(R.string.ig_required) else stringResource(R.string.ig_optional)
    val requiredColor = if (field.required) c.accentText else c.text2
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.ml, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Column(modifier = Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                color = c.accentText,
            )
            Text(
                text = "${field.type} · $requiredLabel",
                style = MaterialTheme.typography.labelSmall,
                color = requiredColor,
            )
        }
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = c.text2,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** A monospace example on the inner surface, scrolling sideways for long lines. */
@Composable
private fun CodeBlock(code: String) {
    val c = PeopleHubTheme.colors
    Text(
        text = code,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = c.text,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(c.surface2, VellutoShapes.Inner)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .clip(VellutoShapes.Inner)
                .horizontalScroll(rememberScrollState())
                .padding(PeopleHubTheme.spacing.l),
    )
}

@Composable
private fun BackupStrategyDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_import_title)) },
        text = { Text(stringResource(R.string.vault_import_message), color = PeopleHubTheme.colors.text2) },
        confirmButton = {
            VellutoButton(
                text = stringResource(R.string.vault_import_merge),
                onClick = onMerge,
                height = PeopleHubTheme.spacing.touchTarget,
            )
        },
        dismissButton = {
            VellutoButton(
                text = stringResource(R.string.vault_import_replace),
                onClick = onReplace,
                style = VellutoButtonStyle.Ghost,
            )
        },
    )
}

private fun csvFields(): List<FieldDoc> =
    listOf(
        FieldDoc("nome", "text", true, "First name."),
        FieldDoc("cognome", "text", true, "Last name."),
        FieldDoc("data_nascita", "date", true, "Birth date — YYYY-MM-DD or DD/MM/YYYY."),
    )

private fun personFields(): List<FieldDoc> =
    listOf(
        FieldDoc("firstName", "string", true, "First name."),
        FieldDoc("lastName", "string", true, "Last name."),
        FieldDoc("birthday", "string", false, "Birth date in ISO form YYYY-MM-DD."),
        FieldDoc("tags", "string[]", false, "Free labels, e.g. Family, Work."),
        FieldDoc("interests", "object[]", false, "List of { \"key\": ..., \"value\": ... } likes & tastes."),
        FieldDoc("notes", "string", false, "Free-text notes."),
        FieldDoc("warningDays", "number", false, "Days seen-ago before a check-in is due (warning)."),
        FieldDoc("criticalDays", "number", false, "Days seen-ago before it becomes critical."),
        FieldDoc("lastCheckInEpochMillis", "number", false, "Unix time in ms you last saw them."),
        FieldDoc("photoPath", "string", false, "Leave out — photos are added inside the app."),
        FieldDoc("id", "number", false, "Ignored on import; a new person is always created."),
    )

private fun backupFields(): List<FieldDoc> =
    listOf(
        FieldDoc("schemaVersion", "number", true, "Always 1 for this app version."),
        FieldDoc("people", "object[]", true, "Person objects (fields above). Give each an id so check-ins/events can link to it."),
        FieldDoc("checkIns", "object[]", false, "Visit history: personId, timestampEpochMillis, optional note."),
        FieldDoc("events", "object[]", false, "Events: title, dateTime (YYYY-MM-DDTHH:MM:SS), optional category/personId."),
    )

private const val CSV_EXAMPLE = """nome,cognome,data_nascita
Eleanor,Vance,1989-10-12
"Smith, Jr.",Marcus,03/11/1990
James,Althorp,1975-02-28"""

private const val PERSON_EXAMPLE = """{
  "firstName": "Eleanor",
  "lastName": "Vance",
  "birthday": "1989-10-12",
  "tags": ["Family", "Art"],
  "interests": [
    { "key": "Favourite food", "value": "Sushi" },
    { "key": "Coffee order", "value": "Oat flat white" }
  ],
  "notes": "Met at the gallery opening.",
  "warningDays": 14,
  "criticalDays": 30
}"""

private const val BACKUP_EXAMPLE = """{
  "schemaVersion": 1,
  "people": [
    {
      "id": 1,
      "firstName": "Eleanor",
      "lastName": "Vance",
      "birthday": "1989-10-12",
      "tags": ["Family"]
    }
  ],
  "checkIns": [
    { "personId": 1, "timestampEpochMillis": 1718000000000, "note": "Coffee" }
  ],
  "events": [
    {
      "title": "Gallery opening",
      "dateTime": "2026-11-15T19:00:00",
      "category": "Art",
      "personId": 1,
      "pinnedToWidget": false
    }
  ]
}"""
