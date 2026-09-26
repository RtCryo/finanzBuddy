package com.finanzbuddy.parser.parser.csv;

import com.opencsv.bean.CsvBindByName;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CsvTransactionRow {

    @CsvBindByName(column = "BookingDate", required = false)
    private String bookingDate;

    @CsvBindByName(column = "ValueDate", required = false)
    private String valueDate;

    @CsvBindByName(column = "Amount", required = false)
    private BigDecimal amount;

    @CsvBindByName(column = "Currency", required = false)
    private String currency;

    @CsvBindByName(column = "Counterparty", required = false)
    private String counterparty;

    @CsvBindByName(column = "Description", required = false)
    private String description;

    @CsvBindByName(column = "Category", required = false)
    private String category;

    public String getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(String bookingDate) {
        this.bookingDate = bookingDate;
    }

    public String getValueDate() {
        return valueDate;
    }

    public void setValueDate(String valueDate) {
        this.valueDate = valueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
