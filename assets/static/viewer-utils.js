/**
 * GLB 查看器工具函数
 * 提供给 PageRenderer 等模块使用的实用方法
 */

class GLBViewerUtils {
    
    /**
     * 类似于 PageRenderer.parseMultiblockPage 的方法
     * 在指定容器中创建一个嵌入式的 GLB 查看器
     */
    static createEmbeddedViewer(containerId, modelUrl, options = {}) {
        const defaultOptions = {
            width: 800,
            height: 400,
            backgroundColor: 0xf0f0f0,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            rotationSpeed: 0.01,
            showLoadingIndicator: true,
            showErrorMessages: true
        };
        
        const finalOptions = { ...defaultOptions, ...options };
        
        // 创建容器包装器（如果不存在）
        const container = document.getElementById(containerId);
        if (!container) {
            console.error(`Container with id '${containerId}' not found`);
            return null;
        }
        
        // 添加包装样式
        const wrapper = document.createElement('div');
        wrapper.className = 'glb-viewer-wrapper';
        wrapper.style.cssText = `
            margin: 15px 0;
            padding: 10px;
            border: 1px solid #dee2e6;
            border-radius: 8px;
            background: #f8f9fa;
        `;
        
        // 添加标题
        if (finalOptions.title) {
            const title = document.createElement('div');
            title.className = 'glb-viewer-title';
            title.style.cssText = `
                font-size: 1rem;
                font-weight: 600;
                margin-bottom: 8px;
                color: #495057;
            `;
            title.innerHTML = `<i class="bi bi-badge-3d"></i> ${finalOptions.title}`;
            wrapper.appendChild(title);
        }
        
        // 创建查看器容器
        const viewerContainer = document.createElement('div');
        viewerContainer.id = `${containerId}-viewer`;
        viewerContainer.style.cssText = `
            width: 100%;
            height: ${finalOptions.height}px;
            border: 2px solid #dee2e6;
            border-radius: 6px;
            background: white;
        `;
        wrapper.appendChild(viewerContainer);
        
        // 添加控制提示
        if (finalOptions.showControlsHint !== false) {
            const hint = document.createElement('div');
            hint.className = 'glb-viewer-hint';
            hint.style.cssText = `
                font-size: 0.875rem;
                color: #6c757d;
                margin-top: 5px;
            `;
            hint.innerHTML = '<i class="bi bi-mouse"></i> 使用鼠标拖拽旋转，滚轮缩放，右键平移';
            wrapper.appendChild(hint);
        }
        
        // 替换原容器内容
        container.innerHTML = '';
        container.appendChild(wrapper);
        
        // 初始化查看器
        try {
            const viewer = new GLBViewer(viewerContainer.id, finalOptions);
            
            // 加载模型
            if (modelUrl) {
                viewer.loadGLB(modelUrl, finalOptions.modelOptions).catch(error => {
                    if (finalOptions.showErrorMessages !== false) {
                        this.showError(viewerContainer, error.message);
                    }
                });
            }
            
            return viewer;
            
        } catch (error) {
            console.error('Failed to create GLB viewer:', error);
            this.showError(viewerContainer, `初始化失败: ${error.message}`);
            return null;
        }
    }
    
    /**
     * 创建多方块模型查看器
     * 专门用于显示多方块结构
     */
    static createMultiblockViewer(containerId, modelUrl, multiblockData = {}) {
        const options = {
            width: 800,
            height: 500,
            backgroundColor: 0xf0f0f0,
            enableGrid: true,
            enableAxes: true,
            enableShadows: true,
            autoRotate: true,
            rotationSpeed: 0.005,
            title: '多方块 3D 视图',
            modelOptions: {
                position: [0, 0, 0],
                scale: [1, 1, 1]
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
            width: 400,
            height: 300,
            backgroundColor: 0xffffff,
            enableGrid: false,
            enableAxes: false,
            enableShadows: true,
            autoRotate: false,
            title: blockData.name || '方块 3D 视图',
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
            const title = element.dataset.title;
            const autoRotate = element.dataset.autoRotate === 'true';
            const height = element.dataset.height || 400;
            
            // 设置元素ID
            element.id = containerId;
            
            // 根据类型创建不同的查看器
            let viewer = null;
            const options = {
                title: title,
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
            
            // 创建HTML内容
            const html = `
                <div id="${containerId}" class="glb-viewer-container"></div>
            `;
            
            // 添加到缓冲区
            buffer.append(html);
            
            // 在DOM更新后初始化查看器
            setTimeout(() => {
                this.createEmbeddedViewer(containerId, modelUrl, {
                    title: options.title || '3D 模型视图',
                    autoRotate: options.autoRotate !== false,
                    enableGrid: options.enableGrid || false,
                    enableAxes: options.enableAxes || false,
                    height: options.height || 400,
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