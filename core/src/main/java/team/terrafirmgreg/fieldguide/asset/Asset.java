package team.terrafirmgreg.fieldguide.asset;

import lombok.Getter;

import java.io.InputStream;

@Getter
public class Asset {
    private final String path;
    private final InputStream inputStream;
    private final AssetSource source;

    public Asset(String path, InputStream inputStream, AssetSource source) {
        this.path = path;
        this.inputStream = inputStream;
        this.source = source;
    }

    @Override
    public String toString() {
        return "Asset{source=" + getSource() + ", resourcePath='" + getPath() + "'}";
    }
}