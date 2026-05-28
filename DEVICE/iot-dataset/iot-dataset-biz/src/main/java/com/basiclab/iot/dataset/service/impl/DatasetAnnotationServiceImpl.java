package com.basiclab.iot.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.basiclab.iot.common.text.UUID;
import com.basiclab.iot.common.utils.object.BeanUtils;
import com.basiclab.iot.dataset.dal.dataobject.DatasetDO;
import com.basiclab.iot.dataset.dal.dataobject.DatasetImageDO;
import com.basiclab.iot.dataset.dal.dataobject.DatasetTagDO;
import com.basiclab.iot.dataset.dal.pgsql.DatasetImageMapper;
import com.basiclab.iot.dataset.dal.pgsql.DatasetMapper;
import com.basiclab.iot.dataset.domain.dataset.vo.*;
import com.basiclab.iot.dataset.service.DatasetAnnotationService;
import com.basiclab.iot.dataset.service.DatasetImageService;
import com.basiclab.iot.dataset.service.DatasetService;
import com.basiclab.iot.dataset.service.DatasetTagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.dataset.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DatasetAnnotationServiceImpl implements DatasetAnnotationService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetAnnotationServiceImpl.class);
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "bmp", "gif");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private DatasetImageMapper datasetImageMapper;
    @Resource
    private DatasetMapper datasetMapper;
    @Resource
    private DatasetImageService datasetImageService;
    @Resource
    private DatasetTagService datasetTagService;
    @Resource
    private DatasetService datasetService;
    @Resource
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String minioBucket;

    @Value("${minio.datasets-bucket:datasets}")
    private String minioDatasetsBucket;

    @Override
    public void exportYoloZip(Long datasetId, DatasetAnnotationExportReqVO reqVO, HttpServletResponse response) throws IOException {
        validateDatasetExists(datasetId);
        List<String> selectedClasses = reqVO.getSelectedClasses();
        if (selectedClasses == null || selectedClasses.isEmpty()) {
            throw exception(FILE_UPLOAD_FAILED, "请至少选择一个导出类别");
        }
        double trainRatio = nz(reqVO.getTrainRatio(), 0.7);
        double valRatio = nz(reqVO.getValRatio(), 0.2);
        double testRatio = nz(reqVO.getTestRatio(), 0.1);
        String sampleSelection = Optional.ofNullable(reqVO.getSampleSelection()).orElse("all");
        Map<String, Integer> classToId = new LinkedHashMap<>();
        for (int i = 0; i < selectedClasses.size(); i++) {
            classToId.put(selectedClasses.get(i), i);
        }

        List<DatasetImageDO> all = datasetImageMapper.selectList(
                new LambdaQueryWrapper<DatasetImageDO>().eq(DatasetImageDO::getDatasetId, datasetId));
        List<DatasetImageDO> filtered = filterBySample(all, sampleSelection);
        if (filtered.isEmpty()) {
            throw exception(DATASET_NO_IMAGES);
        }
        Collections.shuffle(filtered);

        int total = filtered.size();
        int trainCount = trainRatio > 0 ? (int) (total * trainRatio) : 0;
        int valCount = valRatio > 0 ? (int) (total * valRatio) : 0;
        int testCount = testRatio > 0 ? (int) (total * testRatio) : 0;
        List<DatasetImageDO> train = filtered.subList(0, Math.min(trainCount, filtered.size()));
        List<DatasetImageDO> val = trainCount < filtered.size()
                ? filtered.subList(trainCount, Math.min(trainCount + valCount, filtered.size())) : List.of();
        List<DatasetImageDO> test = trainCount + valCount < filtered.size()
                ? filtered.subList(trainCount + valCount, Math.min(trainCount + valCount + testCount, filtered.size())) : List.of();

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String zipName = "datasets_" + timestamp + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, StandardCharsets.UTF_8));

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            StringBuilder namesYaml = new StringBuilder("names:\n");
            for (String name : selectedClasses) {
                namesYaml.append("  - ").append(MAPPER.writeValueAsString(name)).append('\n');
            }
            String dataYaml = "path: .\ntrain: train/images\nval: val/images\ntest: test/images\n\nnc: "
                    + selectedClasses.size() + '\n' + namesYaml;
            writeZipEntry(zos, "data.yaml", dataYaml.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "classes.txt", String.join("\n", selectedClasses).getBytes(StandardCharsets.UTF_8));

            String prefix = Optional.ofNullable(reqVO.getExportPrefix()).orElse("").trim();
            writeSplit(zos, "train", train, classToId, selectedClasses, prefix);
            writeSplit(zos, "val", val, classToId, selectedClasses, prefix);
            writeSplit(zos, "test", test, classToId, selectedClasses, prefix);
            zos.finish();
        }
    }

    @Override
    public DatasetAnnotationImportResultVO importImageFolder(Long datasetId, List<MultipartFile> files) {
        validateDatasetExists(datasetId);
        int ok = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String name = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("")).getFileName().toString();
            if (!isImageName(name)) continue;
            try {
                saveImageBytes(datasetId, file.getBytes(), name, null, null, null);
                ok++;
            } catch (Exception e) {
                logger.warn("上传图片失败: {}", name, e);
            }
        }
        return DatasetAnnotationImportResultVO.builder().imagesCopied(ok).build();
    }

    @Override
    public DatasetAnnotationImportResultVO importLabelmeFolder(Long datasetId, List<MultipartFile> files) {
        validateDatasetExists(datasetId);
        List<MultipartFile> jsonFiles = new ArrayList<>();
        Map<String, MultipartFile> imagesByBase = new HashMap<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            String name = Optional.ofNullable(f.getOriginalFilename()).orElse("").replace('\\', '/');
            String fileName = Paths.get(name).getFileName().toString();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                jsonFiles.add(f);
            } else if (isImageName(fileName)) {
                imagesByBase.put(stripExt(fileName).toLowerCase(Locale.ROOT), f);
            }
        }
        int labelme = 0;
        int images = 0;
        for (MultipartFile maybeJson : jsonFiles) {
            try {
                JsonNode root = MAPPER.readTree(maybeJson.getBytes());
                String imagePath = root.path("imagePath").asText("");
                String jsonName = Paths.get(Optional.ofNullable(maybeJson.getOriginalFilename()).orElse("")).getFileName().toString();
                String base = stripExt(Paths.get(imagePath.isEmpty() ? jsonName : Paths.get(imagePath).getFileName().toString()).getFileName().toString());
                MultipartFile imgFile = imagesByBase.get(base.toLowerCase(Locale.ROOT));
                if (imgFile == null) {
                    imgFile = findImageFile(imagesByBase, base);
                }
                if (imgFile == null) continue;
                byte[] imgBytes = imgFile.getBytes();
                int w = root.path("imageWidth").asInt(0);
                int h = root.path("imageHeight").asInt(0);
                if (w <= 0 || h <= 0) {
                    int[] dim = readImageSize(imgBytes);
                    w = dim[0];
                    h = dim[1];
                }
                String annJson = convertLabelmeToAnnotations(root, w, h);
                saveImageBytes(datasetId, imgBytes, Paths.get(imgFile.getOriginalFilename()).getFileName().toString(), annJson, w, h);
                syncTagsFromAnnotations(datasetId, annJson);
                labelme++;
                images++;
            } catch (Exception ex) {
                logger.warn("解析 labelme 失败: {}", maybeJson.getOriginalFilename(), ex);
            }
        }
        if (images == 0 && !imagesByBase.isEmpty()) {
            for (MultipartFile imgFile : imagesByBase.values()) {
                try {
                    saveImageBytes(datasetId, imgFile.getBytes(),
                            Paths.get(imgFile.getOriginalFilename()).getFileName().toString(), null, null, null);
                    images++;
                } catch (Exception ex) {
                    logger.warn("上传图片失败: {}", imgFile.getOriginalFilename(), ex);
                }
            }
        }
        return DatasetAnnotationImportResultVO.builder()
                .imagesCopied(images)
                .labelmeImages(labelme)
                .build();
    }

    @Override
    public DatasetAnnotationImportResultVO importImageFolderPath(Long datasetId, String path) {
        return importFromLocalRoot(datasetId, path, true, true);
    }

    @Override
    public DatasetAnnotationImportResultVO importYoloPath(Long datasetId, String path) {
        return importFromLocalRoot(datasetId, path, false, true);
    }

    @Override
    public DatasetAnnotationImportResultVO importCocoPath(Long datasetId, DatasetAnnotationCocoImportReqVO reqVO) {
        validateDatasetExists(datasetId);
        Path jsonPath = Paths.get(reqVO.getCocoJson()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jsonPath)) {
            throw exception(FILE_UPLOAD_FAILED, "标注文件不存在: " + jsonPath);
        }
        Path imagesRoot = reqVO.getImagesRoot() != null && !reqVO.getImagesRoot().isBlank()
                ? Paths.get(reqVO.getImagesRoot()).toAbsolutePath().normalize()
                : jsonPath.getParent().getParent();
        try {
            JsonNode root = MAPPER.readTree(Files.readString(jsonPath));
            Map<Long, String> catMap = new HashMap<>();
            for (JsonNode c : root.path("categories")) {
                catMap.put(c.path("id").asLong(), c.path("name").asText());
            }
            Map<Long, String> imageFiles = new HashMap<>();
            for (JsonNode img : root.path("images")) {
                imageFiles.put(img.path("id").asLong(), img.path("file_name").asText());
            }
            Map<Long, List<JsonNode>> annsByImage = new HashMap<>();
            for (JsonNode ann : root.path("annotations")) {
                annsByImage.computeIfAbsent(ann.path("image_id").asLong(), k -> new ArrayList<>()).add(ann);
            }
            int cocoImages = 0;
            for (Map.Entry<Long, List<JsonNode>> entry : annsByImage.entrySet()) {
                String fileName = imageFiles.get(entry.getKey());
                if (fileName == null) continue;
                Path imgPath = resolveUnderRoots(imagesRoot, jsonPath.getParent(), fileName);
                if (imgPath == null || !Files.isRegularFile(imgPath)) continue;
                byte[] bytes = Files.readAllBytes(imgPath);
                int[] dim = readImageSize(bytes);
                String annJson = convertCocoAnns(entry.getValue(), catMap, dim[0], dim[1]);
                saveImageBytes(datasetId, bytes, Paths.get(fileName).getFileName().toString(), annJson, dim[0], dim[1]);
                syncTagsFromAnnotations(datasetId, annJson);
                cocoImages++;
            }
            return DatasetAnnotationImportResultVO.builder().imagesCopied(cocoImages).cocoImages(cocoImages).build();
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "COCO 导入失败: " + e.getMessage());
        }
    }

    @Override
    public List<DatasetRespVO> listCloudDatasets() {
        DatasetPageReqVO pageReqVO = new DatasetPageReqVO();
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(100);
        pageReqVO.setDatasetType(0);
        PageInfo<DatasetDO> page = datasetService.getDatasetPage(pageReqVO);
        return BeanUtils.toBean(page.getList(), DatasetRespVO.class);
    }

    @Override
    public DatasetAnnotationImportResultVO cloudImport(Long targetDatasetId, DatasetAnnotationCloudImportReqVO reqVO) {
        validateDatasetExists(targetDatasetId);
        DatasetDO source = datasetMapper.selectById(reqVO.getSourceDatasetId());
        if (source == null) {
            throw exception(DATASET_NOT_EXISTS);
        }
        if (source.getZipUrl() == null || source.getZipUrl().isBlank()) {
            throw exception(FILE_UPLOAD_FAILED, "源数据集尚未生成压缩包，请先在源数据集完成划分并同步");
        }
        try {
            Path tempZip = Files.createTempFile("cloud-import-", ".zip");
            downloadMinioObject(source.getZipUrl(), tempZip);
            Path extractDir = Files.createTempDirectory("cloud-import-data-");
            unzip(tempZip, extractDir);
            int imported = importExtractedYoloTree(targetDatasetId, extractDir);
            Files.deleteIfExists(tempZip);
            deleteDirectory(extractDir);
            String hint = imported == 0 ? "压缩包已解压，但未解析到有效图片，请确认 zip 内 YOLO 目录结构" : null;
            return DatasetAnnotationImportResultVO.builder()
                    .imagesCopied(imported)
                    .yoloImages(imported)
                    .hint(hint)
                    .build();
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "云平台导入失败: " + e.getMessage());
        }
    }

    @Override
    public DatasetAnnotationImportResultVO cloudExport(Long sourceDatasetId, DatasetAnnotationCloudExportReqVO reqVO) {
        validateDatasetExists(sourceDatasetId);
        DatasetSaveReqVO create = new DatasetSaveReqVO();
        create.setName(reqVO.getName());
        create.setVersion(reqVO.getVersion());
        create.setDatasetCode(reqVO.getVersion());
        create.setDatasetType(0);
        create.setDescription("自动标注平台导出");
        Long newId = datasetService.createDataset(create);

        List<DatasetImageDO> images = datasetImageMapper.selectList(
                new LambdaQueryWrapper<DatasetImageDO>().eq(DatasetImageDO::getDatasetId, sourceDatasetId));
        int created = 0;
        for (DatasetImageDO img : images) {
            try {
                byte[] bytes = readImageBytesFromMinio(img.getPath());
                saveImageBytes(newId, bytes, img.getName(), img.getAnnotations(), img.getWidth(), img.getHeigh());
                created++;
            } catch (Exception e) {
                logger.warn("云导出复制图片失败: {}", img.getName(), e);
            }
        }
        copyTags(sourceDatasetId, newId);
        return DatasetAnnotationImportResultVO.builder()
                .cloudDatasetId(newId)
                .createdImages(created)
                .updatedImages(0)
                .build();
    }

    @Override
    public DatasetAnnotationImportResultVO extractFramesFromVideo(Long datasetId, MultipartFile file, int frameInterval) {
        validateDatasetExists(datasetId);
        if (frameInterval < 1) frameInterval = 30;
        Path tempVideo = null;
        Path outDir = null;
        try {
            tempVideo = Files.createTempFile("video-", "-" + Optional.ofNullable(file.getOriginalFilename()).orElse("video.mp4"));
            file.transferTo(tempVideo.toFile());
            outDir = Files.createTempDirectory("frames-");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", tempVideo.toString(),
                    "-vf", "select='not(mod(n\\," + frameInterval + "))'",
                    "-vsync", "vfr",
                    outDir.resolve("frame_%06d.jpg").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> logger.debug("ffmpeg: {}", line));
            }
            if (p.waitFor() != 0) {
                throw exception(FILE_UPLOAD_FAILED, "视频抽帧失败，请确认服务器已安装 ffmpeg");
            }
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(outDir, "*.jpg")) {
                for (Path frame : stream) {
                    byte[] bytes = Files.readAllBytes(frame);
                    saveImageBytes(datasetId, bytes, frame.getFileName().toString(), null, null, null);
                    count++;
                }
            }
            return DatasetAnnotationImportResultVO.builder().imagesCopied(count).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(FILE_UPLOAD_FAILED, "视频抽帧被中断");
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "视频抽帧失败: " + e.getMessage());
        } finally {
            if (tempVideo != null) try { Files.deleteIfExists(tempVideo); } catch (IOException ignored) {}
            if (outDir != null) deleteDirectory(outDir);
        }
    }

    // —— helpers ——

    private DatasetAnnotationImportResultVO importFromLocalRoot(Long datasetId, String path, boolean tryLabelmeCoco, boolean tryYolo) {
        validateDatasetExists(datasetId);
        Path root = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw exception(FILE_UPLOAD_FAILED, "目录不存在: " + path);
        }
        List<String> yoloNames = tryYolo ? loadYoloClassNames(root) : List.of();
        int images = 0, labelme = 0, coco = 0, yolo = 0;
        try {
            List<Path> imagePaths = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isImageName(file.getFileName().toString())) imagePaths.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
            for (Path imgPath : imagePaths) {
                byte[] bytes = Files.readAllBytes(imgPath);
                int[] dim = readImageSize(bytes);
                String annJson = null;
                if (tryLabelmeCoco) {
                    annJson = tryLabelmeSidecar(imgPath, dim[0], dim[1]);
                    if (annJson != null) labelme++;
                }
                if (annJson == null && tryYolo) {
                    Path ytxt = findYoloTxt(root, imgPath);
                    if (ytxt != null) {
                        annJson = convertYoloTxt(Files.readString(ytxt), yoloNames, dim[0], dim[1]);
                        if (annJson != null) yolo++;
                    }
                }
                saveImageBytes(datasetId, bytes, imgPath.getFileName().toString(), annJson,
                        dim[0] > 0 ? dim[0] : null, dim[1] > 0 ? dim[1] : null);
                if (annJson != null) syncTagsFromAnnotations(datasetId, annJson);
                images++;
            }
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "路径导入失败: " + e.getMessage());
        }
        return DatasetAnnotationImportResultVO.builder()
                .imagesCopied(images)
                .labelmeImages(labelme)
                .cocoImages(coco)
                .yoloImages(yolo)
                .build();
    }

    private int importExtractedYoloTree(Long datasetId, Path extractDir) throws IOException {
        Path root = findDatasetRoot(extractDir);
        int count = 0;
        for (String split : List.of("train", "val", "test")) {
            Path imagesDir = root.resolve(split).resolve("images");
            Path labelsDir = root.resolve(split).resolve("labels");
            if (!Files.isDirectory(imagesDir)) continue;
            List<String> classNames = loadYoloClassNames(root);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(imagesDir)) {
                for (Path img : ds) {
                    if (!Files.isRegularFile(img) || !isImageName(img.getFileName().toString())) continue;
                    byte[] bytes = Files.readAllBytes(img);
                    int[] dim = readImageSize(bytes);
                    String base = stripExt(img.getFileName().toString());
                    Path lbl = labelsDir.resolve(base + ".txt");
                    String ann = Files.isRegularFile(lbl) ? convertYoloTxt(Files.readString(lbl), classNames, dim[0], dim[1]) : null;
                    saveImageBytes(datasetId, bytes, img.getFileName().toString(), ann, dim[0], dim[1]);
                    if (ann != null) syncTagsFromAnnotations(datasetId, ann);
                    count++;
                }
            }
        }
        return count;
    }

    private Path findDatasetRoot(Path extractDir) throws IOException {
        if (Files.exists(extractDir.resolve("data.yaml")) || Files.exists(extractDir.resolve("train"))) {
            return extractDir;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(extractDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    Path inner = findDatasetRoot(p);
                    if (inner != null) return inner;
                }
            }
        }
        return extractDir;
    }

    private void writeSplit(ZipOutputStream zos, String split, List<DatasetImageDO> images,
                            Map<String, Integer> classToId, List<String> selectedClasses, String prefix) throws IOException {
        for (DatasetImageDO image : images) {
            try {
                byte[] imgBytes = readImageBytesFromMinio(image.getPath());
                String flat = image.getName().replace("\\", "/").replace("/", "__");
                String imgName = prefix.isEmpty() ? flat : prefix + "_" + flat;
                writeZipEntry(zos, split + "/images/" + imgName, imgBytes);
                String label = buildYoloLabelContent(image, classToId, selectedClasses);
                String labelName = stripExt(imgName) + ".txt";
                writeZipEntry(zos, split + "/labels/" + labelName, label.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.warn("导出图片失败: {}", image.getName(), e);
            }
        }
    }

    private String buildYoloLabelContent(DatasetImageDO image, Map<String, Integer> classToId, List<String> selectedClasses) {
        if (image.getAnnotations() == null || image.getAnnotations().isBlank()) return "";
        try {
            List<Map<String, Object>> anns = MAPPER.readValue(image.getAnnotations(), new TypeReference<>() {});
            StringBuilder sb = new StringBuilder();
            int w = image.getWidth() != null && image.getWidth() > 0 ? image.getWidth() : 1;
            int h = image.getHeigh() != null && image.getHeigh() > 0 ? image.getHeigh() : 1;
            for (Map<String, Object> ann : anns) {
                String labelName = String.valueOf(ann.getOrDefault("label", ann.getOrDefault("class", "")));
                if (!selectedClasses.contains(labelName) || !classToId.containsKey(labelName)) continue;
                List<Map<String, Object>> points = (List<Map<String, Object>>) ann.get("points");
                if (points == null || points.size() < 4) continue;
                double minX = 1, minY = 1, maxX = 0, maxY = 0;
                for (Map<String, Object> p : points) {
                    double x = toDouble(p.get("x"));
                    double y = toDouble(p.get("y"));
                    if (x <= 1.01 && y <= 1.01) { /* normalized */ } else {
                        x /= w;
                        y /= h;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
                double cx = (minX + maxX) / 2;
                double cy = (minY + maxY) / 2;
                double bw = maxX - minX;
                double bh = maxY - minY;
                sb.append(String.format(Locale.US, "%d %.6f %.6f %.6f %.6f\n",
                        classToId.get(labelName), cx, cy, bw, bh));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void saveImageBytes(Long datasetId, byte[] fileData, String originalFilename,
                                String annotationsJson, Integer width, Integer height) {
        DatasetImageSaveReqVO vo = new DatasetImageSaveReqVO();
        vo.setDatasetId(datasetId);
        vo.setName(originalFilename);
        vo.setAnnotations(annotationsJson);
        vo.setWidth(width);
        vo.setHeigh(height);
        vo.setSize((long) fileData.length);
        vo.setCompleted(annotationsJson != null && !annotationsJson.isBlank() ? 1 : 0);
        try {
            String ext = getFileExtension(originalFilename);
            String storagePath = datasetId + "/" + UUID.randomUUID() + "." + ext;
            uploadToMinio(fileData, storagePath, getContentType(ext));
            vo.setPath("/api/v1/buckets/" + minioBucket + "/objects/download?prefix=" + storagePath);
        } catch (Exception e) {
            throw exception(FILE_UPLOAD_FAILED, "保存图片失败: " + e.getMessage());
        }
        datasetImageService.createDatasetImage(vo);
    }

    private void uploadToMinio(byte[] content, String objectName, String contentType) throws Exception {
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(io.minio.PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        }
    }

    private byte[] readImageBytesFromMinio(String path) throws Exception {
        String object = parseObjectNameFromPath(path);
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder().bucket(minioBucket).object(object).build())) {
            return in.readAllBytes();
        }
    }

    private void downloadMinioObject(String path, Path dest) throws IOException {
        try {
            String object = parseObjectNameFromPath(path);
            String bucket = path.contains("/buckets/") ? path.split("/buckets/")[1].split("/")[0] : minioDatasetsBucket;
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String parseObjectNameFromPath(String path) {
        int idx = path.indexOf("prefix=");
        return idx >= 0 ? path.substring(idx + 7) : path;
    }

    private List<DatasetImageDO> filterBySample(List<DatasetImageDO> all, String sampleSelection) {
        if ("annotated".equals(sampleSelection)) {
            return all.stream().filter(i -> i.getCompleted() != null && i.getCompleted() == 1).collect(Collectors.toList());
        }
        if ("unannotated".equals(sampleSelection)) {
            return all.stream().filter(i -> i.getCompleted() == null || i.getCompleted() != 1).collect(Collectors.toList());
        }
        return new ArrayList<>(all);
    }

    private void validateDatasetExists(Long datasetId) {
        if (datasetMapper.selectById(datasetId) == null) {
            throw exception(DATASET_NOT_EXISTS);
        }
    }

    private static double nz(Double v, double def) {
        return v == null ? def : v;
    }

    private static boolean isImageName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return IMAGE_EXT.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "jpg" : filename.substring(dot + 1);
    }

    private static String getContentType(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static int[] readImageSize(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private MultipartFile findImageFile(Map<String, MultipartFile> byBase, String base) {
        MultipartFile f = byBase.get(base.toLowerCase(Locale.ROOT));
        if (f != null && isImageName(f.getOriginalFilename())) return f;
        for (MultipartFile file : byBase.values()) {
            if (file.getOriginalFilename() != null && stripExt(Paths.get(file.getOriginalFilename()).getFileName().toString())
                    .equalsIgnoreCase(base) && isImageName(file.getOriginalFilename())) {
                return file;
            }
        }
        return null;
    }

    private String convertLabelmeToAnnotations(JsonNode root, int w, int h) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode shape : root.path("shapes")) {
            String label = shape.path("label").asText("");
            String type = shape.path("shape_type").asText("polygon");
            List<Map<String, Double>> points = new ArrayList<>();
            for (JsonNode pt : shape.path("points")) {
                double px = pt.get(0).asDouble();
                double py = pt.get(1).asDouble();
                points.add(Map.of("x", w > 0 ? px / w : px, "y", h > 0 ? py / h : py));
            }
            if ("rectangle".equals(type) && points.size() == 2) {
                double x1 = points.get(0).get("x"), y1 = points.get(0).get("y");
                double x2 = points.get(1).get("x"), y2 = points.get(1).get("y");
                points = List.of(
                        Map.of("x", x1, "y", y1), Map.of("x", x2, "y", y1),
                        Map.of("x", x2, "y", y2), Map.of("x", x1, "y", y2));
                type = "rectangle";
            }
            Map<String, Object> ann = new LinkedHashMap<>();
            ann.put("label", label);
            ann.put("type", type);
            ann.put("points", points);
            ann.put("auto", false);
            list.add(ann);
        }
        return MAPPER.writeValueAsString(list);
    }

    private String tryLabelmeSidecar(Path imgPath, int w, int h) {
        Path json = Paths.get(stripExt(imgPath.toString()) + ".json");
        if (!Files.isRegularFile(json)) return null;
        try {
            return convertLabelmeToAnnotations(MAPPER.readTree(Files.readString(json)), w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private String convertYoloTxt(String txt, List<String> classNames, int w, int h) throws IOException {
        if (w <= 0 || h <= 0) return null;
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : txt.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 5) continue;
            int cls = Integer.parseInt(parts[0]);
            double cx = Double.parseDouble(parts[1]);
            double cy = Double.parseDouble(parts[2]);
            double bw = Double.parseDouble(parts[3]);
            double bh = Double.parseDouble(parts[4]);
            double x1 = cx - bw / 2, y1 = cy - bh / 2, x2 = cx + bw / 2, y2 = cy + bh / 2;
            String label = cls < classNames.size() ? classNames.get(cls) : String.valueOf(cls);
            list.add(Map.of(
                    "label", label,
                    "type", "rectangle",
                    "auto", false,
                    "points", List.of(
                            Map.of("x", x1, "y", y1), Map.of("x", x2, "y", y1),
                            Map.of("x", x2, "y", y2), Map.of("x", x1, "y", y2))));
        }
        return list.isEmpty() ? null : MAPPER.writeValueAsString(list);
    }

    private String convertCocoAnns(List<JsonNode> anns, Map<Long, String> catMap, int w, int h) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode ann : anns) {
            String label = catMap.getOrDefault(ann.path("category_id").asLong(), "unknown");
            JsonNode bbox = ann.path("bbox");
            if (bbox.isArray() && bbox.size() >= 4) {
                double x = bbox.get(0).asDouble(), y = bbox.get(1).asDouble();
                double bw = bbox.get(2).asDouble(), bh = bbox.get(3).asDouble();
                double x2 = x + bw, y2 = y + bh;
                list.add(Map.of(
                        "label", label, "type", "rectangle", "auto", false,
                        "points", List.of(
                                Map.of("x", w > 0 ? x / w : x, "y", h > 0 ? y / h : y),
                                Map.of("x", w > 0 ? x2 / w : x2, "y", h > 0 ? y / h : y),
                                Map.of("x", w > 0 ? x2 / w : x2, "y", h > 0 ? y2 / h : y2),
                                Map.of("x", w > 0 ? x / w : x, "y", h > 0 ? y2 / h : y2))));
            }
        }
        return MAPPER.writeValueAsString(list);
    }

    private List<String> loadYoloClassNames(Path root) {
        for (String fname : List.of("classes.txt", "data.yaml")) {
            Path p = root.resolve(fname);
            if (!Files.isRegularFile(p)) continue;
            try {
                if (fname.endsWith(".txt")) {
                    return Files.readAllLines(p).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                }
                String content = Files.readString(p);
                int namesIdx = content.indexOf("names:");
                if (namesIdx >= 0) {
                    List<String> names = new ArrayList<>();
                    for (String line : content.substring(namesIdx).split("\n")) {
                        line = line.trim();
                        if (line.startsWith("- ")) names.add(line.substring(2).trim().replaceAll("^[\"']|[\"']$", ""));
                    }
                    if (!names.isEmpty()) return names;
                }
            } catch (IOException ignored) {}
        }
        return List.of();
    }

    private Path findYoloTxt(Path root, Path imagePath) {
        String base = stripExt(imagePath.getFileName().toString());
        for (String sub : List.of("labels", "train/labels", "val/labels", "test/labels")) {
            Path p = root.resolve(sub).resolve(base + ".txt");
            if (Files.isRegularFile(p)) return p;
        }
        Path sibling = imagePath.getParent().resolve(base + ".txt");
        return Files.isRegularFile(sibling) ? sibling : null;
    }

    private Path resolveUnderRoots(Path imagesRoot, Path cocoDir, String fileName) {
        for (Path base : List.of(imagesRoot, cocoDir, imagesRoot.resolve("train2017"), imagesRoot.resolve("images"))) {
            Path p = base.resolve(fileName).normalize();
            if (Files.isRegularFile(p)) return p;
            p = base.resolve(Paths.get(fileName).getFileName().toString()).normalize();
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private void syncTagsFromAnnotations(Long datasetId, String annJson) {
        try {
            List<Map<String, Object>> anns = MAPPER.readValue(annJson, new TypeReference<>() {});
            Set<String> names = anns.stream()
                    .map(a -> String.valueOf(a.getOrDefault("label", "")))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            DatasetTagPageReqVO req = new DatasetTagPageReqVO();
            req.setDatasetId(datasetId);
            req.setPageSize(500);
            Set<String> existing = datasetTagService.getDatasetTagPage(req).getList().stream()
                    .map(DatasetTagDO::getName).collect(Collectors.toSet());
            int nextShortcut = existing.size() + 1;
            for (String name : names) {
                if (existing.contains(name)) continue;
                DatasetTagSaveReqVO tag = new DatasetTagSaveReqVO();
                tag.setDatasetId(datasetId);
                tag.setName(name);
                tag.setColor("#3aa757");
                tag.setShortcut(nextShortcut++);
                datasetTagService.createDatasetTag(tag);
            }
        } catch (Exception ignored) {}
    }

    private void copyTags(Long fromId, Long toId) {
        DatasetTagPageReqVO req = new DatasetTagPageReqVO();
        req.setDatasetId(fromId);
        req.setPageSize(500);
        int shortcut = 1;
        for (DatasetTagDO tag : datasetTagService.getDatasetTagPage(req).getList()) {
            DatasetTagSaveReqVO vo = new DatasetTagSaveReqVO();
            vo.setDatasetId(toId);
            vo.setName(tag.getName());
            vo.setColor(tag.getColor());
            vo.setShortcut(tag.getShortcut() != null ? tag.getShortcut() : shortcut++);
            datasetTagService.createDatasetTag(vo);
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) throw new IOException("非法 zip 路径");
                if (entry.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("清理临时目录失败: {}", dir, e);
        }
    }
}
