/*
 * O htmx guarda no histórico o HTML da página (htmx:beforeHistorySave), e marcar uma caixa não muda o atributo checked:
 * voltar mostraria as caixas como o servidor as mandou, sobre o conteúdo que elas mudaram depois (o gráfico da Visão
 * Geral, o painel dos Parcelamentos). Antes de guardar, o estado de cada caixa vira o atributo.
 */
(() => {
    document.addEventListener('htmx:beforeHistorySave', event => {
        event.detail.historyElt.querySelectorAll('input[type=checkbox], input[type=radio]')
            .forEach(box => { box.defaultChecked = box.checked; });
    });
})();
