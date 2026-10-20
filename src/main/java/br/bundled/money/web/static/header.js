/*
 * Altura real do cabeçalho fixo (.top) em --top-height no <html>: o app.css usa para o que gruda ou rola abaixo dele
 * (lista de contas do extrato, scroll-padding-top). O ResizeObserver acompanha quebra de linha e tela estreita; sem
 * JavaScript vale o valor do CSS. A restauração do histórico troca o body inteiro: observa o .top novo (htmx:load).
 */
(() => {
    if (!window.ResizeObserver) return;
    const root = document.documentElement;
    const observer = new ResizeObserver(([entry]) => {
        root.style.setProperty('--top-height', entry.target.getBoundingClientRect().height + 'px');
    });
    let observed = null;

    const watch = () => {
        const top = document.querySelector('.top');
        if (!top || top === observed) return;
        if (observed) observer.unobserve(observed);
        observer.observe(observed = top);
    };

    watch();
    document.addEventListener('htmx:load', watch);
})();
