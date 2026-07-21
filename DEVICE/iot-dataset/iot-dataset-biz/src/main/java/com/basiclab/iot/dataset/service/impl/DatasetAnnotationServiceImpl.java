package com.basiclab.iot.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.basiclab.iot.common.enums.CommonStatusEnum;
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
import com.basiclab.iot.dataset.service.annotation.DatasetAnnotationParseUtil;
import com.basiclab.iot.dataset.service.annotation.YoloDatasetInspector;
import com.basiclab.iot.dataset.service.annotation.YoloLabelContentBuilder;
import com.basiclab.iot.dataset.service.ImportCancelChecker;
import com.basiclab.iot.dataset.service.ImportCancelledException;
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
import java.util.function.BiConsumer;
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
    private YoloDatasetInspector yoloDatasetInspector;
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
        double trainRatio = nzDecimal(reqVO.getTrainRatio(), 0.7);
        double valRatio = nzDecimal(reqVO.getValRatio(), 0.2);
        double testRatio = nzDecimal(reqVO.getTestRatio(), 0.1);
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

        List<DatasetTagDO> datasetTags = datasetTagService.listTagsByDatasetId(datasetId);
        Map<String, String> shortcutToName = YoloLabelContentBuilder.buildShortcutToName(datasetTags);
        Map<String, String> nameToShortcut = YoloLabelContentBuilder.buildNameToShortcut(datasetTags);
        List<DatasetImageDO> train;
        List<DatasetImageDO> val;
        List<DatasetImageDO> test;
        if (isUsageAllocated(filtered)) {
            train = filtered.stream()
                    .filter(img -> img.getIsTrain() != null && img.getIsTrain() == 1)
                    .collect(Collectors.toList());
            val = filtered.stream()
                    .filter(img -> img.getIsValidation() != null && img.getIsValidation() == 1)
                    .collect(Collectors.toList());
            test = filtered.stream()
                    .filter(img -> img.getIsTest() != null && img.getIsTest() == 1)
                    .collect(Collectors.toList());
        } else {
            Collections.shuffle(filtered);
            int total = filtered.size();
            int trainCount = trainRatio > 0 ? (int) (total * trainRatio) : 0;
            int valCount = valRatio > 0 ? (int) (total * valRatio) : 0;
            int testCount = total - trainCount - valCount;
            train = filtered.subList(0, Math.min(trainCount, filtered.size()));
            val = trainCount < filtered.size()
                    ? filtered.subList(trainCount, Math.min(trainCount + valCount, filtered.size())) : List.of();
            test = trainCount + valCount < filtered.size()
                    ? filtered.subList(trainCount + valCount, Math.min(trainCount + valCount + testCount, filtered.size())) : List.of();
        }

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String zipName = "datasets_" + timestamp + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, StandardCharsets.UTF_8));

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            StringBuilder namesYaml = new StringBuilder("names:\n");
            for (String name : selectedClasses) {
                namesYaml.append("  - ").append(MAPPER.writeValueAsString(name)).append('\n');
            }
            String dataYaml = "train: train/images\nval: val/images\ntest: test/images\n\nnc: "
                    + selectedClasses.size() + '\n' + namesYaml;
            writeZipEntry(zos, "data.yaml", dataYaml.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "classes.txt", String.join("\n", selectedClasses).getBytes(StandardCharsets.UTF_8));

            String prefix = Optional.ofNullable(reqVO.getExportPrefix()).orElse("").trim();
            writeSplit(zos, "train", train, classToId, shortcutToName, nameToShortcut, prefix);
            writeSplit(zos, "val", val, classToId, shortcutToName, nameToShortcut, prefix);
            writeSplit(zos, "test", test, classToId, shortcutToName, nameToShortcut, prefix);
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
        return importImageFolderPath(datasetId, path, ImportCancelChecker.NONE, null);
    }

    @Override
    public DatasetYoloPreflightRespVO preflightYoloPath(Long datasetId, String path) {
        validateDatasetExists(datasetId);
        try {
            return buildYoloPreflight(datasetId, yoloDatasetInspector.inspect(path));
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "YOLO 数据集检查失败: " + e.getMessage());
        }
    }

    @Override
    public DatasetAnnotationImportResultVO importYoloPath(Long datasetId, DatasetYoloImportReqVO reqVO) {
        return importYoloPath(datasetId, reqVO, ImportCancelChecker.NONE, null);
    }

    @Override
    public DatasetAnnotationImportResultVO importCocoPath(Long datasetId, DatasetAnnotationCocoImportReqVO reqVO) {
        return importCocoPath(datasetId, reqVO, ImportCancelChecker.NONE, null);
    }

    @Override
    public DatasetAnnotationImportResultVO importImageFolderPath(Long datasetId, String path,
                                                                 ImportCancelChecker cancelChecker,
                                                                 BiConsumer<Integer, Integer> progressCallback) {
        return importFromLocalRoot(datasetId, path, true, true, cancelChecker, progressCallback);
    }

    @Override
    public DatasetAnnotationImportResultVO importYoloPath(Long datasetId, DatasetYoloImportReqVO reqVO,
                                                          ImportCancelChecker cancelChecker,
                                                          BiConsumer<Integer, Integer> progressCallback) {
        return importInspectedYoloDataset(datasetId, reqVO, cancelChecker, progressCallback);
    }

    @Override
    public DatasetAnnotationImportResultVO importCocoPath(Long datasetId, DatasetAnnotationCocoImportReqVO reqVO,
                                                          ImportCancelChecker cancelChecker,
                                                          BiConsumer<Integer, Integer> progressCallback) {
        validateDatasetExists(datasetId);
        if (cancelChecker == null) {
            cancelChecker = ImportCancelChecker.NONE;
        }
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
            Set<String> knownTags = loadKnownTagNames(datasetId);
            int cocoImages = 0;
            int processed = 0;
            int total = annsByImage.size();
            if (progressCallback != null) {
                progressCallback.accept(processed, total);
            }
            for (Map.Entry<Long, List<JsonNode>> entry : annsByImage.entrySet()) {
                cancelChecker.throwIfCancelled();
                try {
                    String fileName = imageFiles.get(entry.getKey());
                    if (fileName == null) continue;
                    Path imgPath = resolveUnderRoots(imagesRoot, jsonPath.getParent(), fileName);
                    if (imgPath == null || !Files.isRegularFile(imgPath)) continue;
                    byte[] bytes = Files.readAllBytes(imgPath);
                    int[] dim = readImageSize(bytes);
                    String annJson = convertCocoAnns(entry.getValue(), catMap, dim[0], dim[1]);
                    saveImageBytes(datasetId, bytes, Paths.get(fileName).getFileName().toString(), annJson, dim[0], dim[1]);
                    syncTagsFromAnnotations(datasetId, annJson, knownTags);
                    cocoImages++;
                } finally {
                    processed++;
                    if (progressCallback != null) {
                        progressCallback.accept(processed, total);
                    }
                }
            }
            return DatasetAnnotationImportResultVO.builder().imagesCopied(cocoImages).cocoImages(cocoImages).build();
        } catch (ImportCancelledException e) {
            throw e;
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

    private static final int PATH_IMPORT_BATCH_SIZE = 100;

    private DatasetYoloPreflightRespVO buildYoloPreflight(Long datasetId,
                                                           YoloDatasetInspector.Inspection inspection) {
        DatasetYoloPreflightRespVO response = new DatasetYoloPreflightRespVO();
        response.setRootPath(inspection.getRoot().toString());
        response.setImportable(inspection.isImportable());
        response.setImageCount(inspection.getImageCount());
        response.setMatchedLabelCount(inspection.getMatchedLabelCount());
        response.setMissingLabelCount(inspection.getMissingLabelCount());
        response.setInvalidLabelCount(inspection.getInvalidLabelCount());
        response.setDuplicateImageNameCount(inspection.getDuplicateImageNameCount());
        response.setWarnings(new ArrayList<>(inspection.getWarnings()));

        Set<String> existingTags = datasetTagService.listTagsByDatasetId(datasetId).stream()
                .map(DatasetTagDO::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        response.setExistingTags(new ArrayList<>(existingTags));

        List<DatasetYoloPreflightRespVO.ClassInfo> classes = new ArrayList<>();
        for (YoloDatasetInspector.ClassDescriptor descriptor : inspection.getClasses()) {
            DatasetYoloPreflightRespVO.ClassInfo item = new DatasetYoloPreflightRespVO.ClassInfo();
            item.setClassId(descriptor.getClassId());
            item.setDetectedName(descriptor.getDetectedName());
            item.setSuggestedName(descriptor.getSuggestedName());
            item.setManualNameRequired(descriptor.isManualNameRequired());
            item.setExistingTag(descriptor.getDetectedName() != null && existingTags.contains(descriptor.getDetectedName()));
            classes.add(item);
        }
        response.setClasses(classes);

        List<DatasetYoloPreflightRespVO.SplitSummary> splits = new ArrayList<>();
        for (YoloDatasetInspector.Split split : YoloDatasetInspector.Split.values()) {
            YoloDatasetInspector.SplitStats stats = inspection.getSplitStats().get(split);
            if (stats == null || stats.getImageCount() == 0) {
                continue;
            }
            DatasetYoloPreflightRespVO.SplitSummary summary = new DatasetYoloPreflightRespVO.SplitSummary();
            summary.setSplit(split.getApiName());
            summary.setImageCount(stats.getImageCount());
            summary.setMatchedLabelCount(stats.getMatchedLabelCount());
            splits.add(summary);
        }
        response.setSplits(splits);
        return response;
    }

    private DatasetAnnotationImportResultVO importInspectedYoloDataset(
            Long datasetId,
            DatasetYoloImportReqVO reqVO,
            ImportCancelChecker cancelChecker,
            BiConsumer<Integer, Integer> progressCallback) {
        validateDatasetExists(datasetId);
        ImportCancelChecker effectiveCancelChecker = cancelChecker != null ? cancelChecker : ImportCancelChecker.NONE;
        try {
            YoloDatasetInspector.Inspection inspection = yoloDatasetInspector.inspect(reqVO.getPath());
            if (!inspection.isImportable()) {
                throw exception(FILE_UPLOAD_FAILED, "YOLO 数据集预检未通过，请重新检查目录和标注格式");
            }
            if (inspection.getMissingLabelCount() > 0 && !Boolean.TRUE.equals(reqVO.getAllowMissingLabels())) {
                throw exception(FILE_UPLOAD_FAILED,
                        "有 " + inspection.getMissingLabelCount() + " 张图片缺少标注，请在预检后明确确认继续导入");
            }

            LinkedHashMap<Integer, String> classMapping = validateYoloClassMapping(inspection, reqVO.getClassMapping());
            List<String> classNames = new ArrayList<>(classMapping.values());
            int tagsCreated = datasetTagService.ensureTagsForDataset(datasetId, classNames);
            List<DatasetTagDO> availableTagList = datasetTagService.listTagsByDatasetId(datasetId);
            Set<String> availableTags = availableTagList.stream()
                    .map(DatasetTagDO::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<String> unavailableTags = classNames.stream()
                    .filter(name -> !availableTags.contains(name))
                    .collect(Collectors.toList());
            if (!unavailableTags.isEmpty()) {
                throw exception(FILE_UPLOAD_FAILED, "无法创建数据集标签: " + String.join(", ", unavailableTags));
            }
            Map<String, String> nameToShortcut = YoloLabelContentBuilder.buildNameToShortcut(availableTagList);
            LinkedHashMap<Integer, String> classLabelMapping = new LinkedHashMap<>();
            classMapping.forEach((classId, name) ->
                    classLabelMapping.put(classId, nameToShortcut.getOrDefault(name, name)));

            Map<String, DatasetImageDO> existingByName = datasetImageMapper.selectImportIndexByDatasetId(datasetId).stream()
                    .collect(Collectors.toMap(DatasetImageDO::getName, image -> image, (first, ignored) -> first,
                            LinkedHashMap::new));
            List<DatasetImageDO> pendingUpdates = new ArrayList<>(PATH_IMPORT_BATCH_SIZE);
            List<DatasetImageImportItem> pendingImports = new ArrayList<>(PATH_IMPORT_BATCH_SIZE);
            DatasetImageUploadRespVO uploadResult = new DatasetImageUploadRespVO();
            boolean annotationsOnly = DatasetYoloImportReqVO.MODE_ANNOTATIONS_ONLY.equals(reqVO.getImportMode());
            boolean preserveSplits = !Boolean.FALSE.equals(reqVO.getPreserveSplits());
            int processed = 0;
            int annotatedImages = 0;
            int updatedExisting = 0;
            int total = inspection.getImageCount();
            boolean fileImportsQueued = false;

            if (progressCallback != null) {
                progressCallback.accept(0, total);
            }
            logger.info("YOLO 路径导入开始: datasetId={}, path={}, images={}, labels={}, mode={}",
                    datasetId, inspection.getRoot(), total, inspection.getMatchedLabelCount(), reqVO.getImportMode());

            for (YoloDatasetInspector.Sample sample : inspection.getSamples()) {
                effectiveCancelChecker.throwIfCancelled();
                String annotationsJson = sample.hasLabel()
                        ? convertYoloBoxes(sample.getBoxes(), classLabelMapping)
                        : null;
                if (annotationsJson != null) {
                    annotatedImages++;
                }

                String filename = sample.getImagePath().getFileName().toString();
                DatasetImageDO existing = existingByName.get(filename);
                if (annotationsOnly && existing != null) {
                    boolean changed = applyExistingYoloFields(existing, sample, annotationsJson, preserveSplits);
                    if (changed) {
                        pendingUpdates.add(existing);
                        updatedExisting++;
                    }
                } else {
                    byte[] bytes = Files.readAllBytes(sample.getImagePath());
                    int[] dimensions = readImageSize(bytes);
                    DatasetImageImportItem item = buildYoloImportItem(
                            sample, filename, bytes, dimensions, annotationsJson, preserveSplits);
                    pendingImports.add(item);
                    fileImportsQueued = true;
                }

                processed++;
                if (processed % PATH_IMPORT_BATCH_SIZE == 0) {
                    flushYoloImportBatches(datasetId, pendingUpdates, pendingImports, existingByName, uploadResult);
                    if (progressCallback != null) {
                        progressCallback.accept(processed, total);
                    }
                }
            }

            effectiveCancelChecker.throwIfCancelled();
            flushYoloImportBatches(datasetId, pendingUpdates, pendingImports, existingByName, uploadResult);
            if (preserveSplits && fileImportsQueued) {
                existingByName = datasetImageMapper.selectImportIndexByDatasetId(datasetId).stream()
                        .collect(Collectors.toMap(DatasetImageDO::getName, image -> image,
                                (first, ignored) -> first, LinkedHashMap::new));
                restoreYoloSplits(inspection.getSamples(), existingByName);
            }
            updateYoloDatasetState(datasetId, inspection, existingByName, preserveSplits);
            if (progressCallback != null) {
                progressCallback.accept(processed, total);
            }

            int createdImages = Math.max(0, uploadResult.getSuccessCount() - uploadResult.getOverwrittenCount());
            int updatedImages = updatedExisting + uploadResult.getOverwrittenCount();
            String hint = uploadResult.getFailedCount() > 0
                    ? uploadResult.getFailedCount() + " 张图片写入失败"
                    : inspection.getMissingLabelCount() > 0
                    ? inspection.getMissingLabelCount() + " 张图片按未标注状态导入"
                    : null;
            logger.info("YOLO 路径导入完成: datasetId={}, images={}, yolo={}, created={}, updated={}, failed={}",
                    datasetId, processed, annotatedImages, createdImages, updatedImages, uploadResult.getFailedCount());
            return DatasetAnnotationImportResultVO.builder()
                    .imagesCopied(updatedExisting + uploadResult.getSuccessCount())
                    .labelmeImages(0)
                    .cocoImages(0)
                    .yoloImages(annotatedImages)
                    .classes(classNames)
                    .tagsCreated(tagsCreated)
                    .createdImages(createdImages)
                    .updatedImages(updatedImages)
                    .hint(hint)
                    .build();
        } catch (ImportCancelledException e) {
            throw e;
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "YOLO 导入失败: " + e.getMessage());
        }
    }

    private LinkedHashMap<Integer, String> validateYoloClassMapping(
            YoloDatasetInspector.Inspection inspection,
            Map<Integer, String> requestedMapping) {
        Map<Integer, String> source = requestedMapping != null ? requestedMapping : Collections.emptyMap();
        LinkedHashMap<Integer, String> validated = new LinkedHashMap<>();
        Set<String> normalizedNames = new HashSet<>();
        for (YoloDatasetInspector.ClassDescriptor descriptor : inspection.getClasses()) {
            String name = source.get(descriptor.getClassId());
            name = name != null ? name.trim() : "";
            if (name.isEmpty() || name.matches("\\d+")) {
                throw exception(FILE_UPLOAD_FAILED,
                        "类别 " + descriptor.getClassId() + " 必须手动填写非数字标签名称");
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalized)) {
                throw exception(FILE_UPLOAD_FAILED, "多个 YOLO 类别不能映射到同一标签: " + name);
            }
            validated.put(descriptor.getClassId(), name);
        }
        return validated;
    }

    private String convertYoloBoxes(List<YoloDatasetInspector.Box> boxes, Map<Integer, String> classMapping)
            throws IOException {
        List<Map<String, Object>> annotations = new ArrayList<>();
        for (YoloDatasetInspector.Box box : boxes) {
            String label = classMapping.get(box.getClassId());
            if (label == null) {
                throw exception(FILE_UPLOAD_FAILED, "缺少 YOLO 类别映射: " + box.getClassId());
            }
            double x1 = box.getCenterX() - box.getWidth() / 2;
            double y1 = box.getCenterY() - box.getHeight() / 2;
            double x2 = box.getCenterX() + box.getWidth() / 2;
            double y2 = box.getCenterY() + box.getHeight() / 2;
            annotations.add(Map.of(
                    "label", label,
                    "type", "rectangle",
                    "auto", false,
                    "points", List.of(
                            Map.of("x", x1, "y", y1), Map.of("x", x2, "y", y1),
                            Map.of("x", x2, "y", y2), Map.of("x", x1, "y", y2))));
        }
        return annotations.isEmpty() ? null : MAPPER.writeValueAsString(annotations);
    }

    private DatasetImageImportItem buildYoloImportItem(
            YoloDatasetInspector.Sample sample,
            String filename,
            byte[] bytes,
            int[] dimensions,
            String annotationsJson,
            boolean preserveSplits) {
        DatasetImageImportItem item = new DatasetImageImportItem();
        item.setFilename(filename);
        item.setData(bytes);
        item.setAnnotationsJson(annotationsJson);
        item.setWidth(dimensions[0] > 0 ? dimensions[0] : null);
        item.setHeight(dimensions[1] > 0 ? dimensions[1] : null);
        item.setCompleted(annotationsJson != null ? 1 : 0);
        if (preserveSplits) {
            applySplit(item, sample.getSplit());
        }
        return item;
    }

    private boolean applyExistingYoloFields(DatasetImageDO image, YoloDatasetInspector.Sample sample,
                                            String annotationsJson, boolean preserveSplits) {
        boolean changed = false;
        if (sample.hasLabel()) {
            image.setAnnotations(annotationsJson);
            image.setCompleted(annotationsJson != null ? 1 : 0);
            image.setModificationCount(Optional.ofNullable(image.getModificationCount()).orElse(0) + 1);
            image.setLastModified(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            changed = true;
        }
        if (preserveSplits && sample.getSplit() != YoloDatasetInspector.Split.UNASSIGNED) {
            applySplit(image, sample.getSplit());
            changed = true;
        }
        return changed;
    }

    private void applySplit(DatasetImageImportItem item, YoloDatasetInspector.Split split) {
        item.setIsTrain(split == YoloDatasetInspector.Split.TRAIN ? 1 : 0);
        item.setIsValidation(split == YoloDatasetInspector.Split.VALIDATION ? 1 : 0);
        item.setIsTest(split == YoloDatasetInspector.Split.TEST ? 1 : 0);
    }

    private void applySplit(DatasetImageDO image, YoloDatasetInspector.Split split) {
        image.setIsTrain(split == YoloDatasetInspector.Split.TRAIN ? 1 : 0);
        image.setIsValidation(split == YoloDatasetInspector.Split.VALIDATION ? 1 : 0);
        image.setIsTest(split == YoloDatasetInspector.Split.TEST ? 1 : 0);
    }

    private void flushYoloImportBatches(
            Long datasetId,
            List<DatasetImageDO> pendingUpdates,
            List<DatasetImageImportItem> pendingImports,
            Map<String, DatasetImageDO> existingByName,
            DatasetImageUploadRespVO totalUploadResult) {
        if (!pendingUpdates.isEmpty()) {
            datasetImageMapper.updateBatch(pendingUpdates, 500);
            pendingUpdates.clear();
        }
        if (!pendingImports.isEmpty()) {
            DatasetImageUploadRespVO batch = datasetImageService.batchImportImages(
                    datasetId, pendingImports, existingByName);
            totalUploadResult.setSuccessCount(totalUploadResult.getSuccessCount() + batch.getSuccessCount());
            totalUploadResult.setFailedCount(totalUploadResult.getFailedCount() + batch.getFailedCount());
            totalUploadResult.setSkippedCount(totalUploadResult.getSkippedCount() + batch.getSkippedCount());
            totalUploadResult.setOverwrittenCount(totalUploadResult.getOverwrittenCount() + batch.getOverwrittenCount());
            pendingImports.clear();
        }
    }

    private void restoreYoloSplits(List<YoloDatasetInspector.Sample> samples,
                                   Map<String, DatasetImageDO> existingByName) {
        EnumMap<YoloDatasetInspector.Split, List<Long>> idsBySplit =
                new EnumMap<>(YoloDatasetInspector.Split.class);
        for (YoloDatasetInspector.Sample sample : samples) {
            DatasetImageDO image = existingByName.get(sample.getImagePath().getFileName().toString());
            if (image == null || image.getId() == null) {
                continue;
            }
            idsBySplit.computeIfAbsent(sample.getSplit(), ignored -> new ArrayList<>()).add(image.getId());
        }
        for (Map.Entry<YoloDatasetInspector.Split, List<Long>> entry : idsBySplit.entrySet()) {
            YoloDatasetInspector.Split split = entry.getKey();
            List<Long> ids = entry.getValue();
            for (int offset = 0; offset < ids.size(); offset += PATH_IMPORT_BATCH_SIZE) {
                List<Long> batch = ids.subList(offset, Math.min(offset + PATH_IMPORT_BATCH_SIZE, ids.size()));
                datasetImageMapper.batchUpdateUsage(
                        batch,
                        split == YoloDatasetInspector.Split.TRAIN ? 1 : 0,
                        split == YoloDatasetInspector.Split.VALIDATION ? 1 : 0,
                        split == YoloDatasetInspector.Split.TEST ? 1 : 0);
            }
        }
    }

    private void updateYoloDatasetState(Long datasetId, YoloDatasetInspector.Inspection inspection,
                                        Map<String, DatasetImageDO> existingByName, boolean preserveSplits) {
        boolean allImagesAssigned = preserveSplits && inspection.getSamples().stream()
                .allMatch(sample -> sample.getSplit() != YoloDatasetInspector.Split.UNASSIGNED
                        && existingByName.containsKey(sample.getImagePath().getFileName().toString()));
        DatasetDO update = new DatasetDO().setId(datasetId)
                .setIsSyncMinio(CommonStatusEnum.NO.getStatus())
                .setZipUrl(null);
        if (allImagesAssigned) {
            update.setIsAllocated(CommonStatusEnum.YES.getStatus());
        }
        datasetMapper.updateById(update);
    }

    private DatasetAnnotationImportResultVO importFromLocalRoot(Long datasetId, String path, boolean tryLabelmeCoco, boolean tryYolo) {
        return importFromLocalRoot(datasetId, path, tryLabelmeCoco, tryYolo, ImportCancelChecker.NONE, null);
    }

    private DatasetAnnotationImportResultVO importFromLocalRoot(Long datasetId, String path, boolean tryLabelmeCoco, boolean tryYolo,
                                                                ImportCancelChecker cancelChecker,
                                                                BiConsumer<Integer, Integer> progressCallback) {
        validateDatasetExists(datasetId);
        if (cancelChecker == null) {
            cancelChecker = ImportCancelChecker.NONE;
        }
        Path root = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw exception(FILE_UPLOAD_FAILED, "目录不存在: " + path);
        }
        List<String> yoloNames = tryYolo ? loadYoloClassNames(root) : List.of();
        int images = 0, labelme = 0, yolo = 0;
        List<DatasetImageImportItem> batch = new ArrayList<>(PATH_IMPORT_BATCH_SIZE);
        Set<String> knownTags = loadKnownTagNames(datasetId);
        try {
            List<Path> imagePaths = collectImagePaths(root);
            int total = imagePaths.size();
            if (progressCallback != null) {
                progressCallback.accept(images, total);
            }
            logger.info("路径导入开始: datasetId={}, path={}, images={}", datasetId, root, imagePaths.size());
            for (Path imgPath : imagePaths) {
                cancelChecker.throwIfCancelled();
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
                if (annJson != null) syncTagsFromAnnotations(datasetId, annJson, knownTags);

                DatasetImageImportItem item = new DatasetImageImportItem();
                item.setFilename(imgPath.getFileName().toString());
                item.setData(bytes);
                item.setAnnotationsJson(annJson);
                item.setWidth(dim[0] > 0 ? dim[0] : null);
                item.setHeight(dim[1] > 0 ? dim[1] : null);
                item.setCompleted(annJson != null && !annJson.isBlank() ? 1 : 0);
                batch.add(item);

                if (batch.size() >= PATH_IMPORT_BATCH_SIZE) {
                    cancelChecker.throwIfCancelled();
                    datasetImageService.batchImportImages(datasetId, batch);
                    images += batch.size();
                    if (progressCallback != null) {
                        progressCallback.accept(images, total);
                    }
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                cancelChecker.throwIfCancelled();
                datasetImageService.batchImportImages(datasetId, batch);
                images += batch.size();
                if (progressCallback != null) {
                    progressCallback.accept(images, total);
                }
            }
            logger.info("路径导入完成: datasetId={}, images={}, yolo={}, labelme={}", datasetId, images, yolo, labelme);
        } catch (ImportCancelledException e) {
            throw e;
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "路径导入失败: " + e.getMessage());
        }
        return DatasetAnnotationImportResultVO.builder()
                .imagesCopied(images)
                .labelmeImages(labelme)
                .cocoImages(0)
                .yoloImages(yolo)
                .build();
    }

    private List<Path> collectImagePaths(Path root) throws IOException {
        List<Path> imagePaths = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isImageName(file.getFileName().toString())) {
                    imagePaths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return imagePaths;
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
                            Map<String, Integer> classToId,
                            Map<String, String> shortcutToName, Map<String, String> nameToShortcut,
                            String prefix) throws IOException {
        for (DatasetImageDO image : images) {
            try {
                byte[] imgBytes = readImageBytesFromMinio(image.getPath());
                String flat = image.getName().replace("\\", "/").replace("/", "__");
                String imgName = prefix.isEmpty() ? flat : prefix + "_" + flat;
                writeZipEntry(zos, split + "/images/" + imgName, imgBytes);
                String label = buildYoloLabelContent(image, classToId, shortcutToName, nameToShortcut);
                String labelName = stripExt(imgName) + ".txt";
                writeZipEntry(zos, split + "/labels/" + labelName, label.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.warn("导出图片失败: {}", image.getName(), e);
            }
        }
    }

    private boolean isUsageAllocated(List<DatasetImageDO> images) {
        if (images.isEmpty()) return false;
        return images.stream().allMatch(img ->
                (img.getIsTrain() != null && img.getIsTrain() == 1)
                        || (img.getIsValidation() != null && img.getIsValidation() == 1)
                        || (img.getIsTest() != null && img.getIsTest() == 1));
    }

    private String buildYoloLabelContent(DatasetImageDO image, Map<String, Integer> classToId,
                                         Map<String, String> shortcutToName,
                                         Map<String, String> nameToShortcut) {
        return YoloLabelContentBuilder.build(
                image.getAnnotations(),
                classToId,
                shortcutToName,
                nameToShortcut,
                image.getWidth(),
                image.getHeigh());
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

    private static double nzDecimal(java.math.BigDecimal v, double def) {
        return v == null ? def : v.doubleValue();
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
            if (parts.length != 5) {
                throw new IOException("仅支持每行五列的 YOLO 目标检测标注");
            }
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
                    List<String> names = parseYamlNamesBlock(content.substring(namesIdx));
                    if (!names.isEmpty()) return names;
                }
            } catch (IOException ignored) {}
        }
        return List.of();
    }

    private List<String> parseYamlNamesBlock(String namesBlock) {
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher inline = java.util.regex.Pattern
                .compile("names:\\s*\\[([^\\]]*)\\]")
                .matcher(namesBlock);
        if (inline.find()) {
            for (String part : inline.group(1).split(",")) {
                String name = part.trim().replaceAll("^[\"']|[\"']$", "");
                if (!name.isEmpty()) names.add(name);
            }
            if (!names.isEmpty()) return names;
        }
        for (String line : namesBlock.split("\n")) {
            line = line.trim();
            if (line.startsWith("- ")) {
                names.add(line.substring(2).trim().replaceAll("^[\"']|[\"']$", ""));
            }
        }
        return names;
    }

    private Path findYoloTxt(Path root, Path imagePath) {
        String base = stripExt(imagePath.getFileName().toString());
        Path parent = imagePath.getParent();
        Path cursor = parent;
        while (cursor != null) {
            Path name = cursor.getFileName();
            if (name != null && "images".equalsIgnoreCase(name.toString()) && cursor.getParent() != null) {
                Path relativeImage = cursor.relativize(imagePath);
                Path relativeParent = relativeImage.getParent();
                Path relativeLabel = relativeParent == null
                        ? Paths.get(base + ".txt")
                        : relativeParent.resolve(base + ".txt");
                Path label = cursor.getParent().resolve("labels").resolve(relativeLabel);
                if (Files.isRegularFile(label)) return label;
            }
            cursor = cursor.getParent();
        }
        for (String sub : List.of("labels", "train/labels", "valid/labels", "val/labels", "test/labels",
                "labels/train", "labels/valid", "labels/val", "labels/test")) {
            Path p = root.resolve(sub).resolve(base + ".txt");
            if (Files.isRegularFile(p)) return p;
        }
        if (parent != null) {
            Path sibling = parent.resolve(base + ".txt");
            if (Files.isRegularFile(sibling)) return sibling;
        }
        return null;
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
        syncTagsFromAnnotations(datasetId, annJson, loadKnownTagNames(datasetId));
    }

    private Set<String> loadKnownTagNames(Long datasetId) {
        DatasetTagPageReqVO req = new DatasetTagPageReqVO();
        req.setDatasetId(datasetId);
        req.setPageSize(500);
        return datasetTagService.getDatasetTagPage(req).getList().stream()
                .map(DatasetTagDO::getName)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void syncTagsFromAnnotations(Long datasetId, String annJson, Set<String> knownTags) {
        try {
            List<Map<String, Object>> anns = MAPPER.readValue(annJson, new TypeReference<>() {});
            Set<String> names = anns.stream()
                    .map(a -> String.valueOf(a.getOrDefault("label", "")))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            int nextShortcut = knownTags.size() + 1;
            for (String name : names) {
                if (knownTags.contains(name)) continue;
                DatasetTagSaveReqVO tag = new DatasetTagSaveReqVO();
                tag.setDatasetId(datasetId);
                tag.setName(name);
                tag.setColor("#3aa757");
                tag.setShortcut(nextShortcut++);
                datasetTagService.createDatasetTag(tag);
                knownTags.add(name);
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
