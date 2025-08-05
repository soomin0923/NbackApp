package com.example.nback

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ManualActivity : AppCompatActivity() {

    private lateinit var manualText: TextView
    private lateinit var backButton: Button
    private lateinit var startButton: Button
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        initializeViews()
        setupManualContent()
        setupClickListeners()
    }

    private fun initializeViews() {
        manualText = findViewById(R.id.manualText)
        backButton = findViewById(R.id.backButton)
        startButton = findViewById(R.id.startButton)
        scrollView = findViewById(R.id.scrollView)
    }

    private fun setupManualContent() {
        val manualContent = """
        📋 N-Back 실험 사용법
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        🎯 실험 목적
        작업 기억(Working Memory) 능력을 측정하는 인지 실험입니다.
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📝 실험 구성
        • 총 5개 블록 (약 40분 소요)
        • 각 블록당 48개 시행
        • 블록별 난이도:
          - 블록 1: 0-Back
          - 블록 2: 1-Back (1회차)
          - 블록 3: 2-Back (1회차)
          - 블록 4: 1-Back (2회차)
          - 블록 5: 2-Back (2회차)
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        🔢 과제별 설명
        
        【0-Back】
        • 현재 화면에 표시되는 숫자를 그대로 써주세요
        • 예: 화면에 '7'이 나오면 → '7'을 써주세요
        
        【1-Back】
        • 1번째 이전에 표시된 숫자를 써주세요
        • 예: 이전에 '3', 현재 '7' → '3'을 써주세요
        
        【2-Back】
        • 2번째 이전에 표시된 숫자를 써주세요
        • 예: 2번째 이전 '5', 1번째 이전 '3', 현재 '7' → '5'를 써주세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ✏️ 입력 방법
        1. S Pen을 사용하여 흰색 캔버스에 숫자를 써주세요
        2. 손가락으로도 입력 가능하지만 S Pen 사용을 권장합니다
        3. 숫자는 0~9 사이의 한 자리 숫자입니다
        4. 명확하고 크게 써주세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ⏰ 시간 제한
        • 숫자 제시: 0.5초
        • 답 작성 시간: 3초
        • 각 시행 사이 간격: 1초
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📊 설문 조사
        • 블록 1, 3, 5 완료 후 간단한 설문을 작성합니다
        • 실험 중 느낀 어려움이나 집중도에 대한 질문입니다
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ⚠️ 주의사항
        • 실험 중에는 집중하여 참여해주세요
        • 각 블록은 연속으로 진행됩니다
        • 틀려도 괜찮으니 최선을 다해주세요
        • 휴대폰 알림 등을 미리 꺼주세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ✅ 준비가 되셨다면 '실험 시작' 버튼을 눌러주세요!
        """.trimIndent()

        manualText.text = manualContent
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish() // 이전 화면으로 돌아가기
        }

        startButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // 메뉴얼 화면 종료
        }
    }
}