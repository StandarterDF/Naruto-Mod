/* ==========================================================================
   Naruto Mod — документация. Небольшие «живые» механики сайта.
   Обычный JS, без фреймворков и CDN. Подключается как app.js с defer.
   Скрипт ничего не создаёт и ничего не удаляет: только показывает/скрывает
   уже свёрстанные блоки, поэтому страницы читаются и без JS.
   ========================================================================== */

(function () {
  'use strict';

  /* ----------------------------------------------------------------------
     1. Подсветка активного пункта навигации
     ---------------------------------------------------------------------- */

  function markActiveNav() {
    var here = window.location.pathname.split('/').pop() || 'index.html';
    var links = document.querySelectorAll('.site-nav a[href], .footer-nav a[href]');

    Array.prototype.forEach.call(links, function (link) {
      var href = (link.getAttribute('href') || '').split('#')[0];
      var inHeader = !!link.closest('.site-nav');

      if (inHeader) {
        link.classList.remove('active');
        link.removeAttribute('aria-current');
      }

      if (href && href === here) {
        link.classList.add('active');
        if (inHeader) {
          link.setAttribute('aria-current', 'page');
        }
      }
    });
  }

  /* ----------------------------------------------------------------------
     2. Фильтр техник (страница techniques.html)
     ---------------------------------------------------------------------- */

  function initJutsuFilter() {
    var panel = document.getElementById('jutsu-filter');
    if (!panel) { return; }

    var search = document.getElementById('jutsu-search');
    var reset = document.getElementById('jutsu-reset');
    var status = document.getElementById('jutsu-status');
    var empty = document.getElementById('jutsu-empty');
    var catGroup = document.getElementById('jutsu-cats');
    var typeGroup = document.getElementById('jutsu-types');

    var cards = Array.prototype.slice.call(document.querySelectorAll('.jutsu'));
    if (!cards.length) { return; }

    var originals = new Map();
    cards.forEach(function (card) {
      originals.set(card, card.innerHTML);
      card.setAttribute('data-hl', '0');

      var badge = card.querySelector('.badge');
      var type = 'other';
      if (badge) {
        var match = /badge--(instant|channel|toggle|auto|icon)/.exec(badge.className);
        if (match) { type = match[1]; }
      }
      card.setAttribute('data-type', type);

      var section = card.closest('section');
      card.setAttribute('data-cat', section && section.id ? section.id : 'other');
      card._section = section;
    });

    var state = { cat: 'all', type: 'all', query: '' };
    var total = cards.length;

    function normalize(text) {
      return (text || '')
        .toLowerCase()
        .replace(/\u0451/g, '\u0435')   // ё -> е
        .replace(/\s+/g, ' ')
        .trim();
    }

    function textOf(card) {
      if (card._text === undefined) {
        card._text = normalize(card.textContent);
      }
      return card._text;
    }

    /* Подсветка совпадений: работает по текстовым узлам, разметку не рвёт. */
    function highlight(root, needle) {
      if (!needle) { return; }
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      var nodes = [];
      var node;
      while ((node = walker.nextNode())) {
        var parent = node.parentNode;
        if (!parent) { continue; }
        var tag = parent.nodeName;
        if (tag === 'KBD' || tag === 'CODE' || tag === 'MARK') { continue; }
        if (normalize(node.nodeValue).indexOf(needle) !== -1) { nodes.push(node); }
      }
      nodes.forEach(function (textNode) {
        var raw = textNode.nodeValue;
        var lower = raw.toLowerCase();
        var at = lower.indexOf(needle);
        if (at === -1) { return; }
        var frag = document.createDocumentFragment();
        var cursor = 0;
        while (at !== -1) {
          if (at > cursor) { frag.appendChild(document.createTextNode(raw.slice(cursor, at))); }
          var mark = document.createElement('mark');
          mark.className = 'hit';
          mark.textContent = raw.slice(at, at + needle.length);
          frag.appendChild(mark);
          cursor = at + needle.length;
          at = lower.indexOf(needle, cursor);
        }
        if (cursor < raw.length) { frag.appendChild(document.createTextNode(raw.slice(cursor))); }
        textNode.parentNode.replaceChild(frag, textNode);
      });
    }

    function chipValue(group) {
      if (!group) { return 'all'; }
      var active = group.querySelector('.chip.is-active');
      return active ? active.getAttribute('data-filter') : 'all';
    }

    function apply() {
      state.cat = chipValue(catGroup);
      state.type = chipValue(typeGroup);
      state.query = normalize(search ? search.value : '');

      var shown = 0;
      var perSection = new Map();

      cards.forEach(function (card) {
        var okCat = state.cat === 'all' || card.getAttribute('data-cat') === state.cat;
        var okType = state.type === 'all' || card.getAttribute('data-type') === state.type;
        var okQuery = !state.query || textOf(card).indexOf(state.query) !== -1;
        var visible = okCat && okType && okQuery;

        if (card.getAttribute('data-hl') === '1') {
          card.innerHTML = originals.get(card);
          card.setAttribute('data-hl', '0');
        }

        card.classList.toggle('is-filtered-out', !visible);
        if (visible) {
          if (state.query) {
            highlight(card, state.query);
            card.setAttribute('data-hl', '1');
          }
          shown++;
        }

        if (card._section) {
          var id = card._section.id || 'x';
          perSection.set(id, (perSection.get(id) || 0) + (visible ? 1 : 0));
          card._section._hasCards = true;
        }
      });

      // Прячем пустые группы, но не трогаем секции без карточек техник.
      Array.prototype.forEach.call(document.querySelectorAll('section.section'), function (section) {
        if (!section._hasCards) { return; }
        var visible = (perSection.get(section.id || 'x') || 0) > 0;
        section.classList.toggle('is-filtered-out', !visible);
      });

      if (status) {
        var parts = [];
        if (state.cat !== 'all') { parts.push('группа: ' + labelOf(catGroup)); }
        if (state.type !== 'all') { parts.push('тип: ' + labelOf(typeGroup)); }
        if (state.query) { parts.push('поиск: «' + (search ? search.value.trim() : '') + '»'); }
        var tail = parts.length ? ' (' + parts.join(', ') + ')' : '';
        status.textContent = 'Показано техник: ' + shown + ' из ' + total + tail + '.';
      }

      if (empty) { empty.hidden = shown !== 0; }
    }

    function labelOf(group) {
      if (!group) { return ''; }
      var active = group.querySelector('.chip.is-active');
      return active ? active.textContent.trim() : '';
    }

    function bindGroup(group) {
      if (!group) { return; }
      group.addEventListener('click', function (event) {
        var chip = event.target.closest ? event.target.closest('.chip') : null;
        if (!chip || !group.contains(chip)) { return; }
        Array.prototype.forEach.call(group.querySelectorAll('.chip'), function (other) {
          other.classList.remove('is-active');
          other.setAttribute('aria-pressed', 'false');
        });
        chip.classList.add('is-active');
        chip.setAttribute('aria-pressed', 'true');
        apply();
      });
    }

    bindGroup(catGroup);
    bindGroup(typeGroup);

    if (search) {
      var timer = null;
      search.addEventListener('input', function () {
        window.clearTimeout(timer);
        timer = window.setTimeout(apply, 120);
      });
      search.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
          search.value = '';
          apply();
        }
      });
    }

    if (reset) {
      reset.addEventListener('click', function () {
        if (search) { search.value = ''; }
        [catGroup, typeGroup].forEach(function (group) {
          if (!group) { return; }
          Array.prototype.forEach.call(group.querySelectorAll('.chip'), function (chip) {
            var isAll = chip.getAttribute('data-filter') === 'all';
            chip.classList.toggle('is-active', isAll);
            chip.setAttribute('aria-pressed', isAll ? 'true' : 'false');
          });
        });
        apply();
      });
    }

    apply();
  }

  /* ----------------------------------------------------------------------
     3. Оглавление «следит» за прокруткой
     ---------------------------------------------------------------------- */

  function initScrollSpy() {
    var links = Array.prototype.slice.call(document.querySelectorAll('.toc-list a[href^="#"]'));
    if (!links.length) { return; }

    var pairs = [];
    links.forEach(function (link) {
      var target = document.getElementById(link.hash.slice(1));
      if (target) { pairs.push({ link: link, target: target }); }
    });
    if (!pairs.length) { return; }

    var ticking = false;

    function update() {
      ticking = false;
      var current = pairs[0];
      pairs.forEach(function (pair) {
        if (pair.target.getBoundingClientRect().top <= 140) { current = pair; }
      });
      if (current.target.classList.contains('is-filtered-out')) { return; }
      pairs.forEach(function (pair) {
        pair.link.classList.toggle('is-current', pair === current);
      });
    }

    function onScroll() {
      if (ticking) { return; }
      ticking = true;
      window.requestAnimationFrame(update);
    }

    window.addEventListener('scroll', onScroll, { passive: true });
    update();
  }

  /* ----------------------------------------------------------------------
     4. Горячие клавиши и якоря
     ---------------------------------------------------------------------- */

  function initShortcuts() {
    var search = document.getElementById('jutsu-search');

    document.addEventListener('keydown', function (event) {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) { return; }
      var tag = (event.target && event.target.nodeName) || '';
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') { return; }
      if (!search) { return; }
      event.preventDefault();
      search.focus();
      search.select();
    });
  }

  function openAnchoredDetails() {
    if (!window.location.hash) { return; }
    var target = document.getElementById(window.location.hash.slice(1));
    if (!target) { return; }
    var details = target.closest ? target.closest('details') : null;
    if (details) { details.open = true; }
  }

  /* ---------------------------------------------------------------------- */

  function init() {
    markActiveNav();
    initJutsuFilter();
    initScrollSpy();
    initShortcuts();
    openAnchoredDetails();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
