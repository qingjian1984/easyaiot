package com.basiclab.iot.dataset.service.annotation;

import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class YoloDatasetInspector {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "gif");
    private static final int MAX_DETAIL_WARNINGS = 20;

    public Inspection inspect(String rawPath) throws IOException {
        Path root = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("目录不存在: " + rawPath);
        }

        List<String> warnings = new ArrayList<>();
        YamlMetadata yamlMetadata = loadYamlMetadata(root, warnings);
        LinkedHashMap<Path, Split> images = collectDeclaredImages(yamlMetadata, warnings);
        collectImages(root, Split.UNASSIGNED, images);

        Map<String, Integer> basenameCounts = new HashMap<>();
        for (Path image : images.keySet()) {
            basenameCounts.merge(image.getFileName().toString(), 1, Integer::sum);
        }
        int duplicateImageNameCount = (int) basenameCounts.values().stream().filter(count -> count > 1).count();
        if (duplicateImageNameCount > 0) {
            warnings.add("存在 " + duplicateImageNameCount + " 个跨目录同名图片，平台无法按文件名安全区分");
        }

        List<Sample> samples = new ArrayList<>(images.size());
        Set<Integer> observedClassIds = new LinkedHashSet<>();
        EnumMap<Split, SplitStats> splitStats = new EnumMap<>(Split.class);
        int matchedLabelCount = 0;
        int invalidLabelCount = 0;

        for (Map.Entry<Path, Split> entry : images.entrySet()) {
            Path image = entry.getKey();
            Split split = entry.getValue() == Split.UNASSIGNED ? detectSplit(root, image) : entry.getValue();
            Path label = resolveLabel(root, image, split);
            List<Box> boxes = Collections.emptyList();
            String validationError = null;
            if (label != null) {
                matchedLabelCount++;
                try {
                    boxes = parseLabel(label);
                    for (Box box : boxes) {
                        observedClassIds.add(box.getClassId());
                    }
                } catch (IllegalArgumentException e) {
                    validationError = e.getMessage();
                    invalidLabelCount++;
                    if (warnings.size() < MAX_DETAIL_WARNINGS) {
                        warnings.add(root.relativize(label) + ": " + validationError);
                    }
                }
            }
            samples.add(new Sample(image, label, split, boxes, validationError));
            SplitStats stats = splitStats.computeIfAbsent(split, ignored -> new SplitStats());
            stats.imageCount++;
            if (label != null) {
                stats.matchedLabelCount++;
            }
        }

        if (images.isEmpty()) {
            warnings.add("目录中未发现支持的图片文件");
        } else if (matchedLabelCount == 0) {
            warnings.add("未找到任何与图片对应的 YOLO .txt 标注");
        }
        if (invalidLabelCount > 0) {
            warnings.add("发现 " + invalidLabelCount + " 个无效标注文件；当前仅支持每行五列的目标检测格式");
        }
        if (matchedLabelCount > 0 && observedClassIds.isEmpty() && invalidLabelCount == 0) {
            warnings.add("标注文件均为空，未发现可导入的 YOLO 类别");
        }

        Map<Integer, String> detectedNames = new TreeMap<>();
        for (Integer classId : observedClassIds) {
            detectedNames.put(classId, yamlMetadata.classNames.get(classId));
        }
        String rootNameSuggestion = buildRootNameSuggestion(root);
        List<ClassDescriptor> classes = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : detectedNames.entrySet()) {
            String detectedName = trimToNull(entry.getValue());
            boolean manualRequired = detectedName == null || detectedName.matches("\\d+");
            String suggestedName = manualRequired && detectedNames.size() == 1 ? rootNameSuggestion : null;
            classes.add(new ClassDescriptor(entry.getKey(), detectedName, suggestedName, manualRequired));
        }

        samples.sort(Comparator.comparing(sample -> sample.getImagePath().toString()));
        boolean importable = !samples.isEmpty()
                && matchedLabelCount > 0
                && invalidLabelCount == 0
                && duplicateImageNameCount == 0
                && !classes.isEmpty();
        return new Inspection(root, samples, classes, splitStats, warnings, matchedLabelCount,
                invalidLabelCount, duplicateImageNameCount, importable);
    }

    private YamlMetadata loadYamlMetadata(Path root, List<String> warnings) throws IOException {
        Path yamlPath = Files.isRegularFile(root.resolve("data.yaml"))
                ? root.resolve("data.yaml")
                : root.resolve("dataset.yaml");
        Map<Integer, String> classNames = new TreeMap<>();
        List<DeclaredImageRoot> declaredRoots = new ArrayList<>();

        if (Files.isRegularFile(yamlPath)) {
            Object loaded;
            try {
                loaded = new Yaml().load(Files.readString(yamlPath));
            } catch (RuntimeException e) {
                throw new IOException("data.yaml 解析失败: " + e.getMessage(), e);
            }
            if (loaded instanceof Map<?, ?>) {
                Map<?, ?> yaml = (Map<?, ?>) loaded;
                readClassNames(yaml.get("names"), classNames);
                Path datasetBase = resolveYamlBase(yamlPath.getParent(), yaml.get("path"), warnings);
                readDeclaredRoots(yamlPath, datasetBase, yaml.get("train"), Split.TRAIN, declaredRoots, warnings);
                Object validationPath = yaml.containsKey("val") ? yaml.get("val") : yaml.get("valid");
                readDeclaredRoots(yamlPath, datasetBase, validationPath, Split.VALIDATION, declaredRoots, warnings);
                readDeclaredRoots(yamlPath, datasetBase, yaml.get("test"), Split.TEST, declaredRoots, warnings);
            }
        }

        if (classNames.isEmpty() && Files.isRegularFile(root.resolve("classes.txt"))) {
            List<String> lines = Files.readAllLines(root.resolve("classes.txt"));
            for (int i = 0; i < lines.size(); i++) {
                String name = trimToNull(lines.get(i));
                if (name != null) {
                    classNames.put(i, name);
                }
            }
        }
        return new YamlMetadata(classNames, declaredRoots);
    }

    private void readClassNames(Object namesValue, Map<Integer, String> target) {
        if (namesValue instanceof Collection<?>) {
            int index = 0;
            for (Object value : (Collection<?>) namesValue) {
                target.put(index++, value == null ? null : String.valueOf(value).trim());
            }
            return;
        }
        if (namesValue instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) namesValue).entrySet()) {
                try {
                    int classId = Integer.parseInt(String.valueOf(entry.getKey()));
                    target.put(classId, entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim());
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric YAML class keys.
                }
            }
        }
    }

    private Path resolveYamlBase(Path yamlDir, Object pathValue, List<String> warnings) {
        String configuredPath = trimToNull(pathValue == null ? null : String.valueOf(pathValue));
        if (configuredPath == null) {
            return yamlDir;
        }
        Path base = resolvePath(yamlDir, configuredPath);
        if (!Files.isDirectory(base)) {
            warnings.add("data.yaml 的 path 不存在: " + base);
        }
        return base;
    }

    private void readDeclaredRoots(Path yamlPath, Path datasetBase, Object value, Split split,
                                   List<DeclaredImageRoot> target, List<String> warnings) {
        if (value == null) {
            return;
        }
        Collection<?> values = value instanceof Collection<?> ? (Collection<?>) value : List.of(value);
        for (Object item : values) {
            String configuredPath = trimToNull(item == null ? null : String.valueOf(item));
            if (configuredPath == null) {
                continue;
            }
            Path imageRoot = resolvePath(datasetBase, configuredPath);
            if (Files.isDirectory(imageRoot)) {
                target.add(new DeclaredImageRoot(imageRoot, split));
            } else {
                warnings.add(yamlPath.getFileName() + " 的 " + split.getApiName() + " 路径不存在: " + imageRoot);
            }
        }
    }

    private Path resolvePath(Path base, String configuredPath) {
        Path path = Paths.get(configuredPath);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private LinkedHashMap<Path, Split> collectDeclaredImages(YamlMetadata metadata, List<String> warnings)
            throws IOException {
        LinkedHashMap<Path, Split> images = new LinkedHashMap<>();
        for (DeclaredImageRoot declaredRoot : metadata.declaredRoots) {
            if (Files.isRegularFile(declaredRoot.path)) {
                warnings.add("暂不支持 data.yaml 图片清单文件: " + declaredRoot.path);
                continue;
            }
            collectImages(declaredRoot.path, declaredRoot.split, images);
        }
        return images;
    }

    private void collectImages(Path root, Split split, Map<Path, Split> target) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && isImage(file)) {
                    target.putIfAbsent(file.toAbsolutePath().normalize(), split);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path resolveLabel(Path root, Path image, Split split) {
        String labelName = stripExtension(image.getFileName().toString()) + ".txt";
        Path cursor = image.getParent();
        while (cursor != null) {
            Path cursorName = cursor.getFileName();
            if (cursorName != null && "images".equalsIgnoreCase(cursorName.toString()) && cursor.getParent() != null) {
                Path relativeImage = cursor.relativize(image);
                Path relativeLabel = replaceExtension(relativeImage, "txt");
                Path candidate = cursor.getParent().resolve("labels").resolve(relativeLabel);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
            cursor = cursor.getParent();
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve("labels").resolve(labelName));
        if (split != Split.UNASSIGNED) {
            for (String splitName : split.getDirectoryNames()) {
                candidates.add(root.resolve("labels").resolve(splitName).resolve(labelName));
                candidates.add(root.resolve(splitName).resolve("labels").resolve(labelName));
            }
        }
        if (image.getParent() != null) {
            candidates.add(image.getParent().resolve(labelName));
        }
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .map(path -> path.toAbsolutePath().normalize())
                .orElse(null);
    }

    private List<Box> parseLabel(Path label) throws IOException {
        List<Box> boxes = new ArrayList<>();
        List<String> lines = Files.readAllLines(label);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length != 5) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行不是五列目标检测格式");
            }
            try {
                int classId = Integer.parseInt(parts[0]);
                double centerX = parseFinite(parts[1]);
                double centerY = parseFinite(parts[2]);
                double width = parseFinite(parts[3]);
                double height = parseFinite(parts[4]);
                if (classId < 0 || width < 0 || height < 0) {
                    throw new NumberFormatException("negative value");
                }
                boxes.add(new Box(classId, centerX, centerY, width, height));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行包含无效数值");
            }
        }
        return boxes;
    }

    private double parseFinite(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("non-finite value");
        }
        return parsed;
    }

    private Split detectSplit(Path root, Path image) {
        Path relative;
        try {
            relative = root.relativize(image);
        } catch (IllegalArgumentException e) {
            relative = image;
        }
        for (Path part : relative) {
            Split split = Split.fromDirectoryName(part.toString());
            if (split != Split.UNASSIGNED) {
                return split;
            }
        }
        return Split.UNASSIGNED;
    }

    private boolean isImage(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private Path replaceExtension(Path path, String extension) {
        Path parent = path.getParent();
        Path filename = Paths.get(stripExtension(path.getFileName().toString()) + "." + extension);
        return parent == null ? filename : parent.resolve(filename);
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String buildRootNameSuggestion(Path root) {
        Path filename = root.getFileName();
        if (filename == null) {
            return null;
        }
        String suggestion = filename.toString()
                .replaceFirst("\\s*\\([^)]*\\)\\s*$", "")
                .trim()
                .replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        return suggestion.isBlank() || suggestion.matches("\\d+") ? null : suggestion;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public enum Split {
        TRAIN("train", List.of("train")),
        VALIDATION("validation", List.of("valid", "val", "validation")),
        TEST("test", List.of("test")),
        UNASSIGNED("unassigned", List.of());

        private final String apiName;
        private final List<String> directoryNames;

        Split(String apiName, List<String> directoryNames) {
            this.apiName = apiName;
            this.directoryNames = directoryNames;
        }

        public String getApiName() {
            return apiName;
        }

        public List<String> getDirectoryNames() {
            return directoryNames;
        }

        static Split fromDirectoryName(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (Split split : values()) {
                if (split.directoryNames.contains(normalized)) {
                    return split;
                }
            }
            return UNASSIGNED;
        }
    }

    public static final class Inspection {
        private final Path root;
        private final List<Sample> samples;
        private final List<ClassDescriptor> classes;
        private final Map<Split, SplitStats> splitStats;
        private final List<String> warnings;
        private final int matchedLabelCount;
        private final int invalidLabelCount;
        private final int duplicateImageNameCount;
        private final boolean importable;

        Inspection(Path root, List<Sample> samples, List<ClassDescriptor> classes,
                   Map<Split, SplitStats> splitStats, List<String> warnings, int matchedLabelCount,
                   int invalidLabelCount, int duplicateImageNameCount, boolean importable) {
            this.root = root;
            this.samples = List.copyOf(samples);
            this.classes = List.copyOf(classes);
            this.splitStats = Map.copyOf(splitStats);
            this.warnings = List.copyOf(warnings);
            this.matchedLabelCount = matchedLabelCount;
            this.invalidLabelCount = invalidLabelCount;
            this.duplicateImageNameCount = duplicateImageNameCount;
            this.importable = importable;
        }

        public Path getRoot() { return root; }
        public List<Sample> getSamples() { return samples; }
        public List<ClassDescriptor> getClasses() { return classes; }
        public Map<Split, SplitStats> getSplitStats() { return splitStats; }
        public List<String> getWarnings() { return warnings; }
        public int getImageCount() { return samples.size(); }
        public int getMatchedLabelCount() { return matchedLabelCount; }
        public int getMissingLabelCount() { return samples.size() - matchedLabelCount; }
        public int getInvalidLabelCount() { return invalidLabelCount; }
        public int getDuplicateImageNameCount() { return duplicateImageNameCount; }
        public boolean isImportable() { return importable; }
    }

    public static final class Sample {
        private final Path imagePath;
        private final Path labelPath;
        private final Split split;
        private final List<Box> boxes;
        private final String validationError;

        Sample(Path imagePath, Path labelPath, Split split, List<Box> boxes, String validationError) {
            this.imagePath = imagePath;
            this.labelPath = labelPath;
            this.split = split;
            this.boxes = List.copyOf(boxes);
            this.validationError = validationError;
        }

        public Path getImagePath() { return imagePath; }
        public Path getLabelPath() { return labelPath; }
        public Split getSplit() { return split; }
        public List<Box> getBoxes() { return boxes; }
        public String getValidationError() { return validationError; }
        public boolean hasLabel() { return labelPath != null; }
    }

    public static final class Box {
        private final int classId;
        private final double centerX;
        private final double centerY;
        private final double width;
        private final double height;

        Box(int classId, double centerX, double centerY, double width, double height) {
            this.classId = classId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
        }

        public int getClassId() { return classId; }
        public double getCenterX() { return centerX; }
        public double getCenterY() { return centerY; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
    }

    public static final class ClassDescriptor {
        private final int classId;
        private final String detectedName;
        private final String suggestedName;
        private final boolean manualNameRequired;

        ClassDescriptor(int classId, String detectedName, String suggestedName, boolean manualNameRequired) {
            this.classId = classId;
            this.detectedName = detectedName;
            this.suggestedName = suggestedName;
            this.manualNameRequired = manualNameRequired;
        }

        public int getClassId() { return classId; }
        public String getDetectedName() { return detectedName; }
        public String getSuggestedName() { return suggestedName; }
        public boolean isManualNameRequired() { return manualNameRequired; }
    }

    public static final class SplitStats {
        private int imageCount;
        private int matchedLabelCount;

        public int getImageCount() { return imageCount; }
        public int getMatchedLabelCount() { return matchedLabelCount; }
    }

    private static final class YamlMetadata {
        private final Map<Integer, String> classNames;
        private final List<DeclaredImageRoot> declaredRoots;

        private YamlMetadata(Map<Integer, String> classNames, List<DeclaredImageRoot> declaredRoots) {
            this.classNames = classNames;
            this.declaredRoots = declaredRoots;
        }
    }

    private static final class DeclaredImageRoot {
        private final Path path;
        private final Split split;

        private DeclaredImageRoot(Path path, Split split) {
            this.path = path;
            this.split = split;
        }
    }
}
