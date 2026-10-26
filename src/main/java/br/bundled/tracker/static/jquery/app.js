/*
 * Horas trabalhadas — a versão jQuery: o servidor entrega o mesmo esqueleto em /, /projects e /people e a API JSON
 * (/api/week, /api/entries, /api/projects, /api/people); as telas são montadas aqui. Mesmo CSS e mesmas regras da
 * versão htmx (a validação de verdade é do servidor: um 422 traz o erro de cada campo).
 * Texto vindo do servidor entra sempre com .text() ou atributos, nunca .html(): sem injeção de HTML.
 */
$(() => {
    const $content = $('#content');
    const $status = $('#status');

    // ---------- API

    function api(method, url, body) {
        $status.prop('hidden', true);
        return $.ajax({
            method,
            url,
            dataType: 'json',
            contentType: body === undefined ? undefined : 'application/json',
            data: body === undefined ? undefined : JSON.stringify(body)
        });
    }

    /** Falha inesperada (rede, 4xx/5xx sem tratamento): avisa sem perder o que está na tela. */
    function failed(xhr) {
        const detail = xhr.responseJSON && xhr.responseJSON.detail;
        $status.text(detail || 'Não foi possível falar com o servidor.').prop('hidden', false);
    }

    /** 422: a mensagem de cada campo ao lado dele, dentro de $scope; outro erro vai para failed. */
    function rejected($scope) {
        return xhr => {
            if (xhr.status !== 422) {
                failed(xhr);
                return;
            }
            $scope.find('.error').remove();
            xhr.responseJSON.errors.forEach(error => {
                $scope.find(`[name="${error.field}"]`).first().closest('.field, td')
                    .append($('<small>', {'class': 'error', role: 'alert'}).text(error.message));
            });
        };
    }

    // ---------- pedaços de tela

    function el(tag, attributes, text) {
        const $el = $(`<${tag}>`, attributes || {});
        return text === undefined ? $el : $el.text(text);
    }

    function button(label, classes, action) {
        return el('button', {type: 'button', 'class': classes || ''}, label).on('click', action);
    }

    /** Um input ou select a partir da descrição do campo; {options} transforma em select. */
    function control(field, value, attributes) {
        const base = Object.assign({name: field.name}, attributes);
        if (field.options) {
            const $select = el('select', Object.assign(base, {required: field.required}));
            $select.append(el('option', {value: ''}, 'Escolha…'));
            field.options.forEach(option => $select.append(el('option', {value: option.id}, option.name)));
            return $select.val(String(value ?? ''));
        }
        return el('input', Object.assign(base, {
            type: field.type || 'text', required: field.required, maxlength: field.maxlength, value: value ?? ''
        }));
    }

    /** Um campo com rótulo, como no formulário de cadastro. */
    function labeled(field, value, prefix) {
        const id = `${prefix}-${field.name}`;
        return el('div', {'class': 'field'}).append(
            el('label', {for: id}, field.label),
            control(field, value, {id}));
    }

    function valuesOf($scope) {
        const values = {};
        $scope.find('[name]').each(function () {
            values[this.name] = $(this).val();
        });
        return values;
    }

    function badge(active) {
        return el('span', {'class': active ? 'badge active' : 'badge inactive'}, active ? 'Ativo' : 'Inativo');
    }

    function time(datetime, label) {
        return el('time', {datetime}, label);
    }

    function fadeOut($row, done) {
        $row.fadeOut(200, done);
    }

    // ---------- semana

    const ENTRY_FIELDS = [
        {name: 'date', label: 'Data', type: 'date', required: true},
        {name: 'person', label: 'Pessoa', required: true, register: 'people'},
        {name: 'project', label: 'Projeto', required: true, register: 'projects'},
        {name: 'description', label: 'Descrição', maxlength: 120},
        {name: 'start', label: 'Início', type: 'time', required: true},
        {name: 'end', label: 'Fim', type: 'time', required: true}
    ];

    /** Os campos com as escolhas da semana: ativos, mais o que o lançamento já tem ({kept}). */
    function entryFields(view, kept) {
        return ENTRY_FIELDS.map(field => field.register ? Object.assign({}, field, {
            options: view[field.register].filter(item => item.active || (kept && item.id === kept[field.name]))
        }) : field);
    }

    function weekQuery(week, person) {
        return `?week=${week}` + (person ? `&person=${person}` : '');
    }

    /** Carrega a semana; {push} põe a URL no histórico (navegação e filtro). */
    function loadWeek(week, person, push) {
        const query = week ? weekQuery(week, person) : (person ? `?person=${person}` : '');
        if (push) {
            history.pushState({}, '', `/${query}`);
        }
        api('GET', `/api/week${query}`).done(renderWeek).fail(failed);
    }

    function renderWeek(view) {
        const $week = el('section', {id: 'week'}).append(
            el('div', {'class': 'week-bar'}).append(weekNav(view), personFilter(view)),
            entryForm(view),
            el('div', {'class': 'layout'}).append(entriesTable(view), totalsPanel(view.totals)));
        $content.empty().append($week);
    }

    function weekNav(view) {
        const link = (week, label, aria) => el('a', {href: `/${weekQuery(week, view.person)}`, 'aria-label': aria}, label)
            .on('click', event => {
                event.preventDefault();
                loadWeek(week, view.person, true);
            });
        const $nav = el('nav', {'class': 'week-nav'}).append(
            link(view.previous, '←', 'Semana anterior'),
            el('h2', {}, view.label),
            link(view.next, '→', 'Próxima semana'));
        if (view.week !== view.current) {
            $nav.append(link(view.current, 'Hoje', 'Semana atual'));
        }
        return $nav;
    }

    function personFilter(view) {
        const $select = el('select', {id: 'filter-person', name: 'person'})
            .append(el('option', {value: ''}, 'Todas as pessoas'));
        view.people.forEach(person => $select.append(el('option', {value: person.id}, person.name)));
        $select.val(String(view.person ?? '')).on('change', () => loadWeek(view.week, $select.val(), true));
        return el('form', {'class': 'person-filter'}).append(
            el('label', {for: 'filter-person', 'class': 'sr-only'}, 'Pessoa'), $select);
    }

    function entryForm(view) {
        const blank = {date: view.date, person: view.person};
        const $form = el('form', {id: 'entry-form', 'class': 'entry-form'});
        entryFields(view, null).forEach(field => $form.append(labeled(field, blank[field.name], 'new')));
        $form.append(el('button', {type: 'submit'}, 'Lançar'));
        return $form.on('submit', event => {
            event.preventDefault(); // o navegador já checou required/maxlength/tipos
            api('POST', '/api/entries', valuesOf($form))
                .done(entry => loadWeek(entry.week, view.person, true))
                .fail(rejected($form));
        });
    }

    function entriesTable(view) {
        const $body = el('tbody', {id: 'entries'});
        view.entries.forEach(entry => $body.append(entryRow(entry, view)));
        if (view.entries.length === 0) {
            $body.append(el('tr', {'class': 'empty'}).append(el('td', {colspan: 8}, 'Nenhum lançamento nesta semana.')));
        }
        const $head = el('tr');
        ['Dia', 'Pessoa', 'Projeto', 'Descrição', 'Início', 'Fim'].forEach(label => $head.append(el('th', {}, label)));
        $head.append(el('th', {'class': 'num'}, 'Horas'), el('th', {'aria-label': 'Ações'}));
        return el('table', {'class': 'entries'}).append(el('thead').append($head), $body);
    }

    function entryRow(entry, view) {
        const $row = el('tr', {id: `entry-${entry.id}`}).append(
            el('td').append(time(entry.date, entry.day)),
            el('td', {}, entry.personName),
            el('td', {}, entry.projectName),
            el('td', {'class': 'description'}, entry.description),
            el('td').append(time(entry.start, entry.start)),
            el('td').append(time(entry.end, entry.end)),
            el('td', {'class': 'num'}, entry.hours),
            el('td', {'class': 'actions'}).append(
                button('Editar', 'link', () => $row.replaceWith(entryEditRow(entry, view))),
                button('Excluir', 'link danger', () => {
                    if (!confirm('Excluir este lançamento?')) {
                        return;
                    }
                    api('DELETE', `/api/entries/${entry.id}`)
                        .done(() => fadeOut($row, () => loadWeek(view.week, view.person, false)))
                        .fail(failed);
                })));
        return $row;
    }

    /** A linha em edição; salvar leva à semana do lançamento, mantendo o filtro de pessoa. */
    function entryEditRow(entry, view) {
        const values = {date: entry.date, person: entry.person, project: entry.project,
            description: entry.description, start: entry.start, end: entry.end};
        const $row = el('tr', {id: `entry-${entry.id}`, 'class': 'editing'});
        entryFields(view, values).forEach(field => $row.append(
            el('td').append(control(field, values[field.name], {'aria-label': field.label}))));
        $row.append(
            el('td', {'class': 'num'}, '—'),
            el('td', {'class': 'actions'}).append(
                button('Salvar', '', () => api('PUT', `/api/entries/${entry.id}`, valuesOf($row))
                    .done(saved => loadWeek(saved.week, view.person, saved.week !== view.week))
                    .fail(rejected($row))),
                button('Cancelar', 'link', () => $row.replaceWith(entryRow(entry, view)))));
        return $row;
    }

    function totalsPanel(totals) {
        const $days = el('ul', {'class': 'days'});
        totals.days.forEach(day => $days.append(el('li').append(
            el('span', {'class': 'day'}, day.day),
            el('meter', {min: 0, max: 10, low: 4, high: 9, optimum: 8, value: day.decimal, title: day.hours}),
            el('span', {'class': 'num'}, day.hours))));
        return el('aside', {id: 'totals', 'class': 'totals'}).append(
            el('h2', {}, 'Totais'),
            el('p', {'class': 'week-total'}, 'Semana ').append(el('strong').append(time('', totals.total))),
            el('h3', {}, 'Por dia'), $days,
            el('h3', {}, 'Por projeto'), shares(totals.projects),
            el('h3', {}, 'Por pessoa'), shares(totals.people));
    }

    function shares(items) {
        if (items.length === 0) {
            return el('p', {'class': 'empty'}, 'Sem horas no período.');
        }
        const $list = el('ul', {'class': 'shares'});
        items.forEach(item => $list.append(el('li').append(
            el('span', {}, item.name), el('span', {'class': 'num'}, `${item.hours} · ${item.percent}%`))));
        return $list;
    }

    // ---------- cadastros (projetos e pessoas)

    const REGISTERS = {
        '/projects': {
            api: '/api/projects', title: 'Projetos', prefix: 'project', empty: 'Nenhum projeto.',
            fields: [
                {name: 'name', label: 'Nome', required: true, maxlength: 60},
                {name: 'client', label: 'Cliente', maxlength: 60}
            ],
            confirm: item => `Excluir o projeto "${item.name}"?`
        },
        '/people': {
            api: '/api/people', title: 'Pessoas', prefix: 'person', empty: 'Nenhuma pessoa.',
            fields: [
                {name: 'name', label: 'Nome', required: true, maxlength: 60},
                {name: 'email', label: 'E-mail', type: 'email', required: true, maxlength: 120}
            ],
            confirm: item => `Excluir "${item.name}"?`
        }
    };

    function loadRegister(register) {
        api('GET', register.api).done(items => renderRegister(register, items)).fail(failed);
    }

    function renderRegister(register, items) {
        const $body = el('tbody');
        items.forEach(item => $body.append(itemRow(register, item)));
        if (items.length === 0) {
            $body.append(el('tr', {'class': 'empty'}).append(el('td', {colspan: 5}, register.empty)));
        }
        const $head = el('tr');
        register.fields.forEach(field => $head.append(el('th', {}, field.label)));
        $head.append(el('th', {}, 'Situação'), el('th', {'class': 'num'}, 'Lançamentos'), el('th', {'aria-label': 'Ações'}));
        $content.empty().append(el('section', {id: register.prefix + 's', 'class': 'registry'}).append(
            el('h2', {}, register.title),
            itemForm(register),
            el('table', {'class': 'records'}).append(el('thead').append($head), $body)));
    }

    /** Cadastrar recarrega a lista (ordenada por nome); os erros voltam ao lado de cada campo. */
    function itemForm(register) {
        const $form = el('form', {id: `${register.prefix}-form`, 'class': 'record-form'});
        register.fields.forEach(field => $form.append(labeled(field, '', register.prefix)));
        $form.append(el('button', {type: 'submit'}, 'Cadastrar'));
        return $form.on('submit', event => {
            event.preventDefault();
            api('POST', register.api, valuesOf($form)).done(() => loadRegister(register)).fail(rejected($form));
        });
    }

    /** Uma linha; {error} explica uma exclusão recusada (item em uso). */
    function itemRow(register, item, error) {
        const path = `${register.api}/${item.id}`;
        const $row = el('tr', {id: `${register.prefix}-${item.id}`, 'class': item.active ? '' : 'inactive'});
        register.fields.forEach(field => $row.append(el('td', {}, item[field.name])));
        const $actions = el('td', {'class': 'actions'}).append(
            button('Editar', 'link', () => $row.replaceWith(itemEditRow(register, item))),
            button(item.active ? 'Desativar' : 'Ativar', 'link', () => api('PATCH', path, {active: !item.active})
                .done(changed => $row.replaceWith(itemRow(register, changed)))
                .fail(failed)),
            button('Excluir', 'link danger', () => {
                if (!confirm(register.confirm(item))) {
                    return;
                }
                api('DELETE', path)
                    .done(() => fadeOut($row, () => $row.remove()))
                    .fail(xhr => xhr.status === 409
                        ? $row.replaceWith(itemRow(register, item, xhr.responseJSON.detail))
                        : failed(xhr));
            }));
        if (error) {
            $actions.append(el('small', {'class': 'error', role: 'alert'}, error));
        }
        return $row.append(el('td').append(badge(item.active)), el('td', {'class': 'num'}, item.entries), $actions);
    }

    function itemEditRow(register, item) {
        const $row = el('tr', {id: `${register.prefix}-${item.id}`, 'class': 'editing'});
        register.fields.forEach(field => $row.append(
            el('td').append(control(field, item[field.name], {'aria-label': field.label}))));
        return $row.append(
            el('td').append(badge(item.active)),
            el('td', {'class': 'num'}, item.entries),
            el('td', {'class': 'actions'}).append(
                button('Salvar', '', () => api('PUT', `${register.api}/${item.id}`, valuesOf($row))
                    .done(changed => $row.replaceWith(itemRow(register, changed)))
                    .fail(rejected($row))),
                button('Cancelar', 'link', () => $row.replaceWith(itemRow(register, item)))));
    }

    // ---------- telas e histórico

    /** A tela do caminho atual; o menu marca a tela corrente. */
    function show() {
        const path = location.pathname;
        $('.menu a[data-screen]').each(function () {
            const current = $(this).data('screen') === path;
            $(this).attr('aria-current', current ? 'page' : null);
        });
        const register = REGISTERS[path];
        if (register) {
            document.title = `${register.title} · Horas trabalhadas`;
            loadRegister(register);
            return;
        }
        document.title = 'Semana · Horas trabalhadas';
        const query = new URLSearchParams(location.search);
        loadWeek(query.get('week'), query.get('person'), false);
    }

    $('.menu').on('click', 'a[data-screen]', function (event) {
        event.preventDefault();
        history.pushState({}, '', $(this).attr('href'));
        show();
    });

    $(window).on('popstate', show);

    show();
});
