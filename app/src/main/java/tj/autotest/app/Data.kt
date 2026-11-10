package tj.autotest.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore("autotest_settings")

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: Int,
    val topic: String,
    val category: String,
    val promptRu: String,
    val promptTj: String,
    val choicesRu: String,
    val choicesTj: String,
    val correctIndex: Int,
    val explanationRu: String,
    val explanationTj: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val sourceEdition: String,
    val demo: Boolean = true
)

@Entity(tableName = "answers", primaryKeys = ["questionId"])
data class AnswerRecord(val questionId: Int, val correct: Boolean, val answeredAt: Long)

@Entity(tableName = "sessions")
data class SessionRecord(@PrimaryKey(autoGenerate = true) val id: Int = 0, val completedAt: Long, val correctCount: Int, val questionCount: Int)

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY id") fun all(): Flow<List<Question>>
    @Query("SELECT * FROM questions WHERE topic = :topic ORDER BY id") suspend fun byTopic(topic: String): List<Question>
    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1") suspend fun get(id: Int): Question?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuestions(items: List<Question>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAnswer(record: AnswerRecord)
    @Query("SELECT * FROM answers ORDER BY answeredAt DESC") fun answers(): Flow<List<AnswerRecord>>
    @Query("SELECT * FROM answers WHERE correct = 0 ORDER BY answeredAt DESC") fun errors(): Flow<List<AnswerRecord>>
    @Query("SELECT * FROM answers WHERE questionId = :id LIMIT 1") fun answerFor(id: Int): Flow<AnswerRecord?>
    @Query("DELETE FROM answers") suspend fun clearProgress()
    @Insert suspend fun addSession(session: SessionRecord)
    @Query("SELECT * FROM sessions ORDER BY completedAt DESC") fun sessions(): Flow<List<SessionRecord>>
    @Query("DELETE FROM sessions") suspend fun clearSessions()
}

@Database(entities = [Question::class, AnswerRecord::class, SessionRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): QuestionDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "autotest-tj.db")
                .fallbackToDestructiveMigration().addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database -> kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { database.dao().insertQuestions(DemoQuestions.items) } }
                    }
                }).build().also { INSTANCE = it }
        }
    }
}

val Context.appPreferences: Flow<Pair<String, String>> get() = settingsStore.data.map { p ->
    (p[stringPreferencesKey("language")] ?: "") to (p[stringPreferencesKey("category")] ?: "B")
}

val Context.bookmarks: Flow<Set<Int>> get() = settingsStore.data.map { p ->
    p[stringPreferencesKey("bookmarks")].orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet()
}

suspend fun Context.setPreference(name: String, value: String) {
    settingsStore.edit { it[stringPreferencesKey(name)] = value }
}
