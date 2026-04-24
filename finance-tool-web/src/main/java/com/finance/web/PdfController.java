package com.finance.web;

import com.finance.common.Result;
import com.finance.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/pdf")
public class PdfController {

    @Autowired
    private PdfService pdfService;

    @PostMapping("/splitZip")
    public ResponseEntity<byte[]> splitPdfZip(@RequestParam("file") MultipartFile file) throws IOException {
        File resultZip = pdfService.splitInvoices(file);

        byte[] content = Files.readAllBytes(resultZip.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", resultZip.getName());

        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping("/split")
    public Result<Map<String, String>> splitPdf(@RequestParam("file") MultipartFile file,
                                                @RequestParam("splitLines") String splitLines) throws IOException {
        java.util.List<Double> lines = new ObjectMapper().readValue(splitLines, new TypeReference<>() {});
        String fileId = pdfService.splitByRatio(file, lines);
        return Result.ok(Map.of("id", fileId));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("id") String id) throws IOException {
        File file = pdfService.getFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = Files.readAllBytes(file.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", file.getName());

        return ResponseEntity.ok().headers(headers).body(content);
    }
}
