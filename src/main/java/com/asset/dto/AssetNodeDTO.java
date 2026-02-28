package com.asset.dto;

import com.asset.entity.AssetNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssetNodeDTO extends AssetNode {
    private List<AssetNodeDTO> children = new ArrayList<>();
}
