package com.finance.web;

import com.finance.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/pdf")
public class PdfController {

    @Autowired
    private PdfService pdfService;

    @PostMapping("/splitZip")
    public ResponseEntity<byte[]> splitPdfZip(@RequestParam("file") MultipartFile file) throws IOException {
        // 调用 Service 层处理
        File resultZip = pdfService.splitInvoices(file);

        byte[] content = Files.readAllBytes(resultZip.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", resultZip.getName());

        return ResponseEntity.ok().headers(headers).body(content);
    }
}
