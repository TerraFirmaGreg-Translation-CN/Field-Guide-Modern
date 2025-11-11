package io.github.tfgcn.fieldguide.asset;

import lombok.Data;

@Data
public class AssetKey {
    private String id;
    private String namespace;
    private String path;
    private String resourcePath;

    public AssetKey(String id, String resourceType, String resourceRoot, String resourceSuffix) {
        this.id = id;
        int index = id.indexOf(':');
        if (index <= 0) {
            this.namespace = "minecraft";
            this.path = id;
        } else {
            this.namespace = id.substring(0, index);
            this.path = id.substring(index + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(resourceRoot).append("/").append(namespace);
        if (resourceType != null && !resourceType.isEmpty()) {
            sb.append("/").append(resourceType);
        }
        sb.append("/").append(path);
        if (!path.endsWith(resourceSuffix)) {
            sb.append(resourceSuffix);
        }

        this.resourcePath = sb.toString();
    }
}
