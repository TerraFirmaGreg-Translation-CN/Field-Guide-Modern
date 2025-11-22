/**
 * GLB 查看器初始化脚本
 * 处理带有 data-glb-viewers 属性的元素，实现多模型循环显示
 */

// 等待文档加载完成
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOMContentLoaded fired, checking if GLBViewerUtils should handle initialization...');
    
    // 如果GLBViewerUtils存在，让它的autoInitViewers处理所有查看器
    // 避免重复初始化
    if (typeof window.GLBViewerUtils !== 'undefined') {
        console.log('GLBViewerUtils available, delegating to autoInitViewers');
        return; // 让GLBViewerUtils.autoInitViewers处理
    }
    
    console.log('GLBViewerUtils not available, using manual initialization');
    
    // 初始化所有带有 data-glb-viewer 属性的单模型查看器
    const singleViewerElements = document.querySelectorAll('[data-glb-viewer]');
    singleViewerElements.forEach((element, index) => {
        initSingleGLBViewer(element, index);
    });
    
    // 初始化所有带有 data-glb-viewers 属性的多模型查看器
    const multiViewerElements = document.querySelectorAll('[data-glb-viewers]');
    multiViewerElements.forEach((element, index) => {
        initMultiGLBViewer(element, index);
    });
});

/**
 * 初始化单个 GLB 模型查看器
 */
function initSingleGLBViewer(element, index) {
    try {
        const viewerId = element.id || `glb-viewer-${index}`;
        const glbPath = element.getAttribute('data-glb-viewer');
        const viewerType = element.getAttribute('data-viewer-type') || 'default';
        const autoRotate = element.getAttribute('data-auto-rotate') === 'true';
        
        // 确保元素有ID
        if (!element.id) {
            element.id = viewerId;
        }
        
        // 设置容器样式
        element.style.position = 'relative';
        element.style.display = 'block';
        element.style.width = '100%';
        element.style.height = '100%';
        
        // 创建查看器选项
        const options = {
            autoRotate: autoRotate,
            rotationSpeed: 0.01
        };
        
        // 如果是多方块类型，设置特定选项
        if (viewerType === 'multiblock') {
            options.backgroundColor = 0xe6f3ff;
            options.enableControls = true;
            options.minDistance = 5;
            options.maxDistance = 50;
        }
        
        // 创建查看器实例
        const viewer = new GLBViewer(viewerId, options);
        
        // 加载模型
        if (glbPath) {
            viewer.loadGLB(glbPath);
        }
        
        console.log(`Initialized single GLB viewer: ${viewerId}`);
        
        // 存储查看器实例以便后续访问
        element.viewer = viewer;
        
        // 清理函数
        return () => {
            if (viewer && typeof viewer.dispose === 'function') {
                viewer.dispose();
            }
        };
        
    } catch (error) {
        console.error('Error initializing GLB viewer:', error);
    }
}

/**
 * 初始化多个 GLB 模型查看器（循环显示）
 */
function initMultiGLBViewer(element, index) {
    try {
        const viewerId = element.id || `multi-glb-viewer-${index}`;
        const glbPathsJson = element.getAttribute('data-glb-viewers');
        const viewerType = element.getAttribute('data-viewer-type') || 'multiblock';
        const autoRotate = element.getAttribute('data-auto-rotate') === 'true';
        const autoLoad = element.getAttribute('data-auto-load') === 'true';
        
        // 确保元素有ID
        if (!element.id) {
            element.id = viewerId;
        }
        
        // 设置容器样式
        element.style.position = 'relative';
        element.style.display = 'block';
        element.style.width = '100%';
        element.style.height = '100%';
        
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
            autoLoad: autoLoad, // 重要：传递autoLoad选项
            rotationSpeed: 0.01,
            // 存储多模型数据以供播放按钮使用
            modelUrls: glbPaths
        };
        
        // 如果是多方块类型，设置特定选项
        if (viewerType === 'multiblock') {
            options.backgroundColor = 0xe6f3ff;
            options.enableControls = true;
            options.minDistance = 5;
            options.maxDistance = 50;
        }
        
        // 创建查看器实例
        const viewer = new GLBViewer(viewerId, options);
        
        // 保存多模型数据到查看器实例
        viewer.modelUrls = glbPaths;
        viewer.currentModelIndex = -1;
        
        // 根据autoLoad决定是否立即加载
        if (autoLoad && glbPaths.length > 0) {
            // 自动加载模式：直接加载并开始循环
            setTimeout(() => {
                if (glbPaths.length > 1) {
                    // 使用工具类启动循环
                    window.GLBViewerUtils.startModelCycle(viewer, glbPaths, 1000, {
                        scale: [1, 1, 1]
                    });
                } else {
                    // 单模型直接加载
                    viewer.loadGLB(glbPaths[0], {
                        scale: [1, 1, 1]
                    });
                }
            }, 100);
        } else {
            // 非自动加载模式：显示播放按钮，等待用户点击
            console.log(`Multi-GLB viewer ${viewerId} in manual mode, showing play button`);
        }
        
        console.log(`Initialized multi GLB viewer with ${glbPaths.length} models: ${viewerId}, autoLoad: ${autoLoad}`);
        
        // 存储查看器实例以便后续访问
        element.viewer = viewer;
        
        // 清理函数
        return () => {
            if (viewer && typeof viewer.dispose === 'function') {
                viewer.dispose();
            }
        };
        
    } catch (error) {
        console.error('Error initializing multi GLB viewer:', error);
    }
}