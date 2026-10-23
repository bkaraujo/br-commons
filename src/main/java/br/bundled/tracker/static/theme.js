/*
 * Tema claro/escuro só no navegador: a escolha fica no localStorage e vira <html data-theme="auto|light|dark">,
 * que o app.css lê. Carregado no <head> sem defer, aplica o tema antes da primeira pintura (sem piscar).
 * Os botões ([data-theme-choice]) são tratados por delegação no document, então continuam valendo quando o
 * htmx troca o conteúdo (hx-boost) ou o body inteiro (restauração do histórico); depois de cada troca (htmx:load) o
 * botão pressionado é marcado de novo.
 */
(() => {
    const KEY = 'theme';
    const THEMES = ['auto', 'light', 'dark'];
    const root = document.documentElement;

    const read = () => {
        try {
            const stored = localStorage.getItem(KEY);
            return THEMES.includes(stored) ? stored : 'auto';
        } catch {
            return 'auto'; // armazenamento bloqueado: segue o sistema
        }
    };

    const save = theme => {
        try {
            localStorage.setItem(KEY, theme);
        } catch {
            // armazenamento bloqueado: vale só até sair da página
        }
    };

    const apply = theme => {
        root.dataset.theme = theme;
    };

    /** Marca o botão do tema atual e revela o seletor, que nasce hidden para quem não tem JavaScript. */
    const mark = () => {
        document.querySelectorAll('[data-theme-choice]').forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.themeChoice === root.dataset.theme));
        });
        document.querySelectorAll('.theme[hidden]').forEach(group => {
            group.hidden = false;
        });
    };

    apply(read());

    document.addEventListener('click', event => {
        const button = event.target.closest('[data-theme-choice]');
        if (!button || !THEMES.includes(button.dataset.themeChoice)) {
            return;
        }
        save(button.dataset.themeChoice);
        apply(button.dataset.themeChoice);
        mark();
    });

    document.addEventListener('DOMContentLoaded', mark);
    document.addEventListener('htmx:load', mark);

    // Outra aba mudou o tema: acompanha.
    window.addEventListener('storage', event => {
        if (event.key === KEY) {
            apply(read());
            mark();
        }
    });
})();
