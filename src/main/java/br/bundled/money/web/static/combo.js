/*
 * Filtro ao vivo em toda dropdown: cada <select> (não múltiplo) ganha um campo de texto que filtra as opções
 * enquanto se digita, sem acento e sem caixa, por todos os termos ("merc alim" acha "Despesas › Alimentação ›
 * Mercado"). Melhoria progressiva: o <select> continua no formulário (escondido) e é ele que vai no POST; sem
 * JavaScript, fica o <select> nativo. Roda no carregamento e em cada conteúdo novo do htmx (htmx:load), então
 * formulários trocados pelo servidor (erros, hx-boost) também ganham o filtro. Teclado: ↑ ↓ Enter Esc.
 */
(() => {
    // os textos vêm do bundle forms, no <html data-texts> (Layout)
    const TEXTS = JSON.parse(document.documentElement.dataset.texts || '{}');
    let sequence = 0;

    const normalize = text => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

    const matches = (label, query) => normalize(query).split(/\s+/).filter(Boolean)
        .every(term => normalize(label).includes(term));

    const enhance = select => {
        if (select.dataset.combo !== undefined || select.multiple) {
            return;
        }
        select.dataset.combo = '';
        const listId = `combo-${++sequence}`;
        const prompt = [...select.options].find(option => option.value === '');

        const wrapper = document.createElement('div');
        wrapper.className = 'combo';

        const input = document.createElement('input');
        input.type = 'text';
        input.autocomplete = 'off';
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-controls', listId);
        input.placeholder = prompt ? prompt.text : '';
        input.required = select.required;
        ['aria-label', 'aria-describedby', 'aria-invalid'].forEach(name => {
            if (select.hasAttribute(name)) input.setAttribute(name, select.getAttribute(name));
        });

        // O <label for> passa a apontar para o campo de texto; o <select> escondido não pode ser required
        // (o navegador não consegue focar um controle inválido escondido): quem valida no cliente é o texto.
        if (select.id) {
            input.id = select.id;
            select.id = `${select.id}-native`;
        }
        select.required = false;
        select.hidden = true;
        select.tabIndex = -1;

        const list = document.createElement('ul');
        list.id = listId;
        list.setAttribute('role', 'listbox');
        list.hidden = true;

        const items = [...select.options].filter(option => option.value !== '').map((option, index) => {
            const item = document.createElement('li');
            item.id = `${listId}-${index}`;
            item.setAttribute('role', 'option');
            item.dataset.value = option.value;
            item.textContent = option.text;
            list.append(item);
            return item;
        });

        const empty = document.createElement('li');
        empty.className = 'combo-empty';
        empty.textContent = TEXTS.noResults;
        empty.hidden = true;
        list.append(empty);

        select.before(wrapper);
        wrapper.append(input, list, select);

        const selectedLabel = () => select.selectedOptions[0] && select.value !== '' ? select.selectedOptions[0].text : '';
        const visible = () => items.filter(item => !item.hidden);
        let active = null;

        const validate = () => {
            input.setCustomValidity(input.value.trim() !== '' && select.value === '' ? TEXTS.chooseFromList : '');
        };

        const activate = item => {
            items.forEach(other => other.setAttribute('aria-selected', String(other === item)));
            active = item;
            if (item) {
                input.setAttribute('aria-activedescendant', item.id);
                item.scrollIntoView({block: 'nearest'});
            } else {
                input.removeAttribute('aria-activedescendant');
            }
        };

        const filter = query => {
            items.forEach(item => {
                item.hidden = !matches(item.textContent, query);
            });
            empty.hidden = visible().length > 0;
            activate(visible()[0] ?? null);
        };

        const open = () => {
            list.hidden = false;
            input.setAttribute('aria-expanded', 'true');
        };

        const close = () => {
            list.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            activate(null);
        };

        const choose = item => {
            select.value = item.dataset.value;
            input.value = item.textContent;
            validate();
            close();
            select.dispatchEvent(new Event('change', {bubbles: true}));
        };

        /** Ao abrir sem ter digitado: todas as opções, com a escolhida em destaque. */
        const openAll = () => {
            filter('');
            open();
            activate(items.find(item => item.dataset.value === select.value) ?? visible()[0] ?? null);
        };

        input.value = selectedLabel();

        // Ao focar, o texto da escolha atual fica selecionado: digitar já substitui e filtra.
        input.addEventListener('focus', () => {
            openAll();
            input.select();
        });
        input.addEventListener('click', () => {
            if (list.hidden) openAll();
        });

        input.addEventListener('input', () => {
            select.value = '';
            filter(input.value);
            open();
            validate();
        });

        input.addEventListener('keydown', event => {
            const options = visible();
            const at = options.indexOf(active);
            switch (event.key) {
                case 'ArrowDown':
                case 'ArrowUp':
                    event.preventDefault();
                    if (list.hidden) {
                        openAll();
                        return;
                    }
                    if (options.length) {
                        const step = event.key === 'ArrowDown' ? 1 : -1;
                        activate(options[(at + step + options.length) % options.length]);
                    }
                    break;
                case 'Enter':
                    if (!list.hidden && active) {
                        event.preventDefault();
                        choose(active);
                    }
                    break;
                case 'Escape':
                    if (!list.hidden) {
                        event.preventDefault();
                        input.value = selectedLabel();
                        validate();
                        close();
                    }
                    break;
                case 'Tab':
                    if (!list.hidden && active && input.value.trim() !== '' && select.value === '') {
                        choose(active);
                    }
                    break;
            }
        });

        // mousedown, não click: escolhe antes do blur fechar a lista.
        list.addEventListener('mousedown', event => {
            event.preventDefault();
            const item = event.target.closest('[role="option"]');
            if (item) choose(item);
        });

        input.addEventListener('blur', () => {
            if (select.value === '' && input.value.trim() !== '') {
                const exact = items.find(item => normalize(item.textContent) === normalize(input.value.trim()));
                if (exact) choose(exact);
            }
            validate();
            close();
        });

        // O formulário pode ser limpo (reset) ou o valor trocado por código: o texto acompanha.
        select.addEventListener('change', () => {
            if (document.activeElement !== input) input.value = selectedLabel();
        });
    };

    const scan = root => {
        if (root instanceof Element && root.matches('select')) enhance(root);
        root.querySelectorAll?.('select').forEach(enhance);
    };

    document.addEventListener('DOMContentLoaded', () => scan(document));
    document.addEventListener('htmx:load', event => scan(event.detail.elt));
})();
