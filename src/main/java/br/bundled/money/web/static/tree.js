/*
 * Ramos recolhíveis do plano de contas: cada conta raiz é um <tbody class="branch"> que nasce recolhido; um clique
 * na linha do grupo (fora do link do nome) abre e fecha. Os abertos ficam no localStorage (pelo id do tbody), reaplicados quando o
 * htmx troca a árvore (salvar, excluir) ou numa nova visita. Tratado por delegação no document.
 */
(() => {
    const KEY = 'tree-open';

    const read = () => {
        try {
            return new Set(JSON.parse(localStorage.getItem(KEY) || '[]'));
        } catch {
            return new Set(); // armazenamento bloqueado ou corrompido: tudo recolhido
        }
    };

    const write = open => {
        try {
            localStorage.setItem(KEY, JSON.stringify([...open]));
        } catch {
            // armazenamento bloqueado: vale só até a próxima troca
        }
    };

    const show = (branch, open) => {
        branch.classList.toggle('collapsed', !open);
        branch.querySelector('.toggle')?.setAttribute('aria-expanded', String(open));
    };

    const apply = root => {
        if (!root.querySelectorAll) return;
        const open = read();
        root.querySelectorAll('tbody.branch').forEach(branch => show(branch, open.has(branch.id)));
    };

    // Qualquer ponto da linha do grupo alterna, menos o link do nome (abre o razão); o botão fica para o teclado.
    document.addEventListener('click', event => {
        const row = event.target.closest('tbody.branch > tr.group');
        if (!row || event.target.closest('a')) return;
        const branch = row.closest('tbody.branch');
        const open = branch.classList.contains('collapsed');
        show(branch, open);
        const stored = read();
        if (open) stored.add(branch.id); else stored.delete(branch.id);
        write(stored);
    });

    document.addEventListener('DOMContentLoaded', () => apply(document));
    document.addEventListener('htmx:load', event => apply(event.detail.elt));
})();
