package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "smart_devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "TOGGLE", "SLIDER", "READONLY"
    val isOn: Boolean,
    val value: Float, // e.g., temperature level, brightness pct, power core rpm
    val statusText: String
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:MM
    val description: String,
    val location: String = "Stark Tower"
)

@Entity(tableName = "conversation_logs")
data class ChatLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "USER" or "JARVIS"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoice: Boolean = false
)

// --- DAOs ---

@Dao
interface DeviceDao {
    @Query("SELECT * FROM smart_devices")
    fun getAllDevicesFlow(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM smart_devices")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("SELECT * FROM smart_devices WHERE id = :id")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Query("UPDATE smart_devices SET isOn = :isOn, value = :value, statusText = :statusText WHERE id = :id")
    suspend fun updateDeviceState(id: String, isOn: Boolean, value: Float, statusText: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevices(devices: List<DeviceEntity>)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY date ASC, time ASC")
    fun getAllEventsFlow(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY date ASC, time ASC")
    suspend fun getAllEvents(): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: Int)

    @Query("DELETE FROM calendar_events")
    suspend fun clearAllEvents()
}

@Dao
interface ChatLogDao {
    @Query("SELECT * FROM conversation_logs ORDER BY timestamp ASC")
    fun getHistoryFlow(): Flow<List<ChatLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ChatLogEntity)

    @Query("DELETE FROM conversation_logs")
    suspend fun clearHistory()
}

// --- Database ---

@Database(
    entities = [DeviceEntity::class, CalendarEventEntity::class, ChatLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun calendarDao(): CalendarEventDao
    abstract fun chatLogDao(): ChatLogDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getDatabase(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository Implementation ---

class JarvisRepository(private val db: JarvisDatabase) {
    val devicesFlow: Flow<List<DeviceEntity>> = db.deviceDao().getAllDevicesFlow()
    val eventsFlow: Flow<List<CalendarEventEntity>> = db.calendarDao().getAllEventsFlow()
    val chatHistoryFlow: Flow<List<ChatLogEntity>> = db.chatLogDao().getHistoryFlow()

    suspend fun restoreDefaultDevicesIfNeeded() {
        val deviceDao = db.deviceDao()
        val existing = deviceDao.getAllDevices()
        if (existing.isEmpty()) {
            val defaults = listOf(
                DeviceEntity("living_room_light", "Living Room Lights", "SLIDER", true, 80f, "80% Intensity"),
                DeviceEntity("thermostat", "Climate Control", "SLIDER", true, 72f, "72°F Target"),
                DeviceEntity("vault_door", "Main Armory Lock", "TOGGLE", true, 1f, "SECURED"),
                DeviceEntity("reactor_core", "Arc Reactor Output", "SLIDER", true, 65f, "65% RPM Mode"),
                DeviceEntity("holo_projector", "Holographic Display", "TOGGLE", false, 0f, "OFFLINE")
            )
            deviceDao.insertOrUpdateDevices(defaults)
        }
    }

    suspend fun preloadDefaultEventsIfNeeded() {
        val calendarDao = db.calendarDao()
        val existing = calendarDao.getAllEvents()
        if (existing.isEmpty()) {
            val defaults = listOf(
                CalendarEventEntity(
                    title = "Pepper Potts Dinner",
                    date = "2026-06-07",
                    time = "20:00",
                    description = "Eight-course formal tasting. Do not be late, Sir.",
                    location = "The Ocean Terrace"
                ),
                CalendarEventEntity(
                    title = "Mark 85 Suit Diagnostic",
                    date = "2026-06-08",
                    time = "09:00",
                    description = "Run complete reactor integrity check & flight stabilizer calibration.",
                    location = "Malibu Workshop"
                ),
                CalendarEventEntity(
                    title = "Shield Intelligence Briefing",
                    date = "2026-06-10",
                    time = "14:30",
                    description = "Secure global telemetry brief with Director Fury.",
                    location = "Helicarrier"
                )
            )
            for (ev in defaults) {
                calendarDao.insertEvent(ev)
            }
        }
    }

    suspend fun getDevices(): List<DeviceEntity> = db.deviceDao().getAllDevices()

    suspend fun updateDevice(id: String, isOn: Boolean, value: Float, statusText: String) =
        db.deviceDao().updateDeviceState(id, isOn, value, statusText)

    suspend fun insertEvent(event: CalendarEventEntity) = db.calendarDao().insertEvent(event)

    suspend fun deleteEvent(id: Int) = db.calendarDao().deleteEvent(id)

    suspend fun clearEvents() = db.calendarDao().clearAllEvents()

    suspend fun insertChatLog(sender: String, message: String, isVoice: Boolean = false) =
        db.chatLogDao().insertLog(ChatLogEntity(sender = sender, message = message, isVoice = isVoice))

    suspend fun clearChatHistory() = db.chatLogDao().clearHistory()
}
