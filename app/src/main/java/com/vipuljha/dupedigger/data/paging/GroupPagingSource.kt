package com.vipuljha.dupedigger.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import kotlinx.coroutines.CancellationException

/** One page of groups; [fetched] counts the rows read before any were dropped, so the last page is detected right. */
class GroupPage(val groups: List<GroupWithMembers>, val fetched: Int)

/**
 * Offset paging over saved groups that invalidates itself when they change.
 * - Watches the group tables and keep choices, not `file_index`, which changes on every batch of a running scan.
 * - The observer is removed when the source is invalidated, so refreshes never pile up observers.
 */
class GroupPagingSource(
    private val db: RoomDatabase,
    private val fetch: suspend (offset: Int, limit: Int) -> GroupPage,
) : PagingSource<Int, GroupWithMembers>() {

    private val observer = object : InvalidationTracker.Observer(WATCHED_TABLES) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }

    init {
        db.invalidationTracker.addObserver(observer)
        registerInvalidatedCallback { db.invalidationTracker.removeObserver(observer) }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GroupWithMembers> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        return try {
            val page = fetch(offset, limit)
            LoadResult.Page(
                data = page.groups,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (page.fetched < limit) null else offset + page.fetched,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GroupWithMembers>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
        }

    private companion object {
        val WATCHED_TABLES = arrayOf("duplicate_group", "group_member", "keep_choice")
    }
}
