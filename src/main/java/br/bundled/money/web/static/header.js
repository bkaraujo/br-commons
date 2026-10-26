/*
 * Altura real do cabeçalho fixo (.top) em --top-height no <html>: o app.css usa para o que gruda ou rola abaixo dele
 * (lista de contas do extrato, scroll-padding-top). O ResizeObserver acompanha quebra de linha e tela estreita; sem
 * JavaScript vale o valor do CSS. A restauração do histórico troca o body inteiro: observa o .top novo (htmx:load).
 *
 * A tela atual no menu: a página inteira vem com o link marcado (aria-current); a navegação troca só o
 * <main id="content">, que diz a tela (data-screen). A cada conteúdo novo (htmx:load, também ao voltar no histórico),
 * fica marcado o link com o mesmo data-screen. Não conhece as telas: compara os dois atributos.
 */
(() => {
    const mark = () => {
        const content = document.getElementById('content');
        if (!content) return;
        document.querySelectorAll('.menu a[data-screen]').forEach(link => {
            if (link.dataset.screen === content.dataset.screen) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    };
    document.addEventListener('htmx:load', mark);

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
