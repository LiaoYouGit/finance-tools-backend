package com.finance.web;

import com.finance.common.Result;
import com.finance.service.InvoiceService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/invoice")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @PostMapping("/parse")
    public Result<InvoiceService.ParseResult> parseInvoices(@RequestParam("file") MultipartFile file) throws IOException {
        InvoiceService.ParseResult result = invoiceService.parseInvoices(file);
        return Result.ok(result);
    }

    @PostMapping("/debug")
    public Result<String> debugExtractText(@RequestParam("file") MultipartFile file) throws IOException {
        File temp = File.createTempFile("invoice_debug_", ".pdf");
        file.transferTo(temp);
        try (PDDocument doc = Loader.loadPDF(temp)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return Result.ok(text);
        } finally {
            temp.delete();
        }
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadExcel(@RequestParam("id") String id) throws IOException {
        File file = invoiceService.getFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = Files.readAllBytes(file.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encodedName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

        return ResponseEntity.ok().headers(headers).body(content);
    }
}
