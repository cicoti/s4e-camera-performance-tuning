# s4e-camera-performance-tuning

POC Java/Maven para descoberta e tuning de performance de cameras RTSP com GStreamer.

## Requisitos

- Java 22
- Maven
- GStreamer instalado no sistema

## Como executar

Configure a camera de teste por variavel de ambiente:

```powershell
$env:S4E_TEST_CAMERA_CODE = "CAM01_INTELBRAS"
$env:S4E_TEST_RTSP_URL = "rtsp://usuario:senha@host:554/cam/realmonitor?channel=1&subtype=0"
```

Ou por propriedades Java:

```powershell
mvn exec:java -Dexec.mainClass=com.s4etech.performance.v2.AppPerformanceTuningV2 -Ds4e.test.cameraCode=CAM01_INTELBRAS -Ds4e.test.rtspUrl="rtsp://usuario:senha@host:554/cam/realmonitor?channel=1&subtype=0"
```

## Fluxo atual

1. Descobre codec e disponibilidade TCP/UDP.
2. Gera candidatos de tuning combinando protocolo, decoder, latency e buffer.
3. Executa benchmark tecnico com `fakesink`.
4. Escolhe recomendacao por regra deterministica priorizando estabilidade.
5. Gera relatorio CSV em `logs/`.

O preview visual existe no codigo, mas esta desabilitado no fluxo principal.
