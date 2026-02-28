package com.asset.dto;

import lombok.Data;

@Data
public class ProductQueryDTO {
    private String keyword;
    private Long teamId;
    private Long domainId;
    private String status;
    private Integer page = 1;
    private Integer size = 10;
}
