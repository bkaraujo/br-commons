/*
 * A tela atual no menu do tracker htmx: a página inteira vem com o link marcado (aria-current); a navegação troca só o
 * <main id="content">, que diz a tela (data-screen). A cada conteúdo novo (htmx:load, também ao voltar no histórico),
 * fica marcado o link com o mesmo data-screen. Não conhece as telas: compara os dois atributos.
 */
(() => {
    document.addEventListener('htmx:load', () => {
        const content = document.getElementById('content');
        if (!content) return;
        document.querySelectorAll('.menu a[data-screen]').forEach(link => {
            if (link.dataset.screen === content.dataset.screen) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    });
})();
