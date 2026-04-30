package com.finance.service;

import com.finance.service.dto.InvoiceItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelGenerator {

    private static final String[] HEADERS = {
            "序号", "发票票种", "开票日期", "数电发票号码",
            "销方识别号", "销方名称", "购方识别号", "购买方名称",
            "税收大类", "货物或应税劳务名称", "规格型号", "单位",
            "数量", "金额", "税率", "税额", "价税合计"
    };

    public static File generate(List<InvoiceItem> items, String outputDir) throws IOException {
        File dir = new File(outputDir);
        dir.mkdirs();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("发票汇总");

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle dataStyle = createDataStyle(workbook);
            // Track which invoice numbers have already shown totalAmount
            List<String> shownTotal = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                InvoiceItem item = items.get(i);
                item.setIndex(i + 1);
                Row row = sheet.createRow(i + 1);

                // Only show totalAmount on the first row of each invoice
                String displayTotal = "";
                if (item.getTotalAmount() != null && !shownTotal.contains(item.getInvoiceNumber())) {
                    displayTotal = item.getTotalAmount();
                    shownTotal.add(item.getInvoiceNumber());
                }

                String[] values = {
                        String.valueOf(item.getIndex()),
                        item.getInvoiceType(), item.getInvoiceDate(), item.getInvoiceNumber(),
                        item.getSellerTaxId(), item.getSellerName(), item.getBuyerTaxId(), item.getBuyerName(),
                        item.getTaxCategory(), item.getItemName(), item.getSpecModel(), item.getUnit(),
                        item.getQuantity(), item.getAmount(), item.getTaxRate(), item.getTaxAmount(), displayTotal
                };

                for (int j = 0; j < values.length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(values[j] != null ? values[j] : "");
                    cell.setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            File excelFile = new File(dir, "发票汇总_" + System.currentTimeMillis() + ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
            return excelFile;
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
