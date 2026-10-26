/*
 * Gráfico de linhas Receitas x Despesas: lê os data-* (label, income, expense) das linhas de .chart-data, mais
 * recente primeiro, e desenha um SVG em .line-chart, do mês mais antigo ao atual. A tabela continua sendo o
 * conteúdo: sem JavaScript, ou para leitores de tela, é ela que vale. Roda no carregamento e em cada conteúdo novo
 * do htmx (htmx:load), então a troca de contas redesenha o gráfico.
 */
(() => {
    // os textos vêm do bundle forms, no <html data-texts> (Layout)
    const TEXTS = JSON.parse(document.documentElement.dataset.texts || '{}');
    const NS = 'http://www.w3.org/2000/svg';
    const WIDTH = 640, HEIGHT = 240;
    const PAD = {top: 16, right: 16, bottom: 28, left: 64};
    const SERIES = [
        {key: 'income', label: TEXTS.income, css: 'income'},
        {key: 'expense', label: TEXTS.expenses, css: 'expense'}
    ];
    const money = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    const compact = new Intl.NumberFormat('pt-BR', {notation: 'compact', maximumFractionDigits: 1});

    const node = (tag, attributes, parent) => {
        const element = document.createElementNS(NS, tag);
        Object.entries(attributes).forEach(([name, value]) => element.setAttribute(name, value));
        parent?.appendChild(element);
        return element;
    };

    /** Um teto "redondo" acima do maior valor: 1, 2 ou 5 × 10ⁿ. */
    const ceiling = max => {
        if (max <= 0) return 1;
        const power = 10 ** Math.floor(Math.log10(max));
        return [1, 2, 5, 10].map(step => step * power).find(value => value >= max);
    };

    const draw = chart => {
        const target = chart.querySelector('.line-chart');
        const rows = [...chart.querySelectorAll('.chart-data tbody tr')].reverse();
        if (!target || rows.length === 0) return;
        const points = rows.map(row => ({
            label: row.dataset.label,
            income: Number(row.dataset.income),
            expense: Number(row.dataset.expense)
        }));
        const top = ceiling(Math.max(...points.flatMap(point => [point.income, point.expense])));
        const plotWidth = WIDTH - PAD.left - PAD.right, plotHeight = HEIGHT - PAD.top - PAD.bottom;
        const x = index => PAD.left + (points.length === 1 ? plotWidth / 2 : index * plotWidth / (points.length - 1));
        const y = value => PAD.top + plotHeight - value / top * plotHeight;

        const svg = node('svg', {viewBox: `0 0 ${WIDTH} ${HEIGHT}`, role: 'presentation'});
        for (let step = 0; step <= 4; step++) {
            const value = top * step / 4;
            node('line', {class: 'grid', x1: PAD.left, x2: WIDTH - PAD.right, y1: y(value), y2: y(value)}, svg);
            node('text', {class: 'axis', x: PAD.left - 8, y: y(value) + 4, 'text-anchor': 'end'}, svg)
                .textContent = compact.format(value);
        }
        points.forEach((point, index) => node('text', {
            class: 'axis', x: x(index), y: HEIGHT - 8, 'text-anchor': 'middle'
        }, svg).textContent = point.label);

        SERIES.forEach(series => {
            node('polyline', {
                class: `series ${series.css}`,
                points: points.map((point, index) => `${x(index)},${y(point[series.key])}`).join(' ')
            }, svg);
            points.forEach((point, index) => node('circle', {
                class: `dot ${series.css}`, cx: x(index), cy: y(point[series.key]), r: 3.5
            }, svg).appendChild(node('title', {})).textContent =
                `${series.label} · ${point.label}: ${money.format(point[series.key])}`);
        });

        const legend = document.createElement('ul');
        legend.className = 'chart-legend';
        SERIES.forEach(series => {
            const item = document.createElement('li');
            item.className = series.css;
            item.textContent = series.label;
            legend.appendChild(item);
        });
        target.replaceChildren(svg, legend);
    };

    const scan = root => {
        if (root instanceof Element && root.matches('.home-chart')) draw(root);
        root.querySelectorAll?.('.home-chart').forEach(draw);
    };

    document.addEventListener('DOMContentLoaded', () => scan(document));
    document.addEventListener('htmx:load', event => scan(event.detail.elt));
})();
