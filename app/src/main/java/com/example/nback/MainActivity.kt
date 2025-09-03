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
    private lateinit var emergencyButton: Button
    private lateinit var restartAppButton: Button

    // 데이터 관리 변수들
    private lateinit var participantBaseDir: File
    private lateinit var participantImagesDir: File
    private lateinit var participantResultsDir: File
    private lateinit var participantSurveyDir: File

    // 실험 변수들
    private var participantName = ""
    private var currentN = 0
    private var currentTrial = 0
    private var totalTrials = 30 // 실제 실험용 (테스트시 2로 변경)
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

        // 화면 꺼짐 방지 (자동 회전 허용)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeViews()
        requestPermissions()

        // Intent에서 participantName 받기
        participantName = intent.getStringExtra("participantName") ?: "Unknown_${System.currentTimeMillis()}"

        // 피험자 폴더 구조 초기화
        initializeParticipantDirectories()

        // 흐름 처리
        val resumeFromBlock = intent.getIntExtra("resumeFromBlock", -1)

        if (resumeFromBlock >= 0) {
            // 설문조사에서 돌아온 경우
            participantName = intent.getStringExtra("participantName") ?: participantName
            initializeParticipantDirectories()
            resumeFromSelfReport(resumeFromBlock)
        } else {
            // 처음 시작하는 경우 → ManualActivity로 이동
            startManualActivity()
        }
    }

    private fun initializeParticipantDirectories() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseExperimentDir = File(downloadsDir, "nback_experiment_results")

            if (!baseExperimentDir.exists()) {
                baseExperimentDir.mkdirs()
            }

            participantBaseDir = File(baseExperimentDir, participantName)
            if (!participantBaseDir.exists()) {
                participantBaseDir.mkdirs()
            }

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
            startBaselineSurvey()
        }
    }

    private fun startBaselineSurvey() {
        try {
            val intent = Intent(this, SelfReportActivity::class.java).apply {
                putExtra("blockNumber", 0)
                putExtra("blockName", "Baseline")
                putExtra("participantName", participantName)
                putExtra("surveyType", "baseline")
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("NBack", "Failed to start baseline survey: ${e.message}")
            Toast.makeText(this, "설문조사 실패. 실험을 바로 시작합니다.", Toast.LENGTH_SHORT).show()
            startExperimentDirectly()
        }
    }

    private fun startExperimentDirectly() {
        generateStimulusSequence(0)
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
        emergencyButton = findViewById(R.id.emergencyButton)
        restartAppButton = findViewById(R.id.restartAppButton)

        // 처음에는 앱 재시작 버튼 숨김
        restartAppButton.visibility = View.GONE

        // 버튼 클릭 리스너 설정
        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }
        startButton.setOnClickListener { startExperiment() }
        emergencyButton.setOnClickListener { showEmergencyDialog() }
        restartAppButton.setOnClickListener { restartApplication() }

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
        when (blockNumber) {
            0 -> {
                currentBlockNumber = 1
                currentN = 0
                generateStimulusSequence(0)
                updateInstructionText()
                startButton.text = "0-Back 시작"
                timerText.text = "사전 설문 완료! 실험을 시작하세요."
                Toast.makeText(this, "사전 설문 완료! 0-Back 실험을 시작하세요.", Toast.LENGTH_LONG).show()
                startButton.isEnabled = true
            }
            4 -> {
                currentBlockNumber = 5
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
                startButton.text = "1-Back (2회차) 시작"
                timerText.text = "중간 설문 완료! 버튼을 눌러 다음 블록을 시작하세요."
                Toast.makeText(this, "중간 설문 완료! 버튼을 눌러 1-Back (2회차)을 시작하세요.", Toast.LENGTH_LONG).show()
                startButton.isEnabled = true
            }
            7 -> {
                saveDataToFile()
                startButton.text = "실험 완료"
                startButton.isEnabled = false
                timerText.text = "모든 실험 완료!"
                Toast.makeText(this, "전체 실험 완료! 수고하셨습니다!", Toast.LENGTH_LONG).show()
                return
            }
            else -> {
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

    // 긴급 옵션 다이얼로그
    private fun showEmergencyDialog() {
        val options = arrayOf(
            "현재 블록 다시 시작",
            "이전 블록으로 이동",
            "특정 블록으로 이동",
            "실험 처음부터 다시 시작",
            "취소"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("긴급 옵션")
            .setMessage("어떤 작업을 수행하시겠습니까?")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> restartCurrentBlock()
                    1 -> moveToPreviousBlock()
                    2 -> showBlockSelectionDialog()
                    3 -> restartExperimentFromBeginning()
                    4 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun restartCurrentBlock() {
        isExperimentRunning = false
        currentTrial = 0
        generateStimulusSequence(currentN)
        updateInstructionText()

        startButton.text = "${currentN}-Back 다시 시작"
        startButton.isEnabled = true
        timerText.text = "현재 블록을 다시 시작합니다."
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"

        Toast.makeText(this, "현재 블록을 다시 시작합니다.", Toast.LENGTH_LONG).show()
        Log.d("NBack", "Emergency restart: Block $currentBlockNumber ($currentN-back)")
    }

    private fun moveToPreviousBlock() {
        if (currentBlockNumber > 1) {
            currentBlockNumber--

            currentN = when (currentBlockNumber) {
                1 -> 0
                2, 5 -> 1
                3, 6 -> 2
                4, 7 -> 3
                else -> 0
            }

            currentTrial = 0
            generateStimulusSequence(currentN)
            updateInstructionText()

            isExperimentRunning = false
            startButton.text = "${currentN}-Back 시작"
            startButton.isEnabled = true
            timerText.text = "이전 블록으로 이동했습니다."
            progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"

            Toast.makeText(this, "블록 $currentBlockNumber (${currentN}-Back)으로 이동했습니다.", Toast.LENGTH_LONG).show()
            Log.d("NBack", "Emergency move to previous block: Block $currentBlockNumber ($currentN-back)")
        } else {
            Toast.makeText(this, "이미 첫 번째 블록입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBlockSelectionDialog() {
        val blocks = arrayOf(
            "블록 1: 0-Back",
            "블록 2: 1-Back (1회차)",
            "블록 3: 2-Back (1회차)",
            "블록 4: 3-Back (1회차)",
            "블록 5: 1-Back (2회차)",
            "블록 6: 2-Back (2회차)",
            "블록 7: 3-Back (2회차)"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("블록 선택")
            .setMessage("이동할 블록을 선택하세요:")
            .setItems(blocks) { dialog, which ->
                moveToSpecificBlock(which + 1)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun moveToSpecificBlock(blockNumber: Int) {
        if (blockNumber in 1..7) {
            currentBlockNumber = blockNumber

            currentN = when (currentBlockNumber) {
                1 -> 0
                2, 5 -> 1
                3, 6 -> 2
                4, 7 -> 3
                else -> 0
            }

            currentTrial = 0
            generateStimulusSequence(currentN)
            updateInstructionText()

            isExperimentRunning = false
            startButton.text = "${currentN}-Back 시작"
            startButton.isEnabled = true
            timerText.text = "선택한 블록으로 이동했습니다."
            progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"

            Toast.makeText(this, "블록 $currentBlockNumber (${currentN}-Back)으로 이동했습니다.", Toast.LENGTH_LONG).show()
            Log.d("NBack", "Emergency move to specific block: Block $currentBlockNumber ($currentN-back)")
        }
    }

    private fun restartExperimentFromBeginning() {
        android.app.AlertDialog.Builder(this)
            .setTitle("실험 재시작 확인")
            .setMessage("실험을 처음부터 다시 시작하시겠습니까?\n현재까지의 데이터는 유지됩니다.")
            .setPositiveButton("재시작") { _, _ ->
                currentBlockNumber = 1
                currentN = 0
                currentTrial = 0
                isExperimentRunning = false

                generateStimulusSequence(0)
                updateInstructionText()

                startButton.text = "0-Back 시작"
                startButton.isEnabled = true
                timerText.text = "실험을 처음부터 다시 시작합니다."
                progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"

                Toast.makeText(this, "실험을 처음부터 다시 시작합니다.", Toast.LENGTH_LONG).show()
                Log.d("NBack", "Emergency restart: Experiment restarted from beginning")
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun restartApplication() {
        android.app.AlertDialog.Builder(this)
            .setTitle("앱 재시작 확인")
            .setMessage("새로운 참가자를 위해 앱을 재시작하시겠습니까?\n시작 화면으로 돌아갑니다.")
            .setPositiveButton("재시작") { _, _ ->
                val intent = Intent(this, StartActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // N-back 시퀀스 생성
    private fun generateStimulusSequence(n: Int, length: Int = 30, targetRatio: Float = 0.33f) {
        stimulusList.clear()
        val digits = (0..9).toList()
        val numTargets = (length * targetRatio).toInt()

        when (n) {
            0 -> {
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
                repeat(length) { stimulusList.add(digits.random()) }
                val candidatePositions = (n until length).toList()
                val targetPositions = candidatePositions.shuffled().take(numTargets)

                targetPositions.forEach { i ->
                    stimulusList[i] = stimulusList[i - n]
                }

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

        object : CountDownTimer(500, 100) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startResponsePeriod()
            }
        }.start()
    }

    private fun startResponsePeriod() {
        val responseTime = 3000L

        object : CountDownTimer(responseTime, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                timerText.text = "답 작성 시간: ${secondsLeft}초"
            }

            override fun onFinish() {
                timerText.text = "시간 종료"
                saveTrialData()

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

        val timestamp = System.currentTimeMillis()
        val imageFileName = "${currentBlockName}_trial${String.format("%02d", currentTrial + 1)}_" +
                "stimulus${currentStimulus}_${timestamp}"

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
        saveTrialDataToCSV(trialData)

        Log.d("NBack", "Trial ${currentTrial + 1}: stimulus=$currentStimulus, correct=$correctAnswer, user=$recognizedText")
        Log.d("NBack", "Total trials saved so far: ${experimentData.size}")
    }

    private fun saveTrialDataToCSV(trialData: TrialData) {
        try {
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val file = File(participantResultsDir, "nback_results_${participantName}_${todayDate}.csv")

            val needsHeader = !file.exists()

            FileWriter(file, true).use { writer ->
                if (needsHeader) {
                    writer.write("participant,block,trial,n,stimulus,correct_answer,computer_time,timestamp,current_time,user_answer\n")
                }

                writer.write("${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                        "${trialData.correctAnswer},${trialData.computerTime},${trialData.timestamp},${trialData.currentTime},${trialData.userAnswer}\n")
                writer.flush()
            }

            Log.d("NBack", "Trial data saved to CSV: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("NBack", "Failed to save trial data to CSV: ${e.message}")
        }
    }

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
                    "총 ${totalBlocks}개 블록 완료!\n\n" +
                    "새로운 참가자를 위해 앱을 재시작하세요."

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // 앱 재시작 버튼 표시
            restartAppButton.visibility = View.VISIBLE
            restartAppButton.text = "새 참가자를 위한 앱 재시작"
            emergencyButton.visibility = View.GONE

            Log.d("NBack", "Final data saved to: ${finalFile.absolutePath}")
            Log.d("NBack", "App restart button enabled")

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
                finish()
                return
            } catch (e: Exception) {
                Log.e("NBack", "Failed to start SelfReportActivity: ${e.message}")
                Toast.makeText(this, "설문조사 화면 로딩 실패. 다음 블록으로 진행합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 블록 1,2,3,5,6 완료 시 → 30초 휴식 후 자동으로 다음 블록 시작
        if (currentBlockNumber in 1..3 || currentBlockNumber in 5..6) {
            startButton.isEnabled = false
            startAutoRestAndNextBlock()
            return
        }

        // 기타 경우
        startButton.isEnabled = true
        updateInstructionText()
        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }

    private fun startAutoRestAndNextBlock() {
        val restTime = 30000L
        var timeLeft = restTime / 1000

        val currentBlockMessage = when (currentBlockNumber) {
            1 -> "0-Back 완료!"
            2 -> "1-Back (1회차) 완료!"
            3 -> "2-Back (1회차) 완료!"
            5 -> "1-Back (2회차) 완료!"
            6 -> "2-Back (2회차) 완료!"
            else -> "블록 완료!"
        }

        Toast.makeText(this, "$currentBlockMessage 30초 후 다음 블록이 자동 시작됩니다.", Toast.LENGTH_LONG).show()

        val restTimer = object : CountDownTimer(restTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished / 1000
                timerText.text = "$currentBlockMessage\n다음 블록까지: ${timeLeft}초"
                startButton.text = "휴식 중... (${timeLeft}초)"
            }

            override fun onFinish() {
                setupNextBlock()

                timerText.text = "3초 후 자동 시작..."
                startButton.text = "자동 시작 중..."

                object : CountDownTimer(3000, 1000) {
                    var countdown = 3
                    override fun onTick(millisUntilFinished: Long) {
                        countdown = (millisUntilFinished / 1000).toInt() + 1
                        timerText.text = "자동 시작까지 $countdown 초"
                    }

                    override fun onFinish() {
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

    private fun setupNextBlock() {
        when (currentBlockNumber) {
            1 -> {
                currentBlockNumber = 2
                currentN = 1
                generateStimulusSequence(1)
                updateInstructionText()
            }
            2 -> {
                currentBlockNumber = 3
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
            }
            3 -> {
                currentBlockNumber = 4
                currentN = 3
                generateStimulusSequence(3)
                updateInstructionText()
            }
            5 -> {
                currentBlockNumber = 6
                currentN = 2
                generateStimulusSequence(2)
                updateInstructionText()
            }
            6 -> {
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