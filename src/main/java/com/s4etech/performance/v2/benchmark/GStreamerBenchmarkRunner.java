package com.s4etech.performance.v2.benchmark;

import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestMetrics;
import com.s4etech.performance.v2.model.RtspProtocol;
import com.s4etech.performance.v2.model.VideoCodec;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;

public class GStreamerBenchmarkRunner {

    private static final String DEFAULT_SINK_NAME = "sink";
    private static final long WARMUP_TIME_MS = 2000;
    private static final boolean PRINT_BENCHMARK_PIPELINE_DETAILS = false;

    public PipelineTestMetrics run(PipelineTestConfig config) {
        PipelineTestMetrics metrics = new PipelineTestMetrics();
        metrics.setConfig(config);

        Pipeline pipeline = null;

        try {
            String pipelineText = buildBenchmarkPipeline(config, DEFAULT_SINK_NAME);
            String gstLaunchCommand = buildGstLaunchCommand(config);

            metrics.setPipelineText(pipelineText);
            metrics.setGstLaunchCommand(gstLaunchCommand);

            printBenchmarkStart(config, pipelineText, gstLaunchCommand);

            Element element = Gst.parseLaunch(pipelineText);

            if (element instanceof Pipeline) {
                pipeline = (Pipeline) element;
            } else {
                pipeline = new Pipeline("pipeline-benchmark-" + cleanName(getCameraCode(config)));
                pipeline.add(element);
            }

            configureBus(pipeline, metrics);
            configureFrameMeasurement(pipeline, DEFAULT_SINK_NAME, metrics);

            metrics.markStarted();

            pipeline.setState(State.PAUSED);
            pipeline.play();

            sleep(getTestDurationMs(config));

            pipeline.setState(State.NULL);
            pipeline.dispose();
            pipeline = null;

            metrics.markFinishedIfRunning();
            metrics.calculateScore();

            printMetrics(metrics);

            return metrics;

        } catch (Exception e) {
            metrics.recordError(e.getMessage(), false);
            metrics.calculateScore();

            System.err.println("Erro ao executar benchmark GStreamer.");
            e.printStackTrace();

            return metrics;

        } finally {
            if (pipeline != null) {
                pipeline.setState(State.NULL);
                pipeline.dispose();
            }
        }
    }

    public String buildGstLaunchCommand(PipelineTestConfig config) {
        return "gst-launch-1.0 -v " + buildBenchmarkPipeline(config, DEFAULT_SINK_NAME);
    }

    public String buildBenchmarkPipeline(PipelineTestConfig config, String sinkName) {
        CameraConfig camera = config.getCameraConfig();
        VideoCodec codec = getCodec(config);
        RtspProtocol protocol = getProtocol(config);
        String decoder = getDecoder(config, codec);
        String queue = buildQueue(getBufferMs(config));
        String jitterBuffer = "";

        if (protocol == RtspProtocol.UDP) {
            jitterBuffer = "rtpjitterbuffer latency=" + getLatencyMs(config) + " ! ";
        }

        return "rtspsrc location=\"" + camera.getRtspUrl() + "\""
                + " protocols=" + protocol.getGstName()
                + " latency=" + getLatencyMs(config)
                + " name=source "
                + "source. ! "
                + jitterBuffer
                + queue
                + codec.getDepay() + " ! "
                + codec.getParser() + " ! "
                + queue
                + decoder + " ! "
                + "identity silent=true ! "
                + "watchdog timeout=" + getWatchdogTimeoutMs(config) + " ! "
                + queue
                + "videoscale ! "
                + "video/x-raw,width=" + getOutputWidth(config)
                + ",height=" + getOutputHeight(config)
                + " ! "
                + "videoconvert ! "
                + "fakesink name=" + sinkName + " sync=false async=false";
    }

    private void configureBus(Pipeline pipeline, PipelineTestMetrics metrics) {
        Bus bus = pipeline.getBus();

        bus.connect((Bus.ERROR) (GstObject source, int code, String message) -> {
            metrics.recordError(message, isWatchdogMessage(source, message));
        });

        bus.connect((Bus.WARNING) (GstObject source, int code, String message) -> {
            metrics.recordWarning(isWatchdogMessage(source, message));
        });

        bus.connect((Bus.EOS) source -> metrics.markEos());
    }

    private void configureFrameMeasurement(
            Pipeline pipeline,
            String sinkName,
            PipelineTestMetrics metrics) {

        Element sink = pipeline.getElementByName(sinkName);

        if (sink == null) {
            throw new IllegalStateException("Elemento fakesink nao encontrado: " + sinkName);
        }

        Pad sinkPad = sink.getStaticPad("sink");

        if (sinkPad == null) {
            throw new IllegalStateException("Sink pad nao encontrado: " + sinkName);
        }

        sinkPad.addProbe(PadProbeType.BUFFER, (pad, info) -> {
            Buffer buffer = info.getBuffer();

            if (buffer != null) {
                metrics.recordFrame(WARMUP_TIME_MS);
            }

            return PadProbeReturn.OK;
        });
    }

    private void printBenchmarkStart(
            PipelineTestConfig config,
            String pipelineText,
            String gstLaunchCommand) {

        System.out.println();
        System.out.println("Benchmark tecnico da camera " + getCameraCode(config) + ":");
        System.out.println(config.getShortDescription());

        if (PRINT_BENCHMARK_PIPELINE_DETAILS) {
            System.out.println();
            System.out.println("Comando GStreamer para benchmark tecnico:");
            System.out.println(gstLaunchCommand);
            System.out.println();
            System.out.println("Pipeline benchmark:");
            System.out.println(pipelineText);
        }
    }

    private void printMetrics(PipelineTestMetrics metrics) {
        System.out.println();
        System.out.println("Resultado benchmark:");
        System.out.println(metrics);
    }

    private String buildQueue(int bufferMs) {
        long bufferNs = bufferMs * 1_000_000L;

        return "queue leaky=downstream max-size-time="
                + bufferNs
                + " max-size-bytes=0 max-size-buffers=0 ! ";
    }

    private VideoCodec getCodec(PipelineTestConfig config) {
        if (config.getVideoCodec() != null) {
            return config.getVideoCodec();
        }

        return VideoCodec.H264;
    }

    private RtspProtocol getProtocol(PipelineTestConfig config) {
        if (config.getProtocol() != null) {
            return config.getProtocol();
        }

        return RtspProtocol.TCP;
    }

    private String getDecoder(PipelineTestConfig config, VideoCodec codec) {
        if (config.getDecoderElement() != null && !config.getDecoderElement().isBlank()) {
            return config.getDecoderElement();
        }

        if (config.isHardwareAcceleration()) {
            return "d3d11" + codec.getCode() + "dec";
        }

        return codec.getSoftwareDecoder();
    }

    private int getLatencyMs(PipelineTestConfig config) {
        if (config.getLatencyMs() > 0) {
            return config.getLatencyMs();
        }

        return 100;
    }

    private int getBufferMs(PipelineTestConfig config) {
        if (config.getBufferMs() > 0) {
            return config.getBufferMs();
        }

        return 200;
    }

    private int getWatchdogTimeoutMs(PipelineTestConfig config) {
        if (config.getWatchdogTimeoutMs() > 0) {
            return config.getWatchdogTimeoutMs();
        }

        return 4000;
    }

    private int getOutputWidth(PipelineTestConfig config) {
        if (config.getOutputWidth() > 0) {
            return config.getOutputWidth();
        }

        return 640;
    }

    private int getOutputHeight(PipelineTestConfig config) {
        if (config.getOutputHeight() > 0) {
            return config.getOutputHeight();
        }

        return 360;
    }

    private long getTestDurationMs(PipelineTestConfig config) {
        if (config.getTestDurationSeconds() > 0) {
            return config.getTestDurationSeconds() * 1000L;
        }

        return 8000;
    }

    private boolean isWatchdogMessage(GstObject source, String message) {
        String sourceText = source == null ? "" : source.toString().toLowerCase(java.util.Locale.ROOT);
        String messageText = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);

        return sourceText.contains("watchdog")
                || messageText.contains("watchdog")
                || messageText.contains("timeout");
    }

    private String getCameraCode(PipelineTestConfig config) {
        if (config.getCameraConfig() == null) {
            return "camera";
        }

        return config.getCameraConfig().getCode();
    }

    private String cleanName(String value) {
        if (value == null) {
            return "camera";
        }

        return value.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
