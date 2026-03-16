package com.caskfive.pingcheck.repository

import com.caskfive.pingcheck.data.db.FavoritesDao
import com.caskfive.pingcheck.data.db.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoritesDao: FavoritesDao,
) {
    fun observeAll(): Flow<List<FavoriteEntity>> {
        return favoritesDao.observeAll()
    }

    suspend fun getByHost(host: String): FavoriteEntity? {
        return favoritesDao.getByHost(host)
    }

    suspend fun add(favorite: FavoriteEntity): Long {
        return favoritesDao.insert(favorite)
    }

    suspend fun update(favorite: FavoriteEntity) {
        favoritesDao.update(favorite)
    }

    suspend fun delete(id: Long) {
        favoritesDao.delete(id)
    }

    suspend fun getNextSortOrder(): Int {
        return favoritesDao.getNextSortOrder()
    }

    // Explicit update method for editing favorites
    suspend fun updateFavorite(favorite: FavoriteEntity) {
        favoritesDao.update(favorite)
    }

    // Explicit delete method for removing favorites
    suspend fun deleteFavorite(id: Long) {
        favoritesDao.delete(id)
    }
}
