package com.s4etech.performance.v2.precheck;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;

public class GStreamerPrecheckService {

    private static final String[] REQUIRED_ELEMENTS = {
            "rtspsrc",
            "rtpjitterbuffer",
            "queue",
            "watchdog",
            "identity",
            "videoscale",
            "videoconvert",
            "fakesink",
            "videotestsrc",
            "h264parse",
            "rtph264depay",
            "avdec_h264"
    };

    private static final String[] OPTIONAL_ELEMENTS = {
            "h265parse",
            "rtph265depay",
            "avdec_h265",
            "d3d11h264dec",
            "d3d11h265dec"
    };

    public boolean run() {
        System.out.println();
        System.out.println("====================================================");
        System.out.println("PRE-CHECK DO GSTREAMER");
        System.out.println("====================================================");

        List<String> missingElements = findMissingElements();
        List<String> missingOptionalElements = findMissingOptionalElements();

        if (!missingElements.isEmpty()) {
            System.out.println("GStreamer encontrado, mas faltam elementos obrigatorios:");

            for (String elementName : missingElements) {
                System.out.println("- " + elementName);
            }

            System.out.println("Verifique a instalacao do GStreamer e os plugins base/good/bad/libav.");
            return false;
        }

        if (!missingOptionalElements.isEmpty()) {
            System.out.println("Aviso: alguns elementos opcionais nao foram encontrados:");

            for (String elementName : missingOptionalElements) {
                System.out.println("- " + elementName);
            }

            System.out.println("Cameras H265 ou testes com aceleracao por hardware podem falhar se esses elementos forem necessarios.");
        }

        if (!runSmokePipeline()) {
            return false;
        }

        System.out.println("GStreamer OK.");
        return true;
    }

    private List<String> findMissingElements() {
        List<String> missingElements = new ArrayList<>();

        for (String elementName : REQUIRED_ELEMENTS) {
            Element element = null;

            try {
                element = ElementFactory.make(elementName, "precheck-" + elementName);

                if (element == null) {
                    missingElements.add(elementName);
                }
            } catch (RuntimeException e) {
                missingElements.add(elementName);
            } finally {
                if (element != null) {
                    element.dispose();
                }
            }
        }

        return missingElements;
    }

    private List<String> findMissingOptionalElements() {
        List<String> missingElements = new ArrayList<>();

        for (String elementName : OPTIONAL_ELEMENTS) {
            if (!isElementAvailable(elementName)) {
                missingElements.add(elementName);
            }
        }

        return missingElements;
    }

    private boolean isElementAvailable(String elementName) {
        Element element = null;

        try {
            element = ElementFactory.make(elementName, "precheck-" + elementName);
            return element != null;
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (element != null) {
                element.dispose();
            }
        }
    }

    private boolean runSmokePipeline() {
        Pipeline pipeline = null;

        try {
            Element element = Gst.parseLaunch("videotestsrc num-buffers=1 ! fakesink sync=false async=false");

            if (element instanceof Pipeline) {
                pipeline = (Pipeline) element;
            } else {
                pipeline = new Pipeline("pipeline-gstreamer-precheck");
                pipeline.add(element);
            }

            pipeline.setState(State.PLAYING);
            pipeline.getState(5_000_000_000L);
            pipeline.setState(State.NULL);
            pipeline.dispose();
            pipeline = null;

            return true;
        } catch (RuntimeException e) {
            System.out.println("GStreamer falhou ao executar pipeline minima de teste.");
            System.out.println("Erro: " + e.getMessage());
            return false;
        } finally {
            if (pipeline != null) {
                pipeline.setState(State.NULL);
                pipeline.dispose();
            }
        }
    }
}
