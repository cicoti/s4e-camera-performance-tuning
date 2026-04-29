package com.s4etech.performance.v2.discovery;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.ProtocolDiscoveryResult;
import com.s4etech.performance.v2.model.RtspProtocol;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.model.VideoCodec;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;

public class RtspStreamDiscoveryService {

    private static final int DISCOVERY_TIMEOUT_SECONDS = 8;

    public StreamDiscoveryResult discover(CameraConfig camera) {
        StreamDiscoveryResult result = new StreamDiscoveryResult();

        result.setCamera(camera);

        ProtocolDiscoveryResult tcpResult = discoverProtocol(camera, RtspProtocol.TCP);
        ProtocolDiscoveryResult udpResult = discoverProtocol(camera, RtspProtocol.UDP);

        result.setTcpResult(tcpResult);
        result.setUdpResult(udpResult);

        if (tcpResult.getCodec() != null) {
            result.setCodec(tcpResult.getCodec());
        } else if (udpResult.getCodec() != null) {
            result.setCodec(udpResult.getCodec());
        } else {
            result.setCodec(VideoCodec.H264);
        }

        return result;
    }

    private ProtocolDiscoveryResult discoverProtocol(CameraConfig camera, RtspProtocol protocol) {
        ProtocolDiscoveryResult result = new ProtocolDiscoveryResult();
        result.setProtocol(protocol);

        Pipeline pipeline = null;

        CountDownLatch latch = new CountDownLatch(1);

        AtomicReference<String> capsDetected = new AtomicReference<>();
        AtomicReference<VideoCodec> codecDetected = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        AtomicReference<Boolean> streamResponded = new AtomicReference<>(false);

        try {
            pipeline = new Pipeline("pipeline-discovery-" + protocol.getGstName());

            Element source = ElementFactory.make("rtspsrc", "source-discovery-" + protocol.getGstName());
            Element sink = ElementFactory.make("fakesink", "sink-discovery-" + protocol.getGstName());

            if (source == null) {
                result.setAvailable(false);
                result.setMessage("Elemento rtspsrc não encontrado.");
                return result;
            }

            if (sink == null) {
                result.setAvailable(false);
                result.setMessage("Elemento fakesink não encontrado.");
                return result;
            }

            source.set("location", camera.getRtspUrl());
            source.set("protocols", protocol.getGstValue());
            source.set("latency", 200);

            sink.set("sync", false);
            sink.set("async", false);

            pipeline.addMany(source, sink);

            source.connect((Element.PAD_ADDED) (element, pad) -> {
                try {
                    Caps caps = pad.getCurrentCaps();

                    if (caps == null) {
                        caps = pad.queryCaps(null);
                    }

                    String capsText = caps == null ? "" : caps.toString();

                    System.out.println("Caps detectado via " + protocol.getGstName() + ": " + capsText);

                    if (capsText.contains("media=(string)video")) {
                        streamResponded.set(true);
                        capsDetected.set(capsText);

                        VideoCodec codec = VideoCodec.fromCaps(capsText);

                        if (codec != null) {
                            codecDetected.set(codec);
                        }

                        Pad sinkPad = sink.getStaticPad("sink");

                        if (sinkPad != null && !sinkPad.isLinked()) {
                            pad.link(sinkPad);
                        }

                        latch.countDown();
                    }

                } catch (Exception e) {
                    errorMessage.set(e.getMessage());
                    latch.countDown();
                }
            });

            pipeline.getBus().connect((Bus.ERROR) (GstObject sourceError, int code, String message) -> {
                errorMessage.set(message);
                latch.countDown();
            });

            pipeline.setState(State.PLAYING);

            boolean answered = latch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!answered) {
                result.setAvailable(false);
                result.setStreamResponded(false);
                result.setCaps(capsDetected.get());
                result.setMessage("Timeout ao descobrir stream via " + protocol.getGstName() + ".");
                return result;
            }

            if (errorMessage.get() != null && !streamResponded.get()) {
                result.setAvailable(false);
                result.setStreamResponded(false);
                result.setCaps(capsDetected.get());
                result.setMessage(errorMessage.get());
                return result;
            }

            if (streamResponded.get()) {
                result.setAvailable(true);
                result.setStreamResponded(true);
                result.setCaps(capsDetected.get());
                result.setCodec(codecDetected.get());

                if (codecDetected.get() != null) {
                    result.setMessage("Codec detectado via " + protocol.getGstName() + ": " + codecDetected.get().getCode());
                } else {
                    result.setMessage("Stream de vídeo respondeu via " + protocol.getGstName() + ", mas o codec não foi identificado automaticamente.");
                }

                return result;
            }

            result.setAvailable(false);
            result.setStreamResponded(false);
            result.setCaps(capsDetected.get());
            result.setMessage("Nenhum pad de vídeo respondeu via " + protocol.getGstName() + ".");

            return result;

        } catch (Exception e) {
            result.setAvailable(false);
            result.setStreamResponded(false);
            result.setCaps(capsDetected.get());
            result.setMessage(e.getMessage());
            return result;

        } finally {
            if (pipeline != null) {
                pipeline.setState(State.NULL);
                pipeline.dispose();
            }
        }
    }
}