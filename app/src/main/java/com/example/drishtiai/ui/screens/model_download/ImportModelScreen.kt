package com.example.drishtiai.ui.screens.model_download

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import com.example.drishtiai.R
import com.example.drishtiai.ui.components.createAlertDialog

@Composable
fun ImportModelScreen(
    onPrevSectionClick: () -> Unit,
    checkGGUFFile: (Uri) -> Boolean,
    copyModelFile: (Uri, Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ggufFileUri by remember { mutableStateOf<Uri?>(null) }
    var mmprojFileUri by remember { mutableStateOf<Uri?>(null) }
    var ggufFileName by remember { mutableStateOf<String?>(null) }
    var mmprojFileName by remember { mutableStateOf<String?>(null) }

    val ggufLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                if (checkGGUFFile(uri)) {
                    ggufFileUri = uri
                    ggufFileName = context.contentResolver.getFileName(uri)
                } else {
                    createAlertDialog(
                        dialogTitle = context.getString(R.string.dialog_invalid_file_title),
                        dialogText = context.getString(R.string.dialog_invalid_file_text),
                        dialogPositiveButtonText = "OK",
                        onPositiveButtonClick = {},
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = null,
                    )
                }
            }
        }

    val mmprojLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                mmprojFileUri = uri
                mmprojFileName = context.contentResolver.getFileName(uri)
            }
        }

    fun launchFilePicker(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/octet-stream"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            )
        }
        launcher.launch(intent)
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.import_model_step_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.import_model_step_des),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { launchFilePicker(ggufLauncher) }) {
            Text(stringResource(R.string.download_models_select_gguf_button))
        }
        ggufFileName?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { launchFilePicker(mmprojLauncher) }) {
            Text("Select MMPROJ File (Optional)")
        }
        mmprojFileName?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }


        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onPrevSectionClick) {
                Icon(FeatherIcons.ArrowLeft, contentDescription = null)
                Text(stringResource(R.string.button_text_back))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { ggufFileUri?.let { copyModelFile(it, mmprojFileUri) } },
                enabled = ggufFileUri != null
            ) {
                Text("Import Model")
            }
        }
    }
}

fun android.content.ContentResolver.getFileName(uri: Uri): String {
    var name = ""
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        it.moveToFirst()
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
            name = it.getString(nameIndex)
        }
    }
    return name
}
