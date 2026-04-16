package com.personal.browser.di

import android.content.Context
import androidx.room.Room
import com.personal.browser.data.database.BookmarkDao
import com.personal.browser.data.database.BrowserDatabase
import com.personal.browser.data.database.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrowserDatabase {
        return Room.databaseBuilder(
            context,
            BrowserDatabase::class.java,
            "browser_database"
        ).build()
    }

    @Provides
    fun provideBookmarkDao(db: BrowserDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideHistoryDao(db: BrowserDatabase): HistoryDao = db.historyDao()
}
