package com.example.nback

import android.Manifest
import android.app.AlertDialog
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

class MainActivity : AppCompatActivity() {

    // UI 요소
    private lateinit var drawingView: DrawingView
    private lateinit var stimulusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var timerText: TextView
    private lateinit var progressText: TextView
    private lateinit var canvasHintText: TextView
    private lateinit var clearButton: Button
    private lateinit var startButton: Button
    private lateinit var skipTutorialButton: Button

    // 실험 변수
    private val tutorialData = mutableListOf<TrialData>()

    private var currentN = 0
    private var currentTrial = 0
    private var totalTrials =  30
    private var stimulusList = mutableListOf<Int>()
    private var experimentStartTime = 0L
    private var isExperimentRunning = false
    private var currentStimulus = 0
    private var currentBlockName = ""
    private var currentBlockNumber = 1
    private val totalBlocks = 5
    private var stimulusOnsetTime = 0L

    // 피험자 정보
    private var participantName = ""
    private var experimentStartDate = ""

    // 데이터 저장
    private val experimentData = mutableListOf<TrialData>()

    // 튜토리얼 관련 변수
    private var isTutorialMode = true
    private var tutorialNumbers = mutableListOf<Int>()
    private var tutorialTrial = 0
    private val totalTutorialTrials = 20

    data class TrialData(
        val participantName: String,
        val experimentDate: String,
        val block: String,
        val trial: Int,
        val n: Int,
        val stimulus: Int,
        val correctAnswer: String,
        val userAnswer: String,
        val timestamp: Long,
        val currentTime: String,
        val firstTouchTime: Long,
        val lastTouchTime: Long,
        val drawingDuration: Long,
        val strokeCount: Int,
        val avgStrokeDuration: Long,
        val sPenUsageRatio: Float,
        val responseTiming: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeViews()
        requestPermissions()

        // 튜토리얼 숫자 시퀀스 (0~9 두 번 반복)
        tutorialNumbers = (MutableList(10) { it } + MutableList(10) { it }).toMutableList()
        isTutorialMode = true
        tutorialTrial = 0
        startButton.text = "튜토리얼 시작"
        startButton.isEnabled = true
        skipTutorialButton.isEnabled = true

        // 튜토리얼 안내 시작
        progressText.text = "튜토리얼: 0 / $totalTutorialTrials"
        instructionText.text = "튜토리얼: 숫자 0~9까지 두 번씩 따라 써보세요!"
        timerText.text = ""
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
        skipTutorialButton = findViewById(R.id.skipTutorialButton)

        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }
        startButton.setOnClickListener {
            when {
                isTutorialMode && tutorialTrial == 0 -> {
                    // 튜토리얼 시작
                    tutorialNumbers = (List(10) { it } + List(10) { it }).toMutableList()
                    // tutorialNumbers.shuffle() // 필요시
                    startButton.isEnabled = false
                    showTutorialNext()
                }
                !isTutorialMode && !isExperimentRunning -> {
                    // 실험 시작(이 시점에서는 블록/시퀀스가 항상 준비되어 있어야 함)
                    startExperiment()
                }
            }
        }

        skipTutorialButton.setOnClickListener {
            if (isTutorialMode) {
                isTutorialMode = false
                tutorialTrial = totalTutorialTrials
                saveTutorialDataToFile()
                startButton.text = "0-Back 실험 시작"
                startButton.isEnabled = true
                skipTutorialButton.isEnabled = false

                // 0-back 실험 준비 (꼭 추가)
                currentBlockNumber = 1
                currentN = 0
                generateStimulusSequence(0, totalTrials)
                updateInstructionText()
                progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
                timerText.text = "튜토리얼을 건너뛰었습니다.\n0-Back 실험을 시작하세요!"
            }
        }
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

    // 튜토리얼 루프
    private fun showTutorialNext() {
        if (!isTutorialMode) return
        if (tutorialTrial >= totalTutorialTrials) {
            finishTutorial()
            return
        }
        val num = tutorialNumbers[tutorialTrial]
        stimulusText.text = num.toString()
        stimulusText.visibility = View.VISIBLE
        instructionText.text = "튜토리얼: 숫자 ${num}을(를) 따라 써보세요!\n(총 ${totalTutorialTrials}회)"
        timerText.text = "연습: 남은 ${totalTutorialTrials - tutorialTrial}회"
        canvasHintText.visibility = View.VISIBLE

        object : CountDownTimer(500, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startTutorialResponsePeriod()
            }
        }.start()
    }


    private fun startTutorialResponsePeriod() {
        val responseTime = 2000L
        val thisNum = tutorialNumbers[tutorialTrial]
        object : CountDownTimer(responseTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = "연습: 남은 시간 ${(millisUntilFinished / 1000) + 1}초"
            }
            override fun onFinish() {
                saveTutorialTrialData(thisNum)
                drawingView.clearCanvas()
                canvasHintText.visibility = View.VISIBLE
                tutorialTrial++
                progressText.text = "튜토리얼: ${tutorialTrial} / ${totalTutorialTrials}"
                showTutorialNext()
            }
        }.start()
    }

    private fun finishTutorial() {
        isTutorialMode = false
        saveTutorialDataToFile()
        // 실험 상태 초기화
        currentBlockNumber = 1
        currentN = 0
        currentTrial = 0
        isExperimentRunning = false
        generateStimulusSequence(0, totalTrials)
        updateInstructionText()
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        startButton.text = "0-Back 실험 시작"
        startButton.isEnabled = true
        instructionText.text = "튜토리얼이 끝났습니다.\n이제 0-Back 실험을 시작하세요!"
    }

    private fun saveTutorialTrialData(num: Int) {
        val recognizedText = drawingView.getRecognizedText()
        val firstTouchTime = drawingView.getFirstTouchTime()
        val lastTouchTime = drawingView.getLastTouchTime()
        val drawingDuration = drawingView.getTotalDrawingDuration()
        val strokeCount = drawingView.getStrokeCount()
        val avgStrokeDuration = drawingView.getAverageStrokeDuration()
        val sPenUsageRatio = drawingView.getSPenUsageRatio()
        val tutorialOnset = System.currentTimeMillis()
        val imageFileName = "tutorial_${participantName}_${String.format("%02d", tutorialTrial + 1)}_${num}_${tutorialOnset}"

        if (drawingView.hasUserDrawing()) {
            drawingView.saveCanvasAsPNG(imageFileName, participantName)
        }

        val trialData = TrialData(
            participantName = participantName,
            experimentDate = experimentStartDate,
            block = "Tutorial",
            trial = tutorialTrial + 1,
            n = 0,
            stimulus = num,
            correctAnswer = num.toString(),
            userAnswer = recognizedText,
            timestamp = tutorialOnset,
            currentTime = String.format("%.7f", tutorialOnset / 1000.0),
            firstTouchTime = firstTouchTime,
            lastTouchTime = lastTouchTime,
            drawingDuration = drawingDuration,
            strokeCount = strokeCount,
            avgStrokeDuration = avgStrokeDuration,
            sPenUsageRatio = sPenUsageRatio,
            responseTiming = "tutorial"
        )
        tutorialData.add(trialData)
    }

    private fun saveTutorialDataToFile() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val csvFileName = "tutorial_${participantName}_${experimentStartDate}_${System.currentTimeMillis()}.csv"
            val file = File(downloadsDir, csvFileName)
            FileWriter(file).use { writer ->
                writer.write("participant_name,experiment_date,currentTime,block,trial,n,stimulus,timestamp,first_touch_time,last_touch_time,drawing_duration\n")
                tutorialData.forEach { data ->
                    writer.write("${data.participantName},${data.experimentDate},${data.currentTime}," +
                            "${data.block},${data.trial},${data.n},${data.stimulus}," +
                            "${data.timestamp},${data.firstTouchTime},${data.lastTouchTime},${data.drawingDuration}\n")
                }
            }
            val imageDir = File(downloadsDir, "nback_images_${participantName}")
            val imageCount = if (imageDir.exists()) imageDir.listFiles()?.count { it.name.startsWith("tutorial_") } ?: 0 else 0
            val message = "튜토리얼 데이터 저장 완료!\nCSV: $csvFileName\n이미지: $imageCount"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d("NBack", "Tutorial data saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("NBack", "튜토리얼 데이터 저장 실패", e)
            Toast.makeText(this, "튜토리얼 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ------ 실험부 ------
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
    private fun generateStimulusSequence2(n: Int, length: Int = 30, targetRatio: Float = 0.1f) {
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
            0 -> "현재 화면에 표시되는 \n 숫자를 써주세요"
            1 -> "1번째 이전에 표시된 \n 숫자를 써주세요"
            2 -> "2번째 이전에 표시된 \n 숫자를 써주세요"
            else -> "N-Back 테스트"
        }
        val blockCount = when (currentN) {
            1 -> if (currentBlockNumber == 2) "1회차" else if (currentBlockNumber == 4) "2회차" else ""
            2 -> if (currentBlockNumber == 3) "1회차" else if (currentBlockNumber == 5) "2회차" else ""
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
        // 피험자 이름 입력 없으면 다이얼로그
        if (participantName.isEmpty()) {
            showParticipantNameDialog()
            return
        }
        isExperimentRunning = true
        currentTrial = 0
        experimentStartTime = System.currentTimeMillis()
        startButton.isEnabled = false
        skipTutorialButton.isEnabled = false
        startButton.text = "실험 진행 중..."
        updateInstructionText()
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        showCountdown()
    }

    private fun showParticipantNameDialog() {
        val input = EditText(this)
        input.hint = "피험자 이름을 입력하세요 (예: 홍길동)"
        AlertDialog.Builder(this)
            .setTitle("실험 참여자 정보")
            .setMessage("실험 데이터 관리를 위해 이름을 입력해주세요.")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    participantName = name
                    experimentStartDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date())
                    Toast.makeText(this, "${participantName}님, 실험에 참여해주셔서 감사합니다!",
                        Toast.LENGTH_LONG).show()
                    startButton.text = "${participantName}님 실험 시작"
                    Log.d("NBack", "Participant registered: $participantName, Date: $experimentStartDate")
                } else {
                    Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .setCancelable(false)
            .show()
    }

    private fun showCountdown() {
        var countdown = 3000
        timerText.text = "시작까지 $countdown 초"
        val countdownTimer = object : CountDownTimer(15000, 1000) {
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
        stimulusOnsetTime = System.currentTimeMillis()
        progressText.text = "시행: ${currentTrial + 1} / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        timerText.text = "숫자 제시 중..."
        drawingView.setStimulusOnsetTime(stimulusOnsetTime)
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
        val firstTouchTime = drawingView.getFirstTouchTime()
        val lastTouchTime = drawingView.getLastTouchTime()
        val drawingDuration = drawingView.getTotalDrawingDuration()
        val strokeCount = drawingView.getStrokeCount()
        val avgStrokeDuration = drawingView.getAverageStrokeDuration()
        val sPenUsageRatio = drawingView.getSPenUsageRatio()
        val absoluteFirstTouch = if (firstTouchTime > 0) firstTouchTime - experimentStartTime else 0L
        val absoluteLastTouch = if (lastTouchTime > 0) lastTouchTime - experimentStartTime else 0L
        val absoluteStimulusOnset = stimulusOnsetTime - experimentStartTime
        val reactionTime = if (firstTouchTime > 0) firstTouchTime - stimulusOnsetTime else 0L
        val responseTiming = when {
            reactionTime <= 0L -> "no_response"
            reactionTime <= 1333 -> "early"
            reactionTime <= 2666 -> "middle"
            else -> "late"
        }
        val currentTime = System.currentTimeMillis()
        val imageFileName = "${participantName}_${currentBlockName}_trial${String.format("%02d", currentTrial + 1)}_" +
                "stimulus${currentStimulus}_${currentTime}"
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
            participantName = participantName,
            experimentDate = experimentStartDate,
            block = currentBlockName,
            trial = currentTrial + 1,
            n = currentN,
            stimulus = currentStimulus,
            correctAnswer = correctAnswer,
            userAnswer = recognizedText,
            timestamp = absoluteStimulusOnset,
            currentTime = String.format("%.7f", stimulusOnsetTime / 1000.0),
            firstTouchTime = absoluteFirstTouch,
            lastTouchTime = absoluteLastTouch,
            drawingDuration = drawingDuration,
            strokeCount = strokeCount,
            avgStrokeDuration = avgStrokeDuration,
            sPenUsageRatio = sPenUsageRatio,
            responseTiming = responseTiming
        )
        experimentData.add(trialData)
        Log.d("NBack", "Trial ${currentTrial + 1}: stimulus=$currentStimulus " +
                "(onset: ${absoluteStimulusOnset}ms), " +
                "correct=$correctAnswer, user=$recognizedText, " +
                "firstTouch=${absoluteFirstTouch}ms, lastTouch=${absoluteLastTouch}ms, " +
                "reactionTime=${reactionTime}ms, duration=${drawingDuration}ms, " +
                "strokes=$strokeCount, timing=$responseTiming, sPenRatio=$sPenUsageRatio")
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
        skipTutorialButton.isEnabled = false
        when (currentBlockNumber) {
            1 -> {
                currentBlockNumber = 2
                currentN = 1
                generateStimulusSequence(1, totalTrials)
                updateInstructionText()
                startButton.text = "1-Back (1회차) 시작"
                timerText.text = "0-Back 완료!"
                Toast.makeText(this, "0-Back 완료! 1-Back (1회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
            2 -> {
                currentBlockNumber = 3
                currentN = 2
                generateStimulusSequence(2, totalTrials)
                updateInstructionText()
                startButton.text = "2-Back (1회차) 시작"
                timerText.text = "1-Back (1회차) 완료!"
                Toast.makeText(this, "1-Back (1회차) 완료! 2-Back (1회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
            3 -> {
                currentBlockNumber = 4
                currentN = 1
                generateStimulusSequence2(1, totalTrials)
                updateInstructionText()
                startButton.text = "1-Back (2회차) 시작"
                timerText.text = "2-Back (1회차) 완료!"
                Toast.makeText(this, "2-Back (1회차) 완료! 1-Back (2회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
            4 -> {
                currentBlockNumber = 5
                currentN = 2
                generateStimulusSequence2(2, totalTrials)
                updateInstructionText()
                startButton.text = "2-Back (2회차) 시작"
                timerText.text = "1-Back (2회차) 완료!"
                Toast.makeText(this, "1-Back (2회차) 완료! 마지막 2-Back (2회차)을 시작하세요.", Toast.LENGTH_LONG).show()
            }
            5 -> {
                saveDataToFile()
                startButton.text = "실험 완료"
                startButton.isEnabled = false
                timerText.text = "모든 실험 완료!"
                Toast.makeText(this, "🎉 전체 실험 완료! 수고하셨습니다!", Toast.LENGTH_LONG).show()
            }
        }
        currentTrial = 0
        progressText.text = "시행: 0 / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
    }

    private fun saveDataToFile() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val csvFileName = "nback_${participantName}_${experimentStartDate}_${System.currentTimeMillis()}.csv"
            val file = File(downloadsDir, csvFileName)
            FileWriter(file).use { writer ->
                writer.write("participant_name,experiment_date, currentTime, block,trial,n,stimulus," +
                        "timestamp,first_touch_time,last_touch_time,drawing_duration\n")
                experimentData.forEach { data ->
                    writer.write("${data.participantName},${data.experimentDate}, ${data.currentTime}," +
                            "${data.block},${data.trial},${data.n},${data.stimulus}," +
                            "${data.timestamp}," +
                            "${data.firstTouchTime},${data.lastTouchTime},${data.drawingDuration}\n")
                }
            }
            val imageDir = File(downloadsDir, "nback_images_${participantName}")
            val imageCount = if (imageDir.exists()) imageDir.listFiles()?.size ?: 0 else 0
            val message = "🎉 ${participantName}님 실험 완료!\n" +
                    "📊 CSV 파일: ${csvFileName}\n" +
                    "🖼️ 이미지 파일: ${imageCount}개\n" +
                    "📁 이미지 위치: Downloads/nback_images_${participantName}/\n" +
                    "총 ${totalBlocks}개 블록 완료!"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d("NBack", "Data saved to: ${file.absolutePath}")
            Log.d("NBack", "Images saved: $imageCount files in nback_images_${participantName} folder")
            Log.d("NBack", "Participant: $participantName, Date: $experimentStartDate")
            Log.d("NBack", "Total blocks completed: $totalBlocks")
        } catch (e: Exception) {
            Log.e("NBack", "데이터 저장 실패", e)
            Toast.makeText(this, "데이터 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
