/*
 * Totais do lançamento em várias contas enquanto se digita: soma as colunas Entra e Sai do formulário de splits e diz
 * a diferença (o servidor faz a mesma conta ao montar o formulário e confere ao salvar). Linhas são adicionadas e
 * removidas pelo servidor; aqui só se lê. Tratado por delegação no document, vale para formulários trocados pelo htmx.
 */
(() => {
    // os textos vêm do bundle forms, no <html data-texts> (Layout)
    const TEXTS = JSON.parse(document.documentElement.dataset.texts || '{}');
    const money = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});

    // Em centavos, para a soma não errar no binário; o que não é número fica fora (o servidor aponta ao salvar).
    const cents = input => {
        const value = Number(input.value);
        return input.value.trim() !== '' && Number.isFinite(value) && value > 0 ? Math.round(value * 100) : 0;
    };

    const sum = (form, name) => [...form.querySelectorAll(`input[name="${name}"]`)]
        .reduce((total, input) => total + cents(input), 0);

    document.addEventListener('input', event => {
        const form = event.target.closest('form.split-form');
        if (!form || !/^split-(in|out)$/.test(event.target.name)) return;
        const entra = sum(form, 'split-in');
        const sai = sum(form, 'split-out');
        form.querySelector('.split-sum-in').textContent = money.format(entra / 100);
        form.querySelector('.split-sum-out').textContent = money.format(sai / 100);
        const balance = form.querySelector('.split-balance');
        balance.textContent = entra === sai ? TEXTS.balanced
            : TEXTS.difference.replace('%s', money.format(Math.abs(entra - sai) / 100));
        balance.classList.toggle('neg', entra !== sai);
    });
})();
