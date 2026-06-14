package com.peoplehub.importguide

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton

private val JSON_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

/** A documented field in an import format. */
private data class FieldDoc(val name: String, val type: String, val required: Boolean, val description: String)

private fun readText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull()

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

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.import_guide_title),
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }

            outcome?.let { result ->
                ResultBanner(result = result, onDismiss = viewModel::onOutcomeShown)
            }

            Text(
                text = stringResource(R.string.ig_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FormatSection(
                title = stringResource(R.string.ig_csv_title),
                fileType = ".csv",
                description = stringResource(R.string.ig_csv_desc),
                fields = csvFields(),
                example = CSV_EXAMPLE,
                importLabel = stringResource(R.string.ig_csv_import),
                onImport = { csvLauncher.launch(CSV_TYPES) },
            )

            FormatSection(
                title = stringResource(R.string.ig_person_title),
                fileType = ".json",
                description = stringResource(R.string.ig_person_desc),
                fields = personFields(),
                example = PERSON_EXAMPLE,
                importLabel = stringResource(R.string.ig_person_import),
                onImport = { personLauncher.launch(JSON_TYPES) },
            )

            FormatSection(
                title = stringResource(R.string.ig_backup_title),
                fileType = ".json",
                description = stringResource(R.string.ig_backup_desc),
                fields = backupFields(),
                example = BACKUP_EXAMPLE,
                importLabel = stringResource(R.string.ig_backup_import),
                onImport = { backupLauncher.launch(JSON_TYPES) },
            )
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

@Composable
private fun ResultBanner(result: ImportOutcome, onDismiss: () -> Unit) {
    val container = if (result.success) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (result.success) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = result.message, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ig_result_dismiss)) }
        }
    }
}

@Composable
private fun FormatSection(
    title: String,
    fileType: String,
    description: String,
    fields: List<FieldDoc>,
    example: String,
    importLabel: String,
    onImport: () -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                CategoryChip(label = fileType)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            GoldDivider()
            CapsLabel(text = stringResource(R.string.ig_fields))
            fields.forEach { FieldRow(it) }

            GoldDivider()
            CapsLabel(text = stringResource(R.string.ig_example))
            CodeBlock(example)

            PrimaryGoldButton(text = importLabel, onClick = onImport, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FieldRow(field: FieldDoc) {
    val requiredLabel = if (field.required) stringResource(R.string.ig_required) else stringResource(R.string.ig_optional)
    val requiredColor = if (field.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(0.42f)) {
            Text(
                field.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("${field.type} · $requiredLabel", style = MaterialTheme.typography.labelSmall, color = requiredColor)
        }
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
        )
    }
}

@Composable
private fun BackupStrategyDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_import_title)) },
        text = { Text(stringResource(R.string.vault_import_message)) },
        confirmButton = { TextButton(onClick = onMerge) { Text(stringResource(R.string.vault_import_merge)) } },
        dismissButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.vault_import_replace)) } },
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
