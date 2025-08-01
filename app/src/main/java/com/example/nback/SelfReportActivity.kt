package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class SelfReportActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var blockInfoText: TextView

    // 설문 항목들
    private lateinit var difficultySeekBar: SeekBar
    private lateinit var difficultyValueText: TextView
    private lateinit var concentrationSeekBar: SeekBar
    private lateinit var concentrationValueText: TextView
    private lateinit var fatigueSeekBar: SeekBar
    private lateinit var fatigueValueText: TextView
    private lateinit var confidenceSeekBar: SeekBar
    private lateinit var confidenceValueText: TextView
    private lateinit var strategyEditText: EditText
    private lateinit var additionalCommentsEditText: EditText

    private lateinit var submitButton: Button

    // 전달받은 데이터
    private var blockNumber = 0
    private var blockName = ""
    private var participantName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_report)

        // Intent에서 데이터 받기
        blockNumber = intent.getIntExtra("blockNumber", 0)
        blockName = intent.getStringExtra("blockName") ?: ""
        participantName = intent.getStringExtra("participantName") ?: "Unknown"

        initializeViews()
        setupSeekBars()
        setupClickListeners()
        updateBlockInfo()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        blockInfoText = findViewById(R.id.blockInfoText)

        difficultySeekBar = findViewById(R.id.difficultySeekBar)
        difficultyValueText = findViewById(R.id.difficultyValueText)
        concentrationSeekBar = findViewById(R.id.concentrationSeekBar)
        concentrationValueText = findViewById(R.id.concentrationValueText)
        fatigueSeekBar = findViewById(R.id.fatigueSeekBar)
        fatigueValueText = findViewById(R.id.fatigueValueText)
        confidenceSeekBar = findViewById(R.id.confidenceSeekBar)
        confidenceValueText = findViewById(R.id.confidenceValueText)

        strategyEditText = findViewById(R.id.strategyEditText)
        additionalCommentsEditText = findViewById(R.id.additionalCommentsEditText)

        submitButton = findViewById(R.id.submitButton)
    }

    private fun setupSeekBars() {
        // 모든 SeekBar를 1-7 범위로 설정 (기본값 4)
        val seekBars = listOf(difficultySeekBar, concentrationSeekBar, fatigueSeekBar, confidenceSeekBar)
        val valueTexts = listOf(difficultyValueText, concentrationValueText, fatigueValueText, confidenceValueText)

        seekBars.forEachIndexed { index, seekBar ->
            seekBar.max = 6 // 0-6으로 설정하여 1-7 범위 구현
            seekBar.progress = 3 // 기본값 4 (0-based이므로 3)

            val valueText = valueTexts[index]
            valueText.text = "4"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = "${progress + 1}" // 1-7 범위로 표시
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            saveSelfReportData()
            returnToMainActivity()
        }
    }

    private fun updateBlockInfo() {
        val blockDescription = when (blockNumber) {
            1 -> "0-Back 블록"
            3 -> "2-Back (1회차) 블록"
            5 -> "2-Back (2회차) 블록 - 최종"
            else -> "블록 $blockNumber"
        }

        blockInfoText.text = "$blockDescription 완료!\n아래 질문들에 답해주세요."
    }

    private fun saveSelfReportData() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "nback_self_reports.csv")

            val timestamp = System.currentTimeMillis()
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // 파일이 없으면 헤더 추가
            val needsHeader = !file.exists()

            FileWriter(file, true).use { writer ->
                if (needsHeader) {
                    writer.write("timestamp,current_time,participant_name,block_number,block_name,difficulty,concentration,fatigue,confidence,strategy,additional_comments\n")
                }

                val difficulty = difficultySeekBar.progress + 1
                val concentration = concentrationSeekBar.progress + 1
                val fatigue = fatigueSeekBar.progress + 1
                val confidence = confidenceSeekBar.progress + 1
                val strategy = strategyEditText.text.toString().replace(",", ";") // CSV 형식을 위해 쉼표 제거
                val additionalComments = additionalCommentsEditText.text.toString().replace(",", ";")

                writer.write("$timestamp,$currentTime,$participantName,$blockNumber,$blockName,$difficulty,$concentration,$fatigue,$confidence,\"$strategy\",\"$additionalComments\"\n")
            }

            Log.d("SelfReport", "Self-report data saved for block $blockNumber")
            Toast.makeText(this, "설문 응답이 저장되었습니다.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("SelfReport", "Failed to save self-report data", e)
            Toast.makeText(this, "설문 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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