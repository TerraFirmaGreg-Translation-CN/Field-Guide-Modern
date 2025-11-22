class GLBViewerUtils {
    
    /**
     * 类似于 PageRenderer.parseMultiblockPage 的方法
     * 在指定容器中创建一个嵌入式的 GLB 查看器
     */
    static createEmbeddedViewer(containerId, modelUrl, options = {}) {
        // 设置自适应容器样式
        const defaultOptions = {
            backgroundColor: 0xf0f0f0,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            rotationSpeed: 0.01,
            showLoadingIndicator: false, // 默认禁用加载指示器
            showErrorMessages: true
        };
        
        const finalOptions = { ...defaultOptions, ...options };
        
        // 获取容器
        const container = document.getElementById(containerId);
        if (!container) {
            console.error(`Container with id '${containerId}' not found`);
            return null;
        }
        
        // 保留容器的position设置，但不覆盖宽高属性
        container.style.position = 'relative';
        container.style.display = 'block';
        
        // 查找并管理loading指示器
        const loadingIndicator = container.querySelector('.glb-viewer-loading');
        
        // 如果启用自动加载，显示loading；否则显示原始的loading
        if (finalOptions.autoLoad && loadingIndicator) {
            // 自动加载模式，使用GLBViewer的loading管理
            // 不需要做特殊处理
        }
        
        // 初始化查看器
        console.log(`createEmbeddedViewer called with:`, {
            containerId, 
            modelUrl, 
            finalOptions,
            autoLoad: finalOptions.autoLoad
        });
        
        try {
            const viewer = new GLBViewer(containerId, {
                ...finalOptions,
                modelUrl: modelUrl, // 传递模型URL
                autoLoad: finalOptions.autoLoad || false // 默认不自动加载
            });
            
            console.log(`GLBViewer created with options:`, viewer.options);
            return viewer;
            
        } catch (error) {
            console.error('Failed to create GLB viewer:', error);
            this.showError(container, `初始化失败: ${error.message}`);
            // 错误时隐藏loading指示器
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
            return null;
        }
    }
    
    /**
     * 创建多方块模型查看器
     * 支持单个模型或多个模型循环显示（每秒切换一次）
     */
    static createMultiblockViewer(containerId, modelUrl, multiblockData = {}) {
        // 获取容器并设置尺寸
        const container = document.getElementById(containerId);
        if (container) {
            container.style.width = '100%';
            container.style.height = '100%';
        }
        
        const options = {
            backgroundColor: 0xf0f0f0,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: true,
            rotationSpeed: 0.005,
            showLoadingIndicator: false,
            modelUrl: modelUrl, // 传递模型URL
            autoLoad: multiblockData.autoLoad || false, // 传递autoLoad选项
            modelOptions: {
                position: [0, 0, 0],
                scale: [1, 1, 1]
            },
            // 添加窗口大小变化事件处理
            onViewerCreated: (viewer) => {
                const updateSize = () => {
                    if (viewer && typeof viewer.updateRendererSize === 'function') {
                        viewer.updateRendererSize();
                    }
                };
                
                window.addEventListener('resize', updateSize);
                
                // 立即执行一次以确保初始大小正确
                updateSize();
                
                // 清理函数
                return () => {
                    window.removeEventListener('resize', updateSize);
                    // 如果有循环计时器，清理它
                    if (viewer && viewer._modelCycleTimer) {
                        clearInterval(viewer._modelCycleTimer);
                        viewer._modelCycleTimer = null;
                    }
                };
            }
        };
        
        // 创建查看器
        const viewer = new GLBViewer(containerId, options);
        
        // 支持从字符串解析多个GLB文件路径（后端返回的格式）
        let glbUrls = [];
        if (typeof modelUrl === 'string' && modelUrl.includes(',')) {
            // 从逗号分隔的字符串解析多个URL
            glbUrls = modelUrl.split(',').map(url => url.trim());
        } else if (Array.isArray(modelUrl)) {
            // 直接使用URL数组
            glbUrls = modelUrl;
        } else if (modelUrl) {
            // 单个URL
            glbUrls = [modelUrl];
        }
        
        // 只有在autoLoad为true时才自动加载
        if (options.autoLoad && glbUrls.length > 0) {
            if (glbUrls.length > 1) {
                // 多模型模式：实现每秒切换一次的循环显示
                GLBViewerUtils.startModelCycle(viewer, glbUrls, 1000, options.modelOptions);
            } else if (glbUrls.length === 1) {
                // 单个模型模式
                viewer.loadGLB(glbUrls[0], options.modelOptions).catch(error => {
                    console.error('Failed to load model:', error);
                    if (options.showErrorMessages !== false) {
                        this.showError(container, error.message);
                    }
                });
            }
        }
        
        // 添加多方块信息
        if (multiblockData.size || multiblockData.pattern || multiblockData.mapping) {
            this.addMultiblockInfo(containerId, multiblockData);
        }
        
        return viewer;
    }
    
    /**
     * 启动模型循环切换显示
     * @param {Object} viewer - GLB查看器实例
     * @param {Array} modelUrls - 模型URL数组
     * @param {number} interval - 切换间隔（毫秒）
     * @param {Object} modelOptions - 模型加载选项
     */
    static startModelCycle(viewer, modelUrls, interval = 1000, modelOptions = {}) {
        console.log(`GLBViewerUtils.startModelCycle called with ${modelUrls.length} models, interval: ${interval}ms`);
        
        let currentIndex = 0;
        
        // 先加载第一个模型
        console.log(`Loading first model: ${modelUrls[0]}`);
        viewer.loadGLB(modelUrls[currentIndex], modelOptions).catch(error => {
            console.error(`Failed to load first model:`, error);
        });
        
        // 如果只有一个模型，不需要循环
        if (modelUrls.length <= 1) {
            console.log('Only one model provided, no cycle needed');
            return;
        }
        
        // 设置循环计时器
        viewer._modelCycleTimer = setInterval(() => {
            currentIndex = (currentIndex + 1) % modelUrls.length;
            console.log(`Switching to model ${currentIndex}: ${modelUrls[currentIndex]}`);
            
            viewer.loadGLB(modelUrls[currentIndex], modelOptions).catch(error => {
                console.error(`Failed to load model at index ${currentIndex}:`, error);
                // 继续尝试下一个模型
                currentIndex = (currentIndex + 1) % modelUrls.length;
            });
        }, interval);
        
        console.log(`Model cycle started with interval: ${interval}ms`);
        
        // 为查看器添加停止循环的方法
        viewer.stopModelCycle = function() {
            if (this._modelCycleTimer) {
                clearInterval(this._modelCycleTimer);
                this._modelCycleTimer = null;
                console.log('Model cycle stopped');
            }
        };
    }
    
    /**
     * 创建单方块模型查看器
     */
    static createBlockViewer(containerId, modelUrl, blockData = {}) {
        const options = {
            backgroundColor: 0xffffff,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            modelOptions: {
                position: [0, 0, 0],
                scale: [0.5, 0.5, 0.5]
            }
        };
        
        return this.createEmbeddedViewer(containerId, modelUrl, options);
    }
    
    /**
     * 添加多方块信息面板
     */
    static addMultiblockInfo(containerId, multiblockData) {
        const container = document.getElementById(containerId);
        if (!container) return;
        
        const info = document.createElement('div');
        info.className = 'glb-viewer-info';
        info.style.cssText = `
            margin-top: 10px;
            padding: 10px;
            background: white;
            border-radius: 6px;
            border-left: 4px solid #007bff;
        `;
        
        let html = '';
        if (multiblockData.size) {
            html += `<div><strong>尺寸:</strong> ${multiblockData.size}</div>`;
        }
        if (multiblockData.energy) {
            html += `<div><strong>能源:</strong> ${multiblockData.energy}</div>`;
        }
        if (multiblockData.description) {
            html += `<div><strong>描述:</strong> ${multiblockData.description}</div>`;
        }
        
        info.innerHTML = html;
        container.appendChild(info);
    }
    
    /**
     * 显示错误信息
     */
    static showError(container, message) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-warning';
        errorDiv.style.cssText = `
            margin: 10px 0;
            border-radius: 6px;
        `;
        errorDiv.innerHTML = `
            <i class="bi bi-exclamation-triangle"></i> 
            <strong>3D Model load failed:</strong> ${message}
            <br><small>Please check if the model file exists and is formatted correctly</small>
        `;
        
        container.appendChild(errorDiv);
    }
    
    /**
     * 批量初始化页面中的所有查看器
     * 扫描 data-glb-viewer 和 data-glb-viewers 属性并自动初始化
     */
    static autoInitViewers() {
        // 处理单模型查看器
        const singleElements = document.querySelectorAll('[data-glb-viewer]');
        
        singleElements.forEach(element => {
            this._initSingleViewer(element);
        });
        
        // 处理多模型查看器
        const multiElements = document.querySelectorAll('[data-glb-viewers]');
        
        multiElements.forEach(element => {
            this._initMultiViewer(element);
        });
    }
    
    /**
     * 初始化单模型查看器
     */
    static _initSingleViewer(element) {
        // 确保元素有ID
        if (!element.id) {
            element.id = `glb-viewer-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        }
        
        const containerId = element.id;
        const modelUrl = element.dataset.glbViewer;
        const type = element.dataset.viewerType || 'default';
        const autoRotate = element.dataset.autoRotate === 'true';
        const autoLoad = element.dataset.autoLoad === 'true';
        
        // 根据类型创建不同的查看器
        let viewer = null;
        const options = {
            autoRotate: autoRotate,
            autoLoad: autoLoad
        };
        
        switch (type) {
            case 'multiblock':
                viewer = this.createMultiblockViewer(containerId, modelUrl, options);
                break;
            case 'block':
                viewer = this.createBlockViewer(containerId, modelUrl, options);
                break;
            default:
                viewer = this.createEmbeddedViewer(containerId, modelUrl, options);
                break;
        }
        
        // 存储查看器实例
        if (viewer) {
            element.dataset.viewerInstance = 'true';
            console.log(`Auto-initialized single GLB viewer: ${containerId}, autoLoad: ${autoLoad}`);
        } else {
            // 如果查看器创建失败，手动隐藏loading
            const loadingIndicator = element.querySelector('.glb-viewer-loading');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        }
    }
    
    /**
     * 初始化多模型查看器
     */
    static _initMultiViewer(element) {
        // 确保元素有ID
        if (!element.id) {
            element.id = `multi-glb-viewer-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        }
        
        const containerId = element.id;
        const glbPathsJson = element.dataset.glbViewers;
        const type = element.dataset.viewerType || 'multiblock';
        const autoRotate = element.dataset.autoRotate === 'true';
        const autoLoad = element.dataset.autoLoad === 'true';
        
        // 解析GLB路径JSON数组
        let glbPaths = [];
        try {
            glbPaths = JSON.parse(glbPathsJson);
            if (!Array.isArray(glbPaths)) {
                console.error('data-glb-viewers must be a valid JSON array');
                return;
            }
        } catch (e) {
            console.error('Failed to parse data-glb-viewers JSON:', e);
            return;
        }
        
        // 创建查看器选项
        const options = {
            autoRotate: autoRotate,
            autoLoad: autoLoad
        };
        
        // 创建查看器实例（不传递modelUrl，让播放按钮逻辑处理）
        const viewer = new GLBViewer(containerId, options);
        
        // 保存多模型数据到查看器实例
        viewer.modelUrls = glbPaths;
        viewer.currentModelIndex = -1;
        
        // 根据autoLoad决定是否立即加载
        if (autoLoad && glbPaths.length > 0) {
            // 自动加载模式：直接开始循环
            setTimeout(() => {
                if (glbPaths.length > 1) {
                    this.startModelCycle(viewer, glbPaths, 1000, { scale: [1, 1, 1] });
                } else {
                    viewer.loadGLB(glbPaths[0], { scale: [1, 1, 1] });
                }
            }, 100);
        } else {
            // 非自动加载模式：显示播放按钮，等待用户点击
            console.log(`Multi-GLB viewer ${containerId} in manual mode, showing play button`);
        }
        
        // 存储查看器实例
        if (viewer) {
            element.dataset.viewerInstance = 'true';
            console.log(`Auto-initialized multi GLB viewer: ${containerId}, models: ${glbPaths.length}, autoLoad: ${autoLoad}`);
        } else {
            // 如果查看器创建失败，手动隐藏loading
            const loadingIndicator = element.querySelector('.glb-viewer-loading');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        }
    }
    
    /**
     * 为 PageRenderer 专门创建的方法
     * 类似于 parseMultiblockPage 的签名
     */
    static parseGLBViewer(buffer, identifier, modelUrl, options = {}) {
        try {
            // 生成容器ID
            const containerId = `glb-viewer-${identifier}`;
            
            // 创建HTML内容 - 不包含loading指示器
            const html = `
                <div id="${containerId}" class="glb-viewer-container" style="min-height: 300px; position: relative; display: inline-block;"></div>
            `;
            
            // 添加到缓冲区
            buffer.append(html);
            
            // 在DOM更新后初始化查看器
            setTimeout(() => {
                this.createEmbeddedViewer(containerId, modelUrl, {
                autoRotate: options.autoRotate !== false,
                enableGrid: options.enableGrid || false,
                enableAxes: options.enableAxes || false,
                showLoadingIndicator: false,
                ...options
            });
            }, 100);
            
        } catch (error) {
            console.error('Failed to parse GLB viewer:', error);
            buffer.append(`<div class="alert alert-danger">3D 查看器初始化失败: ${error.message}</div>`);
        }
    }
}

// 全局暴露
window.GLBViewerUtils = GLBViewerUtils;

// 自动初始化（如果页面加载完成）
document.addEventListener('DOMContentLoaded', function() {
    // 延迟执行，确保所有脚本都已加载
    setTimeout(() => {
        GLBViewerUtils.autoInitViewers();
    }, 200);
});