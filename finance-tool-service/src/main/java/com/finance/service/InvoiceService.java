package com.finance.service;

import com.finance.service.dto.InvoiceItem;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class InvoiceService {

    private final String tempDir = System.getProperty("java.io.tmpdir") + "/invoice_parse/";
    private final ConcurrentHashMap<String, File> fileStore = new ConcurrentHashMap<>();

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
                    String safeName = entry.getName().replaceAll("[^\\x00-\\x7F/\\\\]", "_");
                    File outFile = new File(destDir, safeName);
                    if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                        throw new IOException("ZIP entry outside target directory");
                    }
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
}
