/*
 * A navegação de mês do cabeçalho (Ledger.monthNav) é a mesma em todas as telas com mês: cada controle vai para a URL
 * em que a página está com ?month= trocado, então mantém o resto (a conta do extrato, as contas marcadas dos
 * parcelamentos). O htmx guarda o href do link "boosted" ao processá-lo, então o clique é tratado aqui (fase de
 * captura, antes do htmx): htmx.ajax com o próprio link como origem faz o mesmo que o boost (HX-Boosted, #content,
 * histórico). O seletor de mês usa o link oculto .month-go, para seguir o mesmo caminho. Sem JavaScript, os links
 * levam só o mês.
 */
(() => {
    const to = month => {
        const url = new URL(location.href);
        url.searchParams.set('month', month);
        return url.pathname + url.search;
    };

    document.addEventListener('click', event => {
        const link = event.target.closest('a[data-month]');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0) return;
        event.preventDefault();
        event.stopPropagation();
        htmx.ajax('GET', to(link.dataset.month), {source: link, target: '#content', swap: 'outerHTML'});
    }, true);

    document.addEventListener('change', event => {
        const select = event.target;
        if (!select.matches || !select.matches('select[data-month-pick]')) return;
        const go = select.closest('.month-nav').querySelector('a.month-go');
        go.dataset.month = select.value;
        go.click();
    });
})();
