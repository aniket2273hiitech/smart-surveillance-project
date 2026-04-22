package com.surveillance.facedetection.service;


import com.surveillance.facedetection.entity.User;
import com.surveillance.facedetection.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CameraStreamService {

    @Autowired
    private FaceDetectionService faceDetectionService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.camera.device-index:0}")
    private int cameraDeviceIndex;

    @Value("${app.camera.location:Main Gate}")
    private String cameraLocation;

    @Value("${app.camera.detection-every-n-frames:8}")
    private int detectionEveryNFrames;

    // Last detection result for status endpoint
    private volatile String lastDetectionResult = "WAITING";
    private volatile String lastCriminalName    = "";
    private volatile double lastConfidence      = 0.0;

    public String getLastDetectionResult() { return lastDetectionResult; }
    public String getLastCriminalName()    { return lastCriminalName; }
    public double getLastConfidence()      { return lastConfidence; }

    private VideoCapture videoCapture;
    private ScheduledExecutorService executor;
    private ScheduledExecutorService detectionExecutor;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean detectionInProgress = new AtomicBoolean(false);

    // Latest frame stored as JPEG bytes for MJPEG streaming
    private volatile byte[] latestFrame = null;

    public synchronized void startCamera(String operatorUsername) {
        if (running.get()) return;
        try {
            videoCapture = new VideoCapture(cameraDeviceIndex);
            if (!videoCapture.isOpened()) {
                throw new RuntimeException(
                        "Cannot open camera device " + cameraDeviceIndex);
            }

            User operator = userRepository.findByUsername(operatorUsername)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            executor = Executors.newSingleThreadScheduledExecutor();
            detectionExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduledTask = executor.scheduleWithFixedDelay(() -> {
                try {
                    processNextFrame(operator);
                } catch (Exception e) {
                    System.err.println("⚠️ Frame error: " + e.getMessage());
                }
            }, 0, 120, TimeUnit.MILLISECONDS);

            running.set(true);
            System.out.println("✅ Camera started by: " + operatorUsername);

        } catch (UnsatisfiedLinkError e) {
            System.err.println("❌ OpenCV not loaded.");
        }
    }

    public synchronized void stopCamera() {
        if (!running.get()) return;
        if (scheduledTask != null) scheduledTask.cancel(true);
        if (executor != null) executor.shutdownNow();
        if (detectionExecutor != null) detectionExecutor.shutdownNow();
        if (videoCapture != null && videoCapture.isOpened()) videoCapture.release();
        running.set(false);
        detectionInProgress.set(false);
        latestFrame = null;
        System.out.println("🛑 Camera stopped.");
    }

    public boolean isRunning() { return running.get(); }

    /** Returns latest JPEG frame bytes for MJPEG stream */
    public byte[] getLatestFrame() { return latestFrame; }

    private int frameCount = 0;

    private void processNextFrame(User operator) throws Exception {
        Mat frame = new Mat();
        if (!videoCapture.read(frame) || frame.empty()) return;

        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", frame, buffer);
        latestFrame = buffer.toArray();

        frameCount++;
        if (frameCount % Math.max(1, detectionEveryNFrames) != 0) return;
        if (detectionInProgress.get()) return;

        final byte[] frameForDetection = latestFrame;
        detectionInProgress.set(true);
        detectionExecutor.submit(() -> {
            try {
                FaceDetectionService.LiveFrameResult result =
                        faceDetectionService.detectAndAnnotateLiveFrame(
                                frameForDetection, cameraLocation, operator);

                if (result.getAnnotatedFrame() != null && result.getAnnotatedFrame().length > 0) {
                    latestFrame = result.getAnnotatedFrame();
                }

                lastDetectionResult = result.getStatus();
                lastCriminalName = result.getCriminalName();
                lastConfidence = result.getConfidence();
            } catch (Exception e) {
                System.err.println("⚠️ Detection error: " + e.getMessage());
            } finally {
                detectionInProgress.set(false);
            }
        });
    }

    @PreDestroy
    public void onShutdown() { stopCamera(); }
}