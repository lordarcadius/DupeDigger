package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.detector.MIN_DOCUMENT_WORDS
import com.vipuljha.dupedigger.domain.detector.cosine
import com.vipuljha.dupedigger.domain.detector.groupBySimilarity
import com.vipuljha.dupedigger.domain.detector.normalizeWords
import com.vipuljha.dupedigger.domain.model.EMBEDDING_DIM
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ModelCapability
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.NearScanResult
import com.vipuljha.dupedigger.domain.model.SIMILARITY_PROMPT
import com.vipuljha.dupedigger.domain.model.embeddingKey
import com.vipuljha.dupedigger.domain.model.memberFingerprint
import com.vipuljha.dupedigger.domain.repository.DocumentTextReader
import com.vipuljha.dupedigger.domain.repository.Embedder
import com.vipuljha.dupedigger.domain.repository.EmbeddingInputReader
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

/**
 * Finds files that mean the same thing for the SEMANTIC tier, with the downloaded model [ModelVariant].
 * - Embeds what the model reads: images (small re-encodes) with vision, audio clips with audio, document text with
 *   text, prefixed with the similarity prompt and cut to [MAX_TEXT_CHARS].
 * - Vectors are stored under the model and size, so another model's vectors are kept and never compared.
 * - Each exact group takes part through one member; a semantic group whose files all sit in one near group is dropped,
 *   since that group already shows them.
 * - A batch the engine rejects is retried file by file so one bad file costs only itself.
 * Returns null when the model can't be loaded, so the scan carries on without the tier.
 */
class FindSemanticDuplicatesUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
    private val embedder: Embedder,
    private val inputs: EmbeddingInputReader,
    private val textReader: DocumentTextReader,
) {
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        scanId: Long,
        variant: ModelVariant,
        backend: EmbeddingBackend,
        threshold: Float,
        exactGroups: List<ExactGroup>,
        nearGroups: List<NearGroup>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): NearScanResult? {
        val key = embeddingKey(variant)
        val kinds = buildSet {
            if (ModelCapability.VISION in variant.capabilities) add(MediaKind.IMAGE)
            if (ModelCapability.AUDIO in variant.capabilities) add(MediaKind.AUDIO)
            if (ModelCapability.TEXT in variant.capabilities) add(MediaKind.DOCUMENT)
        }
        val exactCopies = exactGroups.flatMap { it.fileIds.sorted().drop(1) }.toSet()
        val (cached, missing) = fileIndex.embeddingCandidates(scanId, key, kinds)
            .filter { it.id !in exactCopies && (it.kind != MediaKind.DOCUMENT || textReader.supports(it.name, it.mimeType)) }
            .partition { it.vector != null }

        val embedded = mutableListOf<EmbeddedFile>()
        var attempted = 0
        var failed = 0
        for (batch in missing.chunked(BATCH)) {
            val prepared = mutableListOf<Pair<EmbeddedFile, EmbeddingInput>>()
            for (file in batch) {
                when (val input = inputFor(file)) {
                    null -> failed++
                    SKIP -> Unit
                    else -> prepared += file to input
                }
            }
            val vectors = embedBatch(variant, backend, prepared) ?: return null
            failed += prepared.size - vectors.size
            fileIndex.saveEmbeddings(key, vectors.mapKeys { it.key.id })
            embedded += vectors.map { (file, vector) -> file.withVector(vector) }
            attempted += batch.size
            onProgress(attempted, missing.size)
        }

        val files = (cached + embedded).associateBy { it.id }
        val nearSets = nearGroups.map { it.fileIds.toSet() }
        val groups = files.values.groupBy { it.kind }.values.flatMap { ofKind ->
            groupBySimilarity(ofKind.map { it.id to it.vector!! }, threshold)
        }.filterNot { ids -> nearSets.any { it.containsAll(ids) } }.map { ids ->
            val first = files.getValue(ids.first()).vector!!
            NearGroup(
                fingerprint = memberFingerprint(MatchTier.SEMANTIC, ids.map { files.getValue(it).sourceUri }),
                fileIds = ids.toImmutableList(),
                score = ids.drop(1).map { cosine(first, files.getValue(it).vector!!) }.average().toFloat(),
            )
        }
        return NearScanResult(
            groups = groups.toImmutableList(),
            sizes = groups.flatMap { it.fileIds }.associateWith { files.getValue(it).size },
            hashed = embedded.size,
            unreadable = failed,
        )
    }

    /** Vectors for the files that could be embedded; null when the model isn't available at all. */
    private suspend fun embedBatch(
        variant: ModelVariant,
        backend: EmbeddingBackend,
        prepared: List<Pair<EmbeddedFile, EmbeddingInput>>,
    ): Map<EmbeddedFile, FloatArray>? {
        if (prepared.isEmpty()) return emptyMap()
        return when (val outcome = embedder.embed(variant, prepared.map { it.second }, EMBEDDING_DIM, backend)) {
            is Outcome.Success -> prepared.map { it.first }.zip(outcome.data).toMap()
            is Outcome.Failure -> if (outcome.error == DomainError.ModelUnavailable) {
                null
            } else if (prepared.size == 1) {
                emptyMap()
            } else {
                val one = mutableMapOf<EmbeddedFile, FloatArray>()
                for (item in prepared) one += embedBatch(variant, backend, listOf(item)) ?: return null
                one
            }
        }
    }

    /** Null when the file can't be read; [SKIP] when it holds too little text to compare (re-read on each scan). */
    private suspend fun inputFor(file: EmbeddedFile): EmbeddingInput? = when (file.kind) {
        MediaKind.IMAGE -> inputs.image(file.sourceUri)?.let { EmbeddingInput.Image(it) }
        MediaKind.AUDIO -> inputs.audio(file.sourceUri)?.let { EmbeddingInput.Audio(it) }
        MediaKind.DOCUMENT -> textReader.text(file.sourceUri, file.name, file.mimeType)?.let { text ->
            if (normalizeWords(text).size < MIN_DOCUMENT_WORDS) {
                SKIP
            } else {
                EmbeddingInput.Text(SIMILARITY_PROMPT + text.replace(whitespace, " ").trim().take(MAX_TEXT_CHARS))
            }
        }
        else -> null
    }

    private fun EmbeddedFile.withVector(vector: FloatArray) = EmbeddedFile(id, sourceUri, name, mimeType, kind, size, vector)

    private companion object {
        const val BATCH = 8
        /** About 400 tokens of text, inside the model's input window. */
        const val MAX_TEXT_CHARS = 1_500
        val whitespace = Regex("\\s+")
        val SKIP = EmbeddingInput.Text("")
    }
}
