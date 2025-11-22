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
        
        // 获取容器的实际尺寸，如果容器没有设置尺寸则使用默认值
        const containerWidth = this.container.clientWidth || 400;
        const containerHeight = this.container.clientHeight || 300;
        
        this.options = {
            width: containerWidth,
            height: containerHeight,
            backgroundColor: 0xe6f3ff, // Minecraft 风格的浅蓝色背景
            enableControls: true,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            rotationSpeed: 0.01,
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
        
        // 加载状态
        this.isLoaded = false;
        this.isLoading = false;
        
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
        this.initDragAndDrop();
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
     * 创建灯光 - Minecraft 风格的超明亮光照
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
            console.log('Play button clicked', e);
            e.preventDefault();
            e.stopPropagation();
            
            // 防止重复点击
            if (this.isLoading) {
                console.log('Already loading, ignoring click');
                return;
            }
            
            this.loadModel();
        });
        
        // 防止双击
        playButton.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
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
                    window.GLBViewerUtils.startModelCycle(this, this.modelUrls, 1000, {
                        scale: [1, 1, 1]
                    });
                } else {
                    // 单个模型
                    await this.loadGLB(this.modelUrls[0], {
                        scale: [1, 1, 1]
                    });
                }
            } else if (this.options.modelUrl) {
                // 单模型模式
                await this.loadGLB(this.options.modelUrl);
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
     * 初始化拖拽功能
     */
    initDragAndDrop() {
        const dropZone = this.container;
        const dropOverlay = this.createDropOverlay();
        
        // 防止默认的拖拽行为
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, preventDefaults, false);
            document.body.addEventListener(eventName, preventDefaults, false);
        });
        
        // 高亮拖拽区域
        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, highlight, false);
        });
        
        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, unhighlight, false);
        });
        
        // 处理拖拽的文件
        dropZone.addEventListener('drop', handleDrop, false);
        
        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }
        
        function highlight(e) {
            dropZone.classList.add('drag-over');
            dropOverlay.style.display = 'flex';
        }
        
        function unhighlight(e) {
            dropZone.classList.remove('drag-over');
            dropOverlay.style.display = 'none';
        }
        
        function handleDrop(e) {
            const dt = e.dataTransfer;
            const files = dt.files;
            
            if (files.length > 0) {
                handleFiles(files);
            }
        }
        
        function handleFiles(files) {
            ([...files]).forEach(uploadFile);
        }
        
        const self = this;
        function uploadFile(file) {
            if (!file.name.match(/\.(glb|gltf)$/i)) {
                alert('请选择 .glb 或 .gltf 文件');
                return;
            }
            
            console.log('Processing file:', file.name);
            self.loadGLBFromFile(file);
        }
    }
    
    /**
     * 创建拖拽覆盖层
     */
    createDropOverlay() {
        const overlay = document.createElement('div');
        overlay.id = 'drop-overlay';
        overlay.innerHTML = `
            <div style="text-align: center; color: #007bff;">
                <i class="bi bi-cloud-upload" style="font-size: 48px;"></i>
                <h4>释放以加载 GLB 文件</h4>
                <p>支持 .glb 和 .gltf 格式</p>
            </div>
        `;
        overlay.style.cssText = `
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 123, 255, 0.1);
            border: 3px dashed #007bff;
            border-radius: 8px;
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            pointer-events: none;
        `;
        
        this.container.appendChild(overlay);
        return overlay;
    }
    
    /**
     * 从文件加载 GLB
     */
    async loadGLBFromFile(file) {
        try {
            console.log(`Loading GLB from file: ${file.name}`);
            
            // 创建文件 URL
            const url = URL.createObjectURL(file);
            
            // 加载模型
            await this.loadGLB(url, {
                position: [0, 0, 0],
                scale: [1, 1, 1]
            });
            
            // 清理 URL 对象
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            
            console.log('GLB file loaded successfully');
            
        } catch (error) {
            console.error('Failed to load GLB file:', error);
            this.showError(`加载模型失败: ${error.message}`);
        }
    }
    
    /**
     * 加载 GLB 模型
     */
    async loadGLB(url, options = {}) {
        try {
            console.log(`Loading GLB model: ${url}`);
            
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
            
            // 自动调整相机位置
            this.fitCameraToModel();
            
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
                await this.loadGLB(this.modelUrls[0], options.modelOptions);
                
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
     * 循环切换到下一个模型
     */
    async cycleModels() {
        if (this.modelUrls.length <= 1) return;
        
        try {
            // 保存当前相机和控制器状态
            const cameraPosition = this.camera.position.clone();
            const cameraQuaternion = this.camera.quaternion.clone();
            const cameraTarget = this.controls ? this.controls.target.clone() : null;
            
            // 计算下一个模型索引
            this.currentModelIndex = (this.currentModelIndex + 1) % this.modelUrls.length;
            
            console.log(`Cycling to model ${this.currentModelIndex + 1}/${this.modelUrls.length}`);
            
            // 加载下一个模型
            await this.loadGLB(this.modelUrls[this.currentModelIndex], this.options.modelOptions || {});
            
            // 恢复相机和控制器状态
            this.camera.position.copy(cameraPosition);
            this.camera.quaternion.copy(cameraQuaternion);
            this.camera.updateMatrixWorld(true);
            
            if (this.controls && cameraTarget) {
                this.controls.target.copy(cameraTarget);
                this.controls.update();
            }
            
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
        
        console.log(`Updated renderer size: ${width}x${height}`);
    }
    
    /**
     * 渲染循环
     */
    animate() {
        requestAnimationFrame(() => this.animate());
        
        if (this.controls) {
            this.controls.update();
        }
        
        if (this.animationMixer) {
            this.animationMixer.update(0.016); // 假设 60fps
        }
        
        this.renderer.render(this.scene, this.camera);
    }
    
    /**
     * 销毁查看器
     */
    dispose() {
        this.clearModel();
        
        if (this.renderer) {
            this.renderer.dispose();
        }
        
        if (this.controls) {
            this.controls.dispose();
        }
        
        if (this.container) {
            this.container.innerHTML = '';
        }
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