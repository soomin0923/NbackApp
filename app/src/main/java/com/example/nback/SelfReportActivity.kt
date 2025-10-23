package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.os.Environment
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

    // ▼ 변경: 단순화된 데이터 관리 변수
    private lateinit var surveyResultsFile: File
    private lateinit var participantBaseDir: File

    // 전달받은 데이터
    private var blockNumber = 0
    private var blockName = ""
    private var participantName = ""
    private var surveyType = "baseline" // baseline, middle, final
    private var surveyFilePath = ""
    private var participantBaseDirPath = ""

    // 설문 응답 저장
    private val surveyResponses = mutableMapOf<String, Any>()

    // STAI-State 6문항 (표준화된 문항)
    private val staiQuestions = listOf(
        "나는 차분하다", // 역문항 (1)
        "나는 긴장되어 있다", // 정문항 (2)
        "나는 편안하다", // 역문항 (3)
        "나는 걱정스럽다", // 정문항 (4)
        "나는 만족스럽다", // 역문항 (5)
        "나는 불안하다" // 정문항 (6)
    )

    // 역문항 인덱스 (0-based)
    private val reverseItems = setOf(0, 2, 4) // 1, 3, 5번 문항

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_report_dynamic)

        // Intent에서 데이터 받기
        blockNumber = intent.getIntExtra("blockNumber", 0)
        blockName = intent.getStringExtra("blockName") ?: ""
        participantName = intent.getStringExtra("participantName") ?: "Unknown"
        surveyType = intent.getStringExtra("surveyType") ?: determineSurveyType()

        // ▼ 추가: MainActivity에서 전달한 경로 정보 받기
        surveyFilePath = intent.getStringExtra("surveyFilePath") ?: ""
        participantBaseDirPath = intent.getStringExtra("participantBaseDir") ?: ""

        // 피험자 설문 폴더 초기화
        initializeParticipantSurveyDirectory()

        initializeViews()
        setupSurveyContent()
        setupClickListeners()
        updateBlockInfo()
    }

    // ▼ 변경: MainActivity에서 전달받은 경로 사용 또는 fallback
    private fun initializeParticipantSurveyDirectory() {
        try {
            // MainActivity에서 전달받은 경로가 있으면 사용
            if (surveyFilePath.isNotEmpty() && participantBaseDirPath.isNotEmpty()) {
                surveyResultsFile = File(surveyFilePath)
                participantBaseDir = File(participantBaseDirPath)

                Log.d("SelfReport", "Using paths from MainActivity:")
                Log.d("SelfReport", "Survey file: ${surveyResultsFile.absolutePath}")
                Log.d("SelfReport", "Base dir: ${participantBaseDir.absolutePath}")

                // 부모 디렉토리가 존재하는지 확인
                if (!participantBaseDir.exists()) {
                    participantBaseDir.mkdirs()
                    Log.d("SelfReport", "Created participant base directory")
                }

                return
            }

            // MainActivity에서 전달받지 못한 경우 바로 fallback으로 넘어감
            Log.w("SelfReport", "No paths provided from MainActivity, creating fallback directories")

            // 모든 방법이 실패하면 Downloads 폴더에 새로 생성
            Log.w("SelfReport", "No paths from MainActivity, creating new in Downloads")
            createFallbackDirectories()

        } catch (e: Exception) {
            Log.e("SelfReport", "Survey dir init failed: ${e.message}", e)
            // 최종 fallback: 앱 전용 폴더
            createAppDirectoryFallback()
        }
    }

    // ▼ 추가: Downloads 폴더에 새로 생성하는 fallback
    private fun createFallbackDirectories() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseExperimentDir = File(downloadsDir, "NBack_Experiment_Results").apply {
                if (!exists()) mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val participantDirName = if (participantName.isNotEmpty()) {
                "${participantName}"
            } else {
                "Unknown_${timestamp}"
            }

            participantBaseDir = File(baseExperimentDir, participantDirName).apply {
                if (!exists()) mkdirs()
            }
            surveyResultsFile = File(participantBaseDir, "nback_survey.csv")

            Log.d("SelfReport", "Created fallback directories in Downloads")

        } catch (e: Exception) {
            Log.e("SelfReport", "Downloads fallback failed: ${e.message}")
            createAppDirectoryFallback()
        }
    }

    // ▼ 추가: 앱 전용 폴더 최종 fallback
    private fun createAppDirectoryFallback() {
        try {
            val baseRoot = getExternalFilesDir(null) ?: filesDir
            val baseExperimentDir = File(baseRoot, "nback_experiment_results").apply { mkdirs() }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val participantDirName = if (participantName.isNotEmpty()) {
                "${participantName}"
            } else {
                "Unknown_${timestamp}"
            }

            participantBaseDir = File(baseExperimentDir, participantDirName).apply { mkdirs() }
            surveyResultsFile = File(participantBaseDir, "nback_survey.csv")

            Log.d("SelfReport", "Using app directory fallback")

        } catch (e: Exception) {
            Log.e("SelfReport", "App directory fallback failed: ${e.message}")
            // 최후의 수단
            participantBaseDir = filesDir
            surveyResultsFile = File(filesDir, "survey_emergency.csv")
        }
    }

    private fun determineSurveyType(): String {
        return when (blockNumber) {
            0 -> "baseline"
            4 -> "middle"
            7 -> "final"
            else -> "baseline"
        }
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
            "middle" -> setupMiddleSurvey()
            "final" -> setupFinalSurvey()
        }
    }

    private fun setupBaselineSurvey() {
        titleText.text = "사전 설문조사"

        // STAI-State 기본 측정
        addSectionHeader("STAI-State (상태불안척도) - 기본 측정")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")

        staiQuestions.forEachIndexed { index, question ->
            addLikertQuestion("stai_baseline_${index + 1}", "${index + 1}. $question", 4)
        }

        // 기본 정보
        addSectionHeader("기본 정보")
        addLikertQuestion("condition", "현재 전반적인 컨디션은 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")
        addNumberInputQuestion("sleep_hours", "오늘 수면 시간은 몇 시간입니까?")
        addLikertQuestion("baseline_fatigue", "현재 피로도는 어느 정도입니까?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("baseline_mood", "현재 기분 상태는 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")

        // 추가 baseline 정보
        addLikertQuestion("baseline_stress", "현재 스트레스 수준은?", 7, "1: 전혀 스트레스 없음 ~ 7: 매우 스트레스")
        addLikertQuestion("baseline_confidence", "실험에 대한 자신감은?", 7, "1: 전혀 자신 없음 ~ 7: 매우 자신 있음")
    }

    private fun setupMiddleSurvey() {
        titleText.text = "중간 설문조사 (Block 4 완료)"

        // STAI-State 중간 측정
        addSectionHeader("STAI-State (현재 상태 측정)")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")

        staiQuestions.forEachIndexed { index, question ->
            addLikertQuestion("stai_middle_${index + 1}", "${index + 1}. $question", 4)
        }

        // NASA-TLX 3개 항목
        addSectionHeader("NASA-TLX (방금까지의 결과)")
        addScaleDescription("방금 완료한 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")

        addLikertQuestion("mental_demand_mid", "정신적 요구도: 과제를 수행하는 동안 얼마나 많은 정신적 활동이 필요했습니까?", 7)
        addLikertQuestion("frustration_mid", "좌절감: 과제를 수행하는 동안 얼마나 불안하고, 낙담하고, 짜증나고, 스트레스를 받았습니까?", 7)
        addLikertQuestion("effort_mid", "노력: 과제에서 목표를 달성하기 위해 얼마나 열심히 노력해야 했습니까?", 7)

        // 추가 질문
        addSectionHeader("중간 평가")
        addMultipleChoiceQuestion("hardest_task_mid", "지금까지의 과제 중 가장 어려웠던 것은?",
            listOf("0-back", "1-back (1회차)", "2-back (1회차)", "3-back (1회차)"))
        addMultipleChoiceQuestion("easiest_task_mid", "지금까지의 과제 중 가장 쉬웠던 것은?",
            listOf("0-back", "1-back (1회차)", "2-back (1회차)", "3-back (1회차)"))
        addLikertQuestion("concentration_mid", "지금까지 실험에 대한 집중도는?", 7, "1: 전혀 집중 안됨 ~ 7: 매우 집중함")
        addLikertQuestion("current_fatigue_mid", "현재 피로도는?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("motivation_mid", "앞으로 남은 실험을 계속할 의욕은?", 7, "1: 전혀 없음 ~ 7: 매우 높음")
        addLikertQuestion("stress_level_mid", "현재 스트레스 수준은?", 7, "1: 전혀 스트레스 없음 ~ 7: 매우 스트레스")
    }

    private fun setupFinalSurvey() {
        titleText.text = "최종 설문조사 (모든 실험 완료)"

        // STAI-State 최종 측정
        addSectionHeader("STAI-State (최종 상태 측정)")
        addScaleDescription("지금 이 순간 느끼는 상태에 대해 답해주세요.\n1: 전혀 그렇지 않다 ~ 4: 매우 그렇다")

        staiQuestions.forEachIndexed { index, question ->
            addLikertQuestion("stai_final_${index + 1}", "${index + 1}. $question", 4)
        }

        // NASA-TLX 3개 항목 (마지막 과제 대상)
        addSectionHeader("NASA-TLX (모두 마치고 나서)")
        addScaleDescription("방금 완료한 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")

        addLikertQuestion("mental_demand_final", "정신적 요구도: 과제를 수행하는 동안 얼마나 많은 정신적 활동이 필요했습니까?", 7)
        addLikertQuestion("frustration_final", "좌절감: 과제를 수행하는 동안 얼마나 불안하고, 낙담하고, 짜증나고, 스트레스를 받았습니까?", 7)
        addLikertQuestion("effort_final", "노력: 과제에서 목표를 달성하기 위해 얼마나 열심히 노력해야 했습니까?", 7)

        // 전체 실험 평가
        addSectionHeader("전체 실험 종합 평가")
        val allTasks = listOf("0-back", "1-back (1회차)", "2-back (1회차)", "3-back (1회차)", "1-back (2회차)", "2-back (2회차)", "3-back (2회차)")

        addMultipleChoiceQuestion("hardest_overall", "전체 실험에서 가장 어려웠던 과제는?", allTasks)
        addMultipleChoiceQuestion("easiest_overall", "전체 실험에서 가장 쉬웠던 과제는?", allTasks)

        // 실험 전반에 대한 평가
        addSectionHeader("실험 경험 평가")
        addLikertQuestion("overall_difficulty", "전체 실험의 난이도는 어떠셨습니까?", 7, "1: 매우 쉬움 ~ 7: 매우 어려움")
        addLikertQuestion("overall_stress", "전체 실험이 얼마나 스트레스를 주었습니까?", 7, "1: 전혀 스트레스 없음 ~ 7: 매우 스트레스")
        addLikertQuestion("overall_concentration", "전체 실험 동안의 집중도는?", 7, "1: 전혀 집중 안됨 ~ 7: 매우 집중함")
        addLikertQuestion("final_fatigue", "현재 피로도는?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("satisfaction", "전체 실험의 만족도는?", 7, "1: 매우 불만족 ~ 7: 매우 만족")

        // 주관식 질문
        addSectionHeader("자유 응답")
        addTextInputQuestion("strategy_overall", "전체 실험 동안 사용한 주요 전략이나 방법을 설명해주세요.")
        addTextInputQuestion("stress_coping", "실험 중 스트레스나 어려움을 어떻게 극복하려고 노력했습니까?")
        addTextInputQuestion("difficulty_change", "실험이 진행되면서 난이도나 스트레스 수준의 변화를 어떻게 느꼈는지 설명해주세요.")
        addTextInputQuestion("feedback_final", "실험 참여에 대한 전반적인 소감이나 개선사항을 자유롭게 작성해주세요.")
    }

    private fun addSectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 40, 0, 20)
        }
        questionsContainer.addView(header)
    }

    private fun addScaleDescription(description: String) {
        val desc = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(0, 0, 0, 20)
        }
        questionsContainer.addView(desc)
    }

    private fun addLikertQuestion(key: String, question: String, maxScale: Int, scaleDescription: String = "") {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }

        val questionText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(questionText)

        if (scaleDescription.isNotEmpty()) {
            val scaleDesc = TextView(this).apply {
                text = scaleDescription
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                setPadding(0, 0, 0, 8)
            }
            container.addView(scaleDesc)
        }

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            tag = key
            contentDescription = "1부터 ${maxScale}까지의 척도 선택"
        }

        for (i in 1..maxScale) {
            val radioButton = RadioButton(this).apply {
                text = i.toString()
                id = View.generateViewId()
            }
            radioGroup.addView(radioButton)
        }

        // 기본값 설정 (중간값)
        val middleIndex = (maxScale / 2) - 1
        (radioGroup.getChildAt(middleIndex) as RadioButton).isChecked = true
        surveyResponses[key] = middleIndex + 1

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedIndex = group.indexOfChild(group.findViewById(checkedId))
            val selectedValue = selectedIndex + 1
            surveyResponses[key] = selectedValue
        }

        container.addView(radioGroup)
        questionsContainer.addView(container)
    }

    private fun addMultipleChoiceQuestion(key: String, question: String, options: List<String>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }

        val questionText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(questionText)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            tag = key
        }

        options.forEachIndexed { index, option ->
            val radioButton = RadioButton(this).apply {
                text = option
                id = View.generateViewId()
            }
            radioGroup.addView(radioButton)
        }

        // 기본값 설정 (첫 번째 옵션)
        (radioGroup.getChildAt(0) as RadioButton).isChecked = true
        surveyResponses[key] = options[0]

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedIndex = group.indexOfChild(group.findViewById(checkedId))
            surveyResponses[key] = options[selectedIndex]
        }

        container.addView(radioGroup)
        questionsContainer.addView(container)
    }

    private fun addNumberInputQuestion(key: String, question: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }

        val questionText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(questionText)

        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "시간 입력 (예: 7)"
            tag = key
        }

        editText.setOnFocusChangeListener { _, _ ->
            val value = editText.text.toString().toIntOrNull() ?: 0
            surveyResponses[key] = value
        }

        container.addView(editText)
        questionsContainer.addView(container)
    }

    private fun addTextInputQuestion(key: String, question: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }

        val questionText = TextView(this).apply {
            text = question
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(questionText)

        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "자유롭게 작성해주세요"
            tag = key
        }

        editText.setOnFocusChangeListener { _, _ ->
            surveyResponses[key] = editText.text.toString()
        }

        container.addView(editText)
        questionsContainer.addView(container)
    }

    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            // 모든 텍스트 입력 필드의 최종 값 수집
            collectAllResponses()
            saveSurveyData()
            returnToMainActivity()
        }
    }

    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("resumeFromBlock", blockNumber)
        intent.putExtra("participantName", participantName)
        startActivity(intent)
        finish()
    }


    private fun collectAllResponses() {
        // EditText 필드들의 최종 값 수집
        for (i in 0 until questionsContainer.childCount) {
            val view = questionsContainer.getChildAt(i)
            if (view is LinearLayout) {
                for (j in 0 until view.childCount) {
                    val child = view.getChildAt(j)
                    if (child is EditText) {
                        val key = child.tag as? String
                        if (key != null) {
                            val value = if (child.inputType == android.text.InputType.TYPE_CLASS_NUMBER) {
                                child.text.toString().toIntOrNull() ?: 0
                            } else {
                                child.text.toString()
                            }
                            surveyResponses[key] = value
                        }
                    }
                }
            }
        }
    }

    private fun updateBlockInfo() {
        val description = when (surveyType) {
            "baseline" -> "실험 시작 전 기본 상태를 측정합니다.\nSTAI-State로 현재 불안/스트레스 수준을 조사합니다."
            "middle" -> "실험 중간 평가입니다.\n현재 상태불안 수준과 과제 경험을 측정합니다.\n(0-back, 1-back, 2-back, 3-back 1회차 완료)"
            "final" -> "최종 평가입니다.\n전체 실험 완료 후의 상태불안과 종합적인 경험을 측정합니다.\n(모든 7개 블록 완료)"
            else -> "설문에 참여해주세요."
        }

        blockInfoText.text = description
    }

    // ▼ 변경: 강화된 디버깅과 더 안전한 파일 저장
    private fun saveSurveyData() {
        try {
            // Ensure file exists and open in append mode
            val surveyResultsFile = File(surveyFilePath ?: run {
                // Fallback: Downloads/NBack_Experiment_Results/{participantName}/nback_survey.csv
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val baseDir = File(downloads, "NBack_Experiment_Results")
                val participantDir = File(baseDir, participantName)
                if (!participantDir.exists()) participantDir.mkdirs()
                File(participantDir, "nback_survey.csv").absolutePath
            })
            val fileObj = if (surveyResultsFile is File) surveyResultsFile else File(surveyResultsFile.toString())
            val needsHeader = !fileObj.exists() || fileObj.length() == 0L

            // Prepare current context values
            val ts = System.currentTimeMillis()
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))

            FileWriter(fileObj, true).use { fw ->
                if (needsHeader) {
                    fw.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")
                }
                surveyResponses.forEach { (key, value) ->
                    val category = when {
                        key.contains("stress", ignoreCase = true) -> "stress"
                        key.contains("place", ignoreCase = true) -> "place"
                        key.contains("activity", ignoreCase = true) -> "activity"
                        key.contains("social", ignoreCase = true) -> "social"
                        else -> "other"
                    }
                    val escapedResponse = value.toString().replace("\"", "\"\"")
                    val row = "${ts},${currentTime},${participantName},${surveyType},${blockNumber},${blockName},${key},\"${escapedResponse}\",${category}\n"
                    fw.write(row)
                }
            }
            Toast.makeText(this, "설문이 저장되었습니다: ${fileObj.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SelfReportActivity", "설문 저장 중 오류", e)
            Toast.makeText(this, "설문 저장 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }}