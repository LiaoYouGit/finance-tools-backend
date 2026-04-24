package com.finance.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class PdfService {

    private final String tempDir = System.getProperty("java.io.tmpdir") + "/pdfsplit/";
    private final ConcurrentHashMap<String, File> fileStore = new ConcurrentHashMap<>();

    public File splitInvoices(MultipartFile zipFile) throws IOException {
        Path dirPath = Paths.get(tempDir);
        if (Files.exists(dirPath)) {
            FileSystemUtils.deleteRecursively(dirPath.toFile());
        }
        Files.createDirectories(dirPath);

        File uploadedZip = new File(tempDir + zipFile.getOriginalFilename());
        zipFile.transferTo(uploadedZip);

        File unzipDir = new File(tempDir + "unzipped/");
        unzipDir.mkdirs();
        unzipZip(uploadedZip, unzipDir);

        File outputDir = new File(tempDir + "output/");
        outputDir.mkdirs();

        File[] pdfFiles = unzipDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        if (pdfFiles != null) {
            for (File pdf : pdfFiles) {
                splitPdfFile(pdf, outputDir);
            }
        }

        File resultZip = new File(tempDir + "split_invoices.zip");
        zipDirectory(outputDir, resultZip);

        return resultZip;
    }

    public String splitByRatio(MultipartFile file, List<Double> splitLines) throws IOException {
        String id = UUID.randomUUID().toString().replace("-", "");
        Path dirPath = Paths.get(tempDir + "split_ratio_" + id + "/");
        Files.createDirectories(dirPath);

        File uploadedPdf = dirPath.resolve("input.pdf").toFile();
        file.transferTo(uploadedPdf);

        File outputDir = dirPath.resolve("output/").toFile();
        outputDir.mkdirs();

        PDDocument document = Loader.loadPDF(uploadedPdf);
        PDFRenderer renderer = new PDFRenderer(document);

        String baseName = uploadedPdf.getName().replace(".pdf", "");
        int partIndex = 1;

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage pageImage = renderer.renderImageWithDPI(i, 300);
            int height = pageImage.getHeight();

            List<Double> breakpoints = new java.util.ArrayList<>(splitLines);
            breakpoints.add(0, 0.0);
            breakpoints.add(1.0);

            for (int j = 0; j < breakpoints.size() - 1; j++) {
                int yStart = (int) (breakpoints.get(j) * height);
                int yEnd = (int) (breakpoints.get(j + 1) * height);
                int partHeight = yEnd - yStart;
                if (partHeight <= 0) continue;

                BufferedImage partImage = pageImage.getSubimage(0, yStart, pageImage.getWidth(), partHeight);
                saveImageAsPdf(partImage, new File(outputDir, baseName + "_" + partIndex + ".pdf"));
                partIndex++;
            }
        }

        document.close();

        File resultZip = dirPath.resolve(baseName + "_split.zip").toFile();
        zipDirectory(outputDir, resultZip);
        fileStore.put(id, resultZip);
        return id;
    }

    public File getFile(String id) {
        return fileStore.get(id);
    }

    private void splitPdfFile(File pdfFile, File outputDir) throws IOException {
        PDDocument document = Loader.loadPDF(pdfFile);
        PDFRenderer renderer = new PDFRenderer(document);

        int fileIndex = 1;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage pageImage = renderer.renderImageWithDPI(i, 300);

            int width = pageImage.getWidth();
            int height = pageImage.getHeight();

            BufferedImage invoice1 = pageImage.getSubimage(0, 0, width, height / 2);
            BufferedImage invoice2 = pageImage.getSubimage(0, height / 2, width, height / 2);

            saveImageAsPdf(invoice1, new File(outputDir, pdfFile.getName().replace(".pdf", "") + "_" + fileIndex + ".pdf"));
            fileIndex++;
            saveImageAsPdf(invoice2, new File(outputDir, pdfFile.getName().replace(".pdf", "") + "_" + fileIndex + ".pdf"));
            fileIndex++;
        }

        document.close();
    }

    private void saveImageAsPdf(@NotNull BufferedImage image, File outputPdf) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
        doc.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(doc, page);
        contentStream.drawImage(LosslessFactory.createFromImage(doc, image), 0, 0, image.getWidth(), image.getHeight());
        contentStream.close();

        doc.save(outputPdf);
        doc.close();
    }

    private void unzipZip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    Files.copy(zis, outFile.toPath());
                }
            }
        }
    }

    private void zipDirectory(File folder, @NotNull File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            zipFolderRecursive(folder, folder.getName(), zos);
        }
    }

    private void zipFolderRecursive(@NotNull File folder, String baseName, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
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
}
