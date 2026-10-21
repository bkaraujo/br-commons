/*
 * Linhas recolhíveis em qualquer nível: numa <table class="outline">, cada <tr data-row> com data-parent (o data-row
 * da mãe) só aparece quando todas as mães estão abertas (.open); a mãe tem um botão .toggle e qualquer ponto da linha,
 * menos um link, abre e fecha. Nasce com o primeiro nível (as linhas sem mãe) aberto e o resto recolhido. Quando o
 * htmx troca a tabela por outra (outro mês), as abertas passam para a nova; numa nova visita, volta ao início. As
 * linhas vêm em profundidade: a mãe antes das filhas. Delegação no document.
 */
(() => {
    let carried = null; // as abertas da tabela que o htmx está trocando, até a nova assentar

    const show = table => {
        const visible = new Map(); // por data-row: se as filhas aparecem (a linha aparece e está aberta)
        table.querySelectorAll('tr[data-row]').forEach(row => {
            const parent = row.dataset.parent;
            const shown = !parent || visible.get(parent) === true;
            const open = row.classList.contains('open');
            row.classList.toggle('shown', shown);
            visible.set(row.dataset.row, shown && open);
            row.querySelector('.toggle')?.setAttribute('aria-expanded', String(open));
        });
    };

    const start = table => {
        table.querySelectorAll('tr[data-row]').forEach(row => row.classList.toggle('open',
            carried ? carried.has(row.dataset.row) : !row.dataset.parent));
        show(table);
    };

    const apply = root => {
        if (!root.querySelectorAll) return;
        if (root.matches?.('table.outline')) start(root);
        root.querySelectorAll('table.outline').forEach(start);
    };

    document.addEventListener('click', event => {
        const row = event.target.closest('table.outline tr[data-row]');
        if (!row?.querySelector('.toggle') || event.target.closest('a')) return;
        row.classList.toggle('open');
        show(row.closest('table.outline'));
    });

    document.addEventListener('htmx:beforeSwap', event => {
        const table = event.detail.target?.querySelector?.('table.outline');
        carried = table ? new Set([...table.querySelectorAll('tr.open[data-row]')].map(row => row.dataset.row)) : null;
    });
    document.addEventListener('htmx:afterSettle', () => {
        carried = null;
    });

    document.addEventListener('DOMContentLoaded', () => apply(document));
    document.addEventListener('htmx:load', event => apply(event.detail.elt));
})();
