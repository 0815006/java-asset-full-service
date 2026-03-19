package com.asset.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProductUseRankingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long fileId;
    private String fileName; // Assuming we want to return file name as well
    private String ext; // Assuming we want to return file extension
    private Long useCount;
}
