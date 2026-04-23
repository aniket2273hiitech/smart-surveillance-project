package com.surveillance.facedetection.service;

import com.surveillance.facedetection.entity.Criminal;
import com.surveillance.facedetection.entity.DetectionLog;
import com.surveillance.facedetection.entity.User;
import com.surveillance.facedetection.repository.DetectionLogRepository;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.util.Comparator;

@Service
public class FaceDetectionService {
    public static class LiveFrameResult {
        private final byte[] annotatedFrame;
        private final String status;
        private final String criminalName;
        private final double confidence;

        public LiveFrameResult(byte[] annotatedFrame, String status, String criminalName, double confidence) {
            this.annotatedFrame = annotatedFrame;
            this.status = status;
            this.criminalName = criminalName;
            this.confidence = confidence;
        }

        public byte[] getAnnotatedFrame() { return annotatedFrame; }
        public String getStatus() { return status; }
        public String getCriminalName() { return criminalName; }
        public double getConfidence() { return confidence; }
    }

    @Autowired
    private CriminalService criminalService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private DetectionLogRepository detectionLogRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.detection.match-threshold:0.56}")
    private double matchThreshold;
    @Value("${app.detection.min-score-gap:0.08}")
    private double minScoreGap;
    @Value("${app.detection.live-match-threshold:0.50}")
    private double liveMatchThreshold;
    @Value("${app.detection.live-min-score-gap:0.03}")
    private double liveMinScoreGap;
    @Value("${app.detection.scan-match-threshold:0.62}")
    private double scanMatchThreshold;
    @Value("${app.detection.scan-min-score-gap:0.10}")
    private double scanMinScoreGap;

    @Value("${app.detection.use-visual-score:false}")
    private boolean useVisualScore;
    @Value("${app.detection.live-topk:2}")
    private int liveTopK;
    @Value("${app.detection.live-min-face-size:90}")
    private int liveMinFaceSize;
    @Value("${app.detection.live-min-blur-variance:60.0}")
    private double liveMinBlurVariance;
    @Value("${app.detection.use-lbph:true}")
    private boolean useLbph;
    @Value("${app.detection.lbph-threshold:70.0}")
    private double lbphThreshold;
    @Value("${app.detection.lbph-strict-confidence:45.0}")
    private double lbphStrictConfidence;
    @Value("${app.detection.lbph-scan-confidence:65.0}")
    private double lbphScanConfidence;

    private CascadeClassifier faceDetector;
    private boolean opencvAvailable = false;
    private final Map<Long, List<double[]>> embeddingSetCache = new ConcurrentHashMap<>();
    private final Map<Long, String> embeddingSourceSignatureCache = new ConcurrentHashMap<>();
    private volatile Object lbphRecognizer;
    private final Map<Integer, Criminal> lbphLabelMap = new ConcurrentHashMap<>();
    private volatile String lbphTrainingSignature = "";
    private volatile String pendingLiveName = "";
    private volatile int pendingLiveCount = 0;
    @Value("${app.detection.live-confirmation-frames:1}")
    private int liveConfirmationFrames;
    private volatile String lastConfirmedLiveName = "";
    private volatile long lastConfirmedLiveAt = 0L;
    private static final long LIVE_ALERT_COOLDOWN_MS = 12000L;

    private static class MatchCandidate {
        private Criminal bestCriminal;
        private double bestScore;
        private double secondBestScore;
        private boolean fromLbph;
        private double lbphConfidence;
    }

    @PostConstruct
    public void init() {
        try {
            nu.pattern.OpenCV.loadLocally();
            System.out.println("✅ OpenCV loaded via openpnp");
        } catch (Exception e) {
            System.out.println("ℹ️ OpenCV: " + e.getMessage());
        }

        try {
            InputStream cascadeStream = getClass().getResourceAsStream(
                    "/haarcascade_frontalface_default.xml");

            if (cascadeStream == null) {
                System.err.println("⚠️ haarcascade_frontalface_default.xml not found.");
                return;
            }

            Path tempFile = Files.createTempFile("haarcascade", ".xml");
            Files.copy(cascadeStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            faceDetector = new CascadeClassifier(tempFile.toString());

            if (faceDetector.empty()) {
                System.err.println("❌ Failed to load Haar Cascade.");
                return;
            }

            opencvAvailable = true;
            System.out.println("✅ Haar Cascade loaded. Ready for detection.");
            warmEmbeddingCache();
            if (useLbph) {
                rebuildLbphModel();
            }

        } catch (IOException e) {
            System.err.println("❌ Init error: " + e.getMessage());
        }
    }

    public DetectionLog detectAndMatch(MultipartFile imageFile,
                                       String location,
                                       User loggedInUser) throws IOException {
        return detectAndMatch(imageFile, location, loggedInUser, true);
    }

    public DetectionLog detectAndMatchLive(MultipartFile imageFile,
                                           String location,
                                           User loggedInUser) throws IOException {
        return detectAndMatch(imageFile, location, loggedInUser, false);
    }

    private DetectionLog detectAndMatch(MultipartFile imageFile,
                                       String location,
                                       User loggedInUser,
                                       boolean persistNoMatch) throws IOException {

        System.out.println("🔍 Detection started. OpenCV: " + opencvAvailable);

        DetectionLog log = new DetectionLog();
        log.setLocation(location);
        log.setDetectedBy(loggedInUser);

        if (!opencvAvailable) {
            log.setMatchStatus(DetectionLog.MatchStatus.NO_MATCH);
            log.setConfidenceScore(0.0);
            if (persistNoMatch) {
                detectionLogRepository.save(log);
            }
            return log;
        }

        MatOfByte imageBytes = new MatOfByte(imageFile.getBytes());
        Mat inputImage = Imgcodecs.imdecode(imageBytes, Imgcodecs.IMREAD_COLOR);

        if (inputImage.empty()) {
            System.err.println("❌ Cannot read scan image.");
            log.setMatchStatus(DetectionLog.MatchStatus.NO_MATCH);
            if (persistNoMatch) {
                detectionLogRepository.save(log);
            }
            return log;
        }

        // Grayscale + equalize
        Mat grayImage = new Mat();
        Imgproc.cvtColor(inputImage, grayImage, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(grayImage, grayImage);

        // Detect faces
        MatOfRect facesDetected = new MatOfRect();
        faceDetector.detectMultiScale(grayImage, facesDetected,
                1.1, 5, 0, new Size(30, 30), new Size());

        Rect[] faceArray = facesDetected.toArray();
        System.out.println("👤 Faces detected: " + faceArray.length);

        if (faceArray.length == 0) {
            log.setMatchStatus(DetectionLog.MatchStatus.NO_MATCH);
            log.setConfidenceScore(0.0);
            if (persistNoMatch) {
                detectionLogRepository.save(log);
            }
            return log;
        }

        // Crop + resize detected face to 100x100
        Mat detectedFace = new Mat(grayImage, faceArray[0]);
        Mat resizedDetected = new Mat();
        Imgproc.resize(detectedFace, resizedDetected, new Size(100, 100));
        double[] liveEmbedding = buildEmbedding(resizedDetected);

        // Compare with all criminals
        List<Criminal> criminals = criminalService.getAllActiveCriminals();
        System.out.println("👥 Criminals to compare: " + criminals.size());

        MatchCandidate candidate = findBestCandidate(liveEmbedding, resizedDetected, criminals);
        Criminal bestMatch = candidate.bestCriminal;
        double bestScore = candidate.bestScore;

        System.out.println("🎯 Best: " + bestScore
                + " → " + (bestMatch != null ? bestMatch.getName() : "none"));

        log.setConfidenceScore(bestScore);
        if (bestMatch != null) {
            log.setBestCandidateName(bestMatch.getName());
        }
        log.setSecondBestScore(candidate.secondBestScore);

        if (isValidMatchForScan(candidate)) {
            String savedFramePath = saveFrameToDisk(imageFile);
            log.setCapturedFramePath(savedFramePath);
            log.setMatchStatus(DetectionLog.MatchStatus.MATCHED);
            log.setMatchedCriminal(bestMatch);
            log.setScanRejectionNote(null);
            detectionLogRepository.save(log);
            alertService.createAlert(log, bestMatch);
            System.out.println("✅ MATCHED: " + bestMatch.getName());
        } else {
            log.setMatchStatus(DetectionLog.MatchStatus.NO_MATCH);
            log.setScanRejectionNote(buildScanRejectionNote(candidate));
            if (persistNoMatch) {
                detectionLogRepository.save(log);
            }
        }

        return log;
    }

    /**
     * Compares detected face with criminal photo using:
     * 1. Histogram correlation
     * 2. Template matching
     * Returns combined score 0.0 to 1.0
     */
    private double matchFace(Mat detectedFace, String criminalImagePath) {
        Mat criminalImage = Imgcodecs.imread(criminalImagePath);
        if (criminalImage.empty()) {
            System.err.println("❌ Cannot read: " + criminalImagePath);
            return 0.0;
        }

        // Convert to grayscale
        Mat criminalGray = new Mat();
        if (criminalImage.channels() > 1) {
            Imgproc.cvtColor(criminalImage, criminalGray, Imgproc.COLOR_BGR2GRAY);
        } else {
            criminalGray = criminalImage;
        }

        // Detect face in criminal photo too
        MatOfRect criminalFaces = new MatOfRect();
        faceDetector.detectMultiScale(criminalGray, criminalFaces,
                1.1, 5, 0, new Size(30, 30), new Size());

        Mat criminalFaceMat;
        if (criminalFaces.toArray().length > 0) {
            // Use detected face region
            criminalFaceMat = new Mat(criminalGray, criminalFaces.toArray()[0]);
        } else {
            // Use whole image if no face detected
            criminalFaceMat = criminalGray;
        }

        // Resize to same size
        Mat resizedCriminal = new Mat();
        Imgproc.resize(criminalFaceMat, resizedCriminal, new Size(100, 100));

        // ── Score 1: Histogram correlation ───────────────────────────────────
        Mat histDetected = new Mat();
        Mat histCriminal = new Mat();
        MatOfFloat ranges  = new MatOfFloat(0f, 256f);
        MatOfInt histSize  = new MatOfInt(256);
        MatOfInt channels  = new MatOfInt(0);

        Imgproc.calcHist(List.of(detectedFace),   channels,
                new Mat(), histDetected, histSize, ranges);
        Imgproc.calcHist(List.of(resizedCriminal), channels,
                new Mat(), histCriminal, histSize, ranges);

        Core.normalize(histDetected, histDetected, 0, 1, Core.NORM_MINMAX);
        Core.normalize(histCriminal, histCriminal, 0, 1, Core.NORM_MINMAX);

        double histScore = Imgproc.compareHist(
                histDetected, histCriminal, Imgproc.HISTCMP_CORREL);

        // ── Score 2: Template matching ────────────────────────────────────────
        Mat result = new Mat();
        Imgproc.matchTemplate(detectedFace, resizedCriminal, result,
                Imgproc.TM_CCOEFF_NORMED);
        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
        double templateScore = mmr.maxVal;

        // ── Combined score ────────────────────────────────────────────────────
        double combined = (histScore * 0.5) + (templateScore * 0.5);
        return combined;
    }

    private double scoreAgainstCriminal(double[] liveEmbedding, Mat detectedFace, Criminal criminal) {
        List<double[]> criminalEmbeddings = getOrBuildCriminalEmbeddings(criminal);
        // For live mode we use a more robust score than max(): average of top-K similarities.
        // This avoids wrong names caused by one "lucky" reference photo.
        double embeddingScore = averageTopKCosineSimilarity(liveEmbedding, criminalEmbeddings, liveTopK);
        if (!useVisualScore) {
            return embeddingScore;
        }
        double visualScore = bestVisualScoreAgainstCriminal(detectedFace, criminal);
        return (embeddingScore * 0.85) + (visualScore * 0.15);
    }

    private MatchCandidate findBestCandidate(double[] liveEmbedding, Mat detectedFace, List<Criminal> criminals) {
        MatchCandidate candidate = new MatchCandidate();
        candidate.bestScore = 0.0;
        candidate.secondBestScore = 0.0;
        candidate.bestCriminal = null;

        for (Criminal criminal : criminals) {
            double score = scoreAgainstCriminal(liveEmbedding, detectedFace, criminal);
            if (score > candidate.bestScore) {
                candidate.secondBestScore = candidate.bestScore;
                candidate.bestScore = score;
                candidate.bestCriminal = criminal;
            } else if (score > candidate.secondBestScore) {
                candidate.secondBestScore = score;
            }
        }
        return candidate;
    }

    private MatchCandidate findBestCandidateLbph(Mat detectedFace, List<Criminal> criminals) {
        MatchCandidate candidate = new MatchCandidate();
        candidate.bestScore = 0.0;
        candidate.secondBestScore = 0.0;
        candidate.bestCriminal = null;
        candidate.fromLbph = true;
        candidate.lbphConfidence = Double.MAX_VALUE;

        if (lbphRecognizer == null || lbphLabelMap.isEmpty() || isLbphTrainingStale(criminals)) {
            rebuildLbphModel();
        }
        if (lbphRecognizer == null || lbphLabelMap.isEmpty()) {
            return candidate;
        }

        int[] label = new int[1];
        double[] confidence = new double[1];
        if (!invokeLbphPredict(detectedFace, label, confidence)) {
            return candidate;
        }
        candidate.lbphConfidence = confidence[0];
        if (label[0] >= 0 && confidence[0] <= lbphThreshold) {
            candidate.bestCriminal = lbphLabelMap.get(label[0]);
            candidate.bestScore = Math.max(0.0, 1.0 - (confidence[0] / lbphThreshold));
            candidate.secondBestScore = 0.0;
        }
        return candidate;
    }

    private boolean isValidMatchForLive(MatchCandidate candidate) {
        if (candidate.bestCriminal == null) {
            return false;
        }
        if (candidate.fromLbph) {
            return candidate.lbphConfidence <= lbphStrictConfidence;
        }
        if (candidate.bestScore < liveMatchThreshold) {
            return false;
        }
        return (candidate.bestScore - candidate.secondBestScore) >= liveMinScoreGap;
    }

    private boolean isValidMatchForScan(MatchCandidate candidate) {
        if (candidate.bestCriminal == null) {
            return false;
        }
        if (candidate.fromLbph) {
            return candidate.lbphConfidence <= lbphScanConfidence;
        }
        if (candidate.bestScore < scanMatchThreshold) {
            return false;
        }
        return (candidate.bestScore - candidate.secondBestScore) >= scanMinScoreGap;
    }

    private String buildScanRejectionNote(MatchCandidate c) {
        if (c.bestCriminal == null) {
            return "No criminal scored above the minimum similarity. Try a clearer, front-facing photo.";
        }
        if (c.fromLbph) {
            return "Face match confidence did not pass the strict scan check.";
        }
        if (c.bestScore < scanMatchThreshold) {
            return String.format(
                    "Best similarity was %.0f%%, but scan requires at least %.0f%% to accept a match.",
                    c.bestScore * 100, scanMatchThreshold * 100);
        }
        double gap = c.bestScore - c.secondBestScore;
        return String.format(
                "Top candidate \"%s\" scored %.0f%%, but another criminal was also close (second best %.0f%%). "
                        + "Need at least %.0f percentage points gap to avoid a wrong person — gap was only %.0f. "
                        + "This is not \"below score\" for the top person; it is an ambiguous match.",
                c.bestCriminal.getName(),
                c.bestScore * 100,
                c.secondBestScore * 100,
                scanMinScoreGap * 100,
                gap * 100);
    }

    private List<double[]> getOrBuildCriminalEmbeddings(Criminal criminal) {
        String sourceSignature = buildEmbeddingSourceSignature(criminal);
        if (criminal.getId() != null) {
            List<double[]> cached = embeddingSetCache.get(criminal.getId());
            String cachedSignature = embeddingSourceSignatureCache.get(criminal.getId());
            if (cached != null && !cached.isEmpty() && sourceSignature.equals(cachedSignature)) {
                return cached;
            }
        }

        List<double[]> criminalEmbeddings = buildCriminalEmbeddings(criminal);
        if (criminalEmbeddings.isEmpty()) {
            double[] legacy = parseEmbedding(criminal.getFaceEmbedding());
            if (legacy != null) {
                criminalEmbeddings.add(legacy);
            }
        } else {
            criminal.setFaceEmbedding(serializeEmbedding(averageVectors(criminalEmbeddings)));
            criminalService.save(criminal);
        }
        if (!criminalEmbeddings.isEmpty() && criminal.getId() != null) {
            embeddingSetCache.put(criminal.getId(), criminalEmbeddings);
            embeddingSourceSignatureCache.put(criminal.getId(), sourceSignature);
        }
        return criminalEmbeddings;
    }

    private String buildEmbeddingSourceSignature(Criminal criminal) {
        StringBuilder sb = new StringBuilder();
        for (String path : criminalService.getReferenceImagePaths(criminal)) {
            sb.append(path).append('|');
        }
        return sb.toString();
    }

    private double bestVisualScoreAgainstCriminal(Mat detectedFace, Criminal criminal) {
        double best = 0.0;
        for (String relativePath : criminalService.getReferenceImagePaths(criminal)) {
            String absolutePath = resolveAbsoluteImagePath(relativePath);
            double score = matchFace(detectedFace, absolutePath);
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    private List<double[]> buildCriminalEmbeddings(Criminal criminal) {
        List<double[]> vectors = new ArrayList<>();
        for (String relativePath : criminalService.getReferenceImagePaths(criminal)) {
            String absolutePath = resolveAbsoluteImagePath(relativePath);
            Mat image = Imgcodecs.imread(absolutePath);
            if (image.empty()) {
                continue;
            }

            Mat gray = new Mat();
            if (image.channels() > 1) {
                Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = image;
            }
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            faceDetector.detectMultiScale(gray, faces, 1.1, 5, 0, new Size(30, 30), new Size());
            // Strict enrollment: only use real detected face crops as training prototypes.
            if (faces.toArray().length == 0) {
                continue;
            }
            Mat faceMat = new Mat(gray, faces.toArray()[0]);

            Mat resized = new Mat();
            Imgproc.resize(faceMat, resized, new Size(100, 100));
            vectors.add(buildEmbedding(resized));
        }
        return vectors;
    }

    private double bestCosineSimilarity(double[] probe, List<double[]> candidates) {
        if (probe == null || candidates == null || candidates.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (double[] candidate : candidates) {
            double score = cosineSimilarity(probe, candidate);
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    private double averageTopKCosineSimilarity(double[] probe, List<double[]> candidates, int k) {
        if (probe == null || candidates == null || candidates.isEmpty()) {
            return 0.0;
        }
        int kk = Math.max(1, k);
        double[] top = new double[Math.min(kk, candidates.size())];
        for (int i = 0; i < top.length; i++) top[i] = 0.0;

        for (double[] c : candidates) {
            double s = cosineSimilarity(probe, c);
            // insert into top[] descending
            for (int i = 0; i < top.length; i++) {
                if (s > top[i]) {
                    double prev = top[i];
                    top[i] = s;
                    s = prev;
                }
            }
        }
        double sum = 0.0;
        for (double v : top) sum += v;
        return sum / top.length;
    }

    private double[] buildEmbedding(Mat face100x100Gray) {
        Mat hist = new Mat();
        Imgproc.calcHist(List.of(face100x100Gray), new MatOfInt(0), new Mat(), hist,
                new MatOfInt(64), new MatOfFloat(0f, 256f));
        Core.normalize(hist, hist, 1.0, 0.0, Core.NORM_L1);

        double[] vector = new double[(int) hist.total()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = hist.get(i, 0)[0];
        }
        return vector;
    }

    private double[] averageVectors(List<double[]> vectors) {
        if (vectors.isEmpty()) {
            return null;
        }
        int size = vectors.get(0).length;
        double[] avg = new double[size];
        for (double[] v : vectors) {
            for (int i = 0; i < size; i++) {
                avg[i] += v[i];
            }
        }
        for (int i = 0; i < size; i++) {
            avg[i] /= vectors.size();
        }
        return avg;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String serializeEmbedding(double[] embedding) {
        if (embedding == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.8f", embedding[i]));
        }
        return sb.toString();
    }

    private double[] parseEmbedding(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        String[] parts = serialized.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vector[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return vector;
    }

    private String resolveAbsoluteImagePath(String relativePath) {
        String fileName = relativePath.replace("uploads/criminals/", "");
        return Paths.get(System.getProperty("user.dir"))
                .resolve(uploadDir)
                .resolve(fileName)
                .toString();
    }

    private void warmEmbeddingCache() {
        try {
            for (Criminal criminal : criminalService.getAllActiveCriminals()) {
                getOrBuildCriminalEmbeddings(criminal);
            }
            System.out.println("✅ Face embedding cache warmed: " + embeddingSetCache.size());
        } catch (Exception e) {
            System.out.println("ℹ️ Embedding warmup skipped: " + e.getMessage());
        }
    }

    private synchronized void rebuildLbphModel() {
        if (!useLbph) {
            return;
        }
        try {
            List<Criminal> criminals = criminalService.getAllActiveCriminals();
            List<Mat> trainingFaces = new ArrayList<>();
            List<Integer> labels = new ArrayList<>();
            Map<Integer, Criminal> localMap = new HashMap<>();

            for (Criminal criminal : criminals) {
                if (criminal.getId() == null) {
                    continue;
                }
                int label = criminal.getId().intValue();
                for (String path : criminalService.getReferenceImagePaths(criminal)) {
                    Mat prepared = loadPreparedFace(path);
                    if (prepared != null) {
                        trainingFaces.add(prepared);
                        labels.add(label);
                        localMap.put(label, criminal);
                    }
                }
            }

            if (trainingFaces.isEmpty()) {
                lbphRecognizer = null;
                lbphLabelMap.clear();
                return;
            }

            Mat labelMat = new Mat(labels.size(), 1, CvType.CV_32SC1);
            for (int i = 0; i < labels.size(); i++) {
                labelMat.put(i, 0, labels.get(i));
            }

            Object recognizer = createLbphRecognizer();
            if (recognizer == null) {
                useLbph = false;
                lbphRecognizer = null;
                lbphLabelMap.clear();
                System.out.println("ℹ️ LBPH unavailable in current OpenCV build. Falling back.");
                return;
            }
            Method train = recognizer.getClass().getMethod("train", List.class, Mat.class);
            train.invoke(recognizer, trainingFaces, labelMat);
            lbphRecognizer = recognizer;
            lbphLabelMap.clear();
            lbphLabelMap.putAll(localMap);
            lbphTrainingSignature = buildLbphTrainingSignature(criminals);
            System.out.println("✅ LBPH model ready with samples: " + labels.size());
        } catch (Exception ex) {
            lbphRecognizer = null;
            lbphLabelMap.clear();
            lbphTrainingSignature = "";
            System.out.println("ℹ️ LBPH build skipped: " + ex.getMessage());
        }
    }

    private boolean isLbphTrainingStale(List<Criminal> criminals) {
        String current = buildLbphTrainingSignature(criminals);
        return !current.equals(lbphTrainingSignature);
    }

    private String buildLbphTrainingSignature(List<Criminal> criminals) {
        StringBuilder sb = new StringBuilder();
        criminals.stream()
                .sorted(Comparator.comparing(Criminal::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(c -> {
            sb.append(c.getId()).append('|')
                    .append(c.getImagePath()).append('|')
                    .append(c.getAdditionalImagePaths()).append(';');
                });
        return sb.toString();
    }

    private Mat loadPreparedFace(String relativePath) {
        String absolutePath = resolveAbsoluteImagePath(relativePath);
        Mat image = Imgcodecs.imread(absolutePath);
        if (image.empty()) {
            return null;
        }
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = image;
        }
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(gray, faces, 1.1, 5, 0, new Size(30, 30), new Size());
        Mat face = faces.toArray().length > 0 ? new Mat(gray, faces.toArray()[0]) : gray;
        Mat resized = new Mat();
        Imgproc.resize(face, resized, new Size(100, 100));
        return resized;
    }

    private Object createLbphRecognizer() {
        try {
            Class<?> clazz = Class.forName("org.opencv.face.LBPHFaceRecognizer");
            Method create = clazz.getMethod("create");
            return create.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean invokeLbphPredict(Mat detectedFace, int[] label, double[] confidence) {
        try {
            Method predict = lbphRecognizer.getClass().getMethod(
                    "predict", Mat.class, int[].class, double[].class);
            predict.invoke(lbphRecognizer, detectedFace, label, confidence);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String saveFrameToDisk(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir).resolve("frames");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String filename = "frame_" + UUID.randomUUID() + ".jpg";
        Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        return "frames/" + filename;
    }

    public LiveFrameResult detectAndAnnotateLiveFrame(byte[] frameBytes,
                                                      String location,
                                                      User loggedInUser) {
        if (!opencvAvailable || frameBytes == null || frameBytes.length == 0) {
            return new LiveFrameResult(frameBytes, "WAITING", "", 0.0);
        }

        Mat color = Imgcodecs.imdecode(new MatOfByte(frameBytes), Imgcodecs.IMREAD_COLOR);
        if (color.empty()) {
            return new LiveFrameResult(frameBytes, "WAITING", "", 0.0);
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        MatOfRect facesDetected = new MatOfRect();
        faceDetector.detectMultiScale(gray, facesDetected, 1.1, 5, 0, new Size(30, 30), new Size());
        Rect[] faces = facesDetected.toArray();

        List<Criminal> criminals = criminalService.getAllActiveCriminals();
        Criminal bestMatch = null;
        double bestScore = 0.0;
        Rect bestRect = null;

        for (Rect rect : faces) {
            if (rect.width < liveMinFaceSize || rect.height < liveMinFaceSize) {
                continue;
            }
            Mat faceRoi = new Mat(gray, rect);
            Mat resized = new Mat();
            Imgproc.resize(faceRoi, resized, new Size(100, 100));

            // Quality gate: very blurry faces cause random false matches.
            if (varianceOfLaplacian(resized) < liveMinBlurVariance) {
                continue;
            }

            double[] embedding = buildEmbedding(resized);

            MatchCandidate localCandidate = findBestCandidate(embedding, resized, criminals);
            Criminal localBest = localCandidate.bestCriminal;
            double localBestScore = localCandidate.bestScore;

            if (isValidMatchForLive(localCandidate)) {
                if (localBestScore > bestScore) {
                    bestScore = localBestScore;
                    bestMatch = localBest;
                    bestRect = rect;
                }
            }
        }

        boolean liveMatchConfirmedNow = false;
        boolean liveMatchDisplay = false;
        if (bestMatch == null) {
            shouldConfirmLiveMatch("");
        }
        if (bestMatch != null) {
            try {
                liveMatchConfirmedNow = shouldConfirmLiveMatch(bestMatch.getName());
                liveMatchDisplay = liveMatchConfirmedNow || isWithinConfirmedWindow(bestMatch.getName());

                if (liveMatchDisplay && bestRect != null) {
                    Imgproc.rectangle(color, bestRect, new Scalar(0, 0, 255), 2);
                    String label = bestMatch.getName() + " " + (int) (bestScore * 100) + "%";
                    int y = Math.max(bestRect.y - 8, 18);
                    Imgproc.putText(color, label, new Point(bestRect.x, y),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 0, 255), 2);
                }

                if (liveMatchConfirmedNow) {
                    DetectionLog log = new DetectionLog();
                    log.setLocation(location);
                    log.setDetectedBy(loggedInUser);
                    log.setMatchStatus(DetectionLog.MatchStatus.MATCHED);
                    log.setMatchedCriminal(bestMatch);
                    log.setConfidenceScore(bestScore);
                    log.setCapturedFramePath(saveFrameToDisk(
                            new MockMultipartFile("frame", "frame.jpg", "image/jpeg", frameBytes)));
                    detectionLogRepository.save(log);
                    alertService.createAlert(log, bestMatch);
                }
            } catch (Exception ignored) {
            }
        }

        MatOfByte output = new MatOfByte();
        Imgcodecs.imencode(".jpg", color, output);
        byte[] annotated = output.toArray();

        if (bestMatch != null && liveMatchDisplay) {
            String status = liveMatchConfirmedNow ? "MATCHED_NEW" : "MATCHED";
            return new LiveFrameResult(annotated, status, bestMatch.getName(), bestScore);
        }
        return new LiveFrameResult(annotated, "NO_MATCH", "", 0.0);
    }

    private double varianceOfLaplacian(Mat gray100x100) {
        Mat lap = new Mat();
        Imgproc.Laplacian(gray100x100, lap, CvType.CV_64F);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        Core.meanStdDev(lap, mean, std);
        double s = std.get(0, 0)[0];
        return s * s;
    }

    private synchronized boolean shouldConfirmLiveMatch(String criminalName) {
        if (criminalName == null || criminalName.isBlank()) {
            pendingLiveName = "";
            pendingLiveCount = 0;
            return false;
        }
        long now = System.currentTimeMillis();
        if (criminalName.equals(lastConfirmedLiveName)
                && (now - lastConfirmedLiveAt) < LIVE_ALERT_COOLDOWN_MS) {
            return false;
        }
        if (criminalName.equals(pendingLiveName)) {
            pendingLiveCount++;
        } else {
            pendingLiveName = criminalName;
            pendingLiveCount = 1;
        }
        if (pendingLiveCount >= Math.max(1, liveConfirmationFrames)) {
            lastConfirmedLiveName = criminalName;
            lastConfirmedLiveAt = now;
            pendingLiveName = "";
            pendingLiveCount = 0;
            return true;
        }
        return false;
    }

    private synchronized boolean isWithinConfirmedWindow(String criminalName) {
        if (criminalName == null || criminalName.isBlank()) {
            return false;
        }
        if (!criminalName.equals(lastConfirmedLiveName)) {
            return false;
        }
        return (System.currentTimeMillis() - lastConfirmedLiveAt) < LIVE_ALERT_COOLDOWN_MS;
    }
}