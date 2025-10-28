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
import androidx.appcompat.app.AlertDialog
import android.view.MotionEvent

class MainActivity : AppCompatActivity() {
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
        val userAnswer: String,
        val firstTouchTime: Long,
        val lastTouchTime: Long,
        val duration: Long
    )
    // ▼ 각 트라이얼의 최초/최종 터치 시각 기록(ms)
    private var trialFirstTouchTime: Long = 0L
    private var trialLastTouchTime: Long = 0L

    companion object {
        // ▼ 추가: 다른 Activity들이 참조할 수 있도록 static 변수들 추가
        @JvmStatic
        var currentParticipantBaseDir: String = ""
        @JvmStatic
        var currentParticipantName: String = ""
    }

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
// 흐름 처리
        val resumeFromBlock = intent.getIntExtra("resumeFromBlock", -1)
        val startBaselineSurvey = intent.getBooleanExtra("startBaselineSurvey", false)

// 안전장치: 재진입 시 남아있을 수 있는 플래그는 제거
        intent.removeExtra("startBaselineSurvey")

        if (resumeFromBlock >= 0) {
            Log.d("NBack", "Flow: Survey -> Experiment (resuming from block $resumeFromBlock)")
            participantName = intent.getStringExtra("participantName") ?: participantName
            initializeParticipantDirectories()
            resumeFromSelfReport(resumeFromBlock)
        } else if (startBaselineSurvey) {
            Log.d("NBack", "Flow: Tutorial -> Baseline Survey")
            participantName = intent.getStringExtra("participantName") ?: participantName
            initializeParticipantDirectories()
            startBaselineSurvey()
        } else {
            Log.d("NBack", "Flow: Start -> Manual")
            startManualActivity()
        }
    }

    // ▼ 변경: 단순화된 폴더 구조로 변경
    private fun initializeParticipantDirectories() {
        try {
            // Downloads 폴더를 기본 저장 위치로 사용
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Downloads 폴더가 존재하지 않으면 생성
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // NBack 실험 결과 메인 폴더 생성
            val baseExperimentDir = File(downloadsDir, "NBack_Experiment_Results").apply {
                if (!exists()) mkdirs()
            }

            // 피험자별 폴더 생성 (타임스탬프 포함으로 중복 방지)
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val participantDirName = "${participantName}"

            participantBaseDir = File(baseExperimentDir, participantDirName).apply {
                if (!exists()) mkdirs()
            }

            // ▼ 변경: 단순화된 구조
            // nback_images 폴더만 하위 폴더로 생성
            participantImagesDir = File(participantBaseDir, "nback_images").apply {
                if (!exists()) mkdirs()
            }

            // CSV 파일들은 피험자 폴더 직접 아래에 생성
            nbackResultsFile = File(participantBaseDir, "nback_result.csv")
            surveyResultsFile = File(participantBaseDir, "nback_survey.csv")

            // 폴더 생성 확인 및 로그
            val foldersCreated = listOf(
                "Base: ${participantBaseDir.exists()} - ${participantBaseDir.absolutePath}",
                "Images: ${participantImagesDir.exists()} - ${participantImagesDir.absolutePath}",
                "NBack CSV: ${nbackResultsFile.parentFile?.exists()} - ${nbackResultsFile.absolutePath}",
                "Survey CSV: ${surveyResultsFile.parentFile?.exists()} - ${surveyResultsFile.absolutePath}"
            )

            Log.d("NBack", "Participant directories created:")
            foldersCreated.forEach { Log.d("NBack", it) }

            // 사용자에게 저장 위치 알림
            Toast.makeText(
                this,
                "데이터 저장 위치:\nDownloads/NBack_Experiment_Results/\n${participantDirName}/",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("NBack", "폴더 생성 실패: ${e.message}", e)

            // Downloads 폴더 접근 실패 시 앱 전용 폴더로 fallback
            Toast.makeText(this, "Downloads 폴더 접근 실패. 앱 전용 폴더를 사용합니다: ${e.message}", Toast.LENGTH_LONG).show()

            try {
                val fallbackRoot = getExternalFilesDir(null) ?: filesDir
                val baseExperimentDir = File(fallbackRoot, "nback_experiment_results").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val participantDirName = "${participantName}"

                participantBaseDir = File(baseExperimentDir, participantDirName).apply { mkdirs() }
                currentParticipantBaseDir = participantBaseDir.absolutePath
                participantImagesDir = File(participantBaseDir, "nback_images").apply { mkdirs() }
                nbackResultsFile = File(participantBaseDir, "nback_result.csv")
                surveyResultsFile = File(participantBaseDir, "nback_survey.csv")

                Log.d("NBack", "Fallback directories created at: ${participantBaseDir.absolutePath}")
            } catch (fallbackException: Exception) {
                Log.e("NBack", "Fallback 폴더 생성도 실패: ${fallbackException.message}", fallbackException)
                Toast.makeText(this, "폴더 생성 완전 실패: ${fallbackException.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ▼ 변경: Downloads 폴더 접근을 위한 권한 요청 강화
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // API 레벨별 권한 처리
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE 권한 필요
                if (!Environment.isExternalStorageManager()) {
                    // 설정으로 이동하여 권한 허용 요청
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        Toast.makeText(this, "Downloads 폴더 접근을 위해 '모든 파일 액세스' 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("NBack", "권한 설정 화면 열기 실패: ${e.message}")
                        Toast.makeText(this, "권한 설정을 수동으로 허용해주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10 (API 29): 제한된 접근이지만 일부 권한 요청
                permissions.addAll(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ))
            }

            else -> {
                // Android 9 이하: 기존 권한 방식
                permissions.addAll(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ))
            }
        }

        // 필요한 권한들 중 허용되지 않은 것들만 요청
        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), 100)
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

            // 권한 처리 완료 후 폴더 재초기화
            initializeParticipantDirectories()
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
        countdownTimer = object : CountDownTimer(300, 1000) {
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
            Toast.makeText(this, "매뉴얼 화면 로딩 실패. 튜토리얼로 이동합니다.", Toast.LENGTH_SHORT).show()
            startTutorialActivity()
        }
    }

    private fun startTutorialActivity() {
        try {
            val intent = Intent(this, TutorialActivity::class.java).apply {
                putExtra("participantName", participantName)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("NBack", "Failed to start Tutorial Activity: ${e.message}")
            Toast.makeText(this, "튜토리얼 화면 로딩 실패. 설문조사로 이동합니다.", Toast.LENGTH_SHORT).show()
            startBaselineSurvey()
        }
    }

    private fun startBaselineSurvey() {
        Log.d("NBack", "=== Starting Baseline Survey ===")
        Log.d("NBack", "Participant: $participantName")
        Log.d("NBack", "Survey file path: ${surveyResultsFile.absolutePath}")
        Log.d("NBack", "Base dir: ${participantBaseDir.absolutePath}")

        try {
            val intent = Intent(this, SelfReportActivity::class.java).apply {
                putExtra("blockNumber", 0)
                putExtra("blockName", "Baseline")
                putExtra("participantName", participantName)
                putExtra("surveyType", "baseline")
                // ▼ 추가: survey 저장 경로 전달
                putExtra("surveyFilePath", surveyResultsFile.absolutePath)
                putExtra("participantBaseDir", participantBaseDir.absolutePath)
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

            // ✅ 30초 대기 조건: (1) 0-back 첫 시작 (블록1)  (2) 1-back 2회차 시작 (블록5)
            val isFirstStartOf0Back = (currentBlockNumber == 1 && currentN == 0 && currentTrial == 0)
            val isSecondSession1Back = (currentBlockNumber == 5 && currentN == 1 && currentTrial == 0)

            if (isFirstStartOf0Back || isSecondSession1Back) {
                showCountdown(30)   // 30초 카운트다운
            } else {
                showCountdown()     // 기본 3초
            }
        }


        clearButton.setOnClickListener {
            drawingView.clearCanvas()
            canvasHintText.visibility = View.VISIBLE
        }
        restartAppButton.setOnClickListener { restartApplication() }

        // 캔버스 터치 시 힌트 텍스트 숨기기
        drawingView.setOnTouchListener { _, event ->
            // 힌트 텍스트 숨김
            canvasHintText.visibility = View.GONE

            try {
                if (currentPhase == Phase.RESPONSE && event != null) {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            if (trialFirstTouchTime == 0L) {
                                trialFirstTouchTime = System.currentTimeMillis()
                            }
                            trialLastTouchTime = System.currentTimeMillis()
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // 펜이 화면에 닿아있는 동안에도 최신 시각으로 갱신
                            if (trialFirstTouchTime != 0L) {
                                trialLastTouchTime = System.currentTimeMillis()
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            if (trialFirstTouchTime != 0L) {
                                trialLastTouchTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            false
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

    // ▼ 변경: 카운트다운 타이머를 필드에 보관하고 재시작마다 취소
    private fun showCountdown(seconds: Int = 3) {
        currentPhase = Phase.COUNTDOWN
        countdownTimer?.cancel()

        var left = seconds
        timerText.text = "시작까지 $left 초"

        val totalMillis = seconds * 1000L
        countdownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                left = (millisUntilFinished / 1000).toInt() + 1
                timerText.text = "시작까지 $left 초"
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
        // 트라이얼 터치 타이밍 초기화
        trialFirstTouchTime = 0L
        trialLastTouchTime = 0L

        currentPhase = Phase.RESPONSE
        val responseTime = 3000L

        responseTimer?.cancel()
        responseTimer = object : CountDownTimer(responseTime, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                timerText.text = "답 작성 시간: ${secondsLeft}초"
            }

            override fun onFinish() {
                // 바로 저장하고 다음 시행으로 진행
                saveTrialData()
                drawingView.clearCanvas()
                canvasHintText.visibility = View.VISIBLE
                currentTrial++
                showNextStimulus()
            }
        }.start()
    }

    private fun saveTrialData() {
        val recognizedText = drawingView.getRecognizedText()
        val correctAnswer = getCorrectAnswer()

        val timestamp = System.currentTimeMillis()
        if (trialFirstTouchTime > 0L && trialLastTouchTime == 0L) {
            trialLastTouchTime = timestamp
        }
        val imageFileName = "${currentBlockName}_trial${String.format("%02d", currentTrial + 1)}_" +
                "stimulus${currentStimulus}_${timestamp}"

        // ▼ 강화된 이미지 저장 로직
        var imageSaveSuccess = false
        if (drawingView.hasUserDrawing()) {
            Log.d("NBack", "=== Starting image save ===")
            Log.d("NBack", "Image target dir: ${participantImagesDir.absolutePath}")
            Log.d("NBack", "Image dir exists: ${participantImagesDir.exists()}")
            Log.d("NBack", "Image dir writable: ${participantImagesDir.canWrite()}")

            // 이미지 디렉토리 확실히 생성
            if (!participantImagesDir.exists()) {
                val mkdirResult = participantImagesDir.mkdirs()
                Log.d("NBack", "Created image directory: $mkdirResult")
            }

            try {
                imageSaveSuccess = drawingView.saveCanvasAsPNG(imageFileName, participantImagesDir.absolutePath)

                if (imageSaveSuccess) {
                    val savedImageFile = File(participantImagesDir, "$imageFileName.png")
                    val imageSize = if (savedImageFile.exists()) savedImageFile.length() else 0
                    Log.d("NBack", "✅ Image saved successfully: ${savedImageFile.absolutePath}")
                    Log.d("NBack", "Image file size: $imageSize bytes")
                } else {
                    Log.e("NBack", "❌ Failed to save image: $imageFileName.png")

                    // 이미지 저장 실패 시 비상 저장 시도
                    try {
                        val emergencyImageDir = File(filesDir, "emergency_images").apply { mkdirs() }
                        val emergencySuccess = drawingView.saveCanvasAsPNG("emergency_$imageFileName", emergencyImageDir.absolutePath)
                        Log.d("NBack", "Emergency image save: $emergencySuccess")
                    } catch (e: Exception) {
                        Log.e("NBack", "Emergency image save failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("NBack", "Exception during image save", e)
            }
        } else {
            Log.d("NBack", "No drawing to save for trial ${currentTrial + 1}")
        }

        val trialData = TrialData(
            block = currentBlockName,
            trial = currentTrial + 1,
            n = currentN,
            stimulus = currentStimulus,
            correctAnswer = getCorrectAnswer(),
            currentTime = "${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}",
            timestamp = timestamp - experimentStartTime,
            computerTime = "${System.currentTimeMillis()}",
            userAnswer = recognizedText,
            firstTouchTime = trialFirstTouchTime,
            lastTouchTime = trialLastTouchTime,
            duration = if (trialFirstTouchTime > 0L && trialLastTouchTime > 0L) trialLastTouchTime - trialFirstTouchTime else 0L
        )

        experimentData.add(trialData)
        saveTrialDataToCSV(trialData)

        Log.d("NBack", "=== Trial ${currentTrial + 1} Summary ===")
        Log.d("NBack", "Stimulus: $currentStimulus, Correct: $correctAnswer, User: $recognizedText")
        Log.d("NBack", "Image saved: $imageSaveSuccess")
        Log.d("NBack", "Total trials in memory: ${experimentData.size}")
        Log.d("NBack", "CSV file size: ${if (nbackResultsFile.exists()) nbackResultsFile.length() else 0} bytes")
    }

    // ▼ 변경: CSV append 문제 해결
    private fun saveTrialDataToCSV(trialData: TrialData) {
        Log.d("NBack", "=== Starting trial data save ===")
        Log.d("NBack", "Target file: ${nbackResultsFile.absolutePath}")
        Log.d("NBack", "Parent dir exists: ${nbackResultsFile.parentFile?.exists()}")
        Log.d("NBack", "File exists: ${nbackResultsFile.exists()}")

        try {
            // 디렉토리 존재 확인 및 생성
            val parentDir = nbackResultsFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val mkdirResult = parentDir.mkdirs()
                Log.d("NBack", "Created directories: $mkdirResult")
            }

            val needsHeader = !nbackResultsFile.exists() || nbackResultsFile.length() == 0L
            Log.d("NBack", "Needs header: $needsHeader")

            // ▼ 변경: FileWriter를 사용하여 append 모드로 작성
            FileWriter(nbackResultsFile, true).use { writer ->
                if (needsHeader) {
                    writer.write("participant,block,trial,n,stimulus,correct_answer,computer_time,current_time,first_touch_time,last_touch_time,duration\n")
                                Log.d("NBack", "Header written")
                }

                val csvLine = "${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                        "${trialData.correctAnswer},${trialData.computerTime},${trialData.currentTime}," +
                        "${trialData.firstTouchTime},${trialData.lastTouchTime},${trialData.duration}\n"

                writer.write(csvLine)
                writer.flush()

                Log.d("NBack", "CSV line written: $csvLine")
            }

            val fileSize = nbackResultsFile.length()
            Log.d("NBack", "Trial data saved successfully. File size: $fileSize bytes")

        } catch (e: Exception) {
            Log.e("NBack", "Failed to save trial data to CSV", e)

            // Fallback: 내부 저장소에 저장
            try {
                val fallbackFile = File(filesDir, "nback_emergency.csv")
                FileWriter(fallbackFile, true).use { writer ->
                    if (!fallbackFile.exists() || fallbackFile.length() == 0L) {
                        writer.write("participant,block,trial,n,stimulus,correct_answer,computer_time,current_time,first_touch_time,last_touch_time,duration\n")
                    }

                    val csvLine = "${participantName},${trialData.block},${trialData.trial},${trialData.n},${trialData.stimulus}," +
                            "${trialData.correctAnswer},${trialData.computerTime},${trialData.currentTime}," +
                            "${trialData.firstTouchTime},${trialData.lastTouchTime},${trialData.duration}\n"
                    writer.write(csvLine)
                    writer.flush()
                }
                Log.d("NBack", "Emergency save successful: ${fallbackFile.absolutePath}")
                Toast.makeText(this, "데이터 비상 저장 완료", Toast.LENGTH_SHORT).show()

            } catch (fe: Exception) {
                Log.e("NBack", "Emergency save also failed", fe)
                Toast.makeText(this, "데이터 저장 완전 실패: ${fe.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ▼ 변경: 강화된 최종 저장 및 파일 검증
    private fun saveDataToFile() {
        try {
            Log.d("NBack", "=== FINAL DATA VERIFICATION ===")

            // 실시간으로 저장되고 있으므로 최종 검증만 수행
            val imageCount = if (participantImagesDir.exists()) participantImagesDir.listFiles()?.size ?: 0 else 0
            val nbackExists = nbackResultsFile.exists()
            val nbackSize = if (nbackExists) nbackResultsFile.length() else 0
            val surveyExists = surveyResultsFile.exists()
            val surveySize = if (surveyExists) surveyResultsFile.length() else 0

            Log.d("NBack", "=== FILE VERIFICATION RESULTS ===")
            Log.d("NBack", "Base directory: ${participantBaseDir.absolutePath}")
            Log.d("NBack", "Base directory exists: ${participantBaseDir.exists()}")
            Log.d("NBack", "Images directory: ${participantImagesDir.absolutePath}")
            Log.d("NBack", "Images directory exists: ${participantImagesDir.exists()}")
            Log.d("NBack", "N-Back CSV: ${nbackResultsFile.absolutePath}")
            Log.d("NBack", "N-Back CSV exists: $nbackExists, size: $nbackSize bytes")
            Log.d("NBack", "Survey CSV: ${surveyResultsFile.absolutePath}")
            Log.d("NBack", "Survey CSV exists: $surveyExists, size: $surveySize bytes")
            Log.d("NBack", "Image files count: $imageCount")
            Log.d("NBack", "Total trials in memory: ${experimentData.size}")

            // 파일 내용 샘플 확인 (N-Back CSV)
            if (nbackExists && nbackSize > 0) {
                try {
                    val firstLines = nbackResultsFile.readLines().take(3)
                    Log.d("NBack", "N-Back CSV first lines:")
                    firstLines.forEachIndexed { index, line ->
                        Log.d("NBack", "Line $index: $line")
                    }
                } catch (e: Exception) {
                    Log.e("NBack", "Failed to read N-Back CSV sample", e)
                }
            }

            // 이미지 파일 목록 확인
            if (participantImagesDir.exists()) {
                val imageFiles = participantImagesDir.listFiles()
                Log.d("NBack", "Image files:")
                imageFiles?.forEach { file ->
                    Log.d("NBack", "- ${file.name} (${file.length()} bytes)")
                }
            }

            // 문제점 진단
            val issues = mutableListOf<String>()

            if (!nbackExists) {
                issues.add("N-Back 결과 파일 없음")
            } else if (nbackSize == 0L) {
                issues.add("N-Back 결과 파일 비어있음")
            }

            if (!surveyExists) {
                issues.add("설문 결과 파일 없음")
            } else if (surveySize == 0L) {
                issues.add("설문 결과 파일 비어있음")
            }

            if (imageCount == 0) {
                issues.add("이미지 파일 없음")
            }

            val downloadsPath = "Downloads/NBack_Experiment_Results/${participantName}/"
            val message = if (issues.isEmpty()) {
                "✅ 전체 실험 완료!\n" +
                        "참가자: $participantName\n" +
                        "저장 위치: $downloadsPath\n" +
                        "- N-Back 결과: ${if (nbackExists) "저장됨" else "없음"} (${nbackSize} bytes)\n" +
                        "  → nback_results/nback_result.csv\n" +
                        "- 설문 결과: ${if (surveyExists) "저장됨" else "없음"} (${surveySize} bytes)\n" +
                        "  → survey_results/nback_survey.csv\n" +
                        "- 그림 데이터: ${imageCount}개\n" +
                        "  → nback_images/ 폴더\n" +
                        "총 시행 수: ${experimentData.size}\n" +
                        "총 ${totalBlocks}개 블록 완료!\n\n" +
                        "파일 탐색기에서 Downloads 폴더를 확인하세요.\n" +
                        "새로운 참가자를 위해 앱을 재시작하세요."
            } else {
                "⚠️ 실험 완료 (일부 문제 발견)\n" +
                        "참가자: $participantName\n" +
                        "저장 위치: $downloadsPath\n" +
                        "문제점: ${issues.joinToString(", ")}\n" +
                        "- N-Back 결과: ${if (nbackExists) "저장됨" else "❌없음"} (${nbackSize} bytes)\n" +
                        "- 설문 결과: ${if (surveyExists) "저장됨" else "❌없음"} (${surveySize} bytes)\n" +
                        "- 그림 데이터: ${imageCount}개\n" +
                        "메모리 내 시행 수: ${experimentData.size}\n\n" +
                        "⚠️ 일부 데이터가 저장되지 않았을 수 있습니다.\n" +
                        "앱 내부 저장소도 확인해보세요."
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // 앱 재시작 버튼 표시
            restartAppButton.visibility = View.VISIBLE
            restartAppButton.text = "새 참가자를 위한 앱 재시작"
            emergencyButton.visibility = View.GONE

            Log.d("NBack", "=== EXPERIMENT COMPLETION SUMMARY ===")
            Log.d("NBack", "Issues found: ${issues.size}")
            issues.forEach { issue -> Log.w("NBack", "Issue: $issue") }
            Log.d("NBack", "App restart button enabled")

        } catch (e: Exception) {
            Log.e("NBack", "Final data verification failed", e)
            Toast.makeText(this, "데이터 검증 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCorrectAnswer(): String {
        return if (currentTrial >= currentN) {
            // n-back 정답: n번째 이전 자극(0-back이면 현재 자극)
            stimulusList[currentTrial - currentN].toString()
        } else {
            ""
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
                    // ▼ 추가: survey 저장 경로 전달
                    putExtra("surveyFilePath", surveyResultsFile.absolutePath)
                    putExtra("participantBaseDir", participantBaseDir.absolutePath)
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
                // 30초 휴식 종료 → 바로 다음 블록 세팅 후 시작
                setupNextBlock()
                isExperimentRunning = true
                experimentStartTime = System.currentTimeMillis()
                startButton.text = "실험 진행 중..."
                timerText.text = "실험 시작!"
                showNextStimulus()
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