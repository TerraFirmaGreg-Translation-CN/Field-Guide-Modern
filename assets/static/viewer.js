/**
 * GLB 3D 模型查看器组件
 * Minecraft 风格的明亮光照和简洁界面
 */

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

class GLBViewer {
    constructor(containerId, options = {}) {
        console.log('Initializing GLBViewer...');
        
        this.container = document.getElementById(containerId);
        if (!this.container) {
            console.error(`Container with id '${containerId}' not found`);
            return;
        }
        
        // 检查是否已经初始化过
        if (this.container.dataset.viewerInstance === 'true') {
            console.warn(`Viewer for container '${containerId}' already initialized`);
            return;
        }
        
        // 标记为已初始化
        this.container.dataset.viewerInstance = 'true';
        
        // 获取容器的实际尺寸，如果容器没有设置尺寸则使用默认值
        const containerWidth = this.container.clientWidth || 400;
        const containerHeight = this.container.clientHeight || 300;
        
        this.options = {
            width: containerWidth,
            height: containerHeight,
            backgroundColor: 0xf0f0f0, // 淡灰色背景
            enableControls: true,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            rotationSpeed: 0.05,
            minDistance: 2,
            maxDistance: 50,
            autoLoad: false, // 新增：默认不自动加载
            modelUrl: null, // 新增：模型URL
            ...options
        };
        
        // 调试：记录最终的 options
        console.log('GLBViewer options set:', {
            autoLoad: this.options.autoLoad,
            modelUrl: this.options.modelUrl
        });
        
        // 多模型相关属性
        this.modelUrls = [];
        this.currentModelIndex = -1;
        this.cycleTimer = null;
        this.cycleInterval = 1000; // 默认1秒切换一次
        
        // 预加载模型相关属性
        this.preloadedModels = [];
        this.preloadedModelOptions = {};
        
        // 加载状态
        this.isLoaded = false;
        this.isLoading = false;
        
        // 渲染状态控制
        this.isRendering = true;
        this.animationId = null;
        
        // IntersectionObserver 用于检测可见性
        this.intersectionObserver = null;
        
        // 确保容器有相对定位，以便控制渲染器位置
        if (window.getComputedStyle(this.container).position === 'static') {
            this.container.style.position = 'relative';
        }
        
        this.scene = null;
        this.camera = null;
        this.renderer = null;
        this.controls = null;
        this.model = null;
        this.animationMixer = null;
        this.gridHelper = null;
        this.axesHelper = null;
        this.animationActions = [];
        
        this.init();
        this.initIntersectionObserver();
    }
    
    /**
     * 初始化 3D 场景
     */
    init() {
        this.createScene();
        this.createCamera();
        this.createRenderer();
        this.createControls();
        this.createLights();
        this.createHelpers();
        
        this.container.appendChild(this.renderer.domElement);
        
        // 开始渲染循环
        this.animate();
        
        // 处理窗口大小变化
        this.handleResize();
        
        console.log('GLB Viewer initialized');
        
        // 如果启用自动加载且有模型URL，则自动加载
        if (this.options.autoLoad && this.options.modelUrl) {
            // 隐藏播放按钮（如果存在）
            setTimeout(() => {
                if (this.playButton) {
                    this.playButton.style.display = 'none';
                }
                this.loadModel();
            }, 100);
        } else {
            // 非自动加载时显示播放按钮
            this.createPlayButton();
        }
    }
    
    /**
     * 创建场景 - Minecraft 风格
     */
    createScene() {
        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(this.options.backgroundColor);
        
        // 调整雾效为更明亮的颜色和更远的距离
        this.scene.fog = new THREE.Fog(this.options.backgroundColor, 100, 500);
    }
    
    /**
     * 创建相机 - Minecraft 风格的初始视角
     */
    createCamera() {
        const aspect = this.options.width / this.options.height;
        this.camera = new THREE.PerspectiveCamera(75, aspect, 0.1, 1000);
        
        // 从正X、负Z、正Y角度斜向下观察原点
        this.camera.position.set(5, 3, -5);
        this.camera.lookAt(0, 0, 0);
    }
    
    /**
     * 创建渲染器
     */
    createRenderer() {
        this.renderer = new THREE.WebGLRenderer({ 
            antialias: true,
            alpha: true 
        });
        this.renderer.setSize(this.options.width, this.options.height);
        this.renderer.setPixelRatio(window.devicePixelRatio);
        this.renderer.shadowMap.enabled = this.options.enableShadows;
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        this.renderer.outputColorSpace = THREE.SRGBColorSpace;
        this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
        this.renderer.toneMappingExposure = 1.3; // 增加曝光度
        
        // 设置canvas样式，确保它填满容器
        this.renderer.domElement.style.width = '100%';
        this.renderer.domElement.style.height = '100%';
        this.renderer.domElement.style.display = 'block';
        this.renderer.domElement.style.position = 'absolute';
        this.renderer.domElement.style.top = '0';
        this.renderer.domElement.style.left = '0';
    }
    
    /**
     * 创建控制器
     */
    createControls() {
        if (this.options.enableControls) {
            this.controls = new OrbitControls(this.camera, this.renderer.domElement);
            this.controls.enableDamping = true;
            this.controls.dampingFactor = 0.05;
            this.controls.enableZoom = true;
            this.controls.autoRotate = this.options.autoRotate;
            this.controls.autoRotateSpeed = this.options.rotationSpeed * 60;
            this.controls.minDistance = this.options.minDistance;
            this.controls.maxDistance = this.options.maxDistance;
        }
    }
    
    /**
     * 创建灯光 - 统一的Minecraft风格超明亮光照配置
     */
    createLights() {
        // 超明亮的环境光 - 提供基础亮度
        const ambientLight = new THREE.AmbientLight(0xffffff, 1.2);
        this.scene.add(ambientLight);
        
        // 主方向光 - 模拟明亮的太阳光
        const directionalLight = new THREE.DirectionalLight(0xffffff, 1.5);
        directionalLight.position.set(5, 10, 3);
        directionalLight.castShadow = this.options.enableShadows;
        
        if (this.options.enableShadows) {
            directionalLight.shadow.mapSize.width = 2048;
            directionalLight.shadow.mapSize.height = 2048;
            directionalLight.shadow.camera.near = 0.5;
            directionalLight.shadow.camera.far = 50;
            directionalLight.shadow.camera.left = -15;
            directionalLight.shadow.camera.right = 15;
            directionalLight.shadow.camera.top = 15;
            directionalLight.shadow.camera.bottom = -15;
        }
        this.scene.add(directionalLight);
        
        // 顶部补光 - 大幅增加垂直表面的亮度
        const topLight = new THREE.DirectionalLight(0xffffff, 0.8);
        topLight.position.set(0, 10, 0);
        this.scene.add(topLight);
        
        // 多个方向的补光 - 消除所有阴影区域
        const fillLight1 = new THREE.DirectionalLight(0xffffff, 0.6);
        fillLight1.position.set(-5, 5, -5);
        this.scene.add(fillLight1);
        
        const fillLight2 = new THREE.DirectionalLight(0xffffff, 0.6);
        fillLight2.position.set(5, 5, 5);
        this.scene.add(fillLight2);
        
        // 额外的底部补光 - 减少底部阴影
        const bottomLight = new THREE.DirectionalLight(0xffffff, 0.4);
        bottomLight.position.set(0, -5, 0);
        this.scene.add(bottomLight);
        
        console.log('Created unified Minecraft-style lighting configuration');
    }
    
    /**
     * 创建辅助工具
     */
    createHelpers() {
        if (this.options.enableGrid) {
            this.gridHelper = new THREE.GridHelper(20, 20, 0x888888, 0xcccccc);
            this.scene.add(this.gridHelper);
        }
        
        if (this.options.enableAxes) {
            this.axesHelper = new THREE.AxesHelper(5);
            this.scene.add(this.axesHelper);
        }
    }
    
    /**
     * 创建播放按钮
     */
    createPlayButton() {
        // 如果启用自动加载，则不创建播放按钮
        if (this.options.autoLoad) {
            return;
        }
        
        console.log('createPlayButton called, this.playButton =', this.playButton);
        
        // 检查是否已经存在播放按钮
        if (this.playButton) {
            console.log('Play button already exists, skipping creation');
            return;
        }
        
        // 检查容器中是否已有播放按钮
        const existingButtons = this.container.querySelectorAll('.glb-viewer-play-button');
        if (existingButtons.length > 0) {
            console.log('Found existing play buttons in container:', existingButtons.length);
            existingButtons.forEach(btn => btn.remove());
        }
        
        const playButton = document.createElement('div');
        playButton.className = 'glb-viewer-play-button';
        playButton.style.cssText = `
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            width: 60px;
            height: 60px;
            background: rgba(0, 123, 255, 0.9);
            border: 3px solid white;
            border-radius: 50%;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 100;
            transition: all 0.3s ease;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        `;
        
        // 创建三角形播放图标
        playButton.innerHTML = `
            <div style="
                width: 0;
                height: 0;
                border-left: 20px solid white;
                border-top: 12px solid transparent;
                border-bottom: 12px solid transparent;
                margin-left: 4px;
            "></div>
        `;
        
        // 鼠标悬停效果
        playButton.addEventListener('mouseenter', () => {
            playButton.style.background = 'rgba(0, 123, 255, 1)';
            playButton.style.transform = 'translate(-50%, -50%) scale(1.1)';
        });
        
        playButton.addEventListener('mouseleave', () => {
            playButton.style.background = 'rgba(0, 123, 255, 0.9)';
            playButton.style.transform = 'translate(-50%, -50%) scale(1)';
        });
        
        // 点击加载模型
        playButton.addEventListener('click', (e) => {
            console.log('Play button clicked');
            
            // 防止重复点击
            if (this.isLoading) {
                console.log('Already loading, ignoring click');
                return;
            }
            
            this.loadModel();
        });
        
        this.container.appendChild(playButton);
        this.playButton = playButton;
        
        // 确保按钮初始状态正确
        setTimeout(() => {
            if (this.playButton) {
                this.playButton.style.transform = 'translate(-50%, -50%) scale(1)';
            }
        }, 50);
    }
    
    /**
     * 加载模型（手动触发）
     */
    async loadModel() {
        if (this.isLoading || this.isLoaded) {
            return;
        }
        
        console.log('loadModel called, options:', this.options);
        console.log('modelUrl:', this.options.modelUrl);
        console.log('modelUrls:', this.modelUrls);
        
        this.isLoading = true;
        
        // 移除播放按钮
        console.log('Removing play button...', this.playButton);
        if (this.playButton) {
            // 强制隐藏
            this.playButton.style.display = 'none';
            this.playButton.style.visibility = 'hidden';
            
            // 延迟移除以避免渲染问题
            setTimeout(() => {
                if (this.playButton) {
                    this.playButton.remove();
                    this.playButton = null;
                    console.log('Play button removed');
                }
            }, 100);
        } else {
            console.log('No play button found to remove');
        }
        
        try {
            // 检查是否是多模型模式
            if (this.modelUrls && this.modelUrls.length > 0) {
                // 多模型模式：使用工具类开始循环
                if (this.modelUrls.length > 1) {
                    // 检查是否已经在循环中，避免重复启动
                    if (!this._modelCycleTimer) {
                        window.GLBViewerUtils.startModelCycle(this, this.modelUrls, 1000, {
                            scale: [1, 1, 1]
                        });
                    } else {
                        console.log('Model cycle already running, skipping restart');
                    }
                } else {
                    // 单个模型
                    await this.loadGLB(this.modelUrls[0], {
                        scale: [1, 1, 1]
                    }, true);
                }
            } else if (this.options.modelUrl) {
                // 单模型模式
                await this.loadGLB(this.options.modelUrl, {}, true);
            } else {
                throw new Error('没有指定模型URL');
            }
            
            this.isLoaded = true;
        } catch (error) {
            console.error('Failed to load model:', error);
            this.isLoaded = false;
            
            // 加载失败时显示重试按钮
            this.createRetryButton();
        } finally {
            this.isLoading = false;
        }
    }
    
    /**
     * 创建重试按钮
     */
    createRetryButton() {
        const retryButton = document.createElement('div');
        retryButton.className = 'glb-viewer-retry-button';
        retryButton.style.cssText = `
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            padding: 12px 24px;
            background: #dc3545;
            color: white;
            border: 2px solid white;
            border-radius: 6px;
            cursor: pointer;
            font-family: Arial, sans-serif;
            font-size: 14px;
            z-index: 100;
            transition: all 0.3s ease;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
        `;
        retryButton.innerHTML = '重新加载';
        
        retryButton.addEventListener('mouseenter', () => {
            retryButton.style.background = '#c82333';
        });
        
        retryButton.addEventListener('mouseleave', () => {
            retryButton.style.background = '#dc3545';
        });
        
        retryButton.addEventListener('click', () => {
            retryButton.remove();
            this.loadModel();
        });
        
        this.container.appendChild(retryButton);
    }
    

    

    

    
    /**
     * 加载 GLB 模型
     * @param {string} url - 模型URL
     * @param {Object} options - 模型选项
     * @param {boolean} fitCamera - 是否调整摄像机以适应模型（默认true）
     */
    async loadGLB(url, options = {}, fitCamera = true) {
        try {
            console.log(`Loading GLB model: ${url}, fitCamera: ${fitCamera}`);
            
            // 显示加载指示器
            this.showLoadingIndicator();
            
            // 清除之前的模型
            this.clearModel();
            
            // 加载 GLB
            const loader = new GLTFLoader();
            const gltf = await new Promise((resolve, reject) => {
                loader.load(url, resolve, undefined, reject);
            });
            
            // 处理加载的模型
            this.model = gltf.scene;
            this.setupModel(this.model, options);
            
            // 处理动画
            if (gltf.animations && gltf.animations.length > 0) {
                this.setupAnimations(gltf.animations);
            }
            
            // 只在要求时调整相机位置
            if (fitCamera) {
                this.fitCameraToModel();
            }
            
            // 强制更新渲染器大小
            this.updateRendererSize();
            
            // 隐藏加载指示器
            this.hideLoadingIndicator();
            
            console.log('GLB model loaded successfully');
            return gltf;
            
        } catch (error) {
            console.error('Failed to load GLB model:', error);
            this.hideLoadingIndicator();
            this.showError(`加载模型失败: ${error.message}`);
            throw error;
        }
    }
    
    /**
     * 加载多个GLB模型并循环显示
     */
    async loadMultipleGLBs(urls, options = {}) {
        try {
            console.log(`Loading multiple GLB models: ${urls.length} models`);
            
            // 保存模型URL列表
            this.modelUrls = urls;
            this.currentModelIndex = -1;
            
            // 保存循环间隔时间
            if (options.cycleInterval) {
                this.cycleInterval = options.cycleInterval;
            }
            
            // 先加载第一个模型
            if (this.modelUrls.length > 0) {
                this.currentModelIndex = 0;
                await this.loadGLB(this.modelUrls[0], options.modelOptions, true);
                
                // 如果有多个模型，则开始循环
                if (this.modelUrls.length > 1) {
                    this.startModelCycle();
                }
            }
            
        } catch (error) {
            console.error('Failed to load multiple GLB models:', error);
            throw error;
        }
    }
    
    /**
     * 开始模型循环
     */
    startModelCycle() {
        // 清除已存在的计时器
        if (this.cycleTimer) {
            clearInterval(this.cycleTimer);
        }
        
        // 设置新的计时器
        this.cycleTimer = setInterval(() => {
            this.cycleModels();
        }, this.cycleInterval);
        
        console.log(`Started model cycle with interval: ${this.cycleInterval}ms`);
    }
    
    /**
     * 停止模型循环
     */
    stopModelCycle() {
        if (this.cycleTimer) {
            clearInterval(this.cycleTimer);
            this.cycleTimer = null;
            console.log('Stopped model cycle');
        }
    }
    
    /**
     * 循环切换到下一个模型（使用预加载的模型）
     */
    cyclePreloadedModels() {
        if (!this.preloadedModels || this.preloadedModels.length <= 1) return;
        
        try {
            // 计算下一个模型索引
            const nextIndex = (this.currentModelIndex + 1) % this.preloadedModels.length;            
            // 使用预加载的模型，不调整摄像机
            this.showPreloadedModel(nextIndex, this.preloadedModelOptions || {}, false);
            
        } catch (error) {
            console.error('Error cycling preloaded models:', error);
        }
    }
    
    /**
     * 循环切换到下一个模型（兼容性方法）
     */
    async cycleModels() {
        // 如果有预加载的模型，使用预加载方法
        if (this.preloadedModels && this.preloadedModels.length > 1) {
            this.cyclePreloadedModels();
            return;
        }
        
        // 否则使用原来的加载方式
        if (this.modelUrls.length <= 1) return;
        
        try {
            // 计算下一个模型索引
            const nextIndex = (this.currentModelIndex + 1) % this.modelUrls.length;
            
            console.log(`Cycling to model ${nextIndex + 1}/${this.modelUrls.length}`);
            
            // 直接使用loadGLB但不调整摄像机
            await this.loadGLB(this.modelUrls[nextIndex], this.options.modelOptions || {}, false);
            
        } catch (error) {
            console.error('Error cycling models:', error);
            // 继续尝试下一个模型
            this.currentModelIndex = (this.currentModelIndex + 1) % this.modelUrls.length;
        }
    }
    
    /**
     * 设置模型
     */
    setupModel(model, options = {}) {
        // 设置位置
        if (options.position) {
            model.position.set(...options.position);
        } else {
            model.position.set(0, 0, 0);
        }
        
        // 设置旋转
        if (options.rotation) {
            model.rotation.set(...options.rotation);
        }
        
        // 设置缩放
        if (options.scale) {
            model.scale.set(...options.scale);
        } else {
            model.scale.set(1, 1, 1);
        }
        
        // 启用阴影
        if (this.options.enableShadows) {
            model.traverse((child) => {
                if (child.isMesh) {
                    child.castShadow = true;
                    child.receiveShadow = true;
                    
                    // 优化材质
                    if (child.material) {
                        child.material.needsUpdate = true;
                    }
                }
            });
        }
        
        this.scene.add(model);
    }
    
    /**
     * 设置动画
     */
    setupAnimations(animations) {
        this.animationMixer = new THREE.AnimationMixer(this.model);
        this.animationActions = [];
        
        animations.forEach((clip, index) => {
            const action = this.animationMixer.clipAction(clip);
            this.animationActions.push({
                action: action,
                clip: clip,
                index: index
            });
        });
        
        // 默认播放第一个动画
        if (this.animationActions.length > 0) {
            this.playAnimation(0);
        }
    }
    
    /**
     * 播放动画
     */
    playAnimation(index) {
        if (index >= 0 && index < this.animationActions.length) {
            // 停止所有动画
            this.animationActions.forEach(({ action }) => {
                action.stop();
            });
            
            // 播放指定动画
            this.animationActions[index].action.play();
            console.log(`Playing animation ${index}: ${this.animationActions[index].clip.name}`);
        }
    }
    
    /**
     * 停止动画
     */
    stopAnimation() {
        this.animationActions.forEach(({ action }) => {
            action.stop();
        });
    }
    
    /**
     * 清除当前模型
     */
    clearModel() {
        if (this.model) {
            this.scene.remove(this.model);
            
            // 清理资源
            this.model.traverse((child) => {
                if (child.geometry) {
                    child.geometry.dispose();
                }
                if (child.material) {
                    if (Array.isArray(child.material)) {
                        child.material.forEach(material => material.dispose());
                    } else {
                        child.material.dispose();
                    }
                }
            });
        }
        
        // 清理动画
        if (this.animationMixer) {
            this.animationMixer.stopAllAction();
            this.animationMixer = null;
        }
        this.animationActions = [];
    }
    
    /**
     * 显示预加载的模型（不重新加载，保持摄像机状态）
     * @param {number} index - 模型索引
     * @param {Object} options - 模型选项
     * @param {boolean} fitCamera - 是否调整摄像机以适应模型（默认false）
     */
    showPreloadedModel(index, options = {}, fitCamera = false) {
        
        if (!this.preloadedModels || !this.preloadedModels[index]) {
            console.error(`Preloaded model at index ${index} not available`);
            console.log('Available models:', this.preloadedModels?.map((m, i) => ({ index: i, available: m !== null })));
            return;
        }
    
        
        // 清除当前模型
        this.clearModel();
        
        // 获取预加载的模型数据
        const gltf = this.preloadedModels[index];
        this.model = gltf.scene;
        
        // 设置模型
        this.setupModel(this.model, options);
        
        // 处理动画
        if (gltf.animations && gltf.animations.length > 0) {
            this.setupAnimations(gltf.animations);
        }
        
        // 只在明确要求时调整摄像机
        if (fitCamera) {
            console.log('Adjusting camera to fit model');
            this.fitCameraToModel();
        }
        
        // 更新当前模型索引
        this.currentModelIndex = index;
    }
    
    /**
     * 调整相机以适应模型 - 保持初始视角方向
     */
    fitCameraToModel() {
        if (!this.model) return;
        
        const box = new THREE.Box3().setFromObject(this.model);
        const size = box.getSize(new THREE.Vector3());
        const center = box.getCenter(new THREE.Vector3());
        
        const maxDim = Math.max(size.x, size.y, size.z);
        const fov = this.camera.fov * (Math.PI / 180);
        let distance = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        
        distance *= 2; // 添加一些边距
        
        // 保持初始视角方向 (正X、正Y、负Z)，只调整距离和中心点
        const direction = new THREE.Vector3(1, 0.6, -1).normalize(); // 正X、正Y、负Z方向
        this.camera.position.copy(center).add(direction.multiplyScalar(distance));
        this.camera.lookAt(center);
        
        if (this.controls) {
            this.controls.target.copy(center);
            this.controls.update();
        }
    }
    
    /**
     * 显示加载指示器
     */
    showLoadingIndicator() {
        // 首先检查容器内是否已存在loading元素
        let indicator = this.container.querySelector('.glb-viewer-loading');
        
        if (!indicator) {
            // 如果没有，创建一个新的
            this.loadingIndicatorId = 'glb-viewer-loading-' + (this.container.id || Math.random().toString(36).substr(2, 9));
            
            indicator = document.createElement('div');
            indicator.id = this.loadingIndicatorId;
            indicator.className = 'glb-viewer-loading'; // 同时保留class以便样式控制
            indicator.innerHTML = `
                <div style="
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    background: rgba(0, 0, 0, 0.8);
                    color: white;
                    padding: 20px;
                    border-radius: 8px;
                    z-index: 1000;
                    font-family: Arial, sans-serif;
                ">
                    <div style="
                        border: 3px solid #f3f3f3;
                        border-top: 3px solid #3498db;
                        border-radius: 50%;
                        width: 30px;
                        height: 30px;
                        animation: spin 1s linear infinite;
                        margin: 0 auto 10px;
                    "></div>
                    加载 3D 模型中...
                </div>
                <style>
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                </style>
            `;
            
            this.container.style.position = 'relative';
            this.container.appendChild(indicator);
        } else {
            // 如果已存在，确保它可见
            indicator.style.display = 'flex';
        }
    }
    
    /**
     * 隐藏加载指示器
     */
    hideLoadingIndicator() {
        // 优先使用保存的ID来查找特定的加载指示器
        if (this.loadingIndicatorId) {
            const indicator = document.getElementById(this.loadingIndicatorId);
            if (indicator) {
                indicator.remove();
                this.loadingIndicatorId = null;
                return;
            }
        }
        
        // 备选方案：查找容器内的所有加载指示器并移除
        const indicators = this.container.querySelectorAll('.glb-viewer-loading');
        indicators.forEach(indicator => {
            indicator.style.display = 'none';
            // 延迟移除以确保没有闪烁
            setTimeout(() => indicator.remove(), 100);
        });
        
        // 查找任何可能的loading相关元素
        const spinners = this.container.querySelectorAll('.spinner-border');
        spinners.forEach(spinner => {
            const parent = spinner.closest('.glb-viewer-loading');
            if (parent) {
                parent.style.display = 'none';
                setTimeout(() => parent.remove(), 100);
            }
        });
    }
    
    /**
     * 显示错误信息
     */
    showError(message) {
        const errorDiv = document.createElement('div');
        errorDiv.style.cssText = `
            position: absolute;
            bottom: 10px;
            left: 10px;
            right: 10px;
            background: #ff4444;
            color: white;
            padding: 12px 16px;
            border-radius: 6px;
            z-index: 999;
            font-family: Arial, sans-serif;
            font-size: 14px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
            max-width: calc(100% - 20px);
            word-wrap: break-word;
        `;
        errorDiv.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: space-between;">
                <div style="display: flex; align-items: center; flex: 1;">
                    <i class="bi bi-exclamation-triangle-fill" style="margin-right: 8px; font-size: 16px;"></i>
                    <span>${message}</span>
                </div>
                <button onclick="this.parentElement.parentElement.remove()" style="
                    background: none;
                    border: none;
                    color: white;
                    margin-left: 10px;
                    cursor: pointer;
                    font-size: 18px;
                    padding: 0;
                    width: 20px;
                    height: 20px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                ">&times;</button>
            </div>
        `;
        
        this.container.style.position = 'relative';
        this.container.appendChild(errorDiv);
        
        // 8秒后自动隐藏
        setTimeout(() => {
            if (errorDiv.parentNode) {
                errorDiv.remove();
            }
        }, 8000);
    }
    
    /**
     * 初始化 Intersection Observer 用于检测可见性
     */
    initIntersectionObserver() {
        if (!this.renderer || !this.renderer.domElement) {
            return;
        }
        
        // 检查浏览器支持
        if (!('IntersectionObserver' in window)) {
            console.log('IntersectionObserver not supported, rendering will continue');
            return;
        }
        
        // 创建观察器
        this.intersectionObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const containerId = this.container?.id || 'unknown';
                if (entry.isIntersecting) {
                    // canvas 进入视口
                    this.resumeRendering();
                } else {
                    // canvas 离开视口
                    this.stopRendering();
                }
            });
        }, {
            threshold: 0.1 // 10% 可见时就认为可见
        });
        
        // 开始观察 canvas 元素
        this.intersectionObserver.observe(this.renderer.domElement);
    }
    
    /**
     * 处理窗口大小变化和容器大小变化
     */
    handleResize() {
        // 窗口大小变化事件
        window.addEventListener('resize', () => {
            this.updateRendererSize();
        });
        
        // 初始更新尺寸
        this.updateRendererSize();
    }
    
    /**
     * 更新渲染器尺寸并检查渲染状态
     */
    updateRendererSize() {
        const width = this.container.clientWidth || this.options.width;
        const height = this.container.clientHeight || this.options.height;
        
        // 设置渲染器尺寸
        this.renderer.setSize(width, height);
        
        // 更新相机宽高比
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
        
        // 只有在渲染状态下才立即渲染一帧
        if (this.isRendering) {
            this.renderer.render(this.scene, this.camera);
        }
    }
    
    /**
     * 更新渲染器尺寸以适应容器
     */
    updateRendererSize() {
        const width = this.container.clientWidth || this.options.width;
        const height = this.container.clientHeight || this.options.height;
        
        // 设置渲染器尺寸
        this.renderer.setSize(width, height);
        
        // 更新相机宽高比
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
    }
    
    /**
     * 渲染循环
     */
    animate() {
        if (!this.isRendering) {
            return; // 如果停止渲染，直接返回
        }
        
        this.animationId = requestAnimationFrame(() => this.animate());
        
        if (this.controls) {
            this.controls.update();
        }
        
        if (this.animationMixer) {
            this.animationMixer.update(0.016); // 假设 60fps
        }
        
        this.renderer.render(this.scene, this.camera);
    }
    
    /**
     * 停止渲染
     */
    stopRendering() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }
        this.isRendering = false;
    }
    
    /**
     * 恢复渲染
     */
    resumeRendering() {
        if (this.isRendering) {
            return; // 已经在渲染中
        }
        this.isRendering = true;
        this.animate();
    }
    
    /**
     * 销毁查看器
     */
    dispose() {
        this.clearModel();
        
        // 停止渲染
        this.stopRendering();
        
        // 清理 IntersectionObserver
        if (this.intersectionObserver) {
            this.intersectionObserver.disconnect();
            this.intersectionObserver = null;
        }
        
        if (this.renderer) {
            this.renderer.dispose();
        }
        
        if (this.controls) {
            this.controls.dispose();
        }
        
        if (this.container) {
            this.container.innerHTML = '';
        }
        
        console.log('Viewer disposed completely');
    }
    
    /**
     * 导出为图片
     */
    exportImage(width = 1920, height = 1080) {
        const originalSize = this.renderer.getSize(new THREE.Vector2());
        
        this.renderer.setSize(width, height);
        this.renderer.render(this.scene, this.camera);
        
        const dataURL = this.renderer.domElement.toDataURL('image/png');
        
        this.renderer.setSize(originalSize.x, originalSize.y);
        this.renderer.render(this.scene, this.camera);
        
        return dataURL;
    }
}

// 将 GLBViewer 添加到全局作用域，使其可以在其他脚本中使用
window.GLBViewer = GLBViewer;
// 将 THREE 添加到全局作用域，使 viewer-utils.js 可以使用
window.THREE = THREE;
// 将 GLTFLoader 暴露到全局，但不尝试修改 THREE 对象本身
window.GLTFLoader = GLTFLoader;