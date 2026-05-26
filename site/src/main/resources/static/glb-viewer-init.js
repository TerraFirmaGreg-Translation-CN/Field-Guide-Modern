/**
 * GLB 查看器初始化脚本
 * 使用 GLBViewerUtils 自动初始化页面中的 GLB 查看器
 */

(function() {
    'use strict';
    
    /**
     * 等待 GLBViewer 和 GLBViewerUtils 类加载完成
     */
    function waitForClasses() {
        return new Promise((resolve) => {
            let attempts = 0;
            const maxAttempts = 150; // 15秒，每100ms检查一次
            
            const checkInterval = setInterval(() => {
                attempts++;
                
                const hasGLBViewer = typeof window.GLBViewer !== 'undefined';
                const hasGLBViewerUtils = typeof window.GLBViewerUtils !== 'undefined';
                
                console.log(`Waiting for classes... Attempt ${attempts}: GLBViewer=${hasGLBViewer}, GLBViewerUtils=${hasGLBViewerUtils}`);
                
                if (hasGLBViewer && hasGLBViewerUtils) {
                    clearInterval(checkInterval);
                    console.log('Both GLBViewer and GLBViewerUtils are available');
                    resolve(true);
                } else if (attempts >= maxAttempts) {
                    clearInterval(checkInterval);
                    console.error('GLBViewer or GLBViewerUtils class not found after timeout');
                    console.log('Final state: GLBViewer=', hasGLBViewer, 'GLBViewerUtils=', hasGLBViewerUtils);
                    resolve(false);
                }
            }, 100);
        });
    }
    
    /**
     * 初始化所有 GLB 查看器
     */
    async function initGLBViewers() {
        console.log('Starting GLB viewer initialization...');
        
        // 等待 DOM 加载完成
        if (document.readyState === 'loading') {
            console.log('DOM still loading, waiting for DOMContentLoaded...');
            document.addEventListener('DOMContentLoaded', initGLBViewers);
            return;
        }
        
        console.log('DOM loaded, waiting for classes...');
        
        // 等待类加载完成
        const classesLoaded = await waitForClasses();
        
        if (classesLoaded) {
            // 使用 GLBViewerUtils 的自动初始化方法
            console.log('Initializing GLB viewers using GLBViewerUtils...');
            window.GLBViewerUtils.autoInitViewers();
        } else {
            console.error('Failed to initialize GLB viewers: classes not available');
            
            // 尝试手动延迟重试一次
            setTimeout(() => {
                if (typeof window.GLBViewerUtils !== 'undefined') {
                    console.log('Retrying GLB viewer initialization...');
                    window.GLBViewerUtils.autoInitViewers();
                } else {
                    console.error('Retry failed: GLBViewerUtils still not available');
                }
            }, 2000);
        }
    }
    
    /**
     * 创建新的查看器（函数式 API）
     */
    window.createGLBViewer = function(containerId, options = {}) {
        return waitForGLBViewerUtils().then(() => {
            if (typeof window.GLBViewerUtils !== 'undefined') {
                return window.GLBViewerUtils.createEmbeddedViewer(containerId, null, options);
            } else {
                throw new Error('GLBViewerUtils not available');
            }
        });
    };
    
    /**
     * 获取查看器实例
     */
    window.getGLBViewer = function(viewerId) {
        return window.glbViewers?.[viewerId] || null;
    };
    
    /**
     * 重新初始化所有查看器
     */
    window.reinitGLBViewers = initGLBViewers;
    
    // 自动初始化
    initGLBViewers();
    
})();