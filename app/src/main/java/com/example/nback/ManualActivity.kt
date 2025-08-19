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

    // ====== 추가: 참가자 이름 변수 ======
    private var participantName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        // ====== 추가: 참가자 이름 받기 ======
        participantName = intent.getStringExtra("participantName") ?: "Unknown"

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
        
        안녕하세요, $participantName 님! 실험에 참여해주셔서 감사합니다.
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        🎯 실험 목적
        작업 기억(Working Memory) 능력을 측정하는 인지 실험입니다.
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📝 실험 구성
        • 총 7개 블록 (약 60분 소요)
        • 각 블록당 30개 시행
        • 블록별 난이도:
          - 블록 1: 0-Back
          - 블록 2: 1-Back (1회차)
          - 블록 3: 2-Back (1회차)
          - 블록 4: 3-Back (1회차)
          - 블록 5: 1-Back (2회차)
          - 블록 6: 2-Back (2회차)
          - 블록 7: 3-Back (2회차)
        
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
        
        【3-Back】
        • 3번째 이전에 표시된 숫자를 써주세요
        • 예: 3번째 이전 '2', 2번째 이전 '5', 1번째 이전 '3', 현재 '7' → '2'를 써주세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ✏️ 입력 방법
        1. S Pen을 사용하여 흰색 캔버스에 숫자를 써주세요
        2. 손가락으로도 입력 가능하지만 S Pen 사용을 권장합니다
        3. 숫자는 0~9 사이의 한 자리 숫자입니다
        4. 명확하고 크게 써주세요
        5. 잘못 쓰셨다면 '지우기' 버튼을 누르고 다시 써주세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ⏰ 시간 제한
        • 숫자 제시: 0.5초
        • 답 작성 시간: 3초
        • 각 시행 사이 간격: 1초
        • 블록 간 휴식: 30초 (자동 진행)
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📊 설문 조사
        • 실험 시작 전: 기본 상태 측정 (STAI 설문)
        • 블록 4 완료 후: 중간 평가 설문
        • 블록 7 완료 후: 최종 평가 설문
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        🔄 실험 진행 순서
        1. 매뉴얼 완료 (현재)
        2. 사전 설문조사 (기본 상태 측정)
        3. 0-Back 실험 시작
        4. 블록 1→2→3→4 자동 진행
        5. 중간 설문조사
        6. 블록 5→6→7 진행
        7. 최종 설문조사
        8. 실험 완료
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ⚠️ 주의사항
        • 실험 중에는 집중하여 참여해주세요
        • 각 블록은 연속으로 진행됩니다 (1→2→3→4)
        • 틀려도 괜찮으니 최선을 다해주세요
        • 휴대폰 알림 등을 미리 꺼주세요
        • 실험 중간에 휴식이 있으니 걱정하지 마세요
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ✅ 준비가 되셨다면 '실험 시작' 버튼을 눌러주세요!
        (매뉴얼 → 사전 설문 → 0-Back 실험 순서로 진행됩니다)
        """.trimIndent()

        manualText.text = manualContent
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish() // 이전 화면으로 돌아가기
        }

        // ====== 수정: 실험 시작 버튼 클릭 시 Baseline 설문으로 이동 ======
        startButton.setOnClickListener {
            startBaselineSurvey()
        }
    }

    // ====== 새로 추가: Baseline 설문조사 시작 함수 ======
    private fun startBaselineSurvey() {
        val intent = Intent(this, SelfReportActivity::class.java).apply {
            putExtra("blockNumber", 0) // 0은 baseline을 의미
            putExtra("blockName", "Baseline")
            putExtra("participantName", participantName)
            putExtra("surveyType", "baseline")
        }
        startActivity(intent)
        finish() // ManualActivity 종료
    }
}