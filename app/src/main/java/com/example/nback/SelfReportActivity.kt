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

    // ====== 추가된 변수 ======
    private lateinit var participantSurveyDir: File

    // 전달받은 데이터
    private var blockNumber = 0
    private var blockName = ""
    private var participantName = ""
    private var surveyType = "baseline" // baseline, middle, final

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

        // ====== 추가된 초기화 ======
        initializeParticipantSurveyDirectory()

        initializeViews()
        setupSurveyContent()
        setupClickListeners()
        updateBlockInfo()
    }

    // ====== 새로 추가된 함수 ======
    private fun initializeParticipantSurveyDirectory() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseExperimentDir = File(downloadsDir, "nback_experiment_results")
            val participantBaseDir = File(baseExperimentDir, participantName)
            participantSurveyDir = File(participantBaseDir, "survey_results")

            // 폴더가 없으면 생성
            if (!participantSurveyDir.exists()) {
                participantSurveyDir.mkdirs()
            }

            Log.d("SelfReport", "Survey directory initialized: ${participantSurveyDir.absolutePath}")

        } catch (e: Exception) {
            Log.e("SelfReport", "Failed to initialize survey directory: ${e.message}")
            // 실패 시 기본 Downloads 폴더 사용
            participantSurveyDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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
            setTextColor(resources.getColor(android.R.color.darker_gray))
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
                setTextColor(resources.getColor(android.R.color.darker_gray))
                setPadding(0, 0, 0, 8)
            }
            container.addView(scaleDesc)
        }

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            tag = key
            contentDescription = "1부터 ${maxScale}까지의 척도 선택"
        }

        // STAI는 1-4 척도, 다른 척도는 1부터 시작
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
            "middle" -> "실험 중간 평가입니다.\n현재 상태불안 수준과 과제 경험을 측정합니다.\n(0-back, 1-back, 2-back 1회차 완료)"
            "final" -> "최종 평가입니다.\n전체 실험 완료 후의 상태불안과 종합적인 경험을 측정합니다.\n(모든 5개 블록 완료)"
            else -> "설문에 참여해주세요."
        }

        blockInfoText.text = description
    }

    // ====== 수정된 설문 저장 함수 ======
    private fun saveSurveyData() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

            // 개별 파일명 생성 (피험자별 설문 파일)
            val fileName = when (surveyType) {
                "baseline" -> "survey_baseline_${participantName}_${timestamp}.csv"
                "middle" -> "survey_middle_block${blockNumber}_${participantName}_${timestamp}.csv"
                "final" -> "survey_final_block${blockNumber}_${participantName}_${timestamp}.csv"
                else -> "survey_${surveyType}_${participantName}_${timestamp}.csv"
            }

            val file = File(participantSurveyDir, fileName)
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            FileWriter(file).use { writer ->
                // 헤더 작성
                writer.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")

                surveyResponses.forEach { (key, value) ->
                    // 질문 카테고리 분류
                    val category = when {
                        key.startsWith("stai_baseline_") -> "STAI-State_Baseline"
                        key.startsWith("stai_middle_") -> "STAI-State_Middle"
                        key.startsWith("stai_final_") -> "STAI-State_Final"
                        key.contains("mental_demand") -> "NASA-TLX_Mental_Demand"
                        key.contains("frustration") -> "NASA-TLX_Frustration"
                        key.contains("effort") -> "NASA-TLX_Effort"
                        key.contains("fatigue") -> "Fatigue"
                        key.contains("concentration") -> "Concentration"
                        key.contains("satisfaction") -> "Satisfaction"
                        key.contains("strategy") -> "Strategy"
                        key.contains("feedback") -> "Feedback"
                        key == "sleep_hours" -> "Basic_Info"
                        key == "condition" -> "Basic_Info"
                        key.contains("stress_level") -> "Stress_Level"
                        key.contains("hardest") -> "Task_Evaluation"
                        key.contains("easiest") -> "Task_Evaluation"
                        else -> "Other"
                    }

                    writer.write("${System.currentTimeMillis()},$currentTime,$participantName,$surveyType,$blockNumber,$blockName,$key,\"$value\",$category\n")
                }
            }

            // STAI-State 점수 계산 및 로그 (연구자용)
            calculateAndLogSTAIScores()

            Log.d("SelfReport", "Survey data saved: ${file.absolutePath}")
            Toast.makeText(this, "설문 응답이 저장되었습니다: ${file.name}", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("SelfReport", "Failed to save survey data", e)
            Toast.makeText(this, "설문 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // STAI-State 점수 계산 (연구 참고용)
    private fun calculateAndLogSTAIScores() {
        try {
            val staiResponses = surveyResponses.filter { it.key.contains("stai_") }
            if (staiResponses.isNotEmpty()) {
                var totalScore = 0
                var staiItemCount = 0

                staiResponses.forEach { (key, value) ->
                    if (value is Int && key.matches(Regex("stai_\\w+_\\d+"))) {
                        // 문항 번호 추출 (1-6)
                        val itemNumber = key.substringAfterLast("_").toIntOrNull()
                        if (itemNumber != null && itemNumber in 1..6) {
                            val itemIndex = itemNumber - 1 // 0-based index

                            // 역문항 처리 (1, 3, 5번 = 인덱스 0, 2, 4)
                            val processedScore = if (reverseItems.contains(itemIndex)) {
                                5 - value // 역채점: 1->4, 2->3, 3->2, 4->1
                            } else {
                                value // 정채점
                            }

                            totalScore += processedScore
                            staiItemCount++
                        }
                    }
                }

                if (staiItemCount == 6) {
                    Log.d("STAI-Score", "Participant: $participantName, Survey: $surveyType, STAI-State Total: $totalScore")
                    Log.d("STAI-Score", "Score interpretation: 6-24 range, higher = more anxiety/stress")

                    // 개별 문항 점수 로그
                    val itemScores = mutableListOf<Int>()
                    for (i in 1..6) {
                        val key = "stai_${surveyType}_$i"
                        val rawScore = surveyResponses[key] as? Int ?: 0
                        val processedScore = if (reverseItems.contains(i-1)) 5 - rawScore else rawScore
                        itemScores.add(processedScore)
                    }
                    Log.d("STAI-Score", "Item scores (processed): ${itemScores.joinToString(", ")}")
                    Log.d("STAI-Score", "Reverse items (1,3,5) were reverse-scored automatically")
                }
            }
        } catch (e: Exception) {
            Log.e("STAI-Score", "Failed to calculate STAI score: ${e.message}")
        }
    }

    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("resumeFromBlock", blockNumber)
        intent.putExtra("participantName", participantName)
        startActivity(intent)
        finish()
    }
}