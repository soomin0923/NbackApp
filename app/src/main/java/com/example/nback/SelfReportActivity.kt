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

    // 전달받은 데이터
    private var blockNumber = 0
    private var blockName = ""
    private var participantName = ""
    private var surveyType = "baseline" // baseline, middle, final

    // 설문 응답 저장
    private val surveyResponses = mutableMapOf<String, Any>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_report_dynamic)

        // Intent에서 데이터 받기
        blockNumber = intent.getIntExtra("blockNumber", 0)
        blockName = intent.getStringExtra("blockName") ?: ""
        participantName = intent.getStringExtra("participantName") ?: "Unknown"
        surveyType = intent.getStringExtra("surveyType") ?: determineSurveyType()

        initializeViews()
        setupSurveyContent()
        setupClickListeners()
        updateBlockInfo()
    }

    private fun determineSurveyType(): String {
        return when (blockNumber) {
            0 -> "baseline"
            3 -> "middle"
            5 -> "final"
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

        // PSS-10 질문들 (Baseline - 일반적 스트레스)
        val pssQuestions = listOf(
            "지난 한 달 동안, 예상치 못한 일이 일어나서 당황한 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 인생의 중요한 것들을 통제할 수 없다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 신경이 예민하고 스트레스를 받았다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 개인적인 문제들을 성공적으로 다뤄왔다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 인생이 자신의 뜻대로 진행되고 있다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 해야 할 일들을 감당할 수 없다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 자신의 분노를 통제할 수 있었다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 일들이 잘 풀리고 있다고 느낀 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 통제할 수 없는 일들 때문에 화가 났던 적이 얼마나 자주 있었습니까?",
            "지난 한 달 동안, 어려움들이 너무 많이 쌓여서 극복할 수 없을 것 같다고 느낀 적이 얼마나 자주 있었습니까?"
        )

        // PSS-10 척도 설명
        addSectionHeader("PSS-10 (지각된 스트레스 척도) - 기본 측정")
        addScaleDescription("지난 한 달 동안의 전반적인 생활에 대해 질문합니다.\n1: 전혀 그렇지 않다 ~ 5: 매우 자주 그렇다")

        pssQuestions.forEachIndexed { index, question ->
            addLikertQuestion("pss_baseline_${index + 1}", "${index + 1}. $question", 5)
        }

        // 기본 정보
        addSectionHeader("기본 정보")
        addLikertQuestion("condition", "현재 전반적인 컨디션은 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")
        addNumberInputQuestion("sleep_hours", "오늘 수면 시간은 몇 시간입니까?")
        addLikertQuestion("baseline_fatigue", "현재 피로도는 어느 정도입니까?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("baseline_mood", "현재 기분 상태는 어떠십니까?", 7, "1: 매우 나쁨 ~ 7: 매우 좋음")
    }

    private fun setupMiddleSurvey() {
        titleText.text = "중간 설문조사 (Block 3 완료)"

        // PSS-10 질문들 (실험 과정 중 스트레스 - 수정된 문구)
        val pssQuestions = listOf(
            "지금까지의 실험 과정에서, 예상치 못한 일이 일어나서 당황한 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 과제들을 통제할 수 없다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 신경이 예민하고 스트레스를 받았다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 문제들을 성공적으로 해결했다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험이 자신의 예상대로 진행되고 있다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 주어진 과제들을 감당할 수 없다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 자신의 감정을 잘 통제할 수 있었다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 일들이 잘 풀리고 있다고 느낀 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 통제할 수 없는 상황들 때문에 화가 났던 적이 얼마나 자주 있었습니까?",
            "지금까지의 실험 과정에서, 어려움들이 너무 많이 쌓여서 극복할 수 없을 것 같다고 느낀 적이 얼마나 자주 있었습니까?"
        )

        addSectionHeader("PSS-10 (실험 과정 중 스트레스 측정)")
        addScaleDescription("지금까지 진행한 실험 과정에서의 경험에 대해 질문합니다.\n1: 전혀 그렇지 않다 ~ 5: 매우 자주 그렇다")

        pssQuestions.forEachIndexed { index, question ->
            addLikertQuestion("pss_middle_${index + 1}", "${index + 1}. $question", 5)
        }

        // NASA-TLX 3개 항목
        addSectionHeader("NASA-TLX (방금 완료한 2-Back 과제)")
        addScaleDescription("방금 완료한 2-Back (1회차) 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")

        addLikertQuestion("mental_demand_mid", "정신적 요구도: 2-Back 과제를 수행하는 동안 얼마나 많은 정신적 활동이 필요했습니까?", 7)
        addLikertQuestion("frustration_mid", "좌절감: 2-Back 과제를 수행하는 동안 얼마나 불안하고, 낙담하고, 짜증나고, 스트레스를 받았습니까?", 7)
        addLikertQuestion("effort_mid", "노력: 2-Back 과제에서 목표를 달성하기 위해 얼마나 열심히 노력해야 했습니까?", 7)

        // 추가 질문
        addSectionHeader("중간 평가")
        addMultipleChoiceQuestion("hardest_task_mid", "지금까지의 과제 중 가장 어려웠던 것은?",
            listOf("0-back", "1-back (1회차)", "2-back (1회차)"))
        addMultipleChoiceQuestion("easiest_task_mid", "지금까지의 과제 중 가장 쉬웠던 것은?",
            listOf("0-back", "1-back (1회차)", "2-back (1회차)"))
        addLikertQuestion("concentration_mid", "지금까지 실험에 대한 집중도는?", 7, "1: 전혀 집중 안됨 ~ 7: 매우 집중함")
        addLikertQuestion("current_fatigue_mid", "현재 피로도는?", 7, "1: 전혀 피곤하지 않음 ~ 7: 매우 피곤함")
        addLikertQuestion("motivation_mid", "앞으로 남은 실험을 계속할 의욕은?", 7, "1: 전혀 없음 ~ 7: 매우 높음")
    }

    private fun setupFinalSurvey() {
        titleText.text = "최종 설문조사 (모든 실험 완료)"

        // PSS-10 질문들 (최종 스트레스 수준 측정 - 현재 상태 중심)
        val pssQuestions = listOf(
            "전체 실험을 마친 지금, 예상치 못한 일들로 인해 당황스러운 느낌이 드십니까?",
            "전체 실험을 마친 지금, 중요한 것들을 통제할 수 없다고 느끼십니까?",
            "전체 실험을 마친 지금, 신경이 예민하고 스트레스를 받고 있다고 느끼십니까?",
            "전체 실험을 마친 지금, 문제들을 성공적으로 해결했다고 느끼십니까?",
            "전체 실험을 마친 지금, 일들이 자신의 뜻대로 진행되었다고 느끼십니까?",
            "전체 실험을 마친 지금, 감당하기 어려운 상황에 있다고 느끼십니까?",
            "전체 실험을 마친 지금, 자신의 감정을 잘 통제할 수 있다고 느끼십니까?",
            "전체 실험을 마친 지금, 일들이 잘 풀리고 있다고 느끼십니까?",
            "전체 실험을 마친 지금, 통제할 수 없는 일들 때문에 화가 나십니까?",
            "전체 실험을 마친 지금, 어려움들이 너무 많아서 극복하기 어렵다고 느끼십니까?"
        )

        addSectionHeader("PSS-10 (최종 스트레스 수준 측정)")
        addScaleDescription("전체 실험을 마친 현재 상태에 대해 질문합니다.\n1: 전혀 그렇지 않다 ~ 5: 매우 그렇다")

        pssQuestions.forEachIndexed { index, question ->
            addLikertQuestion("pss_final_${index + 1}", "${index + 1}. $question", 5)
        }

        // NASA-TLX 3개 항목 (마지막 과제 대상)
        addSectionHeader("NASA-TLX (마지막 2-Back 과제)")
        addScaleDescription("방금 완료한 2-Back (2회차) 과제에 대해 평가해주세요.\n1: 매우 낮음 ~ 7: 매우 높음")

        addLikertQuestion("mental_demand_final", "정신적 요구도: 마지막 2-Back 과제를 수행하는 동안 얼마나 많은 정신적 활동이 필요했습니까?", 7)
        addLikertQuestion("frustration_final", "좌절감: 마지막 2-Back 과제를 수행하는 동안 얼마나 불안하고, 낙담하고, 짜증나고, 스트레스를 받았습니까?", 7)
        addLikertQuestion("effort_final", "노력: 마지막 2-Back 과제에서 목표를 달성하기 위해 얼마나 열심히 노력해야 했습니까?", 7)

        // 전체 실험 평가
        addSectionHeader("전체 실험 종합 평가")
        val allTasks = listOf("0-back", "1-back (1회차)", "2-back (1회차)", "1-back (2회차)", "2-back (2회차)")

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
        }

        for (i in 1..maxScale) {
            val radioButton = RadioButton(this).apply {
                text = i.toString()
                id = View.generateViewId()
            }
            radioGroup.addView(radioButton)
        }

        // 기본값 설정 (중간값)
        val middleIndex = maxScale / 2
        (radioGroup.getChildAt(middleIndex) as RadioButton).isChecked = true
        surveyResponses[key] = middleIndex + 1

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedIndex = group.indexOfChild(group.findViewById(checkedId))
            surveyResponses[key] = selectedIndex + 1
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
            "baseline" -> "실험 시작 전 기본 상태를 측정합니다.\n일반적인 스트레스 수준과 현재 컨디션을 조사합니다."
            "middle" -> "실험 중간 평가입니다.\n지금까지의 과제 경험과 현재 스트레스 수준을 측정합니다.\n(0-back, 1-back, 2-back 1회차 완료)"
            "final" -> "최종 평가입니다.\n전체 실험 완료 후의 상태와 종합적인 경험을 측정합니다.\n(모든 5개 블록 완료)"
            else -> "설문에 참여해주세요."
        }

        blockInfoText.text = description
    }

    private fun saveSurveyData() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "nback_surveys.csv")

            val timestamp = System.currentTimeMillis()
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // 파일이 없으면 헤더 추가
            val needsHeader = !file.exists()

            FileWriter(file, true).use { writer ->
                if (needsHeader) {
                    writer.write("timestamp,current_time,participant_name,survey_type,block_number,block_name,question_key,response,question_category\n")
                }

                surveyResponses.forEach { (key, value) ->
                    // 질문 카테고리 분류
                    val category = when {
                        key.startsWith("pss_baseline_") -> "PSS-10_Baseline"
                        key.startsWith("pss_middle_") -> "PSS-10_Middle"
                        key.startsWith("pss_final_") -> "PSS-10_Final"
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
                        key.contains("hardest") -> "Task_Evaluation"
                        key.contains("easiest") -> "Task_Evaluation"
                        else -> "Other"
                    }

                    writer.write("$timestamp,$currentTime,$participantName,$surveyType,$blockNumber,$blockName,$key,\"$value\",$category\n")
                }
            }

            // PSS-10 점수 계산 및 로그 (연구자용)
            calculateAndLogPSSScores()

            Log.d("SelfReport", "Survey data saved: $surveyType for participant $participantName")
            Toast.makeText(this, "설문 응답이 저장되었습니다.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("SelfReport", "Failed to save survey data", e)
            Toast.makeText(this, "설문 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // PSS-10 점수 계산 (연구 참고용)
    private fun calculateAndLogPSSScores() {
        try {
            val pssResponses = surveyResponses.filter { it.key.contains("pss_") }
            if (pssResponses.isNotEmpty()) {
                val pssScore = pssResponses.values.filterIsInstance<Int>().sum()
                Log.d("PSS-Score", "Participant: $participantName, Survey: $surveyType, PSS-10 Total: $pssScore")

                // 역채점 문항들 (4, 5, 7, 8번) 처리 안내
                val reverseItems = when (surveyType) {
                    "baseline" -> listOf("pss_baseline_4", "pss_baseline_5", "pss_baseline_7", "pss_baseline_8")
                    "middle" -> listOf("pss_middle_4", "pss_middle_5", "pss_middle_7", "pss_middle_8")
                    "final" -> listOf("pss_final_4", "pss_final_5", "pss_final_7", "pss_final_8")
                    else -> emptyList()
                }
                Log.d("PSS-Score", "Note: Items ${reverseItems.joinToString(", ")} need reverse scoring")
            }
        } catch (e: Exception) {
            Log.e("PSS-Score", "Failed to calculate PSS score: ${e.message}")
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