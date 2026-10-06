/*
 * Tarefas — tudo no navegador com jQuery. O servidor só entrega o esqueleto da página e a API JSON
 * (/api/tasks); a lista, as abas, a busca e o contador são renderizados aqui a partir do estado em memória.
 * Texto vindo do servidor entra sempre com .text(), nunca .html(): sem injeção de HTML.
 */
$(() => {
    const FILTERS = ['all', 'open', 'done'];

    const $list = $('#task-list');
    const $form = $('#new-task');
    const $title = $('#title');
    const $titleError = $('#title-error');
    const $status = $('#status');
    const $counter = $('#counter');
    const $search = $('#q');
    const row = $('#task-template').prop('content').firstElementChild;

    let tasks = [];
    let filter = filterOfUrl();

    // ---------- API

    function api(method, url, body) {
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

    // ---------- estado → tela

    function filterOfUrl() {
        const value = new URLSearchParams(location.search).get('filter');
        return FILTERS.includes(value) ? value : 'all';
    }

    function visible(task) {
        const needle = $search.val().toLowerCase();
        const inTab = filter === 'all' || (filter === 'done') === task.done;
        return inTab && task.title.toLowerCase().includes(needle);
    }

    function render() {
        const shown = tasks.filter(visible);
        $list.empty();
        if (shown.length === 0) {
            $list.append($('<li>', {'class': 'empty'}).text('Nenhuma tarefa'));
        }
        shown.forEach(task => $list.append(item(task)));

        $('.tabs a[data-filter]').each(function () {
            const current = $(this).data('filter') === filter;
            $(this).toggleClass('current', current).attr('aria-current', current ? 'page' : null);
        });

        const open = tasks.filter(task => !task.done).length;
        $counter.text(`${open} aberta(s) de ${tasks.length}`);
    }

    /** Uma linha, clonada do <template> da página. */
    function item(task) {
        const $item = $(row.cloneNode(true)).attr('data-id', task.id).toggleClass('done', task.done);
        $item.find('.toggle')
            .attr('aria-label', task.done ? 'Reabrir' : 'Concluir')
            .text(task.done ? '✓' : '');
        $item.find('.title').text(task.title);
        return $item;
    }

    function taskOf(element) {
        const id = $(element).closest('li').data('id');
        return tasks.find(task => task.id === id);
    }

    function replace(changed) {
        tasks = tasks.map(task => task.id === changed.id ? changed : task);
    }

    // ---------- ações

    $form.on('submit', event => {
        event.preventDefault(); // o navegador já checou required/minlength/maxlength antes deste evento
        $status.prop('hidden', true);
        api('POST', '/api/tasks', {title: $title.val()})
            .done(task => {
                tasks.push(task);
                $title.val('').trigger('focus');
                $titleError.prop('hidden', true).text('');
                render();
                $list.children(`[data-id="${task.id}"]`).addClass('added');
            })
            .fail(xhr => {
                if (xhr.status === 422) {
                    const error = xhr.responseJSON.errors.find(it => it.field === 'title');
                    $titleError.text(error ? error.message : 'Título inválido.').prop('hidden', false);
                } else {
                    failed(xhr);
                }
            });
    });

    $list.on('click', '.toggle', function () {
        const task = taskOf(this);
        api('PATCH', `/api/tasks/${task.id}`, {done: !task.done})
            .done(changed => {
                replace(changed);
                render();
            })
            .fail(failed);
    });

    $list.on('click', '.delete', function () {
        const task = taskOf(this);
        if (!confirm(`Excluir "${task.title}"?`)) {
            return;
        }
        const $item = $(this).closest('li');
        api('DELETE', `/api/tasks/${task.id}`)
            .done(() => $item.fadeOut(200, () => {
                tasks = tasks.filter(it => it.id !== task.id);
                render();
            }))
            .fail(failed);
    });

    // Abas: links de verdade (/?filter=open); com script, trocam a vista sem recarregar e entram no histórico.
    $('.tabs').on('click', 'a[data-filter]', function (event) {
        event.preventDefault();
        filter = $(this).data('filter');
        history.pushState({filter}, '', `?filter=${filter}`);
        render();
    });

    $(window).on('popstate', () => {
        filter = filterOfUrl();
        render();
    });

    $search.on('input', render);

    // ---------- início

    api('GET', '/api/tasks')
        .done(list => {
            tasks = list;
            render();
        })
        .fail(failed);
});
