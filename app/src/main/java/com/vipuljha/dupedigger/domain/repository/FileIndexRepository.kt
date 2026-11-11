package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.AudioFile
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature

interface FileIndexRepository {
    /** Marks [files] as seen in [scanId]; a file whose size or mtime changed loses its cached hashes and embeddings. */
    suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats

    /** Deletes files [scope] can produce that were not seen in [scanId], with everything derived from them. */
    suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int

    /**
     * Every indexed file of [kinds], each with its cached hashes and whether [scanId] saw it.
     * A scope scanned earlier is included, so a copy of one of this scan's files is found wherever it sits.
     */
    suspend fun indexedFiles(scanId: Long, kinds: Set<MediaKind> = AllMediaKinds): List<IndexedFile>

    /** Stores each file's partial and full hash together, replacing what was cached. */
    suspend fun saveHashes(files: List<IndexedFile>)

    /** Every indexed image, each with its cached perceptual hashes and whether [scanId] saw it. */
    suspend fun indexedImages(scanId: Long): List<ImageFile>

    suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>)

    /** Every indexed video, each with its cached signature when every part of it is stored. */
    suspend fun indexedVideos(scanId: Long): List<VideoFile>

    suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>)

    /** Every indexed document, each with its cached SimHash. */
    suspend fun indexedDocuments(scanId: Long): List<DocumentFile>

    suspend fun saveDocumentHashes(hashes: Map<Long, Long>)

    /** Every indexed audio file, each with its cached signature. */
    suspend fun indexedAudio(scanId: Long): List<AudioFile>

    suspend fun saveAudioSignatures(signatures: Map<Long, AudioSignature>)

    /** Every indexed file of [kinds], each with its stored vector under [modelKey] if there is one. */
    suspend fun embeddingCandidates(scanId: Long, modelKey: String, kinds: Set<MediaKind>): List<EmbeddedFile>

    /** Stores normalised vectors under [modelKey]; other models' vectors are left alone. */
    suspend fun saveEmbeddings(modelKey: String, vectors: Map<Long, FloatArray>)

    /** Deletes every vector stored for the model [modelId], at any size. */
    suspend fun deleteEmbeddings(modelId: String)

    /** Of [ids], the ones [scanId] didn't see, so their rows are only as fresh as the scan that did. */
    suspend fun notSeenIn(scanId: Long, ids: Collection<Long>): Set<Long>

    suspend fun filesByIds(ids: Collection<Long>): List<StoredFile>

    /** Deletes the files' rows and everything derived from them, including group memberships. */
    suspend fun remove(ids: Collection<Long>)
}
