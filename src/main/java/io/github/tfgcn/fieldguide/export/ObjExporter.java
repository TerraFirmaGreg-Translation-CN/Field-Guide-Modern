package io.github.tfgcn.fieldguide.export;

import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.math.Vector2f;
import io.github.tfgcn.fieldguide.render3d.math.Vector3f;
import io.github.tfgcn.fieldguide.render3d.scene.Geometry;
import io.github.tfgcn.fieldguide.render3d.scene.Mesh;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import io.github.tfgcn.fieldguide.render3d.scene.Vertex;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OBJ格式导出器
 */
@Slf4j
public class ObjExporter {

    private int vertexOffset = 1;

    private List<String> vertexLines = new ArrayList<>();
    private List<String> texCoordLines = new ArrayList<>();
    private List<String> faceLines = new ArrayList<>();
    private List<String> objectLines = new ArrayList<>();

    private Map<Material, Integer> materialMap = new HashMap<>();
    private List<String> materialLines = new ArrayList<>();

    /**
     * 导出节点树为OBJ文件
     */
    public void export(Node rootNode, String filePath) throws IOException {
        export(rootNode, filePath, "model");
    }

    /**
     * 导出节点树为OBJ文件
     */
    public void export(Node rootNode, String filePath, String modelName) throws IOException {
        reset();

        // 收集所有几何体
        List<Geometry> geometries = rootNode.getGeometryList(null);

        // 处理每个几何体
        for (Geometry geometry : geometries) {
            processGeometry(geometry, modelName);
        }

        // 写入文件
        writeObjFile(filePath, modelName);

        log.info("成功导出OBJ文件: {}, 包含 {} 个几何体", filePath, geometries.size());
    }

    private void reset() {
        vertexOffset = 1;
        vertexLines.clear();
        texCoordLines.clear();
        faceLines.clear();
        objectLines.clear();
        materialMap.clear();
        materialLines.clear();
    }

    private void processGeometry(Geometry geometry, String baseName) {
        Mesh mesh = geometry.getMesh();
        if (mesh == null) return;

        // 获取变换后的顶点数据
        Vertex[] vertexes = mesh.getVertexes();
        Vector3f[] positions = new Vector3f[vertexes.length];
        Vector2f[] texCoords = new Vector2f[vertexes.length];
        for (int i = 0; i < vertexes.length; i++) {
            Vertex vertex = vertexes[i];
            positions[i] = vertex.position;
            texCoords[i] = vertex.texCoord;
        }

        positions = getTransformedPositions(geometry, positions);
        int[] indices = mesh.getIndexes();

        if (positions == null || indices == null || indices.length == 0) {
            return;
        }

        // 添加对象定义
        String objectName = baseName + "_" + objectLines.size();
        objectLines.add("o " + objectName);

        // 处理材质
        Material material = geometry.getMaterial();
        if (material != null) {
            materialMap.computeIfAbsent(material, k -> materialMap.size());
            String materialName = String.valueOf(materialMap.get(material));
            objectLines.add("usemtl " + materialName);
        } else {
            objectLines.add("usemtl default");
        }

        // 添加顶点数据
        for (Vector3f position : positions) {
            vertexLines.add(String.format("v %.6f %.6f %.6f",
                position.x, position.y, position.z));
        }

        // 添加纹理坐标
        for (Vector2f texCoord : texCoords) {
            texCoordLines.add(String.format("vt %.6f %.6f",
                texCoord.x, texCoord.y));
        }

        // 添加面
        for (int i = 0; i < indices.length; i += 3) {
            if (i + 2 >= indices.length) break;

            int idx1 = indices[i] + vertexOffset;
            int idx2 = indices[i + 1] + vertexOffset;
            int idx3 = indices[i + 2] + vertexOffset;

            String faceLine;
            // v/vt 格式
            faceLine = String.format("f %d/%d %d/%d %d/%d",
                idx1, idx1,
                idx2, idx2,
                idx3, idx3);

            faceLines.add(faceLine);
        }

        // 更新偏移量
        vertexOffset += positions.length;
    }

    /**
     * 从材质中提取纹理路径
     */
    private String extractTexturePath(Material material) {
        if (material == null) {
            return null;
        }

        // 尝试获取漫反射贴图
        Texture diffuseMap = material.getDiffuseMap();
        if (diffuseMap != null) {
            // 从Texture对象中获取名称或路径
            String textureName = diffuseMap.getName();
            if (textureName != null && !textureName.isEmpty()) {
                return textureName;
            }
        }

        return null;
    }

    private Vector3f[] getTransformedPositions(Geometry geometry, Vector3f[] positions) {
        if (positions == null) return null;

        // 应用几何体的变换
        Vector3f[] transformed = new Vector3f[positions.length];
        for (int i = 0; i < positions.length; i++) {
            transformed[i] = geometry.getWorldTransform().transformVector(positions[i], new Vector3f());
        }
        return transformed;
    }

    private void writeObjFile(String filePath, String modelName) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // 写入文件头
            writer.println("# Exported Minecraft Block Model");
            writer.println("# OBJ File Generated by FieldGuide");
            writer.println("# Model: " + modelName);
            writer.println();

            // 写入材质库引用
            writer.println("# Material Library");
            writer.println("mtllib " + path.getFileName().toString().replace(".obj", ".mtl"));
            writer.println();

            // 写入顶点
            writer.println("# Vertices");
            vertexLines.forEach(writer::println);
            writer.println();

            // 写入纹理坐标
            if (!texCoordLines.isEmpty()) {
                writer.println("# Texture Coordinates");
                texCoordLines.forEach(writer::println);
                writer.println();
            }

            // 写入对象和面
            writer.println("# Objects and Faces");
            for (String objectLine : objectLines) {
                writer.println(objectLine);
            }
            faceLines.forEach(writer::println);
        }

        // 写入MTL文件
        writeMtlFile(filePath.replace(".obj", ".mtl"), modelName);
    }

    private void writeMtlFile(String mtlFilePath, String modelName) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(mtlFilePath))) {
            writer.println("# Material Library for " + modelName);
            writer.println("# Generated by FieldGuide");
            writer.println();

            // 写入默认材质
            writer.println("newmtl default");
            writer.println("Ka 1.000 1.000 1.000"); // 环境光
            writer.println("Kd 1.000 1.000 1.000"); // 漫反射
            writer.println("Ks 0.000 0.000 0.000"); // 高光
            writer.println("Ns 0.000");             // 高光指数
            writer.println("d 1.0");                // 不透明度
            writer.println("illum 2");              // 光照模型
            writer.println();

            // 写入所有材质
            for (Map.Entry<Material, Integer> entry : materialMap.entrySet()) {
                writer.println("newmtl " + entry.getValue());
                writer.println("Ka 1.000 1.000 1.000"); // 环境光
                writer.println("Kd 0.000 0.000 0.000"); // 漫反射
                writer.println("Ks 0.000 0.000 0.000"); // 高光
                writer.println("Ns 0.000");             // 高光指数
                writer.println("d 1.0");                // 不透明度
                writer.println("illum 2");              // 光照模型

                // 如果找到纹理路径，添加纹理映射
                String texture = extractTexturePath(entry.getKey());
                if (texture != null) {
                    writer.println("map_Kd " + texture);
                }
                writer.println();
            }
        }
    }
}