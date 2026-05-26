(function () {
  function initIconCarousels() {
    document.querySelectorAll('.icon-carousel').forEach((container) => {
      const frames = container.querySelectorAll(':scope > span');
      if (frames.length < 2) {
        return;
      }
      let index = 0;
      frames.forEach((frame, i) => {
        frame.classList.toggle('icon-carousel-active', i === 0);
      });
      const intervalMs = Number.parseInt(container.dataset.carouselInterval || '800', 10);
      window.setInterval(() => {
        frames[index].classList.remove('icon-carousel-active');
        index = (index + 1) % frames.length;
        frames[index].classList.add('icon-carousel-active');
      }, intervalMs);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initIconCarousels);
  } else {
    initIconCarousels();
  }
})();
