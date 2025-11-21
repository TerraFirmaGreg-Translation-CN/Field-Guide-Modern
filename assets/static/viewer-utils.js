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
        
        // 移除之前可能存在的loading元素
        const existingLoading = container.querySelector('.glb-viewer-loading');
        if (existingLoading) {
            container.removeChild(existingLoading);
        }
        

        
        // 初始化查看器
        try {
            const viewer = new GLBViewer(containerId, finalOptions);
            
            // 加载模型
            if (modelUrl) {
                viewer.loadGLB(modelUrl, finalOptions.modelOptions).then(() => {
                    // 确保加载完成后隐藏loading指示器
                    const loadingIndicator = container.querySelector('.glb-viewer-loading');
                    if (loadingIndicator) {
                        loadingIndicator.style.display = 'none';
                    }
                }).catch(error => {
                    if (finalOptions.showErrorMessages !== false) {
                        this.showError(container, error.message);
                    }
                    // 错误时也隐藏loading指示器
                    const loadingIndicator = container.querySelector('.glb-viewer-loading');
                    if (loadingIndicator) {
                        loadingIndicator.style.display = 'none';
                    }
                });
            }
            
            return viewer;
            
        } catch (error) {
            console.error('Failed to create GLB viewer:', error);
            this.showError(container, `初始化失败: ${error.message}`);
            // 错误时也隐藏loading指示器
            const loadingIndicator = container.querySelector('.glb-viewer-loading');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
            return null;
        }
    }
    
    /**
     * 创建多方块模型查看器
     * 专门用于显示多方块结构
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
                };
            }
        };
        
        const viewer = this.createEmbeddedViewer(containerId, modelUrl, options);
        
        // 添加多方块信息
        if (multiblockData.size) {
            this.addMultiblockInfo(containerId, multiblockData);
        }
        
        return viewer;
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
            <strong>3D 模型加载失败:</strong> ${message}
            <br><small>请检查模型文件是否存在且格式正确</small>
        `;
        
        container.appendChild(errorDiv);
    }
    
    /**
     * 批量初始化页面中的所有查看器
     * 扫描 data-glb-viewer 属性并自动初始化
     */
    static autoInitViewers() {
        const elements = document.querySelectorAll('[data-glb-viewer]');
        
        elements.forEach(element => {
            const containerId = element.id || `glb-viewer-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
            const modelUrl = element.dataset.glbViewer;
            const type = element.dataset.viewerType || 'default';
            const autoRotate = element.dataset.autoRotate === 'true';
            const height = element.dataset.height || 400;
            
            // 设置元素ID
            element.id = containerId;
            
            // 根据类型创建不同的查看器
            let viewer = null;
            const options = {
                autoRotate: autoRotate,
                height: parseInt(height)
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
                console.log(`Auto-initialized GLB viewer: ${containerId}`);
            }
        });
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