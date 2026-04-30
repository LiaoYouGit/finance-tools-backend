package com.finance.service;

import com.finance.service.dto.InvoiceItem;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvoiceParser {

    public static List<InvoiceItem> parsePdf(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<InvoiceItem> items = new ArrayList<>();

            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                List<InvoiceItem> pageItems = parseInvoiceText(text);
                items.addAll(pageItems);
            }
            return items;
        }
    }

    private static List<InvoiceItem> parseInvoiceText(String text) {
        String normalized = text.replaceAll("\\r\\n", "\n").replaceAll(" +", " ").trim();

        // --- Parse invoice header (common to all items on this page) ---
        String invoiceType = null;
        String invoiceNumber = null;
        String invoiceDate = null;
        String sellerTaxId = null, sellerName = null;
        String buyerTaxId = null, buyerName = null;
        String totalAmount = null;
        boolean headerFound = false;

        // 发票票种
        Matcher typeMatcher = Pattern.compile(
                "(电子发票（增值税专用发票）|电子发票（普通发票）|电子发票（增值税普通发票）|增值税电子普通发票|增值税电子专用发票|增值税专用发票|增值税普通发票|数电发票|全电发票)"
        ).matcher(normalized);
        if (typeMatcher.find()) {
            invoiceType = typeMatcher.group(1);
            headerFound = true;
        }

        // 发票号码
        Matcher numMatcher = Pattern.compile("\\b(\\d{20})\\b").matcher(normalized);
        if (numMatcher.find()) {
            invoiceNumber = numMatcher.group(1);
            headerFound = true;
        }

        // 开票日期
        Matcher dateMatcher = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(normalized);
        if (dateMatcher.find()) {
            String y = dateMatcher.group(1), m = dateMatcher.group(2), d = dateMatcher.group(3);
            invoiceDate = y + "-" + (m.length() == 1 ? "0" + m : m) + "-" + (d.length() == 1 ? "0" + d : d);
            headerFound = true;
        }

        // 税号
        List<String> taxIds = new ArrayList<>();
        Matcher taxIdMatcher = Pattern.compile("(?<![A-Za-z0-9])([0-9A-Z]{18})(?![A-Za-z0-9])").matcher(normalized);
        while (taxIdMatcher.find()) {
            String found = taxIdMatcher.group(1);
            if (found.matches(".*[A-Z].*") && !taxIds.contains(found)) taxIds.add(found);
        }

        // 公司名
        List<String> companyNames = new ArrayList<>();
        Matcher companyMatcher = Pattern.compile(
                "([\\u4e00-\\u9fff\\(\\)（）、A-Za-z0-9\\-]+?(?:有限公司|股份|公司|厂|店|支队|学校|学院|医院|中心|局|部|处|所))"
        ).matcher(normalized);
        while (companyMatcher.find()) {
            String name = companyMatcher.group(1).trim();
            if (!name.contains("银行") && !name.contains("支行") && !companyNames.contains(name)) {
                companyNames.add(name);
            }
        }

        if (taxIds.size() >= 2) { sellerTaxId = taxIds.get(0); buyerTaxId = taxIds.get(1); headerFound = true; }
        else if (taxIds.size() == 1) {
            int idx = normalized.indexOf(taxIds.get(0));
            if (normalized.indexOf("购买方") >= 0 && idx > normalized.indexOf("购买方")) buyerTaxId = taxIds.get(0);
            else sellerTaxId = taxIds.get(0);
            headerFound = true;
        }

        if (companyNames.size() >= 2) { sellerName = companyNames.get(0); buyerName = companyNames.get(1); headerFound = true; }
        else if (companyNames.size() == 1) { buyerName = companyNames.get(0); headerFound = true; }

        // 价税合计
        String totalSection = normalized.substring(Math.max(0, normalized.indexOf("价税合计")));
        Matcher totalMatcher = Pattern.compile("[¥￥]\\s*(\\d+(?:\\.\\d+)?)").matcher(totalSection);
        if (totalMatcher.find()) { totalAmount = totalMatcher.group(1).trim(); }

        // --- Parse item rows ---
        String itemSection = "";
        int itemStart = normalized.indexOf("项目名称");
        int itemEnd = normalized.indexOf("价税合计");
        if (itemStart >= 0 && itemEnd > itemStart) itemSection = normalized.substring(itemStart, itemEnd);
        else if (itemStart >= 0) itemSection = normalized.substring(itemStart);

        List<InvoiceItem> result = new ArrayList<>();

        if (!itemSection.isEmpty()) {
            String[] lines = itemSection.split("\n");
            StringBuilder currentName = new StringBuilder();
            boolean inItem = false;

            // Per-item accumulator
            String curCategory = "";
            String curSpec = "";
            String curUnit = "";
            String curQuantity = "";
            String curAmount = "";
            String curTaxRate = "";
            String curTaxAmount = "";

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("项目名称")) continue;

                if (line.startsWith("*")) {
                    // Flush previous item
                    if (inItem && currentName.length() > 0) {
                        result.add(buildItem(invoiceType, invoiceDate, invoiceNumber,
                                sellerTaxId, sellerName, buyerTaxId, buyerName, totalAmount,
                                curCategory, currentName.toString().trim(), curSpec,
                                curUnit, curQuantity, curAmount, curTaxRate, curTaxAmount));
                    }

                    inItem = true;
                    currentName = new StringBuilder();
                    curCategory = ""; curSpec = ""; curUnit = "";
                    curQuantity = ""; curAmount = ""; curTaxRate = ""; curTaxAmount = "";

                    // Try match with unit character
                    Matcher rowMatcher = Pattern.compile(
                            "\\*([^*]+)\\*(.+)\\s+([箱盒本个张件台套只支把瓶袋卷根条块片副对双组筒公斤千克吨])\\s+(\\d+(?:\\.\\d+)?)\\s+\\d+(?:\\.\\d+)?\\s+(\\d+(?:\\.\\d+)?)\\s+([\\d.]+%)\\s+(\\d+(?:\\.\\d+)?)"
                    ).matcher(line);

                    if (rowMatcher.find()) {
                        curCategory = rowMatcher.group(1);
                        curUnit = rowMatcher.group(3);
                        curQuantity = rowMatcher.group(4);
                        curAmount = rowMatcher.group(5);
                        curTaxRate = rowMatcher.group(6);
                        curTaxAmount = rowMatcher.group(7);

                        String nameAndSpec = rowMatcher.group(2).trim();
                        String[] tokens = nameAndSpec.split("\\s+");
                        if (tokens.length >= 2) {
                            String lastToken = tokens[tokens.length - 1];
                            if (!lastToken.matches(".*[\\u4e00-\\u9fff].*")) {
                                curSpec = lastToken;
                                StringBuilder nameBuilder = new StringBuilder();
                                for (int t = 0; t < tokens.length - 1; t++) {
                                    if (t > 0) nameBuilder.append(" ");
                                    nameBuilder.append(tokens[t]);
                                }
                                currentName.append(nameBuilder);
                            } else {
                                currentName.append(nameAndSpec);
                            }
                        } else {
                            currentName.append(nameAndSpec);
                        }
                    } else {
                        Matcher rowNoUnit = Pattern.compile(
                                "\\*([^*]+)\\*(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s+\\d+(?:\\.\\d+)?\\s+(\\d+(?:\\.\\d+)?)\\s+([\\d.]+%)\\s+(\\d+(?:\\.\\d+)?)"
                        ).matcher(line);
                        if (rowNoUnit.find()) {
                            curCategory = rowNoUnit.group(1);
                            curQuantity = rowNoUnit.group(3);
                            curAmount = rowNoUnit.group(4);
                            curTaxRate = rowNoUnit.group(5);
                            curTaxAmount = rowNoUnit.group(6);
                            currentName.append(rowNoUnit.group(2).trim());
                        }
                    }
                } else if (inItem) {
                    if (!line.matches("^[\\d.]+$") && !line.matches("^[\\d.]+%$") && !line.matches("^/\\d+$")) {
                        currentName.append(line.trim());
                    }
                }
            }

            // Flush last item
            if (inItem && currentName.length() > 0) {
                result.add(buildItem(invoiceType, invoiceDate, invoiceNumber,
                        sellerTaxId, sellerName, buyerTaxId, buyerName, totalAmount,
                        curCategory, currentName.toString().trim(), curSpec,
                        curUnit, curQuantity, curAmount, curTaxRate, curTaxAmount));
            }
        }

        // If no items parsed but header was found, still return one row with header info
        if (result.isEmpty() && headerFound) {
            result.add(buildItem(invoiceType, invoiceDate, invoiceNumber,
                    sellerTaxId, sellerName, buyerTaxId, buyerName, totalAmount,
                    null, null, null, null, null, null, null, null));
        }

        return result;
    }

    private static InvoiceItem buildItem(String invoiceType, String invoiceDate, String invoiceNumber,
                                           String sellerTaxId, String sellerName, String buyerTaxId, String buyerName,
                                           String totalAmount, String taxCategory, String itemName, String specModel,
                                           String unit, String quantity, String amount, String taxRate, String taxAmount) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoiceType(invoiceType);
        item.setInvoiceDate(invoiceDate);
        item.setInvoiceNumber(invoiceNumber);
        item.setSellerTaxId(sellerTaxId);
        item.setSellerName(sellerName);
        item.setBuyerTaxId(buyerTaxId);
        item.setBuyerName(buyerName);
        item.setTotalAmount(totalAmount);
        item.setTaxCategory(taxCategory);
        item.setItemName(itemName);
        item.setSpecModel(specModel);
        item.setUnit(unit);
        item.setQuantity(quantity);
        item.setAmount(amount);
        item.setTaxRate(taxRate);
        item.setTaxAmount(taxAmount);
        return item;
    }
}
