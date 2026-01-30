package team.terrafirmgreg.fieldguide.asset;

import lombok.Data;

@Data
public class AssetKey {
    private String id;
    private String namespace;
    private String resource;
    private String resourcePath;

    public AssetKey(String id, String resourceType, String resourceRoot, String resourceSuffix) {
        this.id = id;
        int index = id.indexOf(':');
        if (index <= 0) {
            this.namespace = "minecraft";
            this.resource = id;
        } else {
            this.namespace = id.substring(0, index);
            this.resource = id.substring(index + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(resourceRoot).append("/").append(namespace);
        if (resourceType != null && !resourceType.isEmpty()) {
            sb.append("/").append(resourceType);
        }
        sb.append("/").append(resource);
        if (!resource.endsWith(resourceSuffix)) {
            sb.append(resourceSuffix);
        }

        this.resourcePath = sb.toString();
    }
}
