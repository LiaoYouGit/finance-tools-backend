package com.finance.service;

import com.finance.service.dto.InvoiceItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
public class InvoiceService {

    private static final String UNRECOGNIZED_DIR = "未识别";

    private final String tempDir = System.getProperty("java.io.tmpdir") + "/invoice_parse/";
    private final ConcurrentHashMap<String, File> fileStore = new ConcurrentHashMap<>();

    @Value("${file.cleanup.max-age-hours:12}")
    private int maxAgeHours;

    /**
     * 每小时清理超过保留期的临时文件（上传件、解压目录、归类结果 zip 等）。
     * 目录的最后修改时间在处理完成后不再变化，以其判定是否过期。
     */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 60_000)
    public void cleanupExpiredFiles() {
        long maxAgeMs = maxAgeHours * 3600_000L;
        long now = System.currentTimeMillis();

        File base = new File(tempDir);
        File[] dirs = base.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                String name = dir.getName();
                if (dir.isDirectory() && (name.startsWith("work_") || name.startsWith("classify_"))) {
                    if (now - dir.lastModified() > maxAgeMs) {
                        deleteRecursively(dir);
                    }
                }
            }
        }

        // 同步移除 fileStore 中已不存在或过期的条目，使对应下载 id 失效
        fileStore.entrySet().removeIf(e -> {
            File f = e.getValue();
            return !f.exists() || now - f.lastModified() > maxAgeMs;
        });
    }

    public ParseResult parseInvoices(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString().replace("-", "");
        Path workDir = Path.of(tempDir + "work_" + id + "/");
        Files.createDirectories(workDir);

        File uploaded = workDir.resolve("uploaded").toFile();
        file.transferTo(uploaded);

        List<File> pdfFiles = new ArrayList<>();

        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            File unzipDir = workDir.resolve("unzipped").toFile();
            unzipDir.mkdirs();
            unzipFile(uploaded, unzipDir);
            collectPdfs(unzipDir, pdfFiles);
        } else if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            pdfFiles.add(uploaded);
        } else {
            throw new RuntimeException("仅支持 PDF 或 ZIP 文件");
        }

        List<InvoiceItem> allItems = new ArrayList<>();
        for (File pdf : pdfFiles) {
            allItems.addAll(InvoiceParser.parsePdf(pdf));
        }

        if (allItems.isEmpty()) {
            throw new RuntimeException("未能从文件中解析出任何发票信息");
        }

        File excelFile = ExcelGenerator.generate(allItems, workDir.toString());
        fileStore.put(id, excelFile);

        return new ParseResult(id, allItems.size(), allItems);
    }

    public File getFile(String id) {
        return fileStore.get(id);
    }

    public ClassifyResult classifyInvoices(MultipartFile file, String classifyBy, String dateGranularity) throws IOException {
        if (!List.of("date", "buyer", "seller").contains(classifyBy)) {
            throw new RuntimeException("不支持的归类方式：" + classifyBy);
        }
        if (!List.of("month", "day").contains(dateGranularity)) {
            throw new RuntimeException("不支持的日期粒度：" + dateGranularity);
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        Path workDir = Path.of(tempDir + "classify_" + id + "/");
        Files.createDirectories(workDir);

        File uploaded = workDir.resolve("uploaded").toFile();
        file.transferTo(uploaded);

        List<File> pdfFiles = new ArrayList<>();
        List<File> otherFiles = new ArrayList<>();

        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            File unzipDir = workDir.resolve("unzipped").toFile();
            unzipDir.mkdirs();
            unzipFile(uploaded, unzipDir);
            collectPdfs(unzipDir, pdfFiles);
            collectOtherFiles(unzipDir, otherFiles);
        } else if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            pdfFiles.add(uploaded);
        } else {
            throw new RuntimeException("仅支持 PDF 或 ZIP 文件");
        }

        if (pdfFiles.isEmpty() && otherFiles.isEmpty()) {
            throw new RuntimeException("压缩包内没有找到任何文件");
        }

        File outputDir = workDir.resolve("output").toFile();
        outputDir.mkdirs();

        // 解析放在拷贝之前并行执行，几千个发票文件的场景下可大幅缩短耗时
        Map<File, String> folderByFile = pdfFiles.parallelStream().collect(
                Collectors.toConcurrentMap(pdf -> pdf, pdf -> {
                    String folder = resolveFolderName(classifyBy, dateGranularity, pdf);
                    return folder == null ? UNRECOGNIZED_DIR : folder;
                }));

        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        for (File pdf : pdfFiles) {
            String folder = folderByFile.get(pdf);
            copyWithRename(pdf, new File(outputDir, folder));
            groupCounts.merge(folder, 1, Integer::sum);
        }
        for (File other : otherFiles) {
            copyWithRename(other, new File(outputDir, UNRECOGNIZED_DIR));
            groupCounts.merge(UNRECOGNIZED_DIR, 1, Integer::sum);
        }

        File resultZip = workDir.resolve("发票归类结果.zip").toFile();
        zipDirectory(outputDir, resultZip);
        fileStore.put(id, resultZip);

        List<GroupStat> groups = new ArrayList<>();
        for (Map.Entry<String, Integer> e : groupCounts.entrySet()) {
            groups.add(new GroupStat(e.getKey(), e.getValue()));
        }
        return new ClassifyResult(id, pdfFiles.size() + otherFiles.size(), groups);
    }

    private String resolveFolderName(String classifyBy, String dateGranularity, File pdf) {
        InvoiceItem header;
        try {
            List<InvoiceItem> items = InvoiceParser.parsePdf(pdf);
            header = items.isEmpty() ? null : items.get(0);
        } catch (Exception e) {
            header = null;
        }
        if (header == null) return null;

        switch (classifyBy) {
            case "date": {
                String date = header.getInvoiceDate();
                if (date == null || date.isBlank() || date.length() < 7) return null;
                String folder = "day".equals(dateGranularity) ? date : date.substring(0, 7);
                return sanitizeFolderName(folder);
            }
            case "buyer": {
                String name = header.getBuyerName();
                return (name == null || name.isBlank()) ? null : sanitizeFolderName(name);
            }
            case "seller": {
                String name = header.getSellerName();
                return (name == null || name.isBlank()) ? null : sanitizeFolderName(name);
            }
            default:
                throw new RuntimeException("不支持的归类方式：" + classifyBy);
        }
    }

    private String sanitizeFolderName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        cleaned = cleaned.replaceAll("[. ]+$", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void copyWithRename(File src, File targetDir) throws IOException {
        targetDir.mkdirs();
        String name = src.getName();
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        File dest = new File(targetDir, name);
        int i = 1;
        while (dest.exists()) {
            dest = new File(targetDir, base + "(" + i + ")" + ext);
            i++;
        }
        Files.copy(src.toPath(), dest.toPath());
    }

    private void collectOtherFiles(File dir, List<File> otherFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith("__MACOSX")) {
                    collectOtherFiles(f, otherFiles);
                }
            } else if (!f.getName().toLowerCase().endsWith(".pdf") && !f.getName().startsWith(".")) {
                otherFiles.add(f);
            }
        }
    }

    private void zipDirectory(File folder, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            zipFolderRecursive(folder, "发票归类结果", zos);
        }
    }

    private void zipFolderRecursive(File folder, String baseName, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                zipFolderRecursive(file, baseName + "/" + file.getName(), zos);
            } else {
                ZipEntry entry = new ZipEntry(baseName + "/" + file.getName());
                zos.putNextEntry(entry);
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private void unzipFile(File zipFile, File destDir) throws IOException {
        // Try UTF-8 first, fall back to GBK for Windows-generated ZIPs
        Charset[] charsets = {StandardCharsets.UTF_8, Charset.forName("GBK")};
        Exception lastException = null;

        for (Charset charset : charsets) {
            try (ZipFile zf = new ZipFile(zipFile, charset)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                byte[] buffer = new byte[8192];
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    // 仅替换控制字符，保留中文等非 ASCII 字符（归类工具需要保留原文件名）
                    String safeName = entry.getName().replace('[', '_').replace(']', '_')
                            .replaceAll("[\\x00-\\x1F]", "_");
                    if (safeName.contains("../") || safeName.contains("..\\")) {
                        throw new IOException("ZIP entry outside target directory");
                    }
                    File outFile = new File(destDir, safeName);
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        outFile.getParentFile().mkdirs();
                        try (InputStream is = zf.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
                return; // Success
            } catch (Exception e) {
                lastException = e;
                // Clean up any partial extraction
                if (destDir.exists()) {
                    deleteRecursively(destDir);
                    destDir.mkdirs();
                }
            }
        }
        throw new IOException("Failed to unzip file with any encoding", lastException);
    }

    private void deleteRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteRecursively(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private void collectPdfs(File dir, List<File> pdfFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectPdfs(f, pdfFiles);
            } else if (f.getName().toLowerCase().endsWith(".pdf")) {
                pdfFiles.add(f);
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class ParseResult {
        private String id;
        private int count;
        private List<InvoiceItem> items;
    }

    @Data
    @AllArgsConstructor
    public static class ClassifyResult {
        private String id;
        private int total;
        private List<GroupStat> groups;
    }

    @Data
    @AllArgsConstructor
    public static class GroupStat {
        private String folder;
        private int count;
    }
}
