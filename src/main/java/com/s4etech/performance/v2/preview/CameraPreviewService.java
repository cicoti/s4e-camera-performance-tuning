package com.s4etech.performance.v2.preview;

import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.RtspProtocol;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.model.VideoCodec;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.State;

public class CameraPreviewService {

    private static final long DEFAULT_PREVIEW_TIME_MS = 5000;

    public void showPreview(StreamDiscoveryResult discoveryResult) {
        showPreview(discoveryResult, DEFAULT_PREVIEW_TIME_MS);
    }

    public void showPreview(StreamDiscoveryResult discoveryResult, long previewTimeMs) {
        Pipeline pipeline = null;

        try {
            CameraConfig camera = discoveryResult.getCamera();
            RtspProtocol protocol = discoveryResult.getPreferredPreviewProtocol();
            VideoCodec codec = discoveryResult.getCodecOrFallback();

            String pipelineText = buildPreviewPipeline(camera, protocol, codec);

            System.out.println();
            System.out.println("Comando GStreamer para teste visual da câmera " + camera.getCode() + ":");
            System.out.println(buildGstLaunchCommand(camera, protocol, codec));
            System.out.println();

            System.out.println("Abrindo preview da câmera " + camera.getCode() + " por " + (previewTimeMs / 1000) + " segundos.");
            System.out.println("Pipeline preview:");
            System.out.println(pipelineText);

            Element element = Gst.parseLaunch(pipelineText);

            if (element instanceof Pipeline) {
                pipeline = (Pipeline) element;
            } else {
                pipeline = new Pipeline("pipeline-preview-" + cleanName(camera.getCode()));
                pipeline.add(element);
            }

            pipeline.setState(State.PAUSED);
            pipeline.play();

            sleep(previewTimeMs);

        } catch (Exception e) {
            System.err.println("Erro ao abrir preview da câmera.");
            e.printStackTrace();

        } finally {
            if (pipeline != null) {
                pipeline.setState(State.NULL);
                pipeline.dispose();
            }
        }
    }

    public String buildGstLaunchCommand(CameraConfig camera, RtspProtocol protocol, VideoCodec codec) {
        String jitterBuffer = "";

        if (protocol == RtspProtocol.UDP) {
            jitterBuffer = "rtpjitterbuffer latency=100 ! ";
        }

        return "gst-launch-1.0 -v rtspsrc location=\"" + camera.getRtspUrl() + "\" protocols=" + protocol.getGstName() + " latency=100 name=source source. ! " + jitterBuffer + "queue leaky=downstream max-size-time=200000000 max-size-bytes=0 max-size-buffers=0 ! " + codec.getDepay() + " ! " + codec.getParser() + " ! queue leaky=downstream max-size-time=200000000 max-size-bytes=0 max-size-buffers=0 ! " + codec.getSoftwareDecoder() + " ! videoscale ! video/x-raw,width=640,height=360 ! videoconvert ! autovideosink sync=false";
    }

    private String buildPreviewPipeline(CameraConfig camera, RtspProtocol protocol, VideoCodec codec) {
        String jitterBuffer = "";

        if (protocol == RtspProtocol.UDP) {
            jitterBuffer = "rtpjitterbuffer latency=100 ! ";
        }

        return "rtspsrc location=\"" + camera.getRtspUrl() + "\""
                + " protocols=" + protocol.getGstName()
                + " latency=100"
                + " name=source "
                + "source. ! "
                + jitterBuffer
                + "queue leaky=downstream max-size-time=200000000 max-size-bytes=0 max-size-buffers=0 ! "
                + codec.getDepay() + " ! "
                + codec.getParser() + " ! "
                + "queue leaky=downstream max-size-time=200000000 max-size-bytes=0 max-size-buffers=0 ! "
                + codec.getSoftwareDecoder() + " ! "
                + "videoscale ! "
                + "video/x-raw,width=640,height=360 ! "
                + "videoconvert ! "
                + "autovideosink sync=false";
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