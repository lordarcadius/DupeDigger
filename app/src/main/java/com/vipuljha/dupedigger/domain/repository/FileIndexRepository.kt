package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.AudioFile
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature

interface FileIndexRepository {
    /** Marks [files] as seen in [scanId]; a file whose size or mtime changed loses its cached hashes and embeddings. */
    suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats

    /** Deletes files [scope] can produce that were not seen in [scanId], with everything derived from them. */
    suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int

    suspend fun filesSeenIn(scanId: Long): List<IndexedFile>

    /** Stores each file's partial and full hash together, replacing what was cached. */
    suspend fun saveHashes(files: List<IndexedFile>)

    /** Images seen in [scanId], each with its cached perceptual hashes. */
    suspend fun imagesSeenIn(scanId: Long): List<ImageFile>

    suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>)

    /** Videos seen in [scanId], each with its cached signature when every part of it is stored. */
    suspend fun videosSeenIn(scanId: Long): List<VideoFile>

    suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>)

    /** Documents seen in [scanId], each with its cached SimHash. */
    suspend fun documentsSeenIn(scanId: Long): List<DocumentFile>

    suspend fun saveDocumentHashes(hashes: Map<Long, Long>)

    /** Audio files seen in [scanId], each with its cached signature. */
    suspend fun audioSeenIn(scanId: Long): List<AudioFile>

    suspend fun saveAudioSignatures(signatures: Map<Long, AudioSignature>)

    suspend fun filesByIds(ids: Collection<Long>): List<StoredFile>

    /** Deletes the files' rows and everything derived from them, including group memberships. */
    suspend fun remove(ids: Collection<Long>)
}
