/*
 * Valor em reais formatado enquanto se digita (input[data-money]): sempre com 2 casas, os dígitos entram pela direita,
 * como numa maquininha (1 → 0,01; 123 → 1,23; 123456 → 1.234,56). Apagar tira o último dígito; colar "R$ 1.234,56"
 * aproveita só os dígitos. Com data-money="signed" um "-" no começo é mantido. O servidor lê esse formato (Forms.money); sem JavaScript, aceita 1234,56 ou 1234.56.
 */
(() => {
    const BRL = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const DIGITS = 15;

    document.addEventListener('input', event => {
        const input = event.target;
        if (!input.matches || !input.matches('input[data-money]')) return;
        // data-money="signed": um "-" na frente é mantido (saldo inicial negativo)
        const negative = input.dataset.money === 'signed' && input.value.trim().startsWith('-');
        const digits = input.value.replace(/\D/g, '').replace(/^0+/, '').slice(0, DIGITS);
        input.value = digits ? (negative ? '-' : '') + BRL.format(Number(digits) / 100) : negative ? '-' : '';
    });
})();
