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
    // ▼ 추가: 0-back에서 사용자가 눌러서 시작하도록 하는 시작 버튼
    private lateinit var startButton: Button

    private var participantName = ""
    private var currentLevel = 0 // 0, 1, 2, 3 back
    private var currentTrial = 0
    private var isWaitingForResponse = true

    // 튜토리얼 시퀀스 정의
    private val tutorialSequences = mapOf(
        0 to listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        1 to listOf(0, 1, 2, 3, 4),
        2 to listOf(0, 1, 2, 3, 4),
        3 to listOf(0, 1, 2, 3, 4)
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

        // ▼ 변경: 0-back은 자동 시작하지 않고, 사용자가 '시작' 버튼을 눌러서 시작
        startTutorialLevel(0) // 안내/문구/화면 구성까지만 하고, 카운트다운은 startButton으로 시작
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
        // ▼ 추가: 시작 버튼
        startButton = findViewById(R.id.startButton)
        startButton.visibility = View.GONE

        stimulusText.visibility = View.INVISIBLE
        nextButton.isEnabled = true
        nextButton.visibility = View.INVISIBLE  // ✅ 처음에는 숨김

        // 시작 버튼은 기본적으로 표시 (0-back용), 이후 레벨에서는 숨김/미사용
        startButton.visibility = View.VISIBLE
        startButton.isEnabled = true
        startButton.text = "시작"

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
                nextButton.isEnabled = true
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

        // 다음 버튼은 시행 중엔 숨김
        nextButton.isEnabled = true
        nextButton.visibility = View.GONE
        nextButton.text = "다음"

        Log.d("Tutorial", "Starting tutorial level: $currentLevel")

        // ▼ 핵심 변경: 0-back에서는 여기서 바로 showCountdown() 하지 않음
        if (currentLevel == 0) {
            // 버튼 보이기 + 클릭하면 카운트다운 시작
            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true
            startButton.text = "0-Back 시작"
            timerText.text = "시작 버튼을 눌러 연습을 시작하세요"

            // 중복 리스너 방지: 기존 리스너 제거 후 등록
            startButton.setOnClickListener {
                startButton.visibility = View.GONE
                showCountdown()
            }
        } else {
            // 1/2/3-back은 자동 시작
            startButton.visibility = View.GONE
            showCountdown()
        }
    }

    private fun showCountdown() {
        var countdown = 30
        timerText.text = "시작까지 $countdown 초"

        object : CountDownTimer(30000, 1000) {
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
        val responseTime = 3000L
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
        object : CountDownTimer(0, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                showNextStimulus()
            }
        }.start()
    }

    // ▼ 변경: 다음 버튼은 레벨 변경 시에만 사용
    private fun checkAnswerAndProceed() {
        // 레벨 변경 시에만 호출됨
        nextButton.isEnabled = true
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
            // 버튼 없이 자동으로 다음 난이도 진입
            Toast.makeText(this, "${currentLevel}-Back 연습 완료! 다음 난이도로 이동합니다.", Toast.LENGTH_SHORT).show()
            timerText.text = "다음 난이도로 이동 중..."
            instructionText.text = "${currentLevel}-Back 연습 완료!\n다음: ${currentLevel + 1}-Back"

            nextButton.visibility = View.GONE  // 혹시 보이는 경우 숨김
            nextButton.isEnabled = false

            // 1초 정도 안내 후 자동 시작
            object : CountDownTimer(1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    startTutorialLevel(currentLevel + 1)
                }
            }.start()
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
