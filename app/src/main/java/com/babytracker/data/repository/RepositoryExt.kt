package com.babytracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps every emitted list element through [transform], e.g. `dao.observeAll().mapList { it.toDomain() }`. */
internal fun <E, D> Flow<List<E>>.mapList(transform: (E) -> D): Flow<List<D>> = map { list -> list.map(transform) }
