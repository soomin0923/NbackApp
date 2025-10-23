package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var instructionText: TextView
    private lateinit var stimulusText: TextView
    private lateinit var progressText: TextView
    private lateinit var timerText: TextView
    private lateinit var drawingView: DrawingView
    private lateinit var canvasHintText: TextView
    private lateinit var clearButton: Button
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button

    private var participantName = ""
    private var currentLevel = 0 // 0, 1, 2, 3 back
    private var currentTrial = 0
    private var isWaitingForResponse = false

    // 튜토리얼 시퀀스 정의
    private val tutorialSequences = mapOf(
        0 to listOf(5, 3, 7, 2),
        1 to listOf(3, 7, 7, 2),
        2 to listOf(4, 6, 4, 8),
        3 to listOf(2, 5, 8, 2)
    )

    private var currentSequence = listOf<Int>()
    private var stimulusTimer: CountDownTimer? = null
    private var responseTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        participantName = intent.getStringExtra("participantName") ?: "Unknown"

        initializeViews()
        setupClickListeners()
        startTutorialLevel(0)
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        instructionText = findViewById(R.id.instructionText)
        stimulusText = findViewById(R.id.stimulusText)
        progressText = findViewById(R.id.progressText)
        timerText = findViewById(R.id.timerText)
        drawingView = findViewById(R.id.drawingView)
        canvasHintText = findViewById(R.id.canvasHintText)
        clearButton = findViewById(R.id.clearButton)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)

        stimulusText.visibility = View.INVISIBLE
        nextButton.isEnabled = false
        nextButton.visibility = View.GONE  // ✅ 처음에는 숨김

        Log.d("Tutorial", "TutorialActivity initialized successfully")
    }

    private fun setupClickListeners() {
        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }

        // ▼ 변경: 다음 버튼은 레벨 변경 시에만 사용
        nextButton.setOnClickListener {
            if (currentLevel < 3) {
                // 다음 레벨로 이동
                nextButton.isEnabled = false
                nextButton.visibility = View.GONE
                startTutorialLevel(currentLevel + 1)
            }
        }

        skipButton.setOnClickListener {
            // 튜토리얼 건너뛰기
            finishTutorial()
        }

        drawingView.setOnTouchListener { _, _ ->
            canvasHintText.visibility = View.GONE
            false
        }
    }

    private fun startTutorialLevel(level: Int) {
        currentLevel = level
        currentTrial = 0
        currentSequence = tutorialSequences[level] ?: listOf()

        titleText.text = "${currentLevel}-Back 연습"

        val instruction = when (currentLevel) {
            0 -> "화면에 표시되는 숫자를 그대로 써주세요"
            1 -> "1번째 이전에 표시된 숫자를 써주세요"
            2 -> "2번째 이전에 표시된 숫자를 써주세요"
            3 -> "3번째 이전에 표시된 숫자를 써주세요"
            else -> ""
        }

        instructionText.text = instruction
        progressText.text = "연습 ${currentLevel + 1}/4 - 시행: 1/${currentSequence.size}"

        // 다음 버튼 숨기기 (시행 중에는 사용 안 함)
        nextButton.isEnabled = false
        nextButton.visibility = View.GONE
        nextButton.text = "다음"

        Log.d("Tutorial", "Starting tutorial level: $currentLevel")

        // 3초 후 첫 시행 시작
        showCountdown()
    }

    private fun showCountdown() {
        var countdown = 3
        timerText.text = "시작까지 $countdown 초"

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt() + 1
                timerText.text = "시작까지 $countdown 초"
            }

            override fun onFinish() {
                timerText.text = "시작!"
                showNextStimulus()
            }
        }.start()
    }

    private fun showNextStimulus() {
        if (currentTrial >= currentSequence.size) {
            // 현재 레벨 완료
            completeTutorialLevel()
            return
        }

        isWaitingForResponse = false
        nextButton.isEnabled = false
        drawingView.clearCanvas()
        canvasHintText.visibility = View.VISIBLE

        val currentStimulus = currentSequence[currentTrial]
        stimulusText.text = currentStimulus.toString()
        stimulusText.visibility = View.VISIBLE

        progressText.text = "연습 ${currentLevel + 1}/4 - 시행: ${currentTrial + 1}/${currentSequence.size}"
        timerText.text = "숫자 제시 중..."

        // 숫자 표시 시간 (500ms)
        stimulusTimer?.cancel()
        stimulusTimer = object : CountDownTimer(500, 100) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startResponsePeriod()
            }
        }.start()
    }

    private fun startResponsePeriod() {
        isWaitingForResponse = true
        val responseTime = 5000L // 튜토리얼은 5초 (실제 실험보다 길게)

        responseTimer?.cancel()
        responseTimer = object : CountDownTimer(responseTime, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                timerText.text = "답 작성 시간: ${secondsLeft}초"
            }

            override fun onFinish() {
                timerText.text = "시간 종료"
                // 자동으로 다음 시행으로 진행
                autoProgressToNext()
            }
        }.start()
    }

    // ▼ 추가: 자동으로 다음 시행으로 진행
    private fun autoProgressToNext() {
        if (!isWaitingForResponse) return

        isWaitingForResponse = false

        val userAnswer = drawingView.getRecognizedText()
        val correctAnswer = getCorrectAnswer()

        Log.d("Tutorial", "Level: $currentLevel, Trial: $currentTrial, User: $userAnswer, Correct: $correctAnswer")

        // 다음 시행으로
        currentTrial++

        // 1초 후 자동으로 다음 시행
        timerText.text = "다음 시행까지 1초"
        object : CountDownTimer(1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                showNextStimulus()
            }
        }.start()
    }

    // ▼ 변경: 다음 버튼은 레벨 변경 시에만 사용
    private fun checkAnswerAndProceed() {
        // 레벨 변경 시에만 호출됨
        nextButton.isEnabled = false
        nextButton.visibility = View.GONE
        startTutorialLevel(currentLevel + 1)
    }

    private fun getCorrectAnswer(): String {
        return when (currentLevel) {
            0 -> {
                // 0-back: 현재 제시된 숫자
                currentSequence[currentTrial].toString()
            }
            else -> {
                // N-back: N번째 이전 숫자
                if (currentTrial >= currentLevel) {
                    currentSequence[currentTrial - currentLevel].toString()
                } else {
                    "none" // N번째 이전이 없으면 none
                }
            }
        }
    }

    private fun completeTutorialLevel() {
        stimulusTimer?.cancel()
        responseTimer?.cancel()

        if (currentLevel < 3) {
            // 다음 레벨로 - 버튼을 보여주고 사용자가 누르면 진행
            Toast.makeText(this, "${currentLevel}-Back 연습 완료!", Toast.LENGTH_SHORT).show()

            timerText.text = "연습 완료! '다음' 버튼을 눌러주세요"
            instructionText.text = "${currentLevel}-Back 연습 완료!\n다음: ${currentLevel + 1}-Back"

            // 다음 버튼 활성화
            nextButton.isEnabled = true
            nextButton.visibility = View.VISIBLE
            nextButton.text = "다음 난이도 시작"
        } else {
            // 모든 튜토리얼 완료
            finishTutorial()
        }
    }

    private fun finishTutorial() {
        stimulusTimer?.cancel()
        responseTimer?.cancel()

        Log.d("Tutorial", "=== Tutorial Completed ===")
        Log.d("Tutorial", "Participant: $participantName")

        Toast.makeText(this, "튜토리얼 완료! 사전 설문으로 이동합니다.", Toast.LENGTH_LONG).show()

        try {
            // MainActivity를 통해 설문으로 이동 (경로 정보 전달 보장)
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("participantName", participantName)
                putExtra("startBaselineSurvey", true)  // 사전 설문 시작 플래그
            }
            Log.d("Tutorial", "Starting MainActivity with startBaselineSurvey=true")
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("Tutorial", "Failed to start MainActivity: ${e.message}", e)
            // Fallback: 직접 설문으로 이동
            val intent = Intent(this, SelfReportActivity::class.java).apply {
                putExtra("blockNumber", 0)
                putExtra("blockName", "Baseline")
                putExtra("participantName", participantName)
                putExtra("surveyType", "baseline")
            }
            Log.d("Tutorial", "Fallback: Starting SelfReportActivity directly")
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        stimulusTimer?.cancel()
        responseTimer?.cancel()
        super.onDestroy()
    }
}