package com.example.nback

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // UI 요소들
    private lateinit var drawingView: DrawingView
    private lateinit var stimulusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var timerText: TextView
    private lateinit var progressText: TextView
    private lateinit var canvasHintText: TextView
    private lateinit var clearButton: Button
    private lateinit var startButton: Button

    // ====== 추가된 데이터 관리 변수들 ======
    private lateinit var participantBaseDir: File
    private lateinit var participantImagesDir: File
    private lateinit var participantResultsDir: File
    private lateinit var participantSurveyDir: File

    // 실험 변수들
    private var participantName = ""
    private var currentN = 0
    private var currentTrial = 0
    private var totalTrials = 2 // 실제 실험용으로 30으로 변경 (테스트시 2로 설정)
    private var stimulusList = mutableListOf<Int>()
    private var experimentStartTime = 0L
    private var isExperimentRunning = false
    private var currentStimulus = 0
    private var currentBlockName = ""
    private var currentBlockNumber = 1  // 현재 블록 번호 (1~7)
    private val totalBlocks = 7  // 전체 블록 수

    // 데이터 저장
    private val experimentData = mutableListOf<TrialData>()

    data class TrialData(
        val block: String,
        val trial: Int,
        val n: Int,
        val stimulus: Int,
        val correctAnswer: String,
        val timestamp: Long,
        val currentTime: String,
        val computerTime: String,
        val userAnswer: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 화면 꺼짐 방지 및 가로 모드 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeViews()
        requestPermissions()

        // Intent에서 participantName 받기
        participantName = intent.getStringExtra("participantName") ?: "Unknown_${System.currentTimeMillis()}"

        // ====== 피험자 폴더 구조 초기화 ======
        initializeParticipantDirectories()

        // ====== 수정된 흐름 처리 ======
        val resumeFromBlock = intent.getIntExtra("resumeFromBlock", -1)

        if (resumeFromBlock >= 0) {
            // 설문조사에서 돌아온 경우
            participantName = intent.getStringExtra("participantName") ?: participantName
            initializeParticipantDirectories()
            resumeFromSelfReport(resumeFromBlock)
        } else {
            // ====== 처음 시작하는 경우 → ManualActivity로 이동 ======
            startManualActivity()
        }
    }

    // ====== ManualActivity로 이동하는 함수 ======
    private fun startManualActivity() {
        try {
            val intent = Intent(this, ManualActivity::class.java).apply {
                putExtra("participantName", participantName)
            }
            startActivity(intent)
            finish() // MainActivity 종료
        } catch (e: Exception) {
            Log.e("NBack", "Failed to start Manual Activity: ${e.message}")
            Toast.makeText(this, "매뉴얼 화면 로딩 실패. 설문조사로 이동합니다.", Toast.LENGTH_SHORT).show()
            // Manual 실패시 바로 설문조사로
            startBaselineSurvey()
        }
    }

    // ====== 새로 추가된 함수: 피험자 폴더 구조 초기화 ======
    private fun initializeParticipantDirectories() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseExperimentDir = File(downloadsDir, "nback_experiment_results")

            // 기본 실험 폴더 생성
            if (!baseExperimentDir.exists()) {
                baseExperimentDir.mkdirs()
            }

            // 피험자별 기본 폴더 생성
            participantBaseDir = File(baseExperimentDir, participantName)
            if (!participantBaseDir.exists()) {
                participantBaseDir.mkdirs()
            }

            // 피험자별 하위 폴더들 생성
            participantImagesDir = File(participantBaseDir, "nback_images")
            participantResultsDir = File(participantBaseDir, "nback_results")
            participantSurveyDir = File(participantBaseDir, "survey_results")

            if (!participantImagesDir.exists()) participantImagesDir.mkdirs()
            if (!participantResultsDir.exists()) participantResultsDir.mkdirs()
            if (!participantSurveyDir.exists()) participantSurveyDir.mkdirs()

            Log.d("NBack", "Participant directories created: ${participantBaseDir.absolutePath}")

        } catch (e: Exception) {
            Log.e("NBack", "Failed to create participant directories: ${e.message}")
            Toast.makeText(this, "폴더 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 새로 추가할 함수: baseline 설문조사 시작
    private fun startBaselineSurvey() {
        try {
            val intent = Intent(this, SelfReportActivity::class.java).apply {
                putExtra("blockNumber", 0) // 0은 baseline을 의미
                putExtra("blockName", "Baseline")
                putExtra("participantName", participantName)
                putExtra("surveyType", "baseline") // 설문 타입 추가
            }
            startActivity(intent)
            finish() // MainActivity 종료
        } catch (e: Exception) {
            Log.e("NBack", "Failed to start baseline survey: ${e.message}")
            Toast.makeText(this, "설문조사 실패. 실험을 바로 시작합니다.", Toast.LENGTH_SHORT).show()
            // 설문조사 실패시 바로 실험 시작
            startExperimentDirectly()
        }
    }

    // ====== 새로 추가: 실험 직접 시작 준비 (Manual 완료 후 사용) ======
    private fun prepareExperimentFromManual() {
        currentBlockNumber = 1
        currentN = 0
        generateStimulusSequence(0)
        updateInstructionText()
        startButton.text = "0-Back 실험 시작"
        timerText.text = "매뉴얼이 완료되었습니다. 버튼을 눌러 실험을 시작하세요!"
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        startButton.isEnabled = true

        Toast.makeText(this, "매뉴얼 완료! 이제 0-Back 실험을 시작할 수 있습니다.", Toast.LENGTH_LONG).show()
    }
    // 새로 추가할 함수: 설문조사 없이 바로 실험 시작
    private fun startExperimentDirectly() {
        generateStimulusSequence(0) // 0-back부터 시작
        updateInstructionText()
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }

    private fun initializeViews() {
        drawingView = findViewById(R.id.drawingView)
        stimulusText = findViewById(R.id.stimulusText)
        instructionText = findViewById(R.id.instructionText)
        timerText = findViewById(R.id.timerText)
        progressText = findViewById(R.id.progressText)
        canvasHintText = findViewById(R.id.canvasHintText)
        clearButton = findViewById(R.id.clearButton)
        startButton = findViewById(R.id.startButton)

        // 버튼 클릭 리스너 설정
        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }
        startButton.setOnClickListener { startExperiment() }

        // 캔버스 터치 시 힌트 텍스트 숨기기
        drawingView.setOnTouchListener { _, _ ->
            canvasHintText.visibility = View.GONE
            false
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    // ====== 수정된 함수: 설문조사 후 복귀 처리 ======
    private fun resumeFromSelfReport(blockNumber: Int) {
        when (blockNumber) {
            0 -> {
                // Baseline 설문 완료 → 실험 시작 (0-back)
                currentBlockNumber = 1
                currentN = 0
                generateStimulusSequence(0)
                updateInstructionText()
                startButton.text = "0-Back 시작"
                timerText.text = "사전 설문 완료! 실험을 시작하세요."
                Toast.makeText(this, "사전 설문 완료! 0-Back 실험을 시작하세요.", Toast.LENGTH_LONG).show()
                startButton.isEnabled = true // 버튼 활성화
            }
            4 -> {
                // 중간 설문 완료 (Block 4 완료 후) → 1-back (2회차)로 (수동 시작)
                currentBlockNumber = 5
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
                startButton.text = "1-Back (2회차) 시작"
                timerText.text = "중간 설문 완료! 버튼을 눌러 다음 블록을 시작하세요."
                Toast.makeText(this, "중간 설문 완료! 버튼을 눌러 1-Back (2회차)을 시작하세요.", Toast.LENGTH_LONG).show()
                startButton.isEnabled = true // 버튼 활성화 (수동 시작)
            }
            7 -> {
                // 최종 설문 완료 (Block 7 완료 후) → 실험 완전 종료
                saveDataToFile()
                startButton.text = "실험 완료"
                startButton.isEnabled = false
                timerText.text = "모든 실험 완료!"
                Toast.makeText(this, "전체 실험 완료! 수고하셨습니다!", Toast.LENGTH_LONG).show()
                return
            }
            else -> {
                // 예상치 못한 경우 → 실험 시작
                currentBlockNumber = 1
                currentN = 0
                generateStimulusSequence(0)
                updateInstructionText()
                startButton.text = "실험 시작"
                timerText.text = "실험을 시작하세요."
                startButton.isEnabled = true
            }
        }

        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }

    // N-back 시퀀스 생성 (원본 PsychoPy 코드 기반)
    private fun generateStimulusSequence(n: Int, length: Int = 30, targetRatio: Float = 0.33f) {
        stimulusList.clear()
        val digits = (0..9).toList()
        val numTargets = (length * targetRatio).toInt()

        when (n) {
            0 -> {
                // 0-back: 특정 숫자(5)가 타겟
                val targetDigit = 5
                val targetPositions = (0 until length).shuffled().take(numTargets)
                repeat(length) { i ->
                    stimulusList.add(
                        if (i in targetPositions) targetDigit
                        else digits.filter { it != targetDigit }.random()
                    )
                }
            }
            else -> {
                // 1-back, 2-back, 3-back: N번째 이전과 동일한 숫자가 타겟
                repeat(length) { stimulusList.add(digits.random()) }
                val candidatePositions = (n until length).toList()
                val targetPositions = candidatePositions.shuffled().take(numTargets)

                // 타겟 위치에서 N번째 이전과 동일하게 설정
                targetPositions.forEach { i ->
                    stimulusList[i] = stimulusList[i - n]
                }

                // 비타겟 위치에서 N번째 이전과 다르게 설정
                val nonTargetPositions = candidatePositions - targetPositions.toSet()
                nonTargetPositions.forEach { i ->
                    if (stimulusList[i] == stimulusList[i - n]) {
                        stimulusList[i] = digits.filter { it != stimulusList[i - n] }.random()
                    }
                }
            }
        }
    }

    private fun updateInstructionText() {
        val instruction = when (currentN) {
            0 -> "현재 화면에 표시되는\n 숫자를 써주세요"
            1 -> "1번째 이전에 표시된 \n 숫자를 써주세요"
            2 -> "2번째 이전에 표시된 \n 숫자를 써주세요"
            3 -> "3번째 이전에 표시된 \n 숫자를 써주세요"
            else -> "N-Back 테스트"
        }

        // 블록 번호 포함하여 표시
        val blockCount = when (currentN) {
            1 -> if (currentBlockNumber == 2) "1회차" else "2회차"
            2 -> if (currentBlockNumber == 3) "1회차" else "2회차"
            3 -> if (currentBlockNumber == 4) "1회차" else "2회차"
            else -> ""
        }

        val title = if (blockCount.isNotEmpty()) {
            "현재 난이도: ${currentN}-back ($blockCount)"
        } else {
            "현재 난이도: ${currentN}-back"
        }

        instructionText.text = "$title\n$instruction"
        currentBlockName = "${currentN}-Back_${currentBlockNumber}"
    }

    private fun startExperiment() {
        if (isExperimentRunning) return

        isExperimentRunning = true
        currentTrial = 0
        experimentStartTime = System.currentTimeMillis()
        startButton.isEnabled = false
        startButton.text = "실험 진행 중..."

        // 3초 카운트다운 후 시작
        showCountdown()
    }

    private fun showCountdown() {
        var countdown = 3 // 3초로 수정 (원래 30이었음)
        timerText.text = "시작까지 $countdown 초"

        val countdownTimer = object : CountDownTimer(3000, 1000) { // 3초로 수정
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt() + 1
                timerText.text = "시작까지 $countdown 초"
            }

            override fun onFinish() {
                timerText.text = "실험 시작!"
                showNextStimulus()
            }
        }
        countdownTimer.start()
    }

    private fun showNextStimulus() {
        if (currentTrial >= totalTrials) {
            finishCurrentBlock()
            return
        }

        currentStimulus = stimulusList[currentTrial]
        stimulusText.text = currentStimulus.toString()
        stimulusText.visibility = View.VISIBLE

        progressText.text = "시행: ${currentTrial + 1} / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        timerText.text = "숫자 제시 중..."

        // 0.5초 동안 자극 제시
        object : CountDownTimer(500, 100) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startResponsePeriod()
            }
        }.start()
    }

    private fun startResponsePeriod() {
        val responseTime = 3000L // 3초

        object : CountDownTimer(responseTime, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                timerText.text = "답 작성 시간: ${secondsLeft}초"
            }

            override fun onFinish() {
                timerText.text = "시간 종료"
                saveTrialData()

                // 1초 후 다음 시행으로 진행
                object : CountDownTimer(1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        drawingView.clearCanvas()
                        canvasHintText.visibility = View.VISIBLE
                        currentTrial++
                        showNextStimulus()
                    }
                }.start()
            }
        }.start()
    }

    // ====== 수정된 함수: 시행 데이터 저장 ======
    private fun saveTrialData() {
        val recognizedText = drawingView.getRecognizedText()
        val correctAnswer = getCorrectAnswer()

        // PNG 이미지 파일명 생성: [블록]_trial[번호]_stimulus[숫자]_[타임스탬프]
        val timestamp = System.currentTimeMillis()
        val imageFileName = "${currentBlockName}_trial${String.format("%02d", currentTrial + 1)}_" +
                "stimulus${currentStimulus}_${timestamp}"

        // ====== 수정: 캔버스를 PNG로 저장 (피험자 이미지 폴더에 저장) ======
        if (drawingView.hasUserDrawing()) {
            val imageSaved = drawingView.saveCanvasAsPNG(imageFileName, participantImagesDir.absolutePath)
            if (imageSaved) {
                Log.d("NBack", "Image saved: ${participantImagesDir.absolutePath}/$imageFileName.png")
            } else {
                Log.e("NBack", "Failed to save image: $imageFileName.png")
            }
        } else {
            Log.d("NBack", "No drawing to save for trial ${currentTrial + 1}")
        }

        val trialData = TrialData(
            block = currentBlockName,
            trial = currentTrial + 1,
            n = currentN,
            stimulus = currentStimulus,
            correctAnswer = correctAnswer,
            currentTime = "${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}",
            timestamp = timestamp - experimentStartTime,
            computerTime = "${System.currentTimeMillis()}",
            userAnswer = recognizedText
        )

        experimentData.add(trialData)

        // 즉시 CSV에 저장 (데이터 손실 방지)
        saveTrialDataToCSV(trialData)

        Log.d("NBack", "Trial ${currentTrial + 1}: stimulus=$currentStimulus, correct=$correctAnswer, user=$recognizedText")
        Log.d("NBack", "Total trials saved so far: ${experimentData.size}")
    }

    // ====== 수정된 함수: 각 시행마다 즉시 CSV에 저장 ======
    private fun saveTrialDataToCSV(trialData: TrialData) {
        try {
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val file = File(participantResultsDir, "nback_results_${participantName}_${todayDate}.csv")

            // 파일이 없으면 헤더 추가
            val needsHeader = !file.exists()

            FileWriter(file, true).use { writer ->
                if (needsHeader) {
                    // ====== 수정: 헤더에 correct_answer와 user_answer 추가 ======
                    writer.write("participant,block,trial,n,stimulus,correct_answer,computer_time,timestamp,current_time,user_answer\n")
                }

                // ====== 수정: 데이터에 correct_answer와 user_answer 추가 ======
                writer.write("${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                        "${trialData.correctAnswer},${trialData.computerTime},${trialData.timestamp},${trialData.currentTime},${trialData.userAnswer}\n")
                writer.flush()
            }

            Log.d("NBack", "Trial data saved to CSV: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("NBack", "Failed to save trial data to CSV: ${e.message}")
        }
    }

    // ====== 수정된 함수: 최종 데이터 저장 ======
    private fun saveDataToFile() {
        try {
            val finalTimestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val finalFile = File(participantResultsDir, "nback_results_${participantName}_FINAL_${finalTimestamp}.csv")

            FileWriter(finalFile).use { writer ->
                writer.write("participant,block,trial,n,stimulus,correct_answer,computerTime,timestamp,current_time,user_answer\n")
                experimentData.forEach { data ->
                    writer.write("${participantName},${data.block},${data.trial},${data.n},${data.stimulus}," +
                            "${data.correctAnswer},${data.computerTime},${data.timestamp},${data.currentTime},${data.userAnswer}\n")
                }
            }

            // ====== 수정: 저장된 파일들 개수 확인 ======
            val imageCount = if (participantImagesDir.exists()) participantImagesDir.listFiles()?.size ?: 0 else 0
            val csvCount = if (participantResultsDir.exists()) participantResultsDir.listFiles()?.filter { it.extension == "csv" }?.size ?: 0 else 0
            val surveyCount = if (participantSurveyDir.exists()) participantSurveyDir.listFiles()?.filter { it.extension == "csv" }?.size ?: 0 else 0

            val message = "전체 실험 완료!\n" +
                    "참가자: $participantName\n" +
                    "저장 위치: ${participantBaseDir.name}/\n" +
                    "- 실험 결과: ${csvCount}개 CSV 파일\n" +
                    "- 그림 데이터: ${imageCount}개 이미지\n" +
                    "- 설문 결과: ${surveyCount}개 설문 파일\n" +
                    "총 시행 수: ${experimentData.size}\n" +
                    "총 ${totalBlocks}개 블록 완료!"

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d("NBack", "Final data saved to: ${finalFile.absolutePath}")
            Log.d("NBack", "Participant folder: ${participantBaseDir.absolutePath}")
            Log.d("NBack", "Total files - CSV: $csvCount, Images: $imageCount, Surveys: $surveyCount")
            Log.d("NBack", "Total trials in final file: ${experimentData.size}")
            Log.d("NBack", "Total blocks completed: $totalBlocks")

        } catch (e: Exception) {
            Log.e("NBack", "데이터 저장 실패", e)
            Toast.makeText(this, "데이터 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCorrectAnswer(): String {
        return when (currentN) {
            0 -> if (currentStimulus == 5) "5" else "none"
            else -> {
                if (currentTrial >= currentN &&
                    currentStimulus == stimulusList[currentTrial - currentN]
                ) {
                    stimulusList[currentTrial - currentN].toString()
                } else {
                    "none"
                }
            }
        }
    }

    private fun finishCurrentBlock() {
        isExperimentRunning = false

        // 블록 4, 7이 끝나면 설문조사로 이동
        if (currentBlockNumber == 4 || currentBlockNumber == 7) {
            val surveyType = if (currentBlockNumber == 4) "middle" else "final"

            try {
                val intent = Intent(this, SelfReportActivity::class.java).apply {
                    putExtra("blockNumber", currentBlockNumber)
                    putExtra("blockName", currentBlockName)
                    putExtra("participantName", participantName)
                    putExtra("surveyType", surveyType)
                }
                startActivity(intent)
                finish() // MainActivity 종료
                return
            } catch (e: Exception) {
                Log.e("NBack", "Failed to start SelfReportActivity: ${e.message}")
                Toast.makeText(this, "설문조사 화면 로딩 실패. 다음 블록으로 진행합니다.", Toast.LENGTH_SHORT).show()
                // 설문조사 실패시 바로 다음 블록으로 진행
            }
        }

        // 블록 1,2,3 완료 시 → 30초 휴식 후 자동으로 다음 블록 시작
        if (currentBlockNumber in 1..3) {
            startButton.isEnabled = false // 버튼 비활성화
            startAutoRestAndNextBlock()
            return
        }

        // 블록 5,6 완료 시 → 30초 휴식 후 자동으로 다음 블록 시작
        if (currentBlockNumber in 5..6) {
            startButton.isEnabled = false // 버튼 비활성화
            startAutoRestAndNextBlock()
            return
        }

        // 기타 경우 (예상치 못한 상황) → 수동 시작
        startButton.isEnabled = true
        updateInstructionText()
        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }

    // 30초 휴식 후 자동으로 다음 블록 시작하는 함수
    private fun startAutoRestAndNextBlock() {
        val restTime = 30000L // 30초로 수정 (실제 실험용)
        var timeLeft = restTime / 1000

        // 현재 블록 완료 메시지 표시
        val currentBlockMessage = when (currentBlockNumber) {
            1 -> "0-Back 완료!"
            2 -> "1-Back (1회차) 완료!"
            3 -> "2-Back (1회차) 완료!"
            5 -> "1-Back (2회차) 완료!"
            6 -> "2-Back (2회차) 완료!"
            else -> "블록 완료!"
        }

        Toast.makeText(this, "$currentBlockMessage 30초 후 다음 블록이 자동 시작됩니다.", Toast.LENGTH_LONG).show()

        // 30초 카운트다운 타이머
        val restTimer = object : CountDownTimer(restTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished / 1000
                timerText.text = "$currentBlockMessage\n다음 블록까지: ${timeLeft}초"
                startButton.text = "휴식 중... (${timeLeft}초)"
            }

            override fun onFinish() {
                // 다음 블록 설정 및 자동 시작
                setupNextBlock()

                // 3초 후 자동으로 실험 시작 (3초로 수정)
                timerText.text = "3초 후 자동 시작..."
                startButton.text = "자동 시작 중..."

                object : CountDownTimer(3000, 1000) { // 3초로 수정
                    var countdown = 3
                    override fun onTick(millisUntilFinished: Long) {
                        countdown = (millisUntilFinished / 1000).toInt() + 1
                        timerText.text = "자동 시작까지 $countdown 초"
                    }

                    override fun onFinish() {
                        // 자동으로 실험 시작
                        isExperimentRunning = true
                        experimentStartTime = System.currentTimeMillis()
                        startButton.text = "실험 진행 중..."
                        timerText.text = "실험 시작!"
                        showNextStimulus()
                    }
                }.start()
            }
        }
        restTimer.start()
    }

    // 다음 블록 설정하는 함수
    private fun setupNextBlock() {
        when (currentBlockNumber) {
            1 -> {
                // 0-back 완료 → 1-back (1회차)로
                currentBlockNumber = 2
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
            }
            2 -> {
                // 1-back (1회차) 완료 → 2-back (1회차)로
                currentBlockNumber = 3
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
            }
            3 -> {
                // 2-back (1회차) 완료 → 3-back (1회차)로
                currentBlockNumber = 4
                currentN = 3
                generateStimulusSequence(3)
                updateInstructionText()
            }
            5 -> {
                // 1-back (2회차) 완료 → 2-back (2회차)로
                currentBlockNumber = 6
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
            }
            6 -> {
                // 2-back (2회차) 완료 → 3-back (2회차)로
                currentBlockNumber = 7
                currentN = 3
                generateStimulusSequence(3)
                updateInstructionText()
            }
        }

        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }
}