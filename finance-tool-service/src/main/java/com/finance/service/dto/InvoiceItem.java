package com.finance.service.dto;

import lombok.Data;

@Data
public class InvoiceItem {
    private Integer index;
    private String invoiceType;
    private String invoiceDate;
    private String invoiceNumber;
    private String sellerTaxId;
    private String sellerName;
    private String buyerTaxId;
    private String buyerName;
    private String taxCategory;
    private String itemName;
    private String specModel;
    private String unit;
    private String quantity;
    private String amount;
    private String taxRate;
    private String taxAmount;
    private String totalAmount;
}
