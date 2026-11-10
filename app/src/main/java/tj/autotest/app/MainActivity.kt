package tj.autotest.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Navy = Color(0xFF101827)
private val Panel = Color(0xFF1B2638)
private val Yellow = Color(0xFFFFD33D)
private val Muted = Color(0xFFB6C2D1)
private const val PRIVACY_URL = "https://ilhomahmedov488-jpg.github.io/pdd/privacy-policy.html"

class MainActivity : ComponentActivity() {
    private val dao by lazy { AppDatabase.get(this).dao() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by appPreferences.collectAsState(initial = "" to "B")
            val lang = prefs.first
            val category = prefs.second
            val persistedBookmarks by bookmarks.collectAsState(initial = emptySet())
            val allQuestions by dao.all().collectAsState(initial = emptyList())
            val answers by dao.answers().collectAsState(initial = emptyList())
            val sessions by dao.sessions().collectAsState(initial = emptyList())
            val errors by dao.errors().collectAsState(initial = emptyList())
            var savedIds by remember { mutableStateOf(setOf<Int>()) }
            var tab by remember { mutableStateOf("home") }
            var quiz by remember { mutableStateOf<List<Question>>(emptyList()) }
            var quizIndex by remember { mutableIntStateOf(0) }
            var chosen by remember { mutableIntStateOf(-1) }
            var showResult by remember { mutableStateOf(false) }
            var examMode by remember { mutableStateOf(false) }
            var examSeconds by remember { mutableIntStateOf(15 * 60) }
            var examDone by remember { mutableStateOf(false) }
            var examCorrect by remember { mutableIntStateOf(0) }
            var practiceCorrect by remember { mutableIntStateOf(0) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(persistedBookmarks) { savedIds = persistedBookmarks }
            LaunchedEffect(examMode, examDone) {
                while (examMode && !examDone && examSeconds > 0) { delay(1000); examSeconds-- }
                if (examMode && examSeconds == 0) examDone = true
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Navy, surface = Panel, onPrimary = Navy, onBackground = Color.White, onSurface = Color.White, secondary = Yellow)) {
                Surface(Modifier.fillMaxSize(), color = Navy) {
                    if (lang.isBlank()) {
                        LanguageWelcome(onLanguage = { selected -> scope.launch { setPreference("language", selected) } })
                    } else if (quiz.isNotEmpty() && !examDone) {
                        QuizScreen(
                            question = quiz[quizIndex.coerceAtMost(quiz.lastIndex)], language = lang,
                            category = category, index = quizIndex, total = quiz.size, selected = chosen,
                            answered = showResult, isExam = examMode, seconds = examSeconds,
                            onSelect = { chosen = it },
                            onBookmark = { id -> savedIds = if (id in savedIds) savedIds - id else savedIds + id; scope.launch { setPreference("bookmarks", savedIds.joinToString(",")) } },
                            isSaved = quiz[quizIndex.coerceAtMost(quiz.lastIndex)].id in savedIds,
                            onAnswer = {
                                val q = quiz[quizIndex]
                                scope.launch { dao.saveAnswer(AnswerRecord(q.id, chosen == q.correctIndex, System.currentTimeMillis())) }
                                if (examMode && chosen == q.correctIndex) examCorrect++
                                if (!examMode && chosen == q.correctIndex) practiceCorrect++
                                showResult = true
                            },
                            onNext = {
                                if (quizIndex < quiz.lastIndex) { quizIndex++; chosen = -1; showResult = false }
                                else { scope.launch { dao.addSession(SessionRecord(completedAt=System.currentTimeMillis(), correctCount=if(examMode) examCorrect else practiceCorrect, questionCount=quiz.size)) }; if (!examMode) { quiz=emptyList(); tab="home" } }
                                if (examMode && quizIndex==quiz.lastIndex) examDone=true
                            },
                            onExit = { quiz = emptyList(); examMode = false; examDone = false; tab = "home" }
                        )
                    } else if (examDone) {
                        ExamResult(examCorrect, quiz.size, lang, quiz.filterIndexed { i, q -> answers.find { a -> a.questionId == q.id }?.correct == false }, onReview = { examDone = false; examMode = false; quiz = emptyList(); tab = "errors" }, onClose = { examDone = false; examMode = false; quiz = emptyList(); tab = "home" })
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Header(lang, category, onCategory = { next -> scope.launch { setPreference("category", next) } })
                            when (tab) {
                                "home" -> HomeScreen(lang, answers.count { it.correct }, answers.size, errors.size, onTrain = { quiz=allQuestions.shuffled().take(5); quizIndex=0; chosen=-1; showResult=false; examMode=false; practiceCorrect=0 }, onExam = { quiz=allQuestions.filter { category in it.category.split(",") }.shuffled().take(10); quizIndex=0; chosen=-1; showResult=false; examMode=true; examCorrect=0; examSeconds=15*60; examDone=false }, onTopics = { tab="topics" }, onProgress = { tab="progress" })
                                "topics" -> TopicsScreen(lang, allQuestions, onTopic = { t -> val questions=allQuestions.filter { it.topic==t }.shuffled(); if (questions.isNotEmpty()) { quiz=questions; quizIndex=0; chosen=-1; showResult=false; practiceCorrect=0; examMode=false } }, onRandom = { val questions=allQuestions.shuffled().take(5); quiz=questions; quizIndex=0; chosen=-1; showResult=false; practiceCorrect=0; examMode=false })
                                "saved" -> SavedScreen(lang, allQuestions.filter { it.id in savedIds }, errors.mapNotNull { e -> allQuestions.find { it.id==e.questionId } }.distinctBy { it.id }, savedIds, onStart = { list -> if (list.isNotEmpty()) { quiz=list; quizIndex=0; chosen=-1; showResult=false; practiceCorrect=0; examMode=false } }, onToggle = { id -> savedIds=if(id in savedIds) savedIds-id else savedIds+id; scope.launch { setPreference("bookmarks",savedIds.joinToString(",")) } })
                                "progress" -> ProgressScreen(lang, answers, sessions, errors.size)
                                else -> SettingsScreen(lang, category, answers.size, onLanguage = { next -> scope.launch { setPreference("language",next) } }, onCategory = { next -> scope.launch { setPreference("category",next) } }, onReset = { scope.launch { dao.clearProgress(); dao.clearSessions(); savedIds=emptySet(); setPreference("bookmarks", "") } })
                            }
                            BottomBar(tab, lang, onSelect = { tab=it })
                        }
                    }
                }
            }

        }
    }
}

@Composable private fun LanguageWelcome(onLanguage: (String)->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("🚘", fontSize = 48.sp); Spacer(Modifier.height(20.dp)); Text("АвтоТест TJ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color=Color.White)
        Text("ПДД Тоҷикистон", fontSize=20.sp,color=Yellow); Spacer(Modifier.height(10.dp)); Text("Выберите язык интерфейса / Забони барномаро интихоб кунед", color=Muted)
        Spacer(Modifier.height(24.dp)); ActionButton("Русский", onClick={onLanguage("ru")}); Spacer(Modifier.height(12.dp)); ActionButton("Тоҷикӣ", onClick={onLanguage("tj")})
    }
}

@Composable private fun Header(lang:String, category:String, onCategory:(String)->Unit) {
    Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal=18.dp, vertical=12.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
        Column { Text("АвтоТест TJ", color=Color.White, fontWeight=FontWeight.Bold, fontSize=20.sp); Text(if(lang=="ru") "ПДД Тоҷикистон" else "Қоидаҳои роҳ", color=Muted, fontSize=13.sp) }
        var expanded by remember { mutableStateOf(false) }
        Box { OutlinedButton(onClick={expanded=true}, contentPadding=PaddingValues(horizontal=12.dp, vertical=4.dp)) { Text("${if(lang=="ru") "Кат." else "Гурӯҳ"} $category", color=Yellow) }; DropdownMenu(expanded, {expanded=false}) { listOf("A","B","C","D","BE","CE").forEach { DropdownMenuItem(text={Text(it)},onClick={expanded=false;onCategory(it)}) } } }
    }
}

@Composable private fun HomeScreen(lang:String, correct:Int,total:Int,errorCount:Int,onTrain:()->Unit,onExam:()->Unit,onTopics:()->Unit,onProgress:()->Unit) {
    val accuracy=if(total==0) 0 else correct*100/total
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp), verticalArrangement=Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(top=10.dp,bottom=12.dp)) {
        item { CardBox { Text(if(lang=="ru") "Готовься уверенно" else "Бо боварӣ омӯзед",fontSize=24.sp,fontWeight=FontWeight.Bold); Text(if(lang=="ru") "Короткие занятия помогут закрепить знания и заметить пробелы." else "Дарсҳои кӯтоҳ ба мустаҳкам кардани дониш ва ёфтани камбудиҳо кӯмак мекунанд.",color=Muted) } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { StatTile(if(lang=="ru") "Точность" else "Дақиқӣ","$accuracy%",Modifier.weight(1f));StatTile(if(lang=="ru") "На повтор" else "Такрор","$errorCount",Modifier.weight(1f)) } }
        item { ActionButton(if(lang=="ru") "Продолжить обучение" else "Омӯзишро идома диҳед",onClick=onTopics) }
        item { CardBox { Text(if(lang=="ru") "Быстрый старт" else "Оғози зуд",fontWeight=FontWeight.Bold,fontSize=18.sp); Spacer(Modifier.height(10.dp)); ActionButton(if(lang=="ru") "Тренировка · 5 вопросов" else "Машқ · 5 савол",onClick=onTrain); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick=onExam,modifier=Modifier.fillMaxWidth()) { Text(if(lang=="ru") "Тренировочный тест · 10 вопросов" else "Санҷиши омӯзишӣ · 10 савол",color=Yellow) } } }
        item { CardBox(onClick=onProgress) { Text(if(lang=="ru") "Мой прогресс →" else "Пешрафти ман →",fontWeight=FontWeight.SemiBold); Text(if(lang=="ru") "$total ответов · $errorCount ошибок для повтора" else "$total ҷавоб · $errorCount хатогӣ барои такрор",color=Muted) } }
        item { Text(if(lang=="ru") "Учебные примеры демонстрационные. Это не официальный банк вопросов и не официальный экзамен." else "Намунаҳои омӯзишӣ намоишӣ мебошанд. Ин бонки расмии саволҳо ё имтиҳони расмӣ нест.",color=Muted,fontSize=12.sp) }
    }
}

@Composable private fun TopicsScreen(lang:String, questions:List<Question>, onTopic:(String)->Unit,onRandom:()->Unit) {
    val topics=listOf("basic","signs","markings","signals","junctions","speed","stopping","safety","first_aid","driver")
    val ru=listOf("Основы ПДД","Дорожные знаки","Разметка","Светофор","Перекрёстки","Скорость","Остановка и стоянка","Безопасность движения","Первая помощь","Водитель и автомобиль")
    val tj=listOf("Асосҳои ҚҲР","Аломатҳои роҳ","Хатҳои роҳ","Чароғак","Чорроҳаҳо","Суръат","Ист ва таваққуф","Бехатарии роҳ","Ёрии аввал","Ронанда ва мошин")
    Column(Modifier.fillMaxSize().padding(16.dp)) { Text(if(lang=="ru") "Обучение по темам" else "Омӯзиш аз рӯи мавзӯъ",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White); Spacer(Modifier.height(12.dp)); ActionButton(if(lang=="ru") "Случайные вопросы" else "Саволҳои тасодуфӣ",onClick=onRandom); Spacer(Modifier.height(10.dp)); LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(topics.size) { i -> val n=questions.count{it.topic==topics[i]}; CardBox(onClick={onTopic(topics[i])}) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(if(lang=="ru")ru[i] else tj[i],fontWeight=FontWeight.SemiBold); Text("$n  ›",color=Yellow) } } } } }
}

@Composable private fun QuizScreen(question:Question,language:String,category:String,index:Int,total:Int,selected:Int,answered:Boolean,isExam:Boolean,seconds:Int,isSaved:Boolean,onSelect:(Int)->Unit,onBookmark:(Int)->Unit,onAnswer:()->Unit,onNext:()->Unit,onExit:()->Unit) {
    val ru=language=="ru"; val choices=(if(ru)question.choicesRu else question.choicesTj).split("|"); val prompt=if(ru)question.promptRu else question.promptTj
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(ru) "Вопрос ${index+1} из $total" else "Савол ${index+1} аз $total",color=Muted); Text(if(isExam) "%02d:%02d".format(seconds/60,seconds%60) else if(ru) "Категория $category" else "Гурӯҳ $category",color=Yellow); TextButton(onClick=onExit){Text(if(ru)"Выйти" else "Баромад",color=Muted)} }
        LinearProgressIndicator(progress={ (index+if(answered)1 else 0).toFloat()/total.coerceAtLeast(1) },modifier=Modifier.fillMaxWidth(),color=Yellow,trackColor=Panel)
        CardBox { Text(prompt,fontSize=21.sp,fontWeight=FontWeight.SemiBold,lineHeight=28.sp); Text(if(question.demo) if(ru) "Демонстрационный учебный вопрос" else "Саволи омӯзишии намоишӣ" else "",color=Yellow,fontSize=12.sp) }
        choices.forEachIndexed { i, choice ->
            val selectedColor=when { answered && i==question.correctIndex -> Color(0xFF245B42); answered && i==selected -> Color(0xFF713B3B); selected==i -> Color(0xFF34445C); else -> Panel }
            Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=selectedColor),modifier=Modifier.fillMaxWidth().clickable(enabled=!answered){onSelect(i)}) { Text("${('А'+i)}.  $choice",Modifier.padding(16.dp),fontSize=17.sp,color=Color.White) }
        }
        if(answered) CardBox { Text(if(selected==question.correctIndex) if(ru)"Верно" else "Дуруст" else if(ru)"Правильный ответ: ${choices[question.correctIndex]}" else "Ҷавоби дуруст: ${choices[question.correctIndex]}",fontWeight=FontWeight.Bold,color=Yellow); Spacer(Modifier.height(6.dp)); Text(if(ru)question.explanationRu else question.explanationTj,color=Muted); Spacer(Modifier.height(8.dp)); Text(if(ru)"Источник: ${question.sourceTitle}. ${question.sourceEdition}" else "Манбаъ: ${question.sourceTitle}. ${question.sourceEdition}",fontSize=11.sp,color=Muted) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={onBookmark(question.id)},modifier=Modifier.weight(1f)){Text(if(isSaved)if(ru)"★ Сохранено" else "★ Сабт шуд" else if(ru)"☆ Закладка" else "☆ Нишона",color=Yellow)}; if(!answered) Button(onClick=onAnswer,enabled=selected>=0,modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Navy)){Text(if(ru)"Ответить" else "Ҷавоб",fontWeight=FontWeight.Bold)} else Button(onClick=onNext,modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Navy)){Text(if(index==total-1)if(ru)"Завершить" else "Анҷом" else if(ru)"Далее" else "Баъдӣ",fontWeight=FontWeight.Bold)} }
        if(isExam) Text(if(ru)"Тренировочный тест: 10 вопросов, 15 минут. Формат не является официальным." else "Санҷиши омӯзишӣ: 10 савол, 15 дақиқа. Ин формати расмӣ нест.",color=Muted,fontSize=12.sp)
    }
}

@Composable private fun ExamResult(correct:Int,total:Int,lang:String,missed:List<Question>,onReview:()->Unit,onClose:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Text(if(lang=="ru")"Тест завершён" else "Санҷиш анҷом ёфт",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Color.White); Spacer(Modifier.height(16.dp)); Text("$correct / $total",fontSize=40.sp,color=Yellow,fontWeight=FontWeight.Bold); Text(if(lang=="ru")"Это тренировочный результат, не официальный экзамен." else "Ин натиҷаи омӯзишӣ аст, на имтиҳони расмӣ.",color=Muted); Spacer(Modifier.height(20.dp)); if(missed.isNotEmpty()) OutlinedButton(onClick=onReview){Text(if(lang=="ru")"Разобрать ошибки" else "Таҳлили хатоҳо",color=Yellow)}; Button(onClick=onClose,colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Navy)){Text(if(lang=="ru")"На главную" else "Ба саҳифаи асосӣ")} }
}

@Composable private fun SavedScreen(lang:String,bookmarked:List<Question>,wrong:List<Question>,savedIds:Set<Int>,onStart:(List<Question>)->Unit,onToggle:(Int)->Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) { Text(if(lang=="ru")"Ошибки и закладки" else "Хатоҳо ва нишонаҳо",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White); Spacer(Modifier.height(8.dp)); Text(if(lang=="ru")"Сохранено: ${bookmarked.size} · Ошибок: ${wrong.size}" else "Сабтшуда: ${bookmarked.size} · Хатоҳо: ${wrong.size}",color=Muted); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={onStart(wrong)}){Text(if(lang=="ru")"Повторить ошибки" else "Такрори хатоҳо",color=Yellow)};OutlinedButton(onClick={onStart(bookmarked)}){Text(if(lang=="ru")"Закладки" else "Нишонаҳо",color=Yellow)}}; LazyColumn(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items((bookmarked+wrong).distinctBy{it.id}){q->CardBox { Text(if(lang=="ru")q.promptRu else q.promptTj,fontWeight=FontWeight.SemiBold); TextButton(onClick={onToggle(q.id)}){Text(if(q.id in savedIds)if(lang=="ru")"Убрать закладку" else "Хориҷ кардани нишона" else if(lang=="ru")"В закладки" else "Ба нишонаҳо",color=Yellow)} }}} }
}

@Composable private fun ProgressScreen(lang:String,answers:List<AnswerRecord>,sessions:List<SessionRecord>,errors:Int) { val accuracy=if(answers.isEmpty())0 else answers.count{it.correct}*100/answers.size; val days=sessions.map{java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.distinct().sortedDescending(); var streak=0; var expected=java.time.LocalDate.now(); for(day in days){if(day==expected){streak++;expected=expected.minusDays(1)}else if(day==expected.minusDays(1)&&streak==0){streak++;expected=day.minusDays(1)}else break}; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(lang=="ru")"Прогресс" else "Пешрафт",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);StatTile(if(lang=="ru")"Тренировок завершено" else "Машғулиятҳои анҷомшуда",sessions.size.toString(),Modifier.fillMaxWidth());StatTile(if(lang=="ru")"Точность ответов" else "Дақиқии ҷавобҳо","$accuracy%",Modifier.fillMaxWidth());StatTile(if(lang=="ru")"Вопросов на повтор" else "Саволҳо барои такрор",errors.toString(),Modifier.fillMaxWidth());StatTile(if(lang=="ru")"Серия дней" else "Рӯзҳои пайдарпай",streak.toString(),Modifier.fillMaxWidth())} }

@Composable private fun SettingsScreen(lang:String,category:String,total:Int,onLanguage:(String)->Unit,onCategory:(String)->Unit,onReset:()->Unit) { var confirm by remember{mutableStateOf(false)}; val context= androidx.compose.ui.platform.LocalContext.current; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(lang=="ru")"Настройки" else "Танзимот",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);CardBox{Text(if(lang=="ru")"Язык интерфейса" else "Забони барнома",fontWeight=FontWeight.Bold);Row{TextButton(onClick={onLanguage("ru")}){Text("Русский",color=if(lang=="ru")Yellow else Muted)};TextButton(onClick={onLanguage("tj")}){Text("Тоҷикӣ",color=if(lang=="tj")Yellow else Muted)}}};CardBox{Text(if(lang=="ru")"Категория прав" else "Категорияи ҳуқуқ",fontWeight=FontWeight.Bold);Row { listOf("A","B","C","D","BE","CE").forEach{ OutlinedButton(onClick={onCategory(it)},modifier=Modifier.padding(2.dp)){Text(it,color=if(category==it)Yellow else Muted)} } }};CardBox{Text(if(lang=="ru")"Материалы и источники" else "Мавод ва манбаъҳо",fontWeight=FontWeight.Bold);Text("ADLIA · Постановление №323 от 29.06.2017 · статус: действующий · редакция/статус проверены 10.11.2026",color=Muted,fontSize=13.sp);Text("https://mmih.adlia.tj/SEARCH/DocumentView?DocumentId=129930",color=Yellow,fontSize=12.sp);Text(if(lang=="ru")"Вопросы в этой версии — авторские демонстрационные упражнения. Официальный формат экзамена не подтверждён." else "Саволҳои ин версия машқҳои намоишии муаллифӣ мебошанд. Формати расмии имтиҳон тасдиқ нашудааст.",color=Muted,fontSize=13.sp)};CardBox{Text(if(lang=="ru")"Конфиденциальность" else "Махфият",fontWeight=FontWeight.Bold);Text(if(lang=="ru")"Прогресс и настройки хранятся на устройстве; приложение не отправляет личные данные." else "Пешрафт ва танзимот дар дастгоҳ нигоҳ дошта мешаванд; барнома маълумоти шахсиро намефиристад.",color=Muted);TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(PRIVACY_URL)))}){Text(if(lang=="ru")"Открыть политику конфиденциальности" else "Кушодани сиёсати махфият",color=Yellow)}};OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text(if(lang=="ru")"Сбросить прогресс и закладки" else "Пешрафт ва нишонаҳоро пок кардан",color=Color(0xFFFF8A80))};Text("Версия 1.0.0 · $total ${if(lang=="ru")"вопросов" else "савол"}",color=Muted);Text(if(lang=="ru")"Для релиза: сформировать подписанный AAB, заполнить Data safety, целевую аудиторию, карточку приложения и пройти тестирование в Play Console." else "Барои нашр: AAB-и имзошударо омода кунед, Data safety, гурӯҳи мақсаднок ва саҳифаи барномаро пур карда, санҷишро дар Play Console гузаред.",color=Muted,fontSize=12.sp)};if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(if(lang=="ru")"Сбросить данные?" else "Маълумот пок шавад?")},text={Text(if(lang=="ru")"Будут удалены локальные ответы и закладки." else "Ҷавобҳо ва нишонаҳои маҳаллӣ нест мешаванд.")},confirmButton={TextButton(onClick={onReset;confirm=false}){Text(if(lang=="ru")"Сбросить" else "Пок кардан")}},dismissButton={TextButton(onClick={confirm=false}){Text(if(lang=="ru")"Отмена" else "Бекор")}}) }

@Composable private fun BottomBar(current:String,lang:String,onSelect:(String)->Unit) { NavigationBar(containerColor=Panel){ val items=listOf("home" to if(lang=="ru")"Главная" else "Асосӣ","topics" to if(lang=="ru")"Темы" else "Мавзӯъҳо","saved" to if(lang=="ru")"Повтор" else "Такрор","progress" to if(lang=="ru")"Прогресс" else "Пешрафт","settings" to if(lang=="ru")"Ещё" else "Дигар");items.forEach{(key,label)->NavigationBarItem(selected=current==key,onClick={onSelect(key)},icon={Icon(when(key){"home"->Icons.Default.Home;"topics"->Icons.Default.MenuBook;"saved"->Icons.Default.Bookmark;"progress"->Icons.Default.Insights;else->Icons.Default.Settings},label,Modifier.size(21.dp))},label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Yellow,selectedTextColor=Yellow,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=Navy))} } }

@Composable private fun CardBox(onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit) { Card(onClick=onClick?:{},enabled=onClick!=null,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)} }
@Composable private fun StatTile(title:String,value:String,modifier:Modifier=Modifier){Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Panel),modifier=modifier){Column(Modifier.padding(15.dp)){Text(title,color=Muted,fontSize=13.sp);Text(value,color=Yellow,fontSize=24.sp,fontWeight=FontWeight.Bold)}}}
@Composable private fun ActionButton(label:String,onClick:()->Unit){Button(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Navy)){Text(label,fontWeight=FontWeight.Bold,fontSize=16.sp)}}
