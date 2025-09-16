package com.example.nback
// 상단 import 추가
import android.os.Build

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog
import androidx.annotation.RequiresApi
import android.content.ContentValues
import android.provider.MediaStore
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class MainActivity : AppCompatActivity() {
    // MainActivity 클래스 안에 필드로 추가
    private var sessionId: String = ""      // 첫 초기화 때만 만들어 끝까지 재사용
    private var didInitDirs: Boolean = false
    // MainActivity 클래스 안에 추가
    private fun restoreParticipantDirectories(baseDirPath: String) {
        participantBaseDir = File(baseDirPath)
        participantImagesDir = File(participantBaseDir, "nback_images").apply { mkdirs() }
        nbackResultsFile = File(participantBaseDir, "nback_results.csv")
        surveyResultsFile = File(participantBaseDir, "survey.csv")
        didInitDirs = true
        Log.d("NBack", "Restored dirs: ${participantBaseDir.absolutePath}")
    }

    // 실행 단계
    private enum class Phase { NONE, COUNTDOWN, STIMULUS, RESPONSE, INTER_TRIAL, REST, AUTO_START }
    private var currentPhase: Phase = Phase.NONE
    private var isPaused = false
    private var pausedPhase = Phase.NONE
    private var pausedAtTrial = -1
    private var pausedBlock = -1

    private var countdownTimer: CountDownTimer? = null
    private var stimulusTimer: CountDownTimer? = null
    private var responseTimer: CountDownTimer? = null
    private var interTrialTimer: CountDownTimer? = null
    private var restTimer: CountDownTimer? = null
    private var autoStartTimer: CountDownTimer? = null

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

    // ▼ 변경: 단순화된 데이터 관리 변수들
    private lateinit var participantBaseDir: File
    private lateinit var participantImagesDir: File
    private lateinit var nbackResultsFile: File
    private lateinit var surveyResultsFile: File

    // 실험 변수들
    private var participantName = ""
    private var currentN = 0
    private var currentTrial = 0
    private var totalTrials = 1
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
        val userAnswer: String,
        // ▼ 추가
        val firstTouchTimeMs: Long,
        val lastTouchTimeMs: Long,
        val touchDurationMs: Long
    )


    companion object {
        // ▼ 추가: 다른 Activity들이 참조할 수 있도록 static 변수들 추가
        @JvmStatic
        var currentParticipantBaseDir: String = ""
        @JvmStatic
        var currentParticipantName: String = ""
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // ...

        initializeViews()
        requestPermissions()

        participantName = intent.getStringExtra("participantName") ?: "Unknown_${System.currentTimeMillis()}"

        // 🔸 SelfReport 에서 넘겨준 기존 폴더 경로가 있으면 재사용
        val returnedBaseDir = intent.getStringExtra("participantBaseDir")
        if (!returnedBaseDir.isNullOrEmpty()) {
            restoreParticipantDirectories(returnedBaseDir)
        } else {
            initializeParticipantDirectories()  // 기존대로 1회 초기화
        }

        val resumeFromBlock = intent.getIntExtra("resumeFromBlock", -1)
        if (resumeFromBlock >= 0) {
            participantName = intent.getStringExtra("participantName") ?: participantName
            // ❗여기서 다시 initializeParticipantDirectories() 호출하지 말 것!
            resumeFromSelfReport(resumeFromBlock)
        } else {
            startManualActivity()
        }
    }


    // REPLACE: initializeParticipantDirectories()
    private fun initializeParticipantDirectories() {
        // 이미 한 번 했으면 재진입 금지
        if (didInitDirs && ::nbackResultsFile.isInitialized && nbackResultsFile.parentFile?.exists() == true) {
            Log.d("NBack", "Dirs already initialized. Using: ${participantBaseDir.absolutePath}")
            return
        }

        val baseRoot = getExternalFilesDir(null) ?: filesDir
        val baseExperimentDir = File(baseRoot, "nback_experiment_results").apply { mkdirs() }

        // 세션 ID를 처음 한 번만 생성
        if (sessionId.isEmpty()) {
            sessionId = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        }
        val participantDirName = "${participantName}_$sessionId"

        participantBaseDir   = File(baseExperimentDir, participantDirName).apply { mkdirs() }
        participantImagesDir = File(participantBaseDir, "nback_images").apply { mkdirs() }
        nbackResultsFile     = File(participantBaseDir, "nback_results.csv")
        surveyResultsFile    = File(participantBaseDir, "survey.csv")

        didInitDirs = true

        Log.d("NBack", "Init dirs (once): ${participantBaseDir.absolutePath}")
        Toast.makeText(this, "저장 위치: ${participantBaseDir.absolutePath}", Toast.LENGTH_LONG).show()
    }

    // ▼ 변경: Downloads 폴더 접근을 위한 권한 요청 강화
// REPLACE: requestPermissions()
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val perms = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val need = perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (need) ActivityCompat.requestPermissions(this, perms, 100)
        }
    }

    // ▼ 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (deniedPermissions.isNotEmpty()) {
                val message = "다음 권한이 거부되었습니다: ${deniedPermissions.joinToString(", ")}\n" +
                        "실험 데이터가 앱 전용 폴더에 저장됩니다."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.w("NBack", "권한 거부됨: $deniedPermissions")
            } else {
                Toast.makeText(this, "권한이 허용되었습니다. Downloads 폴더에 저장됩니다.", Toast.LENGTH_SHORT).show()
                Log.d("NBack", "모든 권한 허용됨")
            }

            if (!didInitDirs) {
                initializeParticipantDirectories()
            }
        }
    }

    private fun pauseRun() {
        if (isPaused) return
        isPaused = true
        pausedPhase = currentPhase
        pausedAtTrial = currentTrial
        pausedBlock = currentBlockNumber

        cancelAllTimers()

        startButton.text = "일시정지됨"
        timerText.text = "긴급 일시정지"
        Toast.makeText(this, "실험이 일시정지되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun resumeRun() {
        if (!isPaused) return
        isPaused = false
        startButton.text = "실험 진행 중..."
        // 재개 전 3초 카운트다운
        showResumeCountdown {
            when (pausedPhase) {
                Phase.COUNTDOWN, Phase.STIMULUS, Phase.RESPONSE, Phase.INTER_TRIAL -> {
                    // 같은 시행부터 다시 보여주고 응답시간 초기화
                    showStimulusForCurrentTrial()
                }
                Phase.REST, Phase.AUTO_START -> {
                    // 휴식 중이었으면 다음 블록으로 바로 진행(필요시 로직 변경)
                    setupNextBlock()
                    showCountdown()
                }
                else -> showNextStimulus()
            }
        }
    }

    private fun finishToStart() {
        cancelAllTimers()
        isPaused = false
        // 시작 화면으로 이동 (패키지명/StartActivity 클래스명은 프로젝트 기준으로)
        startActivity(Intent(this, StartActivity::class.java))
        finish()
    }

    private fun showResumeCountdown(onFinished: () -> Unit) {
        var left = 3
        timerText.text = "재개까지 $left 초"
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                left = (millisUntilFinished / 1000).toInt() + 1
                timerText.text = "재개까지 $left 초"
            }
            override fun onFinish() {
                timerText.text = "재개!"
                onFinished()
            }
        }.start()
    }

    /** 현재 trial부터 다시 진행(증분 없음) */
    private fun showStimulusForCurrentTrial() {
        // trial 범위 체크
        if (currentTrial >= totalTrials || stimulusList.isEmpty()) {
            finishCurrentBlock()
            return
        }
        currentPhase = Phase.STIMULUS

        currentStimulus = stimulusList[currentTrial]
        stimulusText.text = currentStimulus.toString()
        stimulusText.visibility = View.VISIBLE
        progressText.text = "시행: ${currentTrial + 1} / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        timerText.text = "숫자 제시 중..."

        stimulusTimer?.cancel()
        stimulusTimer = object : CountDownTimer(500, 100) {
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startResponsePeriod()  // 응답시간은 새로 부여
            }
            override fun onTick(millisUntilFinished: Long) {}
        }.start()
    }

    private fun showEmergencyMenu() {
        AlertDialog.Builder(this)
            .setTitle("긴급 메뉴")
            .setItems(arrayOf("이어하기", "현재 블록 다시 시작", "실험 종료")) { _, which ->
                when (which) {
                    0 -> resumeRun()
                    1 -> restartCurrentBlock()
                    2 -> finishToStart()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ▼ 추가: 인덱스/리스트 가드
    private fun ensureStimuliReady(): Boolean {
        if (stimulusList.isEmpty()) {
            Log.w("NBack", "Stimulus list empty. Regenerating for n=$currentN")
            generateStimulusSequence(currentN)
        }
        if (currentTrial >= stimulusList.size) {
            Log.w("NBack", "currentTrial=${currentTrial} out of bounds(size=${stimulusList.size}). Finishing block.")
            return false
        }
        return true
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
                putExtra("surveyFilePath", surveyResultsFile.absolutePath)
                putExtra("participantBaseDir", participantBaseDir.absolutePath) // ✅ 추가
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("NBack", "Failed to start baseline survey: ${e.message}")
            Toast.makeText(this, "설문조사 실패. 실험을 바로 시작합니다.", Toast.LENGTH_SHORT).show()
            startExperimentDirectly()
        }
    }

    // MainActivity.kt 내부에 추가
    private fun ensureSurveyCsvHeader() {
        try {
            if (!::surveyResultsFile.isInitialized) return
            if (!surveyResultsFile.exists()) {
                surveyResultsFile.parentFile?.mkdirs()
                surveyResultsFile.writeText(
                    "participant,block,question_id,answer,current_time\n"
                )
                Log.d("NBack", "Survey CSV created with header: ${surveyResultsFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("NBack", "Failed to ensure survey header: ${e.message}", e)
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
        emergencyButton.setOnClickListener {
            pauseRun()           // 타이머 전부 정지 + 상태 보관
            showEmergencyMenu()  // 이어하기/다시시작/종료 선택
        }

        startButton.setOnClickListener {
            // 실험 시작 상태로 전환
            isExperimentRunning = true
            drawingView.isEnabled = true
            canvasHintText.visibility = View.GONE
            startButton.text = "실험 진행 중..."
            timerText.text = "실험 시작!"
            // 기존에 쓰던 실험 시작/카운트다운 루틴 호출
            showCountdown()  // 혹은 showNextStimulus() 등 네가 쓰는 함수
        }

        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }
        restartAppButton.setOnClickListener { restartApplication() }

        // 캔버스 터치 시 힌트 텍스트 숨기기
        drawingView.setOnTouchListener { _, _ ->
            canvasHintText.visibility = View.GONE
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

    // ▼ 변경: 카운트다운 타이머를 필드에 보관하고 재시작마다 취소
    private fun showCountdown() {
        currentPhase = Phase.COUNTDOWN
        countdownTimer?.cancel()
        var countdown = 3
        timerText.text = "시작까지 $countdown 초"

        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt() + 1
                timerText.text = "시작까지 $countdown 초"
            }

            override fun onFinish() {
                timerText.text = "실험 시작!"
                showNextStimulus()
            }
        }.start()
    }

    // ▼ 변경: 인덱스 가드 & 타이머 보관
    private fun showNextStimulus() {
        currentPhase = Phase.STIMULUS
        if (currentTrial >= totalTrials) {
            finishCurrentBlock()
            return
        }
        if (!ensureStimuliReady()) {
            finishCurrentBlock()
            return
        }

        currentStimulus = stimulusList[currentTrial]
        stimulusText.text = currentStimulus.toString()
        stimulusText.visibility = View.VISIBLE

        progressText.text = "시행: ${currentTrial + 1} / $totalTrials (블록 $currentBlockNumber/$totalBlocks)"
        timerText.text = "숫자 제시 중..."

        stimulusTimer?.cancel()
        stimulusTimer = object : CountDownTimer(500, 100) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stimulusText.visibility = View.INVISIBLE
                startResponsePeriod()
            }
        }.start()
    }

    // ▼ 변경: 타이머 보관 + 종료 후 다음 시행 타이머도 보관
    private fun startResponsePeriod() {
        currentPhase = Phase.RESPONSE
        drawingView.resetTouchCapture()
        val responseTime = 3000L

        responseTimer?.cancel()
        responseTimer = object : CountDownTimer(responseTime, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                timerText.text = "답 작성 시간: ${secondsLeft}초"
            }

            override fun onFinish() {
                currentPhase = Phase.INTER_TRIAL
                timerText.text = "시간 종료"
                saveTrialData()

                interTrialTimer?.cancel()
                interTrialTimer = object : CountDownTimer(1000, 1000) {
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
        }

        // ▼ 추가: 컴퓨터 시간(epoch ms) 기준의 터치 타임
        val firstMs = drawingView.getFirstTouchTimeMillis() ?: -1L
        val lastMs  = drawingView.getLastTouchTimeMillis()  ?: -1L
        val durationMs = if (firstMs > 0 && lastMs >= firstMs) lastMs - firstMs else 0L

        val trialData = TrialData(
            block = currentBlockName,
            trial = currentTrial + 1,
            n = currentN,
            stimulus = currentStimulus,
            correctAnswer = correctAnswer,
            currentTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date()),
            timestamp = timestamp,
            computerTime = System.currentTimeMillis().toString(),
            userAnswer = recognizedText,
            // ▼ 추가 필드
            firstTouchTimeMs = firstMs,
            lastTouchTimeMs = lastMs,
            touchDurationMs = durationMs
        )

        experimentData.add(trialData)
        saveTrialDataToCSV(trialData)

        Log.d("NBack", "Trial ${currentTrial + 1}: stimulus=$currentStimulus, correct=$correctAnswer, user=$recognizedText, first=$firstMs, last=$lastMs, dur=$durationMs")
    }

    // REPLACE: saveTrialDataToCSV(...)
    private fun saveTrialDataToCSV(trialData: TrialData) {
        try {
            nbackResultsFile.parentFile?.mkdirs()

            val needsHeader = !nbackResultsFile.exists() || nbackResultsFile.length() == 0L
            FileWriter(nbackResultsFile, true).use { writer ->
                if (needsHeader) {
                    writer.write(
                        "participant,block,trial,n,stimulus,correct_answer,computer_time,timestamp,current_time,user_answer," +
                                "first_touch_time_ms,last_touch_time_ms,touch_duration_ms\n"
                    )
                }

                writer.write(
                    "${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                            "${trialData.correctAnswer},${trialData.computerTime},${trialData.timestamp},${trialData.currentTime},${trialData.userAnswer}," +
                            "${trialData.firstTouchTimeMs},${trialData.lastTouchTimeMs},${trialData.touchDurationMs}\n"
                )
            }
            Log.d("NBack", "[WRITE OK] ${nbackResultsFile.absolutePath} size=${nbackResultsFile.length()}")
        } catch (e: Exception) {
            Log.e("NBack", "[WRITE FAIL] nback_results.csv : ${e.message}", e)
            Toast.makeText(this, "결과 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // ▼ 변경: 강화된 최종 저장 및 파일 검증
    @RequiresApi(Build.VERSION_CODES.O)
// REPLACE: saveDataToFile()
    private fun saveDataToFile() {
        try {
            // 핵심: nback_results.csv 존재/크기
            val nbackExists = nbackResultsFile.exists()
            val nbackSize   = if (nbackExists) nbackResultsFile.length() else 0L

            // 참고 정보(경고로 취급하지 않음)
            val imageCount  = if (::participantImagesDir.isInitialized && participantImagesDir.exists())
                participantImagesDir.listFiles()?.size ?: 0 else 0
            val surveyExists = surveyResultsFile.exists()
            val surveySize   = if (surveyExists) surveyResultsFile.length() else 0L

            // 요약 로그
            Log.d("NBack", "=== FINAL SUMMARY ===")
            Log.d("NBack", "Base: ${participantBaseDir.absolutePath}")
            Log.d("NBack", "NBack CSV: $nbackExists, size=$nbackSize")
            Log.d("NBack", "Survey CSV: $surveyExists, size=$surveySize")
            Log.d("NBack", "Images: $imageCount")

            // 사용자 메시지
            val downloadsPath = "Downloads/NBack_Experiment_Results/${participantBaseDir.name}/"
            val message = if (nbackExists && nbackSize > 0L) {
                "✅ 전체 실험 완료!\n" +
                        "참가자: $participantName\n" +
                        "저장 위치: $downloadsPath\n" +
                        "- N-Back 결과: 저장됨 (${nbackSize} bytes)\n" +
                        "- 설문 결과: ${if (surveyExists) "있음" else "없음"} (${surveySize} bytes)\n" +
                        "- 그림 데이터: ${imageCount}개\n" +
                        "총 시행 수: ${experimentData.size}\n" +
                        "총 ${totalBlocks}개 블록 완료!"
            } else {
                "⚠️ 핵심 결과 파일(nback_results.csv)이 비어있거나 없습니다.\n" +
                        "참가자: $participantName\n" +
                        "저장 위치: $downloadsPath\n" +
                        "- N-Back 결과: ${if (nbackExists) "있음" else "없음"} (${nbackSize} bytes)\n" +
                        "- 설문 결과: ${if (surveyExists) "있음" else "없음"} (${surveySize} bytes)\n" +
                        "- 그림 데이터: ${imageCount}개\n" +
                        "필요시 다시 실행해 주세요."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // UI 마무리
            restartAppButton.visibility = View.VISIBLE
            restartAppButton.text = "새 참가자를 위한 앱 재시작"
            emergencyButton.visibility = View.GONE

            // 종료 시 ZIP 자동 내보내기
            exportParticipantDataToDownloads()

        } catch (e: Exception) {
            Log.e("NBack", "Finalization failed", e)
            Toast.makeText(this, "데이터 정리 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            exportParticipantDataToDownloads()
        }
    }

    // ZIP 만들기
    @RequiresApi(Build.VERSION_CODES.O)
    private fun zipDir(srcDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            val base = srcDir.toPath()
            srcDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val entryName = base.relativize(f.toPath()).toString()
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportParticipantDataToDownloads() {
        val baseDir = if (::participantBaseDir.isInitialized) participantBaseDir else return
        Thread {
            val outName = "nback_${participantName}_${System.currentTimeMillis()}.zip"
            val tmpZip = File(cacheDir, outName).apply { if (exists()) delete() }
            try {
                // 1) 앱 전용 폴더 전체 ZIP
                zipDir(baseDir, tmpZip)

                // 2) MediaStore로 Downloads/NBack에 저장 (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, outName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NBack")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { os ->
                            FileInputStream(tmpZip).use { it.copyTo(os) }
                        }
                        runOnUiThread { Toast.makeText(this, "Downloads/NBack/$outName 으로 내보냈습니다.", Toast.LENGTH_LONG).show() }
                    } else {
                        runOnUiThread { Toast.makeText(this, "내보내기 실패(MediaStore).", Toast.LENGTH_SHORT).show() }
                    }
                } else {
                    // API 28 이하
                    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outDir = File(dl, "NBack").apply { mkdirs() }
                    tmpZip.copyTo(File(outDir, outName), overwrite = true)
                    runOnUiThread { Toast.makeText(this, "Downloads/NBack/$outName 으로 내보냈습니다.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "내보내기 오류: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                tmpZip.delete()
            }
        }.start()
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
                    putExtra("surveyFilePath", surveyResultsFile.absolutePath)
                    putExtra("participantBaseDir", participantBaseDir.absolutePath) // ✅ 추가
                }
                startActivity(intent)
                finish()
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

    // ▼ 변경: 휴식/자동시작 타이머도 보관
    private fun startAutoRestAndNextBlock() {
        currentPhase = Phase.REST
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

        restTimer?.cancel()
        restTimer = object : CountDownTimer(restTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished / 1000
                timerText.text = "$currentBlockMessage\n다음 블록까지: ${timeLeft}초"
                startButton.text = "휴식 중... (${timeLeft}초)"
            }

            override fun onFinish() {
                currentPhase = Phase.AUTO_START
                setupNextBlock()
                timerText.text = "3초 후 자동 시작..."
                startButton.text = "자동 시작 중..."

                autoStartTimer?.cancel()
                autoStartTimer = object : CountDownTimer(3000, 1000) {
                    var countdown = 3
                    override fun onTick(millisUntilFinished: Long) {
                        countdown = (millisUntilFinished / 1000).toInt() + 1
                        timerText.text = "다음 task의 첫 번째 숫자 제시까지 $countdown 초"
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
        }.start()
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

    // ▼ 추가: 생명주기에서 모든 타이머 취소
    override fun onDestroy() {
        cancelAllTimers()
        super.onDestroy()
    }

    private fun cancelAllTimers() {
        countdownTimer?.cancel()
        stimulusTimer?.cancel()
        responseTimer?.cancel()
        interTrialTimer?.cancel()
        restTimer?.cancel()
        autoStartTimer?.cancel()
    }
}