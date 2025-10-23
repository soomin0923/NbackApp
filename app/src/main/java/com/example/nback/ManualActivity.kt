package com.example.nback

import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class ManualActivity : AppCompatActivity() {

    private lateinit var manualText: TextView
    private lateinit var backButton: Button
    private lateinit var startButton: Button
    private lateinit var scrollView: ScrollView

    private var participantName = ""

    // 토큰(이미지 삽입 지점)
    private val CONFIG_IMG_TOK = "[[CONFIG_IMG]]"
    private val EXPLAIN_IMG_TOK = "[[EXPLAIN_IMG]]"
    private val EXPLAIN_GIF_TOK = "[[EXPLAIN_GIF]]"

    // 리소스 파일명(확장자 제외)
    private val CONFIG_IMAGE_NAME = "config_example"
    private val EXPLAIN_IMAGE_NAME = "explain_example"
    private val EXPLAIN_GIF_NAME   = "explain_anim" // drawable-nodpi/explain_anim.gif

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        participantName = intent.getStringExtra("participantName") ?: "Unknown"
        Log.d("Manual", "=== ManualActivity Started ===")
        Log.d("Manual", "Participant: $participantName")

        initializeViews()
        buildManualWithInlineImages() // ✅ 섹션 사이에 이미지/움짤 삽입
        setupClickListeners()
    }

    private fun initializeViews() {
        manualText = findViewById(R.id.manualText)
        backButton = findViewById(R.id.backButton)
        startButton = findViewById(R.id.startButton)
        scrollView = findViewById(R.id.scrollView)
    }

    /**
     * [실험 구성] 아래: (이미지)
     * [설명] 아래: (이미지) (움짤)
     * 그 다음 [입력 방법] 이어지는 UI 구성
     */
    private fun buildManualWithInlineImages() {
        // 1) ScrollView 자식 확보/래핑
        val firstChild = scrollView.getChildAt(0)
        val container: ViewGroup = if (firstChild is ViewGroup) {
            firstChild
        } else {
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(16))
            }
            scrollView.removeAllViews()
            if (firstChild != null) wrapper.addView(firstChild)
            scrollView.addView(wrapper)
            wrapper
        }

        // 2) 본문(토큰 포함)
        val manualContent = """
        📋 N-Back 실험 사용법
        
        안녕하세요, $participantName 님! 실험에 참여해주셔서 감사합니다.
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        🎯 실험 목적
        작업 기억(Working Memory) 능력을 측정하는 인지 실험입니다.
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📊 실험 구성
        $CONFIG_IMG_TOK
        • 총 7개 블록 (약 20-30분 소요)
        • 각 블록당 총 숫자 30개 제시
            - [pre-survey]
            - [1회차]
            - 블록 1(0-back) -> 블록 2(1-back) -> 블록 3(2-back) -> 블록 4(3-back)
            - [mid-survey]
            - [2회차]
            - 블록 5(1-back) -> 블록 6(2-back) -> 블록 7(3-back)
            - [post-survey]

        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        📢 설명
        $EXPLAIN_IMG_TOK
        (아래 예시 이미지를 참고하세요)
        $EXPLAIN_GIF_TOK
        
        【0-Back】현재 숫자를 그대로 작성 / 【1-Back】1개 전 숫자 작성 / 【2-Back】2개 전 숫자 작성】/ 【3-Back】3개 전 숫자 작성
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ✏️ 입력 방법
        1. 센서를 연결한 S-Pen 
        2. 숫자는 0~9 (한 자리)
        3. 명확하고 크게 작성
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        ⏰ 시간 제한
        • 숫자 제시: 0.5초
        • 답 작성: 3초
        • 블록 간 휴식: 30초
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        준비가 되셨으면 '시작' 버튼을 눌러주세요.
        먼저 연습 단계를 거친 후 본 실험이 진행됩니다.
        """.trimIndent()

        // 3) 토큰 이전/사이/이후 텍스트를 순차 배치
        val partsA = manualContent.split(CONFIG_IMG_TOK, limit = 2)
        val textBeforeConfig = partsA.getOrNull(0).orEmpty()
        val afterConfig = partsA.getOrNull(1).orEmpty()

        manualText.text = textBeforeConfig
        var insertIndex = (container.indexOfChild(manualText).takeIf { it >= 0 } ?: container.childCount) + 1

        // (이미지) — [실험 구성] 아래
        val configImageView = makeStaticImageView(resolveDrawableId(CONFIG_IMAGE_NAME))
        container.addView(configImageView, insertIndex++)

        // CONFIG 이후 ~ EXPLAIN_IMG 전 텍스트
        val partsB = afterConfig.split(EXPLAIN_IMG_TOK, limit = 2)
        val textBeforeExplainImg = partsB.getOrNull(0).orEmpty()
        val afterExplainImg = partsB.getOrNull(1).orEmpty()

        container.addView(makeBodyTextView(textBeforeExplainImg), insertIndex++)

        // (이미지) — [설명] 아래
        val explainImageView = makeStaticImageView(resolveDrawableId(EXPLAIN_IMAGE_NAME))
        container.addView(explainImageView, insertIndex++)

        // EXPLAIN_IMG 이후 ~ EXPLAIN_GIF 전 텍스트
        val partsC = afterExplainImg.split(EXPLAIN_GIF_TOK, limit = 2)
        val textBeforeExplainGif = partsC.getOrNull(0).orEmpty()
        val afterExplainGif = partsC.getOrNull(1).orEmpty()

        container.addView(makeBodyTextView(textBeforeExplainGif), insertIndex++)

        // (움짤) — [설명] 아래
        val gifView = makeAnimatedImageView(resolveDrawableId(EXPLAIN_GIF_NAME))
        container.addView(gifView, insertIndex++)

        // 나머지 텍스트(= [입력 방법] 포함)
        if (afterExplainGif.isNotBlank()) {
            container.addView(makeBodyTextView(afterExplainGif), insertIndex)
        }
    }

    private fun makeBodyTextView(text: String): TextView =
        TextView(this).apply {
            id = View.generateViewId()
            this.text = text
            textSize = 14f
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    /** 정적 이미지 뷰 (없는 경우 sample_nback_static로 대체) */
    private fun makeStaticImageView(drawableId: Int): ImageView =
        ImageView(this).apply {
            id = View.generateViewId()
            contentDescription = "예시 이미지"
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
            setImageResource(if (drawableId != 0) drawableId else R.drawable.sample_nback_static)

            Log.d("Manual", "Static image loaded: drawableId=$drawableId")
        }

    /** GIF/애니 WebP 뷰 (API 28+ 자동 재생, 미만은 정지 이미지 대체) */
    private fun makeAnimatedImageView(drawableId: Int): ImageView =
        ImageView(this).apply {
            id = View.generateViewId()
            contentDescription = "예시 움짤"
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(16)
            }

            if (drawableId == 0) {
                setImageResource(R.drawable.sample_nback_static)
                Log.w("Manual", "Animated image not found, using sample_nback_static")
                return@apply
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val src = ImageDecoder.createSource(resources, drawableId)
                    val dr = ImageDecoder.decodeDrawable(src)
                    setImageDrawable(dr)
                    if (dr is AnimatedImageDrawable) {
                        dr.start()
                        Log.d("Manual", "Animated image started: drawableId=$drawableId")
                    }
                } catch (e: Throwable) {
                    setImageResource(R.drawable.sample_nback_static)
                    Log.e("Manual", "Failed to load animated image: ${e.message}")
                }
            } else {
                // 필요하면 Glide/Coil로 대체 가능
                setImageResource(R.drawable.sample_nback_static)
                Log.d("Manual", "API < 28, using static image instead of animation")
            }
        }

    /** 파일명이 존재하면 drawable id, 없으면 0 반환 */
    private fun resolveDrawableId(name: String): Int {
        val id = resources.getIdentifier(name, "drawable", packageName)
        Log.d("Manual", "Resolved drawable '$name' -> id=$id")
        return id
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            Log.d("Manual", "Back button clicked")
            finish()
        }
        startButton.setOnClickListener {
            Log.d("Manual", "Start button clicked - moving to TutorialActivity")
            startTutorialActivity()
        }
    }

    private fun startTutorialActivity() {
        Log.d("Manual", "Starting TutorialActivity")
        Log.d("Manual", "Participant: $participantName")

        val intent = Intent(this, TutorialActivity::class.java).apply {
            putExtra("participantName", participantName)
        }
        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}