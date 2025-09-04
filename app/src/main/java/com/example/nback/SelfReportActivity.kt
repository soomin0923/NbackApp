package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class SelfReportActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var blockInfoText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var questionsContainer: LinearLayout
    private lateinit var submitButton: Button

    // 저장 대상
    private lateinit var surveyResultsFile: File
    private lateinit var participantBaseDir: File

    // 전달받은 데이터
    private var blockNumber = 0
    private var blockName = ""
    private var participantName = ""
    private var surveyType = "baseline" // baseline, middle, final
    private var surveyFilePath = ""
    private var participantBaseDirPath = ""

    // 응답 저장
    private val surveyResponses = mutableMapOf<String, Any>()

    // STAI 6문항
    private val staiQuestions = listOf(
        "나는 차분하다",   // 역문항 (1)
        "나는 긴장되어 있다",
        "나는 편안하다",   // 역문항 (3)
        "나는 걱정스럽다",
        "나는 만족스럽다", // 역문항 (5)
        "나는 불안하다"
    )
    private val reverseItems = setOf(0, 2, 4) // 1,3,5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_report_dynamic)

        // Intent 데이터
        blockNumber = intent.getIntExtra("blockNumber", 0)
        blockName = intent.getStringExtra("blockName") ?: ""
        participantName = intent.getStringExtra("participantName") ?: "Unknown"
        surveyType = intent.getStringExtra("surveyType") ?: determineSurveyType()

        // 메인에서 넘겨준 경로(같은 참가자 폴더) 사용
        surveyFilePath = intent.getStringExtra("surveyFilePath") ?: ""
        participantBaseDirPath = intent.getStringExtra("participantBaseDir") ?: ""

        // 설문 파일 경로 초기화
        initializeParticipantSurveyDirectory()
        ensureSurveyCsvHeader()

        initializeViews()
        setupSurveyContent()
        setupClickListeners()
        updateBlockInfo()
    }

    /** 메인에서 준 경로 우선 사용. 없으면 앱 전용 폴더에 생성 */
    private fun initializeParticipantSurveyDirectory() {
        try {
            if (surveyFilePath.isNotEmpty() && participantBaseDirPath.isNotEmpty()) {
                surveyResultsFile = File(surveyFilePath)
                participantBaseDir = File(participantBaseDirPath)
                if (!participantBaseDir.exists()) participantBaseDir.mkdirs()
                Log.d("SelfReport", "Using paths from MainActivity: ${surveyResultsFile.absolutePath}")
                return
            }

            // Fallback: 같은 앱 전용 루트에 새 폴더 생성
            val baseRoot = getExternalFilesDir(null) ?: filesDir
            val baseExperimentDir = File(baseRoot, "nback_experiment_results").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val participantDirName = if (participantName.isNotEmpty()) {
                "${participantName}_$timestamp"
            } else {
                "Unknown_$timestamp"
            }
            participantBaseDir = File(baseExperimentDir, participantDirName).apply { mkdirs() }
            surveyResultsFile = File(participantBaseDir, "survey.csv")
            Log.w("SelfReport", "Fallback to app dir: ${surveyResultsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("SelfReport", "Survey dir init failed: ${e.message}", e)
            // 최후: 내부 filesDir
            participantBaseDir = filesDir
            surveyResultsFile = File(filesDir, "survey_emergency.csv")
        }
    }

    /** 헤더가 없으면 생성 (중복 헤더 방지) */
    private fun ensureSurveyCsvHeader() {
        try {
            if (!surveyResultsFile.exists() || surveyResultsFile.length() == 0L) {
                surveyResultsFile.parentFile?.mkdirs()
                FileWriter(surveyResultsFile, true).use { w ->
                    w.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")
                }
                Log.d("SelfReport", "Survey header created: ${surveyResultsFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("SelfReport", "ensureSurveyCsvHeader failed: ${e.message}", e)
        }
    }

    private fun determineSurveyType(): String = when (blockNumber) {
        0 -> "baseline"
        4 -> "middle"
        7 -> "final"
        else -> "baseline"
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        blockInfoText = findViewById(R.id.blockInfoText)
        scrollView = findViewById(R.id.scrollView)
        questionsContainer = findViewById(R.id.questionsContainer)
        submitButton = findViewById(R.id.submitButton)
    }

    private fun setupSurveyContent() {
        questionsContainer.removeAllViews()
        when (surveyType) {
            "baseline" -> setupBaselineSurvey()
            "middle"   -> setupMiddleSurvey()
            "final"    -> setupFinalSurvey()
        }
    }

    private fun setupBaselineSurvey() {
        titleText.text = "사전 설문조사"
        addSectionHeader("STAI-State (상태불안척도) - 기본 측정")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")
        staiQuestions.forEachIndexed { idx, q -> addLikertQuestion("stai_baseline_${idx + 1}", "${idx + 1}. $q", 4) }

        addSectionHeader("기본 정보")
        addLikertQuestion("condition", "현재 전반적인 컨디션은 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")
        addNumberInputQuestion("sleep_hours", "오늘 수면 시간은 몇 시간입니까?")
        addLikertQuestion("baseline_fatigue", "현재 피로도는 어느 정도입니까?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("baseline_mood", "현재 기분 상태는 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")
        addLikertQuestion("baseline_stress", "현재 스트레스 수준은?", 7, "1: 전혀 스트레스 없음 ~ 7: 매우 스트레스")
        addLikertQuestion("baseline_confidence", "실험에 대한 자신감은?", 7, "1: 전혀 자신 없음 ~ 7: 매우 자신 있음")
    }

    private fun setupMiddleSurvey() {
        titleText.text = "중간 설문조사 (Block 4 완료)"
        addSectionHeader("STAI-State (현재 상태 측정)")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")
        staiQuestions.forEachIndexed { idx, q -> addLikertQuestion("stai_middle_${idx + 1}", "${idx + 1}. $q", 4) }

        addSectionHeader("NASA-TLX (방금까지의 결과)")
        addScaleDescription("방금 완료한 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")
        addLikertQuestion("mental_demand_mid", "정신적 요구도...", 7)
        addLikertQuestion("frustration_mid", "좌절감...", 7)
        addLikertQuestion("effort_mid", "노력...", 7)

        addSectionHeader("중간 평가")
        addMultipleChoiceQuestion("hardest_task_mid", "가장 어려웠던 것은?", listOf("0-back","1-back (1회차)","2-back (1회차)","3-back (1회차)"))
        addMultipleChoiceQuestion("easiest_task_mid", "가장 쉬웠던 것은?", listOf("0-back","1-back (1회차)","2-back (1회차)","3-back (1회차)"))
        addLikertQuestion("concentration_mid", "집중도는?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("current_fatigue_mid", "현재 피로도는?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("motivation_mid", "남은 실험 의욕은?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("stress_level_mid", "현재 스트레스 수준은?", 7, "1: 전혀 ~ 7: 매우")
    }

    private fun setupFinalSurvey() {
        titleText.text = "최종 설문조사 (모든 실험 완료)"
        addSectionHeader("STAI-State (최종 상태 측정)")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")
        staiQuestions.forEachIndexed { idx, q -> addLikertQuestion("stai_final_${idx + 1}", "${idx + 1}. $q", 4) }

        addSectionHeader("NASA-TLX (모두 마치고 나서)")
        addScaleDescription("방금 완료한 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")
        addLikertQuestion("mental_demand_final", "정신적 요구도...", 7)
        addLikertQuestion("frustration_final", "좌절감...", 7)
        addLikertQuestion("effort_final", "노력...", 7)

        addSectionHeader("전체 실험 종합 평가")
        val all = listOf("0-back","1-back (1회차)","2-back (1회차)","3-back (1회차)","1-back (2회차)","2-back (2회차)","3-back (2회차)")
        addMultipleChoiceQuestion("hardest_overall", "가장 어려웠던 과제?", all)
        addMultipleChoiceQuestion("easiest_overall", "가장 쉬웠던 과제?", all)

        addSectionHeader("실험 경험 평가")
        addLikertQuestion("overall_difficulty", "전체 난이도?", 7, "1: 매우 쉬움 ~ 7: 매우 어려움")
        addLikertQuestion("overall_stress", "전체 스트레스?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("overall_concentration", "전체 집중도?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("final_fatigue", "현재 피로도?", 7, "1: 전혀 ~ 7: 매우")
        addLikertQuestion("satisfaction", "만족도?", 7, "1: 매우 불만족 ~ 7: 매우 만족")

        addSectionHeader("자유 응답")
        addTextInputQuestion("strategy_overall", "사용한 전략/방법")
        addTextInputQuestion("stress_coping", "스트레스/어려움 대처")
        addTextInputQuestion("difficulty_change", "난이도/스트레스 변화")
        addTextInputQuestion("feedback_final", "전반 소감/개선사항")
    }

    private fun addSectionHeader(title: String) {
        val tv = TextView(this).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 40, 0, 20)
        }
        questionsContainer.addView(tv)
    }

    private fun addScaleDescription(description: String) {
        val tv = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(0, 0, 0, 20)
        }
        questionsContainer.addView(tv)
    }

    private fun addLikertQuestion(key: String, question: String, maxScale: Int, scaleDescription: String = "") {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        val qText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(qText)

        if (scaleDescription.isNotEmpty()) {
            val d = TextView(this).apply {
                text = scaleDescription
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                setPadding(0, 0, 0, 8)
            }
            container.addView(d)
        }

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            tag = key
            contentDescription = "1부터 ${maxScale}까지의 척도 선택"
        }
        for (i in 1..maxScale) {
            val rb = RadioButton(this).apply {
                text = i.toString()
                id = View.generateViewId()
            }
            group.addView(rb)
        }
        val middleIndex = (maxScale / 2) - 1
        (group.getChildAt(middleIndex) as RadioButton).isChecked = true
        surveyResponses[key] = middleIndex + 1

        group.setOnCheckedChangeListener { g, checkedId ->
            val idx = g.indexOfChild(g.findViewById(checkedId))
            surveyResponses[key] = idx + 1
        }

        container.addView(group)
        questionsContainer.addView(container)
    }

    private fun addMultipleChoiceQuestion(key: String, question: String, options: List<String>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        val qText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(qText)

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            tag = key
        }
        options.forEach { opt ->
            val rb = RadioButton(this).apply {
                text = opt
                id = View.generateViewId()
            }
            group.addView(rb)
        }
        (group.getChildAt(0) as RadioButton).isChecked = true
        surveyResponses[key] = options[0]

        group.setOnCheckedChangeListener { g, checkedId ->
            val idx = g.indexOfChild(g.findViewById(checkedId))
            surveyResponses[key] = options[idx]
        }

        container.addView(group)
        questionsContainer.addView(container)
    }

    private fun addNumberInputQuestion(key: String, question: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        val qText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(qText)

        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "시간 입력 (예: 7)"
            tag = key
        }
        container.addView(et)
        questionsContainer.addView(container)
    }

    private fun addTextInputQuestion(key: String, question: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        val qText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(qText)

        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "자유롭게 작성해주세요"
            tag = key
        }
        container.addView(et)
        questionsContainer.addView(container)
    }

    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            collectAllResponses()
            saveSurveyData()
            returnToMainActivity()
        }
    }

    /** 포커스 이동 없이도 EditText 값 모두 수집 */
    private fun collectAllResponses() {
        for (i in 0 until questionsContainer.childCount) {
            val section = questionsContainer.getChildAt(i)
            if (section is LinearLayout) {
                for (j in 0 until section.childCount) {
                    val child = section.getChildAt(j)
                    if (child is EditText) {
                        val key = child.tag as? String ?: continue
                        val isNumber = (child.inputType and android.text.InputType.TYPE_CLASS_NUMBER) == android.text.InputType.TYPE_CLASS_NUMBER
                        val value = if (isNumber) child.text.toString().toIntOrNull() ?: 0 else child.text.toString()
                        surveyResponses[key] = value
                    }
                }
            }
        }
    }

    private fun updateBlockInfo() {
        val desc = when (surveyType) {
            "baseline" -> "실험 시작 전 기본 상태를 측정합니다.\nSTAI-State로 현재 불안/스트레스 수준을 조사합니다."
            "middle"   -> "실험 중간 평가입니다.\n현재 상태불안 수준과 과제 경험을 측정합니다.\n(0-back, 1-back, 2-back, 3-back 1회차 완료)"
            "final"    -> "최종 평가입니다.\n모든 실험 완료 후 상태불안과 종합 경험을 측정합니다.\n(7개 블록 완료)"
            else       -> "설문에 참여해주세요."
        }
        blockInfoText.text = desc
    }

    /** 안전한 append 저장 */
    private fun saveSurveyData() {
        Log.d("SelfReport", "Saving survey: ${surveyResultsFile.absolutePath}, ${surveyResponses.size} entries")

        if (surveyResponses.isEmpty()) {
            Toast.makeText(this, "저장할 설문 응답이 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            surveyResultsFile.parentFile?.mkdirs()
            val needsHeader = !surveyResultsFile.exists() || surveyResultsFile.length() == 0L

            FileWriter(surveyResultsFile, true).use { w ->
                if (needsHeader) {
                    w.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")
                }
                val nowFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentTime = nowFmt.format(Date())
                val nowMs = System.currentTimeMillis()

                // 키 정렬해서 안정적 기록
                surveyResponses.toSortedMap().forEach { (key, value) ->
                    val category = when {
                        key.startsWith("stai_baseline_") -> "STAI-State_Baseline"
                        key.startsWith("stai_middle_")   -> "STAI-State_Middle"
                        key.startsWith("stai_final_")     -> "STAI-State_Final"
                        key.contains("mental_demand")     -> "NASA-TLX_Mental_Demand"
                        key.contains("frustration")       -> "NASA-TLX_Frustration"
                        key.contains("effort")            -> "NASA-TLX_Effort"
                        key.contains("fatigue")           -> "Fatigue"
                        key.contains("concentration")     -> "Concentration"
                        key.contains("satisfaction")      -> "Satisfaction"
                        key.contains("strategy")          -> "Strategy"
                        key.contains("feedback")          -> "Feedback"
                        key == "sleep_hours"              -> "Basic_Info"
                        key == "condition"                -> "Basic_Info"
                        key.contains("stress")            -> "Stress_Level"
                        key.contains("hardest")           -> "Task_Evaluation"
                        key.contains("easiest")           -> "Task_Evaluation"
                        key.contains("mood")              -> "Mood"
                        key.contains("confidence")        -> "Confidence"
                        key.contains("motivation")        -> "Motivation"
                        key.contains("overall")           -> "Overall_Evaluation"
                        else                              -> "Other"
                    }
                    val escaped = value.toString().replace("\"", "\"\"")
                    w.write("$nowMs,$currentTime,$participantName,$surveyType,$blockNumber,$blockName,$key,\"$escaped\",$category\n")
                }
            }

            calculateAndLogSTAIScores()
            val size = surveyResultsFile.length()
            Toast.makeText(this, "✅ 설문 저장 완료 (${size} bytes)\n${surveyResultsFile.name}", Toast.LENGTH_LONG).show()
            Log.d("SelfReport", "Survey save OK, size=$size")

        } catch (e: Exception) {
            Log.e("SelfReport", "Survey save failed, fallback to internal", e)
            tryInternalStorageFallback()
        }
    }

    /** 내부 저장소 비상 저장 */
    private fun tryInternalStorageFallback() {
        try {
            val file = File(filesDir, "survey_emergency.csv")
            val needsHeader = !file.exists() || file.length() == 0L
            FileWriter(file, true).use { w ->
                if (needsHeader) {
                    w.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")
                }
                val nowFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentTime = nowFmt.format(Date())
                val nowMs = System.currentTimeMillis()
                surveyResponses.forEach { (key, value) ->
                    val escaped = value.toString().replace("\"", "\"\"")
                    w.write("$nowMs,$currentTime,$participantName,$surveyType,$blockNumber,$blockName,$key,\"$escaped\",Emergency_Save\n")
                }
            }
            Toast.makeText(this, "⚠️ 비상 저장(내부): ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ 완전 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("SelfReport", "Emergency save failed", e)
        }
    }

    /** STAI 점수 로깅(연구자 참고) */
    private fun calculateAndLogSTAIScores() {
        try {
            val stai = surveyResponses.filter { it.key.startsWith("stai_") }
            if (stai.isEmpty()) return

            var total = 0
            var count = 0
            for (i in 1..6) {
                val key = "stai_${surveyType}_$i"
                val raw = surveyResponses[key] as? Int ?: continue
                val processed = if (reverseItems.contains(i - 1)) 5 - raw else raw
                total += processed
                count++
            }
            if (count == 6) {
                Log.d("STAI-Score", "Participant=$participantName, Survey=$surveyType, Total=$total (6~24)")
            }
        } catch (e: Exception) {
            Log.e("STAI-Score", "calc failed: ${e.message}")
        }
    }

    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("resumeFromBlock", blockNumber)
            putExtra("participantName", participantName)
            putExtra("participantBaseDir", participantBaseDir.absolutePath)   // ✅ 추가
            putExtra("surveyFilePath", surveyResultsFile.absolutePath)        // (옵션) 유지
        }
        startActivity(intent)
        finish()
    }

}
