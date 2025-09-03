package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var participantNameEditText: EditText
    private lateinit var confirmNameButton: Button
    private lateinit var manualButton: Button
    private var participantName = ""
    private var isNameConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        initializeViews()
        setupInitialState()
        setupClickListeners()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        participantNameEditText = findViewById(R.id.participantNameEditText)
        confirmNameButton = findViewById(R.id.confirmNameButton)
        manualButton = findViewById(R.id.manualButton)
    }

    private fun setupInitialState() {
        // 제목 설정
        titleText.text = "N-Back 실험에 오신 것을 환영합니다"

        // 처음에는 Manual 버튼 비활성화
        manualButton.isEnabled = false
        manualButton.alpha = 0.5f
        manualButton.text = "이름을 먼저 입력해주세요"

        // 이름 확인 버튼 설정
        confirmNameButton.text = "이름 확인"

        // EditText 힌트 설정
        participantNameEditText.hint = "예: 홍길동"
        participantNameEditText.requestFocus()
    }

    private fun setupClickListeners() {
        // 1단계: 피험자 이름 확인
        confirmNameButton.setOnClickListener {
            confirmParticipantName()
        }

        // 2단계: Manual 시작 (이름 확인 후에만 가능)
        manualButton.setOnClickListener {
            if (isNameConfirmed && participantName.isNotEmpty()) {
                startManualActivity()
            } else {
                Toast.makeText(this, "먼저 피험자 이름을 확인해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 엔터키로 이름 확인
        participantNameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmParticipantName()
                true
            } else {
                false
            }
        }
    }

    private fun confirmParticipantName() {
        val name = participantNameEditText.text.toString().trim()
        if (name.isNotEmpty() && name.length >= 2) {
            participantName = name
            isNameConfirmed = true

            // 이름 입력 완료 후 상태 변경
            participantNameEditText.isEnabled = false
            participantNameEditText.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))

            confirmNameButton.text = "확인완료: $participantName"
            confirmNameButton.isEnabled = false

            // Manual 버튼 활성화
            manualButton.isEnabled = true
            manualButton.alpha = 1.0f
            manualButton.text = "매뉴얼 및 실험 시작"
            manualButton.setBackgroundColor(resources.getColor(android.R.color.holo_blue_bright, null))

            Toast.makeText(this, "이름이 확인되었습니다. 이제 매뉴얼을 진행해주세요.", Toast.LENGTH_LONG).show()

        } else if (name.isEmpty()) {
            Toast.makeText(this, "피험자 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            participantNameEditText.requestFocus()
        } else {
            Toast.makeText(this, "이름은 2글자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            participantNameEditText.requestFocus()
        }
    }

    private fun startManualActivity() {
        val intent = Intent(this, ManualActivity::class.java).apply {
            putExtra("participantName", participantName)
        }
        startActivity(intent)
        // StartActivity는 종료하지 않음 (뒤로가기 가능하도록)
    }

    // 앱이 다시 포어그라운드로 올 때 (실험 완료 후 돌아올 때)
    override fun onResume() {
        super.onResume()

        // 이름이 이미 확인된 상태라면 초기화
        if (isNameConfirmed) {
            resetForNewParticipant()
        }
    }

    private fun resetForNewParticipant() {
        // 새로운 참가자를 위한 초기화
        participantNameEditText.setText("")
        participantNameEditText.isEnabled = true
        participantNameEditText.setBackgroundColor(resources.getColor(android.R.color.white, null))
        participantNameEditText.requestFocus()

        confirmNameButton.text = "이름 확인"
        confirmNameButton.isEnabled = true

        manualButton.isEnabled = false
        manualButton.alpha = 0.5f
        manualButton.text = "이름을 먼저 입력해주세요"
        manualButton.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))

        participantName = ""
        isNameConfirmed = false

        Toast.makeText(this, "새로운 참가자를 위해 화면이 초기화되었습니다.", Toast.LENGTH_LONG).show()
    }
}