package com.vipuljha.dupedigger.presentation.common

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.presentation.quarantine.restoreMessage
import com.vipuljha.dupedigger.presentation.results.UndoUi
import com.vipuljha.dupedigger.presentation.results.failedMessage
import com.vipuljha.dupedigger.presentation.results.removedMessage
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Deletes files in place from a detail screen, recoverably and without a confirmation dialog: Undo stands in for it (D-007).
 * - Media the system trash needs consent for comes back as [Event.LaunchTrash]; [trashAnswered] finishes it.
 * - Not a singleton: it holds the consent awaiting an answer, so each ViewModel gets its own.
 */
class FileRemoval @Inject constructor(
    private val deleteDuplicates: DeleteDuplicatesUseCase,
    private val confirmTrash: ConfirmTrashUseCase,
    private val undoRemoval: UndoRemovalUseCase,
) {
    sealed interface Event {
        data class Message(val text: UiText, val undo: UndoUi? = null) : Event
        data class LaunchTrash(val request: Any) : Event
    }

    private var pending: Pair<TrashConsent, Long>? = null

    /** [sizes] maps each file id to delete to its size, for the "freed" message. */
    suspend fun delete(sizes: Map<Long, Long>): List<Event> =
        when (val outcome = deleteDuplicates(sizes.keys)) {
            is Outcome.Failure -> listOf(Event.Message(outcome.error.toUiText()))
            is Outcome.Success -> buildList {
                val result = outcome.data
                removedMessage(
                    result.removedIds.size,
                    result.removedIds.sumOf { sizes[it] ?: 0L })?.let {
                    add(
                        Event.Message(
                            it,
                            UndoUi(
                                result.trashedUris,
                                result.quarantineIds
                            ).takeIf { undo -> undo.count > 0 })
                    )
                }
                failedMessage(result.failedIds.size)?.let { add(Event.Message(it)) }
                result.consent?.let { consent ->
                    pending = consent to consent.fileIds.sumOf { sizes[it] ?: 0L }
                    add(Event.LaunchTrash(consent.platformRequest))
                }
            }
        }

    suspend fun trashAnswered(accepted: Boolean): List<Event> {
        val (consent, bytes) = pending ?: return emptyList()
        pending = null
        if (!accepted) return emptyList()
        confirmTrash(consent)
        return listOfNotNull(removedMessage(consent.fileIds.size, bytes)?.let { Event.Message(it) })
    }

    suspend fun undo(undo: UndoUi): List<Event> {
        val failed = undoRemoval(undo.trashedUris, undo.quarantineIds)
        return listOf(Event.Message(restoreMessage(undo.count - failed, failed)))
    }
}

/** Shows a [FileRemoval.Event]: the system trash prompt, or a snackbar that offers Undo when the event has one. */
@Composable
fun rememberRemovalEventHandler(
    snackbarHostState: SnackbarHostState,
    onTrashAnswered: (accepted: Boolean) -> Unit,
    onUndo: (UndoUi) -> Unit,
): (FileRemoval.Event) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val trashPrompt =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onTrashAnswered(result.resultCode == Activity.RESULT_OK)
        }
    return { event ->
        when (event) {
            is FileRemoval.Event.LaunchTrash -> trashPrompt.launch(
                IntentSenderRequest.Builder(event.request as IntentSender).build()
            )

            is FileRemoval.Event.Message -> scope.launch {
                val undoLabel = event.undo?.let { resources.getString(R.string.action_undo) }
                val answer = snackbarHostState.showSnackbar(
                    event.text.asString(context),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long
                )
                if (answer == SnackbarResult.ActionPerformed) event.undo?.let(onUndo)
            }
        }
    }
}
