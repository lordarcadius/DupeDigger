package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList
import java.security.MessageDigest

/** An image's 64-bit difference hash and DCT perceptual hash. */
data class ImageHashes(val dHash: Long, val pHash: Long)

/** An image seen in a scan, with its cached perceptual hashes if they were computed before. */
data class ImageFile(val id: Long, val sourceUri: String, val size: Long, val hashes: ImageHashes?)

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
