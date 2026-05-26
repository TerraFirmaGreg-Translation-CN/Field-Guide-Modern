package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.asset.AssetKey;
import team.terrafirmgreg.fieldguide.asset.AssetLoader;
import team.terrafirmgreg.fieldguide.exception.AssetNotFoundException;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.BlockModel;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.ElementFace;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.ElementRotation;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.ModelElement;
import team.terrafirmgreg.fieldguide.render3d.material.Material;
import team.terrafirmgreg.fieldguide.render3d.material.RenderState;
import team.terrafirmgreg.fieldguide.render3d.material.Texture;
import team.terrafirmgreg.fieldguide.render3d.math.Quaternion;
import team.terrafirmgreg.fieldguide.render3d.math.Vector2f;
import team.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import team.terrafirmgreg.fieldguide.render3d.math.Vector4f;
import team.terrafirmgreg.fieldguide.render3d.renderer.Image;
import team.terrafirmgreg.fieldguide.render3d.scene.Geometry;
import team.terrafirmgreg.fieldguide.render3d.scene.Mesh;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import team.terrafirmgreg.fieldguide.render3d.shader.UnshadedShader;
import team.terrafirmgreg.fieldguide.render3d.animation.AnimatedTexture;
import team.terrafirmgreg.fieldguide.render3d.animation.AnimatedMaterial;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Slf4j
public class BaseModelBuilder {
    
    public static final int SCALER = 16;
    public static final float SCALE = 1f / SCALER;

    protected static final Vector4f LIGHT = new Vector4f(1f, 1f, 1f, 1f);      // 顶部
    protected static final Vector4f LIGHT_GRAY = new Vector4f(0.8f, 0.8f, 0.8f, 1f);  // 北面和南面
    protected static final Vector4f DARK_GRAY = new Vector4f(0.6f, 0.6f, 0.6f, 1f);   // 东面和西面
    protected static final Vector4f DARK = new Vector4f(0.5f, 0.5f, 0.5f, 1f);        // 底部

    protected static final Vector4f[] COLOR_LIGHT = {LIGHT, LIGHT, LIGHT, LIGHT};
    protected static final Vector4f[] COLOR_LIGHT_GRAY = {LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY};
    protected static final Vector4f[] COLOR_DARK_GRAY = {DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY};
    protected static final Vector4f[] COLOR_DARK = {DARK, DARK, DARK, DARK};

    protected static final Vector3f UP = new Vector3f(0, 1, 0);
    protected static final Vector3f DOWN = new Vector3f(0, -1, 0);
    protected static final Vector3f EAST = new Vector3f(1, 0, 0);
    protected static final Vector3f WEST = new Vector3f(-1, 0, 0);
    protected static final Vector3f NORTH = new Vector3f(0, 0, -1);
    protected static final Vector3f SOUTH = new Vector3f(0, 0, 1);

    protected AssetLoader assetLoader;
    protected Map<String, Material> materialCache = new HashMap<>();
    protected Map<String, AnimatedTexture> animatedTextureCache = new HashMap<>();

    public BaseModelBuilder(AssetLoader assetLoader) {
        this.assetLoader = assetLoader;
    }

    public Node buildModel(String modelId) {
        BlockModel blockModel = loadModel(modelId);
        return buildModel(blockModel);
    }

    /**
     * 构建模型节点
     */
    public Node buildModel(BlockModel blockModel) {
        Map<String, String> textures = blockModel.getTextures();
        Node node = new Node();
        for (ModelElement element : blockModel.getElements()) {
            buildNode(node, element, textures);
        }
        return node;
    }

    /**
     * 加载模型 - 子类可以覆盖此方法以提供不同的加载逻辑
     */
    protected BlockModel loadModel(String modelId) {
        return assetLoader.loadModel(modelId);
    }

    /**
     * 构建节点元素 - 相同材质的Face组合为一个Mesh
     */
    public void buildNode(Node rootNode, ModelElement element, Map<String, String> textures) {
        Map<String, ElementFace> faces = element.getFaces();
        if (faces == null || faces.isEmpty()) {
            return;
        }

        double x1 = element.getFrom()[0];
        double y1 = element.getFrom()[1];
        double z1 = element.getFrom()[2];
        double x2 = element.getTo()[0];
        double y2 = element.getTo()[1];
        double z2 = element.getTo()[2];

        // 旋转原点
        double o1 = (x1 + x2) * 0.5;
        double o2 = (y1 + y2) * 0.5;
        double o3 = (z1 + z2) * 0.5;

        // 处理旋转
        ElementRotation rotation = element.getRotation();
        Quaternion rot = processRotation(rotation, element);
        if (rot != null && rotation.getOrigin() != null) {
            double[] origin = rotation.getOrigin();
            o1 = origin[0];
            o2 = origin[1];
            o3 = origin[2];
        }

        // 不应用阴影
        boolean noShade = element.getShade() != null && !element.getShade();

        // 相对于原点调整坐标
        x1 -= o1;
        y1 -= o2;
        z1 -= o3;
        x2 -= o1;
        y2 -= o2;
        z2 -= o3;

        // 按材质分组收集面数据
        Map<String, MaterialFaceGroup> materialGroups = new HashMap<>();

        String overlayTexture = null;
        if (textures.containsKey("overlay")) {
            overlayTexture = getTexture(textures, "#overlay");
        }

        // 处理每个面，按材质分组
        for (Map.Entry<String, ElementFace> entry : faces.entrySet()) {
            String dir = entry.getKey();
            ElementFace face = entry.getValue();

            String texture = getTexture(textures, face.getTexture());

            // 获取或创建材质组
            MaterialFaceGroup group = materialGroups.computeIfAbsent(texture, MaterialFaceGroup::new);

            // 创建面数据
            Vector2f[] texCoords = createTextureCoords(face);
            int[] indices = createIndices(dir, face);
            FaceData faceData = createFaceData(dir, x1, y1, z1, x2, y2, z2, noShade);
            
            if (faceData != null) {
                group.addFace(faceData, texCoords, indices);
                
                // 处理色调索引
                if (face.getTintIndex() != null && face.getTintIndex() >= 0) {
                    // TODO: 处理色调
                    log.info("tintindex: {}", face.getTintIndex());
                }
            }
        }

        // 为每个材质组创建Geometry
        for (MaterialFaceGroup group : materialGroups.values()) {
            Mesh mesh = group.createMesh();
            if (mesh != null) {
                Geometry geometry = new Geometry(mesh);
                Material material = createAnimatedMaterial(group.texture, overlayTexture);
                geometry.setMaterial(material);

                // 应用旋转和位移
                if (rot != null) {
                    geometry.getLocalTransform().setRotation(rot);
                }
                geometry.getLocalTransform().setTranslation(v3(o1, o2, o3));

                rootNode.attachChild(geometry);
            }
        }
    }

    /**
     * 处理旋转
     */
    protected Quaternion processRotation(ElementRotation rotation, ModelElement element) {
        if (rotation == null || rotation.getAxis() == null || rotation.getAngle() == null || rotation.getAngle() == 0) {
            return null;
        }

        String axis = rotation.getAxis();
        double angle = rotation.getAngle(); // 角度制
        double rad = Math.toRadians(angle);

        Quaternion rot;
        switch (axis) {
            case "x":
                rot = new Quaternion().rotateX((float) rad);
                break;
            case "y":
                rot = new Quaternion().rotateY((float) rad);
                break;
            case "z":
                rot = new Quaternion().rotateZ((float) rad);
                break;
            default:
                log.error("Unknown axis: {}", axis);
                return null;
        }

        // 处理缩放（TODO）
        if (rotation.getRescale() != null && rotation.getRescale()) {
            processRescale(angle);
        }

        return rot;
    }

    /**
     * 处理缩放 - 待实现
     */
    protected void processRescale(double angle) {
        // TODO: 实现缩放逻辑
        if (angle == 45.0 || angle == -45.0) {
            // double rescale = 1.41421356237309
        } else if (angle == 22.5 || angle == -22.5) {
            // double rescale = 0.76536686473018
        }
    }

    /**
     * 创建面的几何体
     */
    protected Geometry createFaceGeometry(String dir, ElementFace face, 
                                        double x1, double y1, double z1, 
                                        double x2, double y2, double z2, 
                                        boolean noShade) {
        // 纹理坐标
        Vector2f[] texCoords = createTextureCoords(face);

        // 索引
        int[] indices = createIndices(dir, face);

        // 顶点位置、法线和颜色
        FaceData faceData = createFaceData(dir, x1, y1, z1, x2, y2, z2, noShade);
        if (faceData == null) {
            return null;
        }

        // 处理色调索引
        if (face.getTintIndex() != null && face.getTintIndex() >= 0) {
            // TODO: 处理色调
            log.info("tintindex: {}", face.getTintIndex());
        }

        Mesh mesh = new Mesh(faceData.positions, indices, texCoords, faceData.normals, faceData.colors);
        return new Geometry(mesh);
    }

    /**
     * 创建纹理坐标
     */
    protected Vector2f[] createTextureCoords(ElementFace face) {
        double s1 = face.getUv()[0];
        double t1 = face.getUv()[1];
        double s2 = face.getUv()[2];
        double t2 = face.getUv()[3];

        Vector2f uv0 = v2(s1, t1);
        Vector2f uv1 = v2(s1, t2);
        Vector2f uv2 = v2(s2, t2);
        Vector2f uv3 = v2(s2, t1);

        Vector2f[] texCoords = new Vector2f[]{uv0, uv1, uv2, uv3};

        // 处理纹理旋转
        if (face.getRotation() != null && face.getRotation() > 0) {
            switch (face.getRotation()) {
                case 90:
                    texCoords = new Vector2f[]{uv1, uv2, uv3, uv0};
                    break;
                case 180:
                    texCoords = new Vector2f[]{uv2, uv1, uv0, uv3};
                    break;
                case 270:
                    texCoords = new Vector2f[]{uv3, uv0, uv1, uv2};
                    break;
            }
        }

        return texCoords;
    }

    /**
     * 创建索引
     */
    protected int[] createIndices(String dir, ElementFace face) {
        int[] indices = {0, 1, 2, 0, 2, 3};

        // 处理背面剔除
        if (face.getCullface() != null && !dir.equals(face.getCullface())) {
            indices = new int[]{0, 2, 1, 0, 3, 2};
            // TODO: 改变法线方向
        }

        return indices;
    }

    /**
     * 创建面的数据
     */
    protected FaceData createFaceData(String dir, double x1, double y1, double z1, 
                                    double x2, double y2, double z2, boolean noShade) {
        switch (dir) {
            case "down":
                return createDownFace(x1, y1, z1, x2, y2, z2, noShade);
            case "up":
                return createUpFace(x1, y1, z1, x2, y2, z2, noShade);
            case "north":
                return createNorthFace(x1, y1, z1, x2, y2, z2, noShade);
            case "south":
                return createSouthFace(x1, y1, z1, x2, y2, z2, noShade);
            case "west":
                return createWestFace(x1, y1, z1, x2, y2, z2, noShade);
            case "east":
                return createEastFace(x1, y1, z1, x2, y2, z2, noShade);
            default:
                return null;
        }
    }

    // 各个面的创建方法
    protected FaceData createDownFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y1, z2), v3(x1, y1, z1), v3(x2, y1, z1), v3(x2, y1, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK;
        
        return new FaceData(positions, new Vector3f[]{DOWN, DOWN, DOWN, DOWN}, colors);
    }

    protected FaceData createUpFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z1), v3(x1, y2, z2), v3(x2, y2, z2), v3(x2, y2, z1)
        };
        Vector4f[] colors = COLOR_LIGHT;
        
        return new FaceData(positions, new Vector3f[]{UP, UP, UP, UP}, colors);
    }

    protected FaceData createNorthFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x2, y2, z1), v3(x2, y1, z1), v3(x1, y1, z1), v3(x1, y2, z1)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_LIGHT_GRAY;
        
        return new FaceData(positions, new Vector3f[]{NORTH, NORTH, NORTH, NORTH}, colors);
    }

    protected FaceData createSouthFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z2), v3(x1, y1, z2), v3(x2, y1, z2), v3(x2, y2, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_LIGHT_GRAY;
        
        return new FaceData(positions, new Vector3f[]{SOUTH, SOUTH, SOUTH, SOUTH}, colors);
    }

    protected FaceData createWestFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z1), v3(x1, y1, z1), v3(x1, y1, z2), v3(x1, y2, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK_GRAY;
        
        return new FaceData(positions, new Vector3f[]{WEST, WEST, WEST, WEST}, colors);
    }

    protected FaceData createEastFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x2, y2, z2), v3(x2, y1, z2), v3(x2, y1, z1), v3(x2, y2, z1)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK_GRAY;
        
        return new FaceData(positions, new Vector3f[]{EAST, EAST, EAST, EAST}, colors);
    }

    /**
     * 创建材质
     */
    protected Material makeMaterial(String texture, String overlayTexture) {
        // 创建唯一的缓存键，包含基础纹理和overlay纹理信息
        String cacheKey = overlayTexture != null ? texture + "_overlay_" + overlayTexture : texture;
        return materialCache.computeIfAbsent(cacheKey, it -> createMaterial(texture, overlayTexture));
    }
    
    /**
     * 创建动画材质（如果检测到动画纹理）
     */
    protected Material createAnimatedMaterial(String texture, String overlayTexture) {
        // 检测基础纹理是否为动画
        AnimatedTexture baseAnimated = getOrCreateAnimatedTexture(texture);
        AnimatedTexture overlayAnimated = overlayTexture != null ? getOrCreateAnimatedTexture(overlayTexture) : null;
        
        // 如果有动画纹理，创建动画材质
        if (baseAnimated.isAnimated() || (overlayAnimated != null && overlayAnimated.isAnimated())) {
            return createAnimatedMaterialInternal(baseAnimated, overlayAnimated);
        }
        
        // 否则创建普通材质
        return createMaterial(texture, overlayTexture);
    }
    
    /**
     * 获取或创建动画纹理
     */
    private AnimatedTexture getOrCreateAnimatedTexture(String texture) {
        return animatedTextureCache.computeIfAbsent(texture, t -> {
            AssetKey assetKey = new AssetKey(texture, "textures", "assets", ".png");
            BufferedImage img = assetLoader.loadTexture(assetKey);
            
            AnimatedTexture animatedTexture = new AnimatedTexture();
            animatedTexture.setTexturePath(texture);
            animatedTexture.setAnimated(AnimatedTexture.isAnimationAtlas(img));
            
            if (animatedTexture.isAnimated()) {
                animatedTexture.setFrameCount(AnimatedTexture.calculateFrameCount(img));
                animatedTexture.setFrames(AnimatedTexture.extractFrames(img));
                log.info("Created animated texture: {} with {} frames", texture, animatedTexture.getFrameCount());
            } else {
                animatedTexture.setFrameCount(1);
                animatedTexture.setFrames(List.of(img));
            }
            
            return animatedTexture;
        });
    }
    
    /**
     * 创建动画材质内部实现
     */
    private Material createAnimatedMaterialInternal(AnimatedTexture baseAnimated, AnimatedTexture overlayAnimated) {
        // 如果有overlay，需要合并动画帧
        if (overlayAnimated != null && overlayAnimated.isAnimated()) {
            return createCombinedAnimatedMaterial(baseAnimated, overlayAnimated);
        } else {
            return createSingleAnimatedMaterial(baseAnimated, overlayAnimated);
        }
    }
    
    /**
     * 创建单个动画材质
     */
    private Material createSingleAnimatedMaterial(AnimatedTexture baseAnimated, AnimatedTexture overlayAnimated) {
        AnimatedMaterial material = new AnimatedMaterial(baseAnimated);
        
        // 检测是否为玻璃材质，设置相应的混合模式
        boolean isGlassTexture = isGlassTexture(baseAnimated.getTexturePath()) || 
                                 (overlayAnimated != null && isGlassTexture(overlayAnimated.getTexturePath()));
        if (isGlassTexture) {
            // 玻璃材质使用ALPHA_BLEND模式，不使用Alpha测试
            material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND);
            log.debug("Detected glass animated texture: {}, using ALPHA_BLEND", baseAnimated.getTexturePath());
        } else {
            // 非玻璃材质使用Alpha测试和MASK模式
            material.getRenderState().setAlphaTest(true);
            material.getRenderState().setAlphaFalloff(0.1f);
            material.getRenderState().setBlendMode(RenderState.BlendMode.OFF);
        }
        
        material.setUseVertexColor(true);
        material.setShader(new UnshadedShader());
        
        // 如果有静态overlay，合并到第一帧
        if (overlayAnimated != null && !overlayAnimated.isAnimated() && !overlayAnimated.getFrames().isEmpty()) {
            BufferedImage overlayFrame = overlayAnimated.getFrames().get(0);
            List<BufferedImage> combinedFrames = combineFramesWithOverlay(
                baseAnimated.getFrames(), overlayFrame);
            baseAnimated.setFrames(combinedFrames);
        }
        
        // 设置纹理
        if (!baseAnimated.getFrames().isEmpty()) {
            Image image =
                new Image(baseAnimated.getFrames().get(0));
            Texture diffuseMap = new Texture(image);
            diffuseMap.setName(baseAnimated.getTexturePath());
            diffuseMap.setMagFilter(Texture.MagFilter.NEAREST);
            material.setDiffuseMap(diffuseMap);
        }
        
        return material;
    }
    
    /**
     * 创建组合动画材质（两个动画纹理）
     */
    private Material createCombinedAnimatedMaterial(AnimatedTexture baseAnimated, AnimatedTexture overlayAnimated) {
        // 合并两个动画的帧数（取最大值）
        int maxFrames = Math.max(baseAnimated.getFrameCount(), overlayAnimated.getFrameCount());
        List<BufferedImage> combinedFrames = combineAnimatedFrames(
            baseAnimated.getFrames(), overlayAnimated.getFrames(), maxFrames);
        
        baseAnimated.setFrames(combinedFrames);
        baseAnimated.setFrameCount(maxFrames);
        
        return createSingleAnimatedMaterial(baseAnimated, null);
    }
    
    /**
     * 合并静态overlay到所有帧
     */
    private List<BufferedImage> combineFramesWithOverlay(List<BufferedImage> frames, BufferedImage overlay) {
        return frames.stream().map(frame -> {
            BufferedImage combined = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combined.createGraphics();
            g.drawImage(frame, 0, 0, null);
            g.drawImage(overlay, 0, 0, null);
            g.dispose();
            return combined;
        }).collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 合并两个动画的帧
     */
    private List<BufferedImage> combineAnimatedFrames(List<BufferedImage> baseFrames, List<BufferedImage> overlayFrames, int totalFrames) {
        List<BufferedImage> combined = new ArrayList<>();
        
        for (int i = 0; i < totalFrames; i++) {
            BufferedImage baseFrame = baseFrames.get(i % baseFrames.size());
            BufferedImage overlayFrame = overlayFrames.get(i % overlayFrames.size());
            
            BufferedImage combinedFrame = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combinedFrame.createGraphics();
            g.drawImage(baseFrame, 0, 0, null);
            g.drawImage(overlayFrame, 0, 0, null);
            g.dispose();
            
            combined.add(combinedFrame);
        }
        
        return combined;
    }

    protected Material createMaterial(String texture, String overlayTexture) {
        // 直接在内存中生成纹理，不再保存到文件系统
        String fileName = texture.replace("assets/minecraft/textures/", "").replace(".png", "").replace(":", "_");
        if (overlayTexture != null) {
            String overlayName = overlayTexture.replace("assets/minecraft/textures/", "").replace(".png", "").replace(":", "_").replace("/", "_");
            fileName += "_overlay_" + overlayName;
        }
        fileName += ".png";
        
        BufferedImage img = generateCombinedImage(texture, overlayTexture);

        Image image = new Image(img);
        Texture diffuseMap = new Texture(image);
        // 使用唯一的文件路径设置纹理名称，包含overlay信息
        diffuseMap.setName(fileName);
        diffuseMap.setMagFilter(Texture.MagFilter.NEAREST);

        Material material = new Material();
        
        // 检测是否为玻璃材质，设置相应的混合模式
        boolean isGlassTexture = isGlassTexture(texture) || (overlayTexture != null && isGlassTexture(overlayTexture));
        if (isGlassTexture) {
            // 玻璃材质使用ALPHA_BLEND模式，不使用Alpha测试
            material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND);
            log.debug("Detected glass texture: {}, using ALPHA_BLEND", texture);
        } else {
            // 非玻璃材质使用Alpha测试和MASK模式
            material.getRenderState().setAlphaTest(true);
            material.getRenderState().setAlphaFalloff(0.1f);
            material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND);
        }
        
        material.setUseVertexColor(true);
        material.setShader(new UnshadedShader());
        material.setDiffuseMap(diffuseMap);

        return material;
    }

    /**
     * 生成组合纹理图像（向后兼容方法）
     */
    private BufferedImage generateCombinedImage(String texture, String overlayTexture) {
        AssetKey assetKey = new AssetKey(texture, "textures", "assets", ".png");
        BufferedImage img = assetLoader.loadTexture(assetKey);
        log.debug("Loading base texture: {}, original size: {}x{}", texture, img.getWidth(), img.getHeight());

        // 对于动画纹理图集，提取第一帧
        img = extractFirstFrameIfNeeded(img);
        log.debug("Processed texture size: {}x{}", img.getWidth(), img.getHeight());

        if (overlayTexture != null) {
            AssetKey overlayKey = new AssetKey(overlayTexture, "textures", "assets", ".png");
            BufferedImage overlayImg = assetLoader.loadTexture(overlayKey);
            log.debug("Loading overlay texture: {}, original size: {}x{}", overlayTexture, overlayImg.getWidth(), overlayImg.getHeight());
            
            // 同样处理overlay纹理
            overlayImg = extractFirstFrameIfNeeded(overlayImg);
            log.debug("Processed overlay size: {}x{}", overlayImg.getWidth(), overlayImg.getHeight());

            BufferedImage combined = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combined.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.drawImage(overlayImg, 0, 0, null);
            g.dispose();

            img = combined;
        }
        
        return img;
    }

    /**
     * 检查并提取动画纹理的第一帧
     * 只处理垂直动画图集（16x16N），其他NPOT纹理不处理
     */
    private BufferedImage extractFirstFrameIfNeeded(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        // 检查是否是垂直动画图集：宽度16，高度是16的倍数
        if (width == 16 && height > 16 && height % 16 == 0) {
            log.debug("Detected animation atlas (16x{}), extracting first frame (16x16)", height);
            // 提取第一帧 (0, 0, 16, 16)
            return img.getSubimage(0, 0, 16, 16);
        }
        
        return img; // 其他情况直接返回原图
    }

    /**
     * 检测纹理是否为玻璃材质
     */
    private boolean isGlassTexture(String texturePath) {
        if (texturePath == null) {
            return false;
        }
        
        // 转换为小写进行匹配
        String lowerPath = texturePath.toLowerCase();
        
        // 检测常见的玻璃纹理关键词
        return lowerPath.contains("glass") || 
               lowerPath.contains("stained_glass") ||
               lowerPath.contains("tinted_glass") ||
               lowerPath.contains("glass_pane") ||
               lowerPath.contains("stained_glass_pane") ||
               // TFC 相关玻璃纹理
               lowerPath.contains("tfc:glass") ||
               lowerPath.contains("tfc/glass");
    }

    /**
     * 解析纹理引用
     */
    protected String getTexture(Map<String, String> map, String id) {
        if (id.startsWith("#")) {
            String ref = map.get(id.substring(1));
            if (ref == null) {
                throw new AssetNotFoundException("Texture not found: " + id);
            }
            if (ref.startsWith("#")) {
                return getTexture(map, ref);
            } else {
                return ref;
            }
        } else {
            return map.getOrDefault(id, "assets/minecraft/textures/block/missing.png");
        }
    }

    // 工具方法
    public static Vector3f v3(double x, double y, double z) {
        return new Vector3f((float)(x * SCALE), (float)(y * SCALE), (float)(z * SCALE));
    }

    public static Vector2f v2(double s, double t) {
        return new Vector2f((float) (s / 16.0), (float) (1.0 - t / 16.0));
    }

    /**
     * 面数据容器类
     */
    protected static class FaceData {
        public final Vector3f[] positions;
        public final Vector3f[] normals;
        public final Vector4f[] colors;

        public FaceData(Vector3f[] positions, Vector3f[] normals, Vector4f[] colors) {
            this.positions = positions;
            this.normals = normals;
            this.colors = colors;
        }
    }

    /**
     * 相同材质的面数据收集器
     */
    protected static class MaterialFaceGroup {
        public final String texture;
        public final List<Vector3f> positions = new ArrayList<>();
        public final List<Vector3f> normals = new ArrayList<>();
        public final List<Vector2f> texCoords = new ArrayList<>();
        public final List<Vector4f> colors = new ArrayList<>();
        public final List<Integer> indices = new ArrayList<>();

        public MaterialFaceGroup(String texture) {
            this.texture = texture;
        }

        public void addFace(FaceData faceData, Vector2f[] faceTexCoords, int[] faceIndices) {
            int baseIndex = positions.size();
            
            // 添加顶点数据
            for (int i = 0; i < faceData.positions.length; i++) {
                positions.add(faceData.positions[i]);
                normals.add(faceData.normals[i]);
                texCoords.add(faceTexCoords[i]);
                colors.add(faceData.colors[i]);
            }
            
            // 添加索引（调整偏移）
            for (int index : faceIndices) {
                indices.add(baseIndex + index);
            }
        }

        public Mesh createMesh() {
            if (positions.isEmpty()) {
                return null;
            }
            
            Vector3f[] posArray = positions.toArray(new Vector3f[0]);
            Vector3f[] normArray = normals.toArray(new Vector3f[0]);
            Vector2f[] texArray = texCoords.toArray(new Vector2f[0]);
            Vector4f[] colorArray = colors.toArray(new Vector4f[0]);
            int[] indexArray = indices.stream().mapToInt(i -> i).toArray();
            
            return new Mesh(posArray, indexArray, texArray, normArray, colorArray);
        }
    }
}