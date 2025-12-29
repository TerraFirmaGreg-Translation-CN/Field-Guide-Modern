<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="title" content="${long_title}" />
    <meta name="description" content="${short_description}" />
    <meta property="og:type" content="website" />
    <meta property="og:url" content="https://terrafirmagreg-team.github.io/Field-Guide-Modern" />
    <meta property="og:title" content="${long_title}" />
    <meta property="og:description" content="${short_description}" />
    <meta property="og:image" content="https://terrafirmagreg-team.github.io/Field-Guide-Modern/${preview_image}" />

    <title>${long_title}</title>

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <link rel="stylesheet" href="${root}/static/style.css">
  </head>
  <body>
    <!-- Load theme switcher JavaScript early to avoid flashes of incorrectly-themed content -->
    <script src="${root}/static/theme-switcher.js"></script>

    <nav id="nav-primary" class="navbar navbar-expand-lg mb-5" data-bs-theme="dark">
      <div class="container">
        <a class="navbar-brand fw-bold" href="${index}">${title}</a>

        <!-- Navbar toggle control for small screens -->
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbar-content" aria-controls="navbar-content" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>

        <div class="collapse navbar-collapse d-flex justify-content-end" id="navbar-content">
          <ul class="navbar-nav">
            <!-- Search bar-->
             <li class="nav-item">
              <form class="d-flex" role="search" onsubmit="handleSearch(event)">
                <input id="search-box" class="form-control me-2" type="search" placeholder="Search" aria-label="Search" />
                <button class="btn btn-outline-success" type="submit">Search</button>
              </form>
             </li>

            <!-- Language submenu -->
            <li class="nav-item px-2 dropdown">
              <a class="nav-link dropdown-toggle" id="lang-dropdown-button" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <i class="bi bi-translate"></i> ${current_lang.value}
              </a>
              <div class="dropdown-menu" aria-labelledby="lang-dropdown-button">
                <#list languages as lang>
                <a href="${root}/${lang.key}/${current_entry.id}.html" class="dropdown-item">${lang.value}</a>
                </#list>
              </div>
            </li>

            <!-- Theme switcher -->
            <li class="nav-item ps-2 dropdown">
              <button id="bd-theme" class="btn btn-link nav-link dropdown-toggle" type="button" aria-expanded="false" data-bs-toggle="dropdown" data-bs-display="static">
                <i id="bd-theme-icon-active"></i>
                <span id="bd-theme-text" class="d-lg-none ms-2">Toggle theme</span>
              </button>

              <ul class="dropdown-menu dropdown-menu-end">
                <!-- Light theme -->
                <li>
                  <button class="dropdown-item d-flex align-items-center active" type="button" data-bs-theme-value="light">
                    <i class="bi bi-sun-fill me-2"></i>
                    Light
                  </button>
                </li>

                <!-- Dark theme -->
                <li>
                  <button class="dropdown-item d-flex align-items-center" type="button" data-bs-theme-value="dark">
                    <i class="bi bi-moon-stars-fill me-2"></i>
                    Dark
                  </button>
                </li>

                <!-- System theme -->
                <li>
                  <button class="dropdown-item d-flex align-items-center" type="button" data-bs-theme-value="auto">
                    <i class="bi bi-circle-half me-2"></i>
                    Auto
                  </button>
                </li>
              </ul>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <div class="container">
      <div class="row">
        <!-- Table of contents -->
        <nav class="col-md-3">
          <p class="mb-3"><strong>${text_contents}</strong></p>

          <ul class="list-unstyled lh-lg">
            <li><a href="${index}">${text_index}</a></li>
            <#list categories as category>
            <li>
              <a href="../${category.id}.html">${category.name}</a>
              <#if current_category.id == category.id && category.entries?? && category.entries?size gt 0>
              <ul>
                <#list category.entries as entry>
                <li><a href="${entry.relId}.html"<#if current_entry.relId == entry.relId> class="active"</#if>>${entry.name}</a></li>
                </#list>
              </ul>
              </#if>
            </li>
            </#list>
          </ul>
        </nav>

        <!-- Page content -->
        <div class="col-md-9">
          <nav class="mb-4" aria-label="breadcrumb">
            <ol class="breadcrumb">
              <li class="breadcrumb-item">
                <a href="../"><i class="bi bi-house-fill"></i></a>
              </li>
              <li class="breadcrumb-item">
                <a href="../${current_category.id}.html">${current_category.name}</a>
              </li>
              <li class="breadcrumb-item active" aria-current="page">
                ${current_entry.name}
              </li>
            </ol>
          </nav>

          <h1 class="d-flex align-items-center mb-4">
            <#if current_entry.iconPath?? && current_entry.iconPath?has_content>
            <img class="icon-title me-3" src="${root}/${current_entry.iconPath}" alt="${current_entry.name}" title="${current_entry.iconName!current_entry.name}" />
            </#if>
            <span>${current_entry.name}</span>
          </h1>

          ${current_entry.innerHtml}
        </div>
      </div>
    </div>

    <div class="container">
      <footer class="py-3 my-5 border-top">
        <ul class="nav justify-content-center">

          <!-- GitHub repo -->
          <li class="nav-item">
            <a class="nav-link px-3 text-body-secondary" href="https://github.com/TerraFirmaGreg-Team/Field-Guide-Modern">
              <i class="bi bi-github"></i> ${text_github}
            </a>
          </li>

          <!-- Discord server -->
          <li class="nav-item">
            <a class="nav-link px-3 text-body-secondary" href="https://discord.gg/AEaCzCTUwQ">
              <i class="bi bi-discord"></i> ${text_discord}
            </a>
          </li>
        </ul>
      </footer>
    </div>

    <!-- Three.js and GLB loader - 使用 ES Modules -->
    <script type="importmap">
    {
        "imports": {
            "three": "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js",
            "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/"
        }
    }
    </script>
    
    <!-- Bootstrap -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz" crossorigin="anonymous"></script>
    <script src="${root}/static/icons.min.js"></script>
    <script src="${root}/static/tooltips.js"></script>
    <script src="${root}/static/search.js"></script>
    
    <!-- GLB Viewer - 根据协议选择加载方式 -->
    <script>
    // 检测是否为本地文件协议
    if (window.location.protocol === 'file:') {
        console.warn('GLB Viewer disabled for file:// protocol due to CORS restrictions');
        window.GLBViewerUtils = { parseGLBViewer: function() {} };
    } else {
        document.write('<script src="${root}/static/viewer-utils.js"><\/script>');
        document.write('<script type="module" src="${root}/static/viewer.js"><\/script>');
        document.write('<script src="${root}/static/glb-viewer-init.js"><\/script>');
    }
    </script>
  </body>
</html>