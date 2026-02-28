package com.asset.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BusiProductDTO {
    private Long id;
    private String name;
    private Long teamId;
    private String teamName;
    private Long domainId;
    private String domainName;
    private Long ownerId;
    private String ownerName; // Assuming we might want owner name too, though not explicitly asked, good practice.
    private Integer assetCount;
    private String status;
    private LocalDateTime lastUpdate;
    private LocalDateTime createdAt;
}
