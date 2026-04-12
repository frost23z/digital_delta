package me.zayedbinhasan.android_app.data.local.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import me.zayedbinhasan.data.Database

object LocalDatabaseFactory {
    fun create(context: Context): Database {
        val driver = AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = "digital_delta.db",
        )
        return Database(driver)
    }
}
