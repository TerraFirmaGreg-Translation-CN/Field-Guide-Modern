package io.github.tfgcn.fieldguide.export;

import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.RenderState;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.math.Transform;
import io.github.tfgcn.fieldguide.render3d.math.Vector2f;
import io.github.tfgcn.fieldguide.render3d.math.Vector3f;
import io.github.tfgcn.fieldguide.render3d.scene.Geometry;
import io.github.tfgcn.fieldguide.render3d.scene.Mesh;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import io.github.tfgcn.fieldguide.render3d.scene.Vertex;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedMaterial;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedTexture;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * GlTF 2.0格式导出器
 * 只支持GLB二进制格式，JSON和二进制数据在同一个文件中
 */
@Slf4j
public class GlTFExporter {

    // GLB文件头常量
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int GLB_VERSION = 2;
    private static final int GLB_JSON_CHUNK_TYPE = 0x4E4F534A; // "JSON"
    private static final int GLB_BIN_CHUNK_TYPE = 0x004E4942; // "BIN\0"

    // 数据访问器类型
    private static final String ACCESSOR_TYPE_SCALAR = "SCALAR";
    private static final String ACCESSOR_TYPE_VEC3 = "VEC3";
    private static final String ACCESSOR_TYPE_VEC2 = "VEC2";

    // 组件类型
    private static final int COMPONENT_TYPE_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_TYPE_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;
    private static final int COMPONENT_TYPE_FLOAT = 5126;

    // 缓冲区视图目标
    private static final int TARGET_ARRAY_BUFFER = 34962;
    private static final int TARGET_ELEMENT_ARRAY_BUFFER = 34963;

    // 材质Alpha模式
    private static final String ALPHA_MODE_OPAQUE = "OPAQUE";
    private static final String ALPHA_MODE_BLEND = "BLEND";
    private static final String ALPHA_MODE_MASK = "MASK";
    
    // 纹理过滤模式
    private static final int MAG_FILTER_NEAREST = 9728;
    private static final int MAG_FILTER_LINEAR = 9729;
    private static final int MIN_FILTER_NEAREST = 9728;
    private static final int MIN_FILTER_LINEAR = 9729;
    private static final int MIN_FILTER_NEAREST_MIPMAP_NEAREST = 9984;
    private static final int MIN_FILTER_LINEAR_MIPMAP_NEAREST = 9985;
    private static final int MIN_FILTER_NEAREST_MIPMAP_LINEAR = 9986;
    private static final int MIN_FILTER_LINEAR_MIPMAP_LINEAR = 9987;
    
    // 纹理包装模式
    private static final int WRAP_REPEAT = 10497;
    private static final int WRAP_CLAMP_TO_EDGE = 33071;
    private static final int WRAP_MIRRORED_REPEAT = 33648;

    /**
     * GLTF数据结构
     */
    private Map<String, Object> gltf;
    private List<Map<String, Object>> accessors;
    private List<Map<String, Object>> bufferViews;
    private List<Map<String, Object>> buffers;
    private List<Map<String, Object>> meshes;
    private List<Map<String, Object>> materials;
    private List<Map<String, Object>> textures;
    private List<Map<String, Object>> samplers;
    private List<Map<String, Object>> images;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> scenes;
    private List<Map<String, Object>> animations;

    // 二进制数据
    private ByteArrayOutputStream binaryData;
    private Map<Material, Integer> materialIndexMap;
    private Map<Texture, Integer> imageIndexMap;


    /**
     * 导出节点树为GLB文件
     */
    public void export(Node rootNode, String filePath) throws IOException {
        export(rootNode, filePath, "model");
    }

    /**
     * 导出节点树为GLB文件
     */
    public void export(Node rootNode, String filePath, String modelName) throws IOException {
        reset();

        List<Geometry> geometries = rootNode.getGeometryList(null);
        
        // 处理所有几何体
        processGeometries(geometries);
        
        // 构建场景结构
        buildSceneStructure(geometries, modelName);
        
        // 写入GLB文件
        writeGlbFile(filePath);

        log.info("成功导出GLB文件: {}, 包含 {} 个几何体", filePath, geometries.size());
    }

    private void reset() {
        gltf = new LinkedHashMap<>();
        accessors = new ArrayList<>();
        bufferViews = new ArrayList<>();
        buffers = new ArrayList<>();
        meshes = new ArrayList<>();
        materials = new ArrayList<>();
        textures = new ArrayList<>();
        samplers = new ArrayList<>();
        images = new ArrayList<>();
        nodes = new ArrayList<>();
        scenes = new ArrayList<>();
        animations = new ArrayList<>();
        binaryData = new ByteArrayOutputStream();
        materialIndexMap = new HashMap<>();
        imageIndexMap = new HashMap<>();

    }

    private void processGeometries(List<Geometry> geometries) throws IOException {
        for (Geometry geometry : geometries) {
            processGeometry(geometry);
        }
    }

    private void processGeometry(Geometry geometry) throws IOException {
        Mesh mesh = geometry.getMesh();
        if (mesh == null) return;

        Vertex[] vertices = mesh.getVertexes();
        int[] indices = mesh.getIndexes();

        if (vertices == null || vertices.length == 0 || indices == null || indices.length == 0) {
            return;
        }

        // 获取几何体的世界变换
        Transform transform = geometry.getWorldTransform();

        // 提取顶点数据并应用变换
        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();

        for (Vertex vertex : vertices) {
            // 应用位置变换
            Vector3f pos = transform.transformVector(vertex.position, null);
            positions.add(pos.x);
            positions.add(pos.y);
            positions.add(pos.z);

            // 应用法线变换
            Vector3f norm = transform.transformNormal(vertex.normal, null);
            normals.add(norm.x);
            normals.add(norm.y);
            normals.add(norm.z);

            // 纹理坐标保持不变（Transform不包含UV变换）
            Vector2f uv = vertex.texCoord;
            texCoords.add(uv.x);
            texCoords.add(1.0f - uv.y); // GLTF使用不同的V坐标系
        }

        // 创建访问器和缓冲区视图
        int positionAccessor = createFloatAccessor(positions, ACCESSOR_TYPE_VEC3);
        int normalAccessor = createFloatAccessor(normals, ACCESSOR_TYPE_VEC3);
        int texCoordAccessor = createFloatAccessor(texCoords, ACCESSOR_TYPE_VEC2);
        int indexAccessor = createUnsignedIntAccessor(indices);

        // 处理材质
        int materialIndex = processMaterial(geometry.getMaterial());

        // 创建网格
        Map<String, Object> gltfMesh = new LinkedHashMap<>();
        List<Map<String, Object>> primitives = new ArrayList<>();
        
        Map<String, Object> primitive = new LinkedHashMap<>();
        primitive.put("attributes", createAttributesMap(positionAccessor, normalAccessor, texCoordAccessor));
        primitive.put("indices", indexAccessor);
        primitive.put("material", materialIndex);
        primitive.put("mode", 4); // TRIANGLES

        primitives.add(primitive);
        gltfMesh.put("primitives", primitives);
        meshes.add(gltfMesh);
    }

    private Map<String, Object> createAttributesMap(int positionAccessor, int normalAccessor, int texCoordAccessor) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("POSITION", positionAccessor);
        attributes.put("NORMAL", normalAccessor);
        attributes.put("TEXCOORD_0", texCoordAccessor);
        return attributes;
    }

    private int createFloatAccessor(List<Float> data, String type) throws IOException {
        // 创建缓冲区视图
        int bufferView = createFloatBufferView(data);
        
        // 创建访问器
        Map<String, Object> accessor = new LinkedHashMap<>();
        accessor.put("bufferView", bufferView);
        accessor.put("componentType", COMPONENT_TYPE_FLOAT);
        accessor.put("count", data.size() / getComponentCount(type));
        accessor.put("type", type);
        accessor.put("min", getMinValues(data, type));
        accessor.put("max", getMaxValues(data, type));

        int index = accessors.size();
        accessors.add(accessor);
        return index;
    }

    private int createUnsignedIntAccessor(int[] data) throws IOException {
        // 创建缓冲区视图
        int bufferView = createIntBufferView(data);
        
        // 创建访问器
        Map<String, Object> accessor = new LinkedHashMap<>();
        accessor.put("bufferView", bufferView);
        accessor.put("componentType", COMPONENT_TYPE_UNSIGNED_INT);
        accessor.put("count", data.length);
        accessor.put("type", ACCESSOR_TYPE_SCALAR);
        accessor.put("min", getMinValues(data));
        accessor.put("max", getMaxValues(data));

        int index = accessors.size();
        accessors.add(accessor);
        return index;
    }

    private int createFloatBufferView(List<Float> data) throws IOException {
        byte[] bytes = new byte[data.size() * 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (Float f : data) {
            buffer.putFloat(f);
        }

        return createBufferView(bytes, TARGET_ARRAY_BUFFER);
    }

    private int createIntBufferView(int[] data) throws IOException {
        byte[] bytes = new byte[data.length * 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i : data) {
            buffer.putInt(i);
        }

        return createBufferView(bytes, TARGET_ELEMENT_ARRAY_BUFFER);
    }

    private int createBufferView(byte[] data, int target) throws IOException {
        // 确保4字节对齐
        int currentOffset = binaryData.size();
        int padding = (4 - (currentOffset % 4)) % 4;
        
        // 添加填充字节
        for (int i = 0; i < padding; i++) {
            binaryData.write(0);
        }
        
        // 添加到二进制数据
        int offset = binaryData.size();
        binaryData.write(data);

        // 创建缓冲区视图
        Map<String, Object> bufferView = new LinkedHashMap<>();
        bufferView.put("buffer", 0); // 使用第一个缓冲区
        bufferView.put("byteOffset", offset);
        bufferView.put("byteLength", data.length);
        if (target != 0) {
            bufferView.put("target", target);
        }

        int index = bufferViews.size();
        bufferViews.add(bufferView);
        return index;
    }

    private int processMaterial(Material material) throws IOException {
        if (material == null) {
            // 创建默认材质
            return createDefaultMaterial();
        }

        // 检查是否已经处理过这个材质
        Integer existingIndex = materialIndexMap.get(material);
        if (existingIndex != null) {
            return existingIndex;
        }

        // 创建新材质
        Map<String, Object> gltfMaterial = createGltfMaterial(material);
        int index = materials.size();
        materials.add(gltfMaterial);
        materialIndexMap.put(material, index);

        return index;
    }
    
    /**
     * 处理动画纹理
     * 由于 glTF 2.0 标准不支持材质动画，我们只导出第一帧作为静态纹理
     */
    private void processAnimatedTexture(AnimatedTexture animatedTexture) throws IOException {
        if (!animatedTexture.isAnimated()) {
            return;
        }
        
        log.info("Processing animated texture: {} with {} frames (exporting first frame only)", 
            animatedTexture.getTexturePath(), animatedTexture.getFrameCount());
        
        // 只导出第一帧作为静态纹理
        BufferedImage firstFrame = animatedTexture.getFrames().get(0);
        String frameName = animatedTexture.getTexturePath() + "_first_frame";
        
        // 为第一帧创建一个纹理
        int imageIndex = processImageFromBufferedImage(firstFrame, frameName);
        
        // 创建纹理引用
        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("name", frameName);
        texture.put("source", imageIndex);
        texture.put("sampler", createNearestSampler());
        
        textures.add(texture);
        
        log.info("Exported first frame of animated texture: {}", frameName);
    }
    

    
    /**
     * 添加缓冲区视图（辅助方法）
     */
    private int addBufferView(byte[] data, String name) throws IOException {
        return createBufferView(data, 0); // target=0 for generic data
    }
    
    /**
     * 从BufferedImage处理图像
     */
    private int processImageFromBufferedImage(BufferedImage image, String imageName) throws IOException {
        if (imageIndexMap.containsKey(imageName)) {
            return imageIndexMap.get(imageName);
        }
        
        // 转换图像为PNG字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageData = baos.toByteArray();
        
        // 添加到缓冲区
        int bufferViewIndex = addBufferView(imageData, imageName + "_data");
        
        // 创建图像
        Map<String, Object> gltfImage = new LinkedHashMap<>();
        gltfImage.put("name", imageName);
        gltfImage.put("bufferView", bufferViewIndex);
        gltfImage.put("mimeType", "image/png");
        
        int imageIndex = images.size();
        images.add(gltfImage);
        
        return imageIndex;
    }

    private int createDefaultMaterial() {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("name", "default");
        
        Map<String, Object> pbr = new LinkedHashMap<>();
        pbr.put("baseColorFactor", new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        pbr.put("metallicFactor", 0.0f);
        pbr.put("roughnessFactor", 1.0f);
        material.put("pbrMetallicRoughness", pbr);
        
        material.put("alphaMode", ALPHA_MODE_OPAQUE);

        int index = materials.size();
        materials.add(material);
        return index;
    }

    private Map<String, Object> createGltfMaterial(Material material) throws IOException {
        Map<String, Object> gltfMaterial = new LinkedHashMap<>();
        
        // 处理纹理
        Texture diffuseTexture = material.getDiffuseMap();
        if (diffuseTexture != null) {
            int textureIndex = processTexture(diffuseTexture);
            
            Map<String, Object> pbr = new LinkedHashMap<>();
            Map<String, Object> baseColorTexture = new LinkedHashMap<>();
            baseColorTexture.put("index", textureIndex);
            baseColorTexture.put("texCoord", 0);
            pbr.put("baseColorTexture", baseColorTexture);
            
            // 检查透明度和混合模式
            String alphaMode = ALPHA_MODE_OPAQUE;
            float alpha = 1.0f;
            
            if (material.getRenderState() != null) {
                RenderState.BlendMode blendMode = material.getRenderState().getBlendMode();
                
                // 检查是否启用了Alpha测试
                if (material.getRenderState().isAlphaTest()) {
                    alphaMode = ALPHA_MODE_MASK;
                    alpha = 0.8f;
                    gltfMaterial.put("alphaCutoff", material.getRenderState().getAlphaFalloff());
                } else if (blendMode == RenderState.BlendMode.ALPHA_BLEND || blendMode == RenderState.BlendMode.ADD) {
                    // 检查混合模式
                    alphaMode = ALPHA_MODE_BLEND;
                    alpha = 0.8f;
                }
            }

            pbr.put("baseColorFactor", new float[]{1.0f, 1.0f, 1.0f, alpha});
            gltfMaterial.put("alphaMode", alphaMode);
            
            // 使用材质的漫反射颜色作为基础颜色
            if (material.getDiffuse() != null) {
                pbr.put("baseColorFactor", new float[]{
                    material.getDiffuse().x,
                    material.getDiffuse().y, 
                    material.getDiffuse().z,
                    alpha
                });
            } else {
                pbr.put("baseColorFactor", new float[]{1.0f, 1.0f, 1.0f, alpha});
            }
            
            // 设置光泽度转换为粗糙度（光泽度越高，粗糙度越低）
            float shininess = material.getShininess();
            float roughness = Math.max(0.0f, 1.0f - (shininess / 128.0f));
            pbr.put("roughnessFactor", roughness);
            pbr.put("metallicFactor", 0.0f); // Minecraft材质通常非金属
            
            gltfMaterial.put("pbrMetallicRoughness", pbr);
            
            // 添加双面渲染支持
            if (material.getRenderState() != null) {
                RenderState.CullMode cullMode = material.getRenderState().getCullMode();
                if (cullMode == RenderState.CullMode.NEVER || cullMode == RenderState.CullMode.ALWAYS) {
                    gltfMaterial.put("doubleSided", true);
                }
            }
        } else {
            // 无纹理材质，使用材质的漫反射颜色
            Map<String, Object> pbr = new LinkedHashMap<>();
            
            if (material.getDiffuse() != null) {
                pbr.put("baseColorFactor", new float[]{
                    material.getDiffuse().x,
                    material.getDiffuse().y, 
                    material.getDiffuse().z, 
                    material.getDiffuse().w
                });
            } else {
                pbr.put("baseColorFactor", new float[]{0.8f, 0.8f, 0.8f, 1.0f});
            }
            
            // 设置光泽度转换为粗糙度
            float shininess = material.getShininess();
            float roughness = Math.max(0.0f, 1.0f - (shininess / 128.0f));
            pbr.put("roughnessFactor", roughness);
            pbr.put("metallicFactor", 0.0f);
            
            gltfMaterial.put("pbrMetallicRoughness", pbr);
            gltfMaterial.put("alphaMode", ALPHA_MODE_OPAQUE);
        }

        return gltfMaterial;
    }

    private int processTexture(Texture texture) throws IOException {
        log.info("处理纹理: {}", texture.getName());
        
        // 检查是否已经处理过这个纹理
        Integer existingIndex = imageIndexMap.get(texture);
        if (existingIndex != null) {
            log.info("纹理已存在，复用图像索引: {}", existingIndex);
            // 创建纹理引用（指向现有图像）
            Map<String, Object> gltfTexture = new LinkedHashMap<>();
            gltfTexture.put("source", existingIndex);
            gltfTexture.put("sampler", createNearestSampler());
            
            int index = textures.size();
            textures.add(gltfTexture);
            return index;
        }
        
        // 从Texture创建PNG数据
        byte[] pngData = createPNGFromTexture(texture);
        log.info("生成的PNG数据大小: {} bytes", pngData.length);
        
        // 添加到二进制数据缓冲区
        int imageBufferView = createBufferView(pngData, 0); // target=0 for images
        
        // 创建图像对象
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("name", texture.getName() != null ? texture.getName() : "texture_" + images.size());
        image.put("bufferView", imageBufferView);
        image.put("mimeType", "image/png");
        
        int imageIndex = images.size();
        images.add(image);
        imageIndexMap.put(texture, imageIndex);
        
        // 创建纹理
        int textureIndex = textures.size();
        Map<String, Object> gltfTexture = new LinkedHashMap<>();
        gltfTexture.put("source", imageIndex);
        gltfTexture.put("sampler", createNearestSampler());
        textures.add(gltfTexture);
        
        log.info("创建新纹理，图像索引: {}, 纹理索引: {}", imageIndex, textureIndex);
        return textureIndex;
    }
    
    /**
     * 创建最近邻过滤的采样器（适合像素纹理）
     */
    private int createNearestSampler() {
        // 检查是否已经创建了最近邻采样器
        for (int i = 0; i < samplers.size(); i++) {
            Map<String, Object> sampler = samplers.get(i);
            if (MAG_FILTER_NEAREST == (Integer) sampler.get("magFilter") &&
                MIN_FILTER_NEAREST_MIPMAP_NEAREST == (Integer) sampler.get("minFilter")) {
                return i;
            }
        }
        
        // 创建新的最近邻采样器
        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("magFilter", MAG_FILTER_NEAREST);
        sampler.put("minFilter", MIN_FILTER_NEAREST_MIPMAP_NEAREST);
        sampler.put("wrapS", WRAP_CLAMP_TO_EDGE);
        sampler.put("wrapT", WRAP_CLAMP_TO_EDGE);
        
        int index = samplers.size();
        samplers.add(sampler);
        return index;
    }
    
    /**
     * 创建线性过滤的采样器
     */
    private int createLinearSampler() {
        // 检查是否已经创建了线性采样器
        for (int i = 0; i < samplers.size(); i++) {
            Map<String, Object> sampler = samplers.get(i);
            if (MAG_FILTER_LINEAR == (Integer) sampler.get("magFilter") &&
                MIN_FILTER_LINEAR_MIPMAP_LINEAR == (Integer) sampler.get("minFilter")) {
                return i;
            }
        }
        
        // 创建新的线性采样器
        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("magFilter", MAG_FILTER_LINEAR);
        sampler.put("minFilter", MIN_FILTER_LINEAR_MIPMAP_LINEAR);
        sampler.put("wrapS", WRAP_REPEAT);
        sampler.put("wrapT", WRAP_REPEAT);
        
        int index = samplers.size();
        samplers.add(sampler);
        return index;
    }

    private void buildSceneStructure(List<Geometry> geometries, String modelName) {
        // 创建缓冲区
        Map<String, Object> buffer = new LinkedHashMap<>();
        buffer.put("byteLength", binaryData.size());
        buffers.add(buffer);

        // 创建节点
        for (int i = 0; i < geometries.size(); i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", modelName + "_" + i);
            node.put("mesh", i);
            nodes.add(node);
        }

        // 创建场景
        Map<String, Object> scene = new LinkedHashMap<>();
        List<Integer> sceneNodes = new ArrayList<>();
        for (int i = 0; i < geometries.size(); i++) {
            sceneNodes.add(i);
        }
        scene.put("nodes", sceneNodes);
        scenes.add(scene);

        // 构建主要GLTF结构
        gltf.put("asset", createAsset());
        gltf.put("accessors", accessors);
        gltf.put("bufferViews", bufferViews);
        gltf.put("buffers", buffers);
        gltf.put("meshes", meshes);
        gltf.put("materials", materials);
        gltf.put("textures", textures);
        gltf.put("samplers", samplers);
        gltf.put("images", images);
        gltf.put("nodes", nodes);
        gltf.put("scenes", scenes);
        gltf.put("scene", 0);
        
        // 添加动画数据（如果有）
        if (!animations.isEmpty()) {
            gltf.put("animations", animations);
            log.info("Added {} animations to glTF", animations.size());
        }
    }

    private Map<String, Object> createAsset() {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("version", "2.0");
        asset.put("generator", "FieldGuide");
        return asset;
    }

    private void writeGlbFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());

        // 创建JSON字符串
        String json = mapToJson(gltf);
        byte[] jsonData = json.getBytes(StandardCharsets.UTF_8);

        // 填充JSON到4字节对齐
        int jsonPadding = (4 - (jsonData.length % 4)) % 4;
        byte[] paddedJsonData = new byte[jsonData.length + jsonPadding];
        System.arraycopy(jsonData, 0, paddedJsonData, 0, jsonData.length);
        for (int i = 0; i < jsonPadding; i++) {
            paddedJsonData[jsonData.length + i] = 32; // 空格字符
        }

        // 填充二进制数据到4字节对齐
        int binPadding = (4 - (binaryData.size() % 4)) % 4;
        for (int i = 0; i < binPadding; i++) {
            binaryData.write(0);
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            
            // GLB文件头 (12字节)
            header.putInt(GLB_MAGIC);           // 4字节: "glTF"
            header.putInt(GLB_VERSION);        // 4字节: 版本 2
            header.putInt(12 + 8 + paddedJsonData.length + 8 + binaryData.size()); // 4字节: 总长度
            
            fos.write(header.array());
            
            // JSON块 (12 + JSON数据长度)
            ByteBuffer jsonChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            jsonChunkHeader.putInt(paddedJsonData.length); // 4字节: JSON块长度
            jsonChunkHeader.putInt(GLB_JSON_CHUNK_TYPE); // 4字节: "JSON"
            
            fos.write(jsonChunkHeader.array());
            fos.write(paddedJsonData);
            
            // 二进制块 (8 + 二进制数据长度)
            ByteBuffer binChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            binChunkHeader.putInt(binaryData.size()); // 4字节: 二进制块长度
            binChunkHeader.putInt(GLB_BIN_CHUNK_TYPE); // 4字节: "BIN\0"
            
            fos.write(binChunkHeader.array());
            fos.write(binaryData.toByteArray());
        }
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            json.append(valueToJson(entry.getValue()));
        }
        
        json.append("}");
        return json.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        } else if (value instanceof List) {
            return arrayToJson((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof float[]) {
            return arrayToJson((float[]) value);
        }
        return "null";
    }

    private String arrayToJson(List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(valueToJson(list.get(i)));
        }
        
        json.append("]");
        return json.toString();
    }

    private String arrayToJson(float[] array) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        for (int i = 0; i < array.length; i++) {
            if (i > 0) json.append(",");
            json.append(String.format("%.6f", array[i]));
        }
        
        json.append("]");
        return json.toString();
    }

    private int getComponentCount(String type) {
        switch (type) {
            case ACCESSOR_TYPE_SCALAR: return 1;
            case ACCESSOR_TYPE_VEC2: return 2;
            case ACCESSOR_TYPE_VEC3: return 3;
            default: return 1;
        }
    }

    private List<Float> getMinValues(List<Float> data, String type) {
        int components = getComponentCount(type);
        List<Float> min = new ArrayList<>();
        
        for (int i = 0; i < components; i++) {
            min.add(data.get(i));
        }
        
        for (int i = components; i < data.size(); i += components) {
            for (int j = 0; j < components; j++) {
                int idx = i + j;
                if (idx < data.size()) {
                    min.set(j, Math.min(min.get(j), data.get(idx)));
                }
            }
        }
        
        return min;
    }

    private List<Float> getMaxValues(List<Float> data, String type) {
        int components = getComponentCount(type);
        List<Float> max = new ArrayList<>();
        
        for (int i = 0; i < components; i++) {
            max.add(data.get(i));
        }
        
        for (int i = components; i < data.size(); i += components) {
            for (int j = 0; j < components; j++) {
                int idx = i + j;
                if (idx < data.size()) {
                    max.set(j, Math.max(max.get(j), data.get(idx)));
                }
            }
        }
        
        return max;
    }

    private List<Integer> getMinValues(int[] data) {
        int min = data[0];
        for (int value : data) {
            min = Math.min(min, value);
        }
        return Collections.singletonList(min);
    }

    private List<Integer> getMaxValues(int[] data) {
        int max = data[0];
        for (int value : data) {
            max = Math.max(max, value);
        }
        return Collections.singletonList(max);
    }
    
    /**
     * 从Texture创建PNG二进制数据
     */
    private byte[] createPNGFromTexture(Texture texture) throws IOException {
        int width = 64;  // 默认尺寸
        int height = 64;
        byte[] components = null;
        
        // 尝试使用反射获取Texture的私有字段
        try {
            java.lang.reflect.Field widthField = Texture.class.getDeclaredField("width");
            java.lang.reflect.Field heightField = Texture.class.getDeclaredField("height");
            java.lang.reflect.Field componentsField = Texture.class.getDeclaredField("components");
            
            widthField.setAccessible(true);
            heightField.setAccessible(true);
            componentsField.setAccessible(true);
            
            width = widthField.getInt(texture);
            height = heightField.getInt(texture);
            components = (byte[]) componentsField.get(texture);
            
            log.info("成功获取纹理数据: {}x{}, 纹理名称: {}", width, height, texture.getName());
        } catch (Exception e) {
            log.warn("无法从纹理获取像素数据，使用默认图案: {}", e.getMessage());
            components = new byte[width * height * 4];
            generateTestPattern(components, width, height);
        }
        
        // 创建BufferedImage
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        // 填充像素数据
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcIndex = (y * width + x) * 4;
                int r = components[srcIndex] & 0xFF;
                int g = components[srcIndex + 1] & 0xFF;
                int b = components[srcIndex + 2] & 0xFF;
                int a = components[srcIndex + 3] & 0xFF;
                
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        
        // 写入PNG到字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        
        log.info("生成的PNG数据大小: {} bytes, 纹理: {}", baos.size(), texture.getName());
        return baos.toByteArray();
    }
    
    /**
     * 生成测试图案
     */
    private void generateTestPattern(byte[] components, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y * width + x) * 4;
                
                // 创建一个类似Minecraft风格的图案
                int blockX = x / 16;
                int blockY = y / 16;
                
                if ((blockX + blockY) % 2 == 0) {
                    // 浅色方块
                    components[index] = (byte) 200;     // R
                    components[index + 1] = (byte) 200; // G  
                    components[index + 2] = (byte) 200; // B
                    components[index + 3] = (byte) 255; // A
                } else {
                    // 深色方块
                    components[index] = (byte) 100;     // R
                    components[index + 1] = (byte) 100; // G
                    components[index + 2] = (byte) 100; // B
                    components[index + 3] = (byte) 255; // A
                }
            }
        }
    }
    
    /**
     * 创建1x1透明PNG的二进制数据
     */
    private byte[] createPlaceholderPNG() {
        // 1x1透明PNG的字节数据
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG签名
            0x00, 0x00, 0x00, 0x0D, // IHDR块长度 (13字节)
            0x49, 0x48, 0x44, 0x52, // IHDR
            0x00, 0x00, 0x00, 0x01, // 宽度: 1
            0x00, 0x00, 0x00, 0x01, // 高度: 1
            0x08, 0x06, 0x00, 0x00, 0x00, // 位深度: 8, 颜色类型: 6 (RGBA), 压缩: 0, 过滤: 0, 隔行扫描: 0
            (byte) 0x5F, (byte) 0x9A, (byte) 0x86, 0x60, // IHDR CRC32
            0x00, 0x00, 0x00, 0x0C, // IDAT块长度 (12字节)
            0x49, 0x44, 0x41, 0x54, // IDAT
            0x08, (byte) 0x99, 0x01, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, // 压缩的图像数据 (1x1透明像素)
            (byte) 0x72, (byte) 0x24, (byte) 0x9D, (byte) 0x1A, // IDAT CRC32
            0x00, 0x00, 0x00, 0x00, // IEND块长度 (0字节)
            0x49, 0x45, 0x4E, 0x44, // IEND
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82  // IEND CRC32
        };
    }
}