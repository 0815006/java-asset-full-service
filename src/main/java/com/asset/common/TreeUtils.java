package com.asset.common;

import com.asset.dto.AssetNodeDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TreeUtils {

    public static List<AssetNodeDTO> buildTree(List<AssetNodeDTO> nodes) {
        Map<Long, AssetNodeDTO> nodeMap = nodes.stream()
                .collect(Collectors.toMap(AssetNodeDTO::getId, node -> node));

        List<AssetNodeDTO> tree = new ArrayList<>();

        for (AssetNodeDTO node : nodes) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0) {
                tree.add(node);
            } else {
                AssetNodeDTO parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return tree;
    }
}
