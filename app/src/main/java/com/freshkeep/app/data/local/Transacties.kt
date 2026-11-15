package com.freshkeep.app.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/** Abstractie rond databasetransacties, zodat repositories met nep-DAO's te testen zijn. */
interface TransactieUitvoerder {
    suspend fun <T> uitvoeren(blok: suspend () -> T): T
}

class RoomTransactieUitvoerder @Inject constructor(
    private val db: FreshKeepDatabase,
) : TransactieUitvoerder {
    override suspend fun <T> uitvoeren(blok: suspend () -> T): T = db.withTransaction { blok() }
}
