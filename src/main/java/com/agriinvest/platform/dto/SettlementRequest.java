package com.agriinvest.platform.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class SettlementRequest {
    @JsonAlias({"finalRevenue"})
    private Double totalRevenue;

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(Double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
}
