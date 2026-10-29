package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList
import java.security.MessageDigest

/** An image's 64-bit difference hash and DCT perceptual hash, and its full-size pixel count when just measured. */
data class ImageHashes(val dHash: Long, val pHash: Long, val pixels: Long? = null)

/** An image seen in a scan, with its cached perceptual hashes if they were computed before. */
data class ImageFile(val id: Long, val sourceUri: String, val size: Long, val hashes: ImageHashes?)

/** How many frames a video signature samples, evenly spaced through the video. */
const val VIDEO_FRAMES = 8

/** A video's duration and the pHash of each sampled frame, in order, and its pixel count when just measured. */
data class VideoSignature(val durationMs: Long, val frames: ImmutableList<Long>, val pixels: Long? = null)

/** A video seen in a scan, with its cached signature if it was computed before. */
data class VideoFile(val id: Long, val sourceUri: String, val size: Long, val signature: VideoSignature?)

/** A stored SimHash for a document that holds too little text to compare; it never matches anything. */
const val NO_TEXT_SIMHASH = 0L

/** A document seen in a scan; [simHash] is null until computed. */
data class DocumentFile(val id: Long, val sourceUri: String, val name: String, val mimeType: String?, val size: Long, val simHash: Long?)

/** An audio file's duration and the keys of its tagged title and artist, when it has those tags. */
data class AudioSignature(val durationMs: Long, val titleKey: Long?, val artistKey: Long?, val bitrate: Long? = null)

/** An audio file seen in a scan, with its cached signature if it was read before. */
data class AudioFile(val id: Long, val sourceUri: String, val name: String, val size: Long, val signature: AudioSignature?)

/** What an audio file's metadata says; blank tags are null. */
data class AudioTags(val durationMs: Long, val title: String?, val artist: String?, val bitrate: Long? = null)

/** [score] is the group's similarity, 1 for identical hashes. */
data class NearGroup(val fingerprint: String, val fileIds: ImmutableList<Long>, val score: Float)

data class NearScanResult(
    val groups: ImmutableList<NearGroup>,
    /** Members' sizes, for the scan summary. */
    val sizes: Map<Long, Long>,
    val hashed: Int,
    val unreadable: Int,
)

/** A near group is identified by its members (D-012): `NEAR:` and the SHA-256 of their sorted source URIs. */
fun nearFingerprint(sourceUris: Collection<String>): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(sourceUris.sorted().joinToString("\n").toByteArray())
    return "${MatchTier.NEAR}:" + digest.joinToString("") { "%02x".format(it) }
}
