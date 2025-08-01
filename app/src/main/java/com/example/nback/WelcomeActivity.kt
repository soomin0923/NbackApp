package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var participantNameEditText: EditText
    private lateinit var manualButton: Button
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        participantNameEditText = findViewById(R.id.participantNameEditText)
        manualButton = findViewById(R.id.manualButton)
        startButton = findViewById(R.id.startButton)
    }

    private fun setupClickListeners() {
        manualButton.setOnClickListener {
            val intent = Intent(this, ManualActivity::class.java)
            startActivity(intent)
        }

        startButton.setOnClickListener {
            val participantName = participantNameEditText.text.toString().trim()

            if (participantName.isEmpty()) {
                participantNameEditText.error = "참가자 이름을 입력해주세요"
                participantNameEditText.requestFocus()
                return@setOnClickListener
            }

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("participantName", participantName)
            startActivity(intent)
            finish() // 시작화면 종료
        }
    }
}