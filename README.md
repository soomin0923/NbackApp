# NbackApp

N-Back 인지부하 실험을 위한 Android 앱 (Kotlin)

## 개요
FingSense 연구에서 피험자의 인지부하를 측정하기 위해 직접 개발한 실험용 Android app
펜 센서(PPG/IMU/FSR)로 수집되는 생체 신호 데이터와 동기화하여 실험 진행

## 주요 기능
- N-Back 과제 실험 진행
- (튜토리얼 및 pre-survey 진행, 0-back, 1-back, 2-back, 3-back, mid-survey, 1-back, 2-back, 3-back, post-survey)
- 각 trial 30, stimulus 0.5s, interval time 3.0s
- 실험 중 타임스탬프 기록으로 센서 데이터와 동기화
- 실험 전후 설문 통합 (NASA-TLX, STAI-6)

## 개발 환경
- Language: Kotlin
- Platform: Android
