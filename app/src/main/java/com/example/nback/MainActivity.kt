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

    // 실험 변수들
    private var participantName = ""
    private var currentN = 0
    private var currentTrial = 0
    private var totalTrials = 30
    private var stimulusList = mutableListOf<Int>()
    private var experimentStartTime = 0L
    private var isExperimentRunning = false
    private var currentStimulus = 0
    private var currentBlockName = ""
    private var currentBlockNumber = 1  // 현재 블록 번호 (1~5)
    private val totalBlocks = 5  // 전체 블록 수

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

        // Intent에서 resumeFromBlock 확인 (설문 조사에서 돌아온 경우)
        val resumeFromBlock = intent.getIntExtra("resumeFromBlock", 0)
        if (resumeFromBlock > 0) {
            // 설문조사에서 돌아온 경우 participantName도 다시 받기
            participantName = intent.getStringExtra("participantName") ?: participantName
            resumeFromSelfReport(resumeFromBlock)
        } else {
            // 처음 시작하는 경우
            generateStimulusSequence(0) // 0-back부터 시작
            updateInstructionText()
            progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        }
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

    private fun resumeFromSelfReport(blockNumber: Int) {
        currentBlockNumber = blockNumber + 1 // 다음 블록으로 이동

        when (currentBlockNumber) {
            2 -> {
                // 0-back 완료 후 설문 → 1-back (1회차)로
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
                startButton.text = "1-Back (1회차) 시작"
                timerText.text = "설문 완료! 다음 블록을 시작하세요."
            }
            4 -> {
                // 2-back (1회차) 완료 후 설문 → 1-back (2회차)로
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
                startButton.text = "1-Back (2회차) 시작"
                timerText.text = "설문 완료! 다음 블록을 시작하세요."
            }
            6 -> {
                // 2-back (2회차) 완료 후 설문 → 실험 완전 종료
                saveDataToFile()
                startButton.text = "실험 완료"
                startButton.isEnabled = false
                timerText.text = "모든 실험 완료!"
                Toast.makeText(this, "전체 실험 완료! 수고하셨습니다!", Toast.LENGTH_LONG).show()
                return
            }
        }

        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        startButton.isEnabled = true
    }

    // N-back 시퀀스 생성 (원본 PsychoPy 코드 기반)
    private fun generateStimulusSequence(n: Int, length: Int = 48, targetRatio: Float = 0.33f) {
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
                // 1-back, 2-back: N번째 이전과 동일한 숫자가 타겟
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
            else -> "N-Back 테스트"
        }

        // 블록 번호 포함하여 표시
        val blockCount = when (currentN) {
            1 -> if (currentBlockNumber == 2) "1회차" else "2회차"
            2 -> if (currentBlockNumber == 3) "1회차" else "2회차"
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
        var countdown = 3
        timerText.text = "시작까지 $countdown 초"

        val countdownTimer = object : CountDownTimer(3000, 1000) {
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
    private fun saveTrialData() {
        val recognizedText = drawingView.getRecognizedText()
        val correctAnswer = getCorrectAnswer()

        // PNG 이미지 파일명 생성: [블록]_trial[번호]_stimulus[숫자]_[타임스탬프]
        val timestamp = System.currentTimeMillis()
        val imageFileName = "${currentBlockName}_trial${String.format("%02d", currentTrial + 1)}_" +
                "stimulus${currentStimulus}_${timestamp}"

        // 캔버스를 PNG로 저장 (그려진 내용이 있을 때만)
        if (drawingView.hasUserDrawing()) {
            val imageSaved = drawingView.saveCanvasAsPNG(imageFileName, participantName)
            if (imageSaved) {
                Log.d("NBack", "Image saved: $imageFileName.png")
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

            currentTime="${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}",
            timestamp = timestamp - experimentStartTime,
            computerTime = "${System.currentTimeMillis()}",
            userAnswer = recognizedText)

        experimentData.add(trialData)

        // 즉시 CSV에 저장 (데이터 손실 방지)
        saveTrialDataToCSV(trialData)

        Log.d("NBack", "Trial ${currentTrial + 1}: stimulus=$currentStimulus, correct=$correctAnswer, user=$recognizedText")
        Log.d("NBack", "Total trials saved so far: ${experimentData.size}")
    }

    // 각 시행마다 즉시 CSV에 저장하는 함수
    private fun saveTrialDataToCSV(trialData: TrialData) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "nback_results_${participantName}_${System.currentTimeMillis() / 86400000}.csv") // 날짜별로 파일 구분

            // 파일이 없으면 헤더 추가
            val needsHeader = !file.exists()

            FileWriter(file, true).use { writer ->
                if (needsHeader) {
                    writer.write("participant,block,trial,n,stimulus,correct_answer,user_answer,timestamp,current_time\n")
                }

                writer.write("${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                        "${trialData.correctAnswer},${trialData.computerTime},${trialData.timestamp},${trialData.currentTime}\n")
                writer.flush()
            }

            Log.d("NBack", "Trial data saved to CSV: ${file.name}")
        } catch (e: Exception) {
            Log.e("NBack", "Failed to save trial data to CSV: ${e.message}")
        }
    }

    private fun saveDataToFile() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val finalFile = File(downloadsDir, "nback_results_${participantName}_FINAL_${System.currentTimeMillis()}.csv")

            FileWriter(finalFile).use { writer ->
                writer.write("participant,block,trial,n,stimulus,correct_answer,computerTime,timestamp,current_time\n")
                experimentData.forEach { data ->
                    writer.write("${participantName},${data.block},${data.trial},${data.n},${data.stimulus}," +
                            "${data.correctAnswer},${data.computerTime},${data.timestamp},${data.currentTime}\n")
                }
            }

            // 저장된 이미지 개수 확인
            val imageDir = File(downloadsDir, "nback_images")
            val imageCount = if (imageDir.exists()) imageDir.listFiles()?.size ?: 0 else 0

            val message = "전체 실험 완료!\n" +
                    "참가자: $participantName\n" +
                    "최종 CSV 파일: ${finalFile.name}\n" +
                    "총 시행 수: ${experimentData.size}\n" +
                    "이미지 파일: ${imageCount}개\n" +
                    "설문 파일: nback_self_reports.csv\n" +
                    "위치: Downloads/\n" +
                    "총 ${totalBlocks}개 블록 완료!"

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d("NBack", "Final data saved to: ${finalFile.absolutePath}")
            Log.d("NBack", "Total trials in final file: ${experimentData.size}")
            Log.d("NBack", "Images saved: $imageCount files in nback_images folder")
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
                    currentStimulus == stimulusList[currentTrial - currentN]) {
                    stimulusList[currentTrial - currentN].toString()
                } else {
                    "none"
                }
            }
        }
    }

    private fun finishCurrentBlock() {
        isExperimentRunning = false
        startButton.isEnabled = true

        // 블록 1, 3, 5가 끝나면 설문조사로 이동
        if (currentBlockNumber == 1 || currentBlockNumber == 3 || currentBlockNumber == 5) {
            try {
                val intent = Intent(this, SelfReportActivity::class.java).apply {
                    putExtra("blockNumber", currentBlockNumber)
                    putExtra("blockName", currentBlockName)
                    putExtra("participantName", participantName)
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

        // 설문조사 없는 블록들의 처리
        when (currentBlockNumber) {
            2 -> {
                // 1-back (1회차) 완료 → 2-back (1회차)로
                currentBlockNumber = 3
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
                startButton.text = "2-Back (1회차) 시작"
                timerText.text = "1-Back (1회차) 완료!"
                Toast.makeText(this, "1-Back (1회차) 완료! 2-Back (1회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
            4 -> {
                // 1-back (2회차) 완료 → 2-back (2회차)로
                currentBlockNumber = 5
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
                startButton.text = "2-Back (2회차) 시작"
                timerText.text = "1-Back (2회차) 완료!"
                Toast.makeText(this, "1-Back (2회차) 완료! 마지막 2-Back (2회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
        }

        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }


}

