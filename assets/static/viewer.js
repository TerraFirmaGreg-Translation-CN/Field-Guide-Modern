/**
 * GLB 3D 模型查看器组件
 * 类似于 PageRenderer.parseMultiblockPage 的结构
 */
class GLBViewer {
    constructor(containerId, options = {}) {
        this.container = document.getElementById(containerId);
        if (!this.container) {
            throw new Error(`Container with id '${containerId}' not found`);
        }
        
        this.options = {
            width: 800,
            height: 600,
            backgroundColor: 0xf0f0f0,
            enableControls: true,
            enableGrid: true,
            enableAxes: true,
            enableShadows: true,
            autoRotate: false,
            rotationSpeed: 0.01,
            ...options
        };
        
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
    }
    
    /**
     * 创建场景
     */
    createScene() {
        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(this.options.backgroundColor);
        this.scene.fog = new THREE.Fog(0xf0f0f0, 500, 10000);
    }
    
    /**
     * 创建相机
     */
    createCamera() {
        const aspect = this.options.width / this.options.height;
        this.camera = new THREE.PerspectiveCamera(75, aspect, 0.1, 1000);
        this.camera.position.set(5, 5, 5);
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
        this.renderer.outputEncoding = THREE.sRGBEncoding;
        this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
        this.renderer.toneMappingExposure = 1;
    }
    
    /**
     * 创建控制器
     */
    createControls() {
        if (this.options.enableControls && THREE.OrbitControls) {
            this.controls = new THREE.OrbitControls(this.camera, this.renderer.domElement);
            this.controls.enableDamping = true;
            this.controls.dampingFactor = 0.05;
            this.controls.enableZoom = true;
            this.controls.autoRotate = this.options.autoRotate;
            this.controls.autoRotateSpeed = this.options.rotationSpeed * 60;
            this.controls.minDistance = 1;
            this.controls.maxDistance = 50;
        }
    }
    
    /**
     * 创建灯光
     */
    createLights() {
        // 环境光
        const ambientLight = new THREE.AmbientLight(0x404040, 0.4);
        this.scene.add(ambientLight);
        
        // 主方向光
        const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
        directionalLight.position.set(10, 10, 5);
        directionalLight.castShadow = this.options.enableShadows;
        directionalLight.shadow.mapSize.width = 2048;
        directionalLight.shadow.mapSize.height = 2048;
        directionalLight.shadow.camera.near = 0.5;
        directionalLight.shadow.camera.far = 50;
        directionalLight.shadow.camera.left = -10;
        directionalLight.shadow.camera.right = 10;
        directionalLight.shadow.camera.top = 10;
        directionalLight.shadow.camera.bottom = -10;
        this.scene.add(directionalLight);
        
        // 补充光
        const fillLight = new THREE.DirectionalLight(0xffffff, 0.3);
        fillLight.position.set(-5, 5, -5);
        this.scene.add(fillLight);
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
     * 加载 GLB 模型
     * 类似于 parseMultiblockPage 中的模型处理
     */
    async loadGLB(url, options = {}) {
        try {
            console.log(`Loading GLB model: ${url}`);
            
            // 显示加载指示器
            this.showLoadingIndicator();
            
            // 清除之前的模型
            this.clearModel();
            
            // 加载 GLB
            const loader = new THREE.GLTFLoader();
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
     * 调整相机以适应模型
     */
    fitCameraToModel() {
        if (!this.model) return;
        
        const box = new THREE.Box3().setFromObject(this.model);
        const size = box.getSize(new THREE.Vector3());
        const center = box.getCenter(new THREE.Vector3());
        
        const maxDim = Math.max(size.x, size.y, size.z);
        const fov = this.camera.fov * (Math.PI / 180);
        let cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        
        cameraZ *= 2; // 添加一些边距
        
        this.camera.position.set(center.x, center.y, center.z + cameraZ);
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
        const indicator = document.createElement('div');
        indicator.id = 'glb-viewer-loading';
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
    }
    
    /**
     * 隐藏加载指示器
     */
    hideLoadingIndicator() {
        const indicator = document.getElementById('glb-viewer-loading');
        if (indicator) {
            indicator.remove();
        }
    }
    
    /**
     * 显示错误信息
     */
    showError(message) {
        const errorDiv = document.createElement('div');
        errorDiv.innerHTML = `
            <div style="
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                background: #ff4444;
                color: white;
                padding: 20px;
                border-radius: 8px;
                z-index: 1000;
                font-family: Arial, sans-serif;
                max-width: 300px;
            ">
                <h3 style="margin: 0 0 10px 0;">错误</h3>
                <p style="margin: 0;">${message}</p>
            </div>
        `;
        
        this.container.style.position = 'relative';
        this.container.appendChild(errorDiv);
        
        // 5秒后自动隐藏
        setTimeout(() => {
            errorDiv.remove();
        }, 5000);
    }
    
    /**
     * 处理窗口大小变化
     */
    handleResize() {
        window.addEventListener('resize', () => {
            const width = this.container.clientWidth || this.options.width;
            const height = this.container.clientHeight || this.options.height;
            
            this.camera.aspect = width / height;
            this.camera.updateProjectionMatrix();
            this.renderer.setSize(width, height);
        });
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

// 全局函数，便于在 HTML 中调用
window.GLBViewer = GLBViewer;