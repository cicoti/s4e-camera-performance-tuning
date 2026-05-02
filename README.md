# s4e-camera-performance-tuning

POC Java/Maven para descoberta e tuning de performance de cameras RTSP com GStreamer.

## Requisitos

- Java 22
- Maven
- GStreamer instalado no sistema

## Como executar

Configure a camera de teste em um arquivo externo:

```text
C:\caminho\para\tunning.properties
```

Esse arquivo nao deve ficar dentro do projeto.

Exemplo:

```properties
tuning.repetitions=3

llm.enabled=false
llm.endpoint=http://localhost:11434/api/generate
llm.model=llama3.1:8b
llm.autoPullModel=false
llm.timeoutSeconds=300
llm.maxTokens=500

camera.1.code=CAM01_INTELBRAS
camera.1.rtspUrl=rtsp://usuario:senha@192.168.15.21:554/cam/realmonitor?channel=1&subtype=0

camera.2.code=CAM02_INTELBRAS
camera.2.rtspUrl=rtsp://usuario:senha@192.168.15.22:554/cam/realmonitor?channel=1&subtype=0
```

Quando `llm.enabled=true`, a aplicacao valida no inicio se o modelo existe no
Ollama local. Para baixar automaticamente o modelo ausente, habilite:

```properties
llm.autoPullModel=true
```

Tambem e possivel usar o formato curto:

```properties
camera.CAM01_INTELBRAS=rtsp://usuario:senha@192.168.15.21:554/cam/realmonitor?channel=1&subtype=0
camera.CAM02_STONKAM=rtsp://usuario:senha@192.168.15.88:554/cam1/mainstream
```

Tambem e possivel sobrescrever por variavel de ambiente:

```powershell
$env:S4E_TEST_CAMERA_CODE = "CAM01_INTELBRAS"
$env:S4E_TEST_RTSP_URL = "rtsp://usuario:senha@host:554/cam/realmonitor?channel=1&subtype=0"
$env:S4E_TUNING_REPETITIONS = "3"
```

Ou por propriedades Java:

```powershell
mvn exec:java -Dexec.mainClass=com.s4etech.performance.v2.AppPerformanceTuningV2 -Ds4e.test.cameraCode=CAM01_INTELBRAS -Ds4e.test.rtspUrl="rtsp://usuario:senha@host:554/cam/realmonitor?channel=1&subtype=0"
```

O numero de repeticoes por configuracao tambem pode ser informado com:

```powershell
-Ds4e.tuning.repetitions=3
```

Informe o caminho do arquivo:

```powershell
$env:S4E_CAMERA_CONFIG_FILE = "C:\caminho\para\tunning.properties"
```

ou:

```powershell
-Ds4e.camera.configFile=C:\caminho\para\tunning.properties
```

## Procedimento de teste de redes

O objetivo deste procedimento e comparar a estabilidade de transmissao RTSP em
diferentes tipos de rede usando sempre o mesmo criterio de medicao. O tecnico
deve executar o programa uma vez para cada rede avaliada:

- Rede local cabeada.
- Rede Wi-Fi.
- Rede mesh.
- Rede via radio.
- Rede via satelite.

Em cada rede, o teste deve usar duas fontes de video:

- Uma camera IP com URL RTSP direta.
- Uma camera conectada a DVR/NVR, usando a URL RTSP do canal correspondente.

Antes de iniciar cada execucao, conecte o computador somente na rede que sera
testada e confirme que as duas cameras estao acessiveis por essa rede. As URLs
RTSP devem estar configuradas no arquivo `tunning.properties`.

Exemplo:

```properties
tuning.repetitions=3

camera.1.code=CAM01_IP
camera.1.rtspUrl=rtsp://usuario:senha@ip-da-camera:554/stream

camera.2.code=CAM02_DVR
camera.2.rtspUrl=rtsp://usuario:senha@ip-do-dvr:554/caminho-do-canal
```

Para cada tipo de rede, o tecnico deve:

1. Ajustar o `tunning.properties` com a camera IP e a camera DVR/NVR.
2. Executar o programa.
3. Aguardar o pre-check do GStreamer, da LLM local quando habilitada, e das cameras.
4. Permitir que o programa conclua todos os testes de tuning.
5. Separar os relatorios gerados em `logs/<camera>/` identificando qual rede foi testada.
6. Registrar qualquer indisponibilidade, timeout, erro de pipeline ou watchdog.

O teste so deve ser considerado valido quando as duas cameras responderem no
pre-check e o tuning terminar completamente. Se alguma camera falhar, corrija
credenciais, URL RTSP, rota, sinal, cabeamento ou disponibilidade da rede antes
de repetir a execucao.

Na comparacao entre redes, priorize:

- FPS medio e FPS minimo.
- Picos acima de 80 ms, 120 ms e 200 ms.
- Erros de pipeline.
- Ocorrencias de watchdog.
- Score medio e score minimo.
- Status `RECOMENDADA`, `RESSALVA` ou `REPROVADA`.
- Configuracao recomendada pelo programa.

A melhor rede e a que apresentar maior estabilidade nas duas cameras, menor
quantidade de picos de latencia, ausencia de erros, ausencia de watchdog e melhor
score consolidado. A decisao deve priorizar estabilidade da transmissao, nao
apenas o maior FPS medio.

## Fluxo atual

1. Executa pre-check do GStreamer e da LLM local, quando habilitada.
2. Descobre codec e disponibilidade TCP/UDP.
3. Gera candidatos de tuning combinando protocolo, decoder, latency e buffer.
4. Executa benchmark tecnico com `fakesink`, com repeticoes por configuracao.
5. Consolida score medio, score minimo, pior intervalo, picos, erros e watchdog.
6. Escolhe recomendacao por regra deterministica priorizando estabilidade.
7. Classifica cada resultado como `RECOMENDADA`, `RESSALVA` ou `REPROVADA`.
8. Gera relatorio CSV tecnico em `logs/<camera>/`.
9. Opcionalmente chama LLM local via Ollama para explicar a decisao ja tomada pelo algoritmo.
10. Gera relatorio TXT por camera em `logs/<camera>/` com a recomendacao do programa e a analise da LLM, quando disponivel.

## Criterios de status

- `RECOMENDADA`: score medio >= 90, score minimo >= 85, sem picos acima de 120 ms, sem picos acima de 200 ms, sem erro e sem watchdog.
- `RESSALVA`: tem picos acima de 120 ms, mas nao tem picos acima de 200 ms, erro, watchdog ou score abaixo do minimo.
- `REPROVADA`: tem picos acima de 200 ms, erro, watchdog, score medio < 90 ou score minimo < 85.

O preview visual existe no codigo, mas esta desabilitado no fluxo principal.

## Escopo do stream

A aplicacao testa somente a URL RTSP informada. Ela nao gera variações de URL e nao testa
automaticamente `subtype=1`, `subtype=2` ou streams secundarios. Para cameras Intelbras/Dahua,
o tuning deve ser executado sobre o stream principal/mainstream, normalmente `subtype=0`.
