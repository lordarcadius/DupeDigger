package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList

/** An indexed file with what removing it needs. */
data class StoredFile(
    val id: Long,
    val sourceUri: String,
    val path: String?,
    val name: String,
    val mediaKind: MediaKind,
    val size: Long,
)

/**
 * Media the system trash can take only after the user agrees in a system prompt.
 * [platformRequest] is the prompt to launch (an `IntentSender` on Android).
 */
class TrashConsent(
    val fileIds: ImmutableList<Long>,
    val mediaUris: ImmutableList<String>,
    val platformRequest: Any,
)

/**
 * The outcome of removing files.
 * - [removedIds]: trashed or quarantined now; [trashedUris] and [quarantineIds] are what undo needs.
 * - [consent]: media still waiting for the user's agreement.
 * - [failedIds]: left exactly where they were.
 */
data class RemovalResult(
    val removedIds: ImmutableList<Long>,
    val trashedUris: ImmutableList<String>,
    val quarantineIds: ImmutableList<Long>,
    val consent: TrashConsent?,
    val failedIds: ImmutableList<Long>,
)
