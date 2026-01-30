package team.terrafirmgreg.fieldguide.data.minecraft;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.function.UnaryOperator;

@Data
public class ResourceLocation implements Comparable<ResourceLocation> {
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    private final String namespace;
    private final String path;

    protected ResourceLocation(String namespace, String path, @Nullable Dummy dummy) {
        this.namespace = namespace;
        this.path = path;
    }

    public ResourceLocation(String namespace, String path) {
        this(assertValidNamespace(namespace, path), assertValidPath(namespace, path), (Dummy)null);
    }

    private ResourceLocation(String[] decomposedLocation) {
        this(decomposedLocation[0], decomposedLocation[1]);
    }

    public ResourceLocation(String location) {
        this(decompose(location, ':'));
    }

    public static ResourceLocation of(String location, char separator) {
        return new ResourceLocation(decompose(location, separator));
    }

    protected static String[] decompose(String location, char separator) {
        String[] astring = new String[]{"minecraft", location};
        int i = location.indexOf(separator);
        if (i >= 0) {
            astring[1] = location.substring(i + 1);
            if (i >= 1) {
                astring[0] = location.substring(0, i);
            }
        }

        return astring;
    }


    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public ResourceLocation withPath(String path) {
        return new ResourceLocation(this.namespace, assertValidPath(this.namespace, path), (Dummy)null);
    }

    public ResourceLocation withPath(UnaryOperator<String> pathOperator) {
        return this.withPath((String)pathOperator.apply(this.path));
    }

    public ResourceLocation withPrefix(String pathPrefix) {
        return this.withPath(pathPrefix + this.path);
    }

    public ResourceLocation withSuffix(String pathSuffix) {
        return this.withPath(this.path + pathSuffix);
    }

    public String toString() {
        return this.namespace + ":" + this.path;
    }


    public int compareTo(ResourceLocation other) {
        int i = this.path.compareTo(other.path);
        if (i == 0) {
            i = this.namespace.compareTo(other.namespace);
        }

        return i;
    }

    public int compareNamespaced(ResourceLocation o) {
        int ret = this.namespace.compareTo(o.namespace);
        return ret != 0 ? ret : this.path.compareTo(o.path);
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toLanguageKey(String type) {
        return type + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String type, String key) {
        return type + "." + this.toLanguageKey() + "." + key;
    }


    public static boolean isAllowedInResourceLocation(char character) {
        return character >= '0' && character <= '9' || character >= 'a' && character <= 'z' || character == '_' || character == ':' || character == '/' || character == '.' || character == '-';
    }

    public static boolean isValidPath(String path) {
        for(int i = 0; i < path.length(); ++i) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        for(int i = 0; i < namespace.length(); ++i) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String namespae, String path) {
        return namespae;
    }

    public static boolean validPathChar(char pathChar) {
        return pathChar == '_' || pathChar == '-' || pathChar >= 'a' && pathChar <= 'z' || pathChar >= '0' && pathChar <= '9' || pathChar == '/' || pathChar == '.';
    }

    public static boolean validNamespaceChar(char namespaceChar) {
        return namespaceChar == '_' || namespaceChar == '-' || namespaceChar >= 'a' && namespaceChar <= 'z' || namespaceChar >= '0' && namespaceChar <= '9' || namespaceChar == '.';
    }

    public static boolean isValidResourceLocation(String location) {
        String[] astring = decompose(location, ':');
        return isValidNamespace(StringUtils.isEmpty(astring[0]) ? "minecraft" : astring[0]) && isValidPath(astring[1]);
    }

    private static String assertValidPath(String namespace, String path) {
        return path;
    }

    protected interface Dummy {
    }
}