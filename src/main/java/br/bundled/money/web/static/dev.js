/*
 * Hot reload no desenvolvimento: pergunta a /dev/reload a cada segundo por um token que muda quando o servidor
 * reinicia ou um arquivo observado muda (estáticos, classes do hotswap). Mudou: recarrega o CSS e troca o <body>
 * pelo htmx, como uma navegação boosted da mesma URL, sem recarregar a página. Sem a rota (404, fora do
 * desenvolvimento) para de perguntar; servidor fora do ar (reiniciando) só tenta de novo.
 */
(() => {
    const INTERVAL = 1000;
    let token = null;

    const reload = () => {
        document.querySelectorAll('link[rel="stylesheet"]').forEach(link => {
            const url = new URL(link.href);
            url.searchParams.set('v', token);
            link.href = url.toString();
        });
        return htmx.ajax('GET', location.pathname + location.search, {
            source: document.body, target: 'body', swap: 'innerHTML', headers: {'HX-Boosted': 'true'}
        });
    };

    const check = async () => {
        try {
            const response = await fetch('/dev/reload', {cache: 'no-store'});
            if (response.status === 404) return;
            const current = await response.text();
            const changed = token !== null && current !== token;
            token = current;
            if (changed) await reload();
        } catch {
            // servidor reiniciando: tenta de novo
        }
        setTimeout(check, INTERVAL);
    };

    check();
})();
