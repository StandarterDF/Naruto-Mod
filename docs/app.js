/* ==========================================================================
   Naruto Mod — вики. Скрипты сайта: боковое меню, поиск, оглавление.
   Обычный JS без фреймворков. Страницы читаются и без него: скрипт только
   подсвечивает навигацию, открывает поиск и следит за прокруткой.
   Поисковый индекс (search-index.js) грузится лениво — при первом обращении
   к строке поиска, поэтому на чтение страниц он не влияет.
   ========================================================================== */

(function () {
  "use strict";

  /* ------------------------------------------------------------------------
     Утилиты
     ------------------------------------------------------------------------ */

  function norm(text) {
    return (text || "")
      .toLowerCase()
      .replace(/\u0451/g, "\u0435")   // ё -> е
      .replace(/\s+/g, " ")
      .trim();
  }

  function esc(text) {
    return (text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function normWithMap(text) {
    // Возвращает нормализованный текст и карту «позиция -> индекс в оригинале»,
    // чтобы подсветка совпадала с исходной строкой даже после свёртки пробелов.
    var lower = text.toLowerCase().replace(/\u0451/g, "\u0435");
    var out = "";
    var map = [];
    var lastSpace = false;
    for (var i = 0; i < lower.length; i++) {
      var ch = lower.charAt(i);
      if (/\s/.test(ch)) {
        if (lastSpace || out.length === 0) { continue; }
        out += " ";
        map.push(i);
        lastSpace = true;
      } else {
        out += ch;
        map.push(i);
        lastSpace = false;
      }
    }
    while (out.charAt(out.length - 1) === " ") {
      out = out.slice(0, -1);
      map.pop();
    }
    return { text: out, map: map };
  }

  /* ------------------------------------------------------------------------
     1. Подсветка активного пункта бокового меню
     ------------------------------------------------------------------------ */

  function markActiveNav() {
    var here = window.location.pathname.split("/").pop() || "index.html";
    var links = document.querySelectorAll(".side__link[href]");
    Array.prototype.forEach.call(links, function (link) {
      var href = (link.getAttribute("href") || "").split("#")[0];
      if (href && href === here) {
        link.classList.add("is-active");
        link.setAttribute("aria-current", "page");
      }
    });
  }

  /* ------------------------------------------------------------------------
     2. Поиск
     ------------------------------------------------------------------------ */

  function initSearch() {
    var root = document.getElementById("search");
    if (!root) { return; }

    var input = document.getElementById("search-input");
    var panel = document.getElementById("search-panel");
    var index = null;
    var loading = false;
    var cursor = -1;
    var hits = [];

    function loadIndex(done) {
      if (index) { done(); return; }
      if (loading) { return; }
      loading = true;
      var script = document.createElement("script");
      var base = document.querySelector("script[data-search-index]");
      script.src = base ? base.getAttribute("data-search-index") : "search-index.js";
      script.onload = function () {
        index = window.NARUTO_INDEX || { pages: [], items: [] };
        done();
      };
      script.onerror = function () {
        index = { pages: [], items: [] };
        panel.innerHTML = '<div class="search__none">Не удалось загрузить поисковый индекс.</div>';
        open();
      };
      document.head.appendChild(script);
    }

    function open() { root.classList.add("search--open"); }
    function close() { root.classList.remove("search--open"); cursor = -1; }

    function snippetFor(body, tokens) {
      var prepared = normWithMap(body);
      var pos = -1;
      for (var t = 0; t < tokens.length && pos === -1; t++) {
        pos = prepared.text.indexOf(tokens[t]);
      }
      if (pos === -1) { pos = 0; }
      var from = Math.max(0, pos - 70);
      var to = Math.min(prepared.text.length, pos + 170);
      var slice = prepared.text.slice(from, to);
      var prefix = from > 0 ? "…" : "";
      var suffix = to < prepared.text.length ? "…" : "";
      var html = esc(prefix + slice + suffix);
      tokens.forEach(function (token) {
        if (token.length < 2) { return; }
        var re = new RegExp("(" + token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "gi");
        html = html.replace(re, "<mark>$1</mark>");
      });
      return html;
    }

    function titleHtml(title, tokens) {
      var html = esc(title);
      tokens.forEach(function (token) {
        if (token.length < 2) { return; }
        var re = new RegExp("(" + token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "gi");
        html = html.replace(re, "<mark>$1</mark>");
      });
      return html;
    }

    function search(query) {
      var tokens = norm(query).split(" ").filter(function (t) { return t.length > 0; });
      if (!tokens.length) { hits = []; return; }

      hits = [];
      var items = index.items;
      for (var i = 0; i < items.length; i++) {
        var item = items[i];
        var title = norm(item[2]);
        var body = norm(item[3]);
        var score = 0;
        var ok = true;
        for (var t = 0; t < tokens.length; t++) {
          var token = tokens[t];
          var inTitle = title.indexOf(token) !== -1;
          var occurrences = body.split(token).length - 1;
          if (!inTitle && occurrences === 0) { ok = false; break; }
          score += inTitle ? 8 : 0;
          score += Math.min(occurrences, 4);
        }
        if (!ok) { continue; }
        if (title === norm(query)) { score += 20; }
        hits.push({ item: item, score: score });
      }

      hits.sort(function (a, b) { return b.score - a.score; });
      hits = hits.slice(0, 24);
    }

    function render(query) {
      var tokens = norm(query).split(" ").filter(function (t) { return t.length > 0; });
      if (!tokens.length) { close(); return; }
      if (!hits.length) {
        panel.innerHTML = '<div class="search__none">Ничего не найдено. Попробуйте короче: «клоны», «камуи», «рецепт».</div>';
        open();
        return;
      }
      var html = '<div class="search__meta">Найдено: ' + hits.length +
        (hits.length === 24 ? " (первые 24)" : "") + "</div>";
      hits.forEach(function (hit, i) {
        var item = hit.item;
        var url = item[0] + (item[1] ? "#" + item[1] : "");
        var page = index.pages[item[4]];
        html += '<a class="search__hit' + (i === cursor ? " is-cursor" : "") + '" href="' + url + '">' +
          '<span class="search__hit-title">' + titleHtml(item[2], tokens) + "</span>" +
          '<span class="search__hit-where">' + esc(page) + "</span>" +
          '<span class="search__hit-snippet">' + snippetFor(item[3], tokens) + "</span>" +
          "</a>";
      });
      panel.innerHTML = html;
      open();
    }

    function onInput() {
      var query = input.value;
      if (!norm(query)) { close(); return; }
      loadIndex(function () {
        search(input.value);
        cursor = -1;
        render(input.value);
      });
    }

    function moveCursor(delta) {
      if (!hits.length) { return; }
      cursor = (cursor + delta + hits.length + 1) % (hits.length + 1) - 1;
      if (cursor < -1) { cursor = hits.length - 1; }
      if (cursor >= hits.length) { cursor = -1; }
      var nodes = panel.querySelectorAll(".search__hit");
      Array.prototype.forEach.call(nodes, function (node, i) {
        node.classList.toggle("is-cursor", i === cursor);
      });
      if (cursor >= 0 && nodes[cursor]) {
        nodes[cursor].scrollIntoView({ block: "nearest" });
      }
    }

    var timer = null;
    input.addEventListener("input", function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(onInput, 90);
    });
    input.addEventListener("focus", function () { loadIndex(function () {}); if (input.value) { onInput(); } });
    input.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown") { event.preventDefault(); moveCursor(1); }
      else if (event.key === "ArrowUp") { event.preventDefault(); moveCursor(-1); }
      else if (event.key === "Enter") {
        var node = cursor >= 0 ? panel.querySelectorAll(".search__hit")[cursor]
                               : panel.querySelector(".search__hit");
        if (node) { window.location.href = node.getAttribute("href"); }
      } else if (event.key === "Escape") {
        input.value = "";
        close();
        input.blur();
      }
    });

    document.addEventListener("click", function (event) {
      if (!root.contains(event.target)) { close(); }
    });

    document.addEventListener("keydown", function (event) {
      var tag = (event.target && event.target.nodeName) || "";
      var inField = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || event.target.isContentEditable;
      if ((event.ctrlKey || event.metaKey) && (event.key === "k" || event.key === "K")) {
        event.preventDefault();
        input.focus();
        input.select();
        return;
      }
      if (event.key === "/" && !inField && !event.ctrlKey && !event.metaKey && !event.altKey) {
        event.preventDefault();
        input.focus();
        input.select();
      }
      if (event.key === "/" && inField && event.target === input) { return; }
    });
  }

  /* ------------------------------------------------------------------------
     3. Оглавление страницы следит за прокруткой
     ------------------------------------------------------------------------ */

  function initTocSpy() {
    var links = Array.prototype.slice.call(document.querySelectorAll(".page-toc a[href^='#']"));
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
      pairs.forEach(function (pair) {
        pair.link.classList.toggle("is-current", pair === current);
      });
    }
    function onScroll() {
      if (ticking) { return; }
      ticking = true;
      window.requestAnimationFrame(update);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    update();
  }

  /* ------------------------------------------------------------------------
     4. Якоря заголовков и раскрытие спойлеров
     ------------------------------------------------------------------------ */

  function initHeadingLinks() {
    var headings = document.querySelectorAll(".doc h2[id], .doc h3[id]");
    Array.prototype.forEach.call(headings, function (heading) {
      var link = document.createElement("a");
      link.className = "hlink";
      link.href = "#" + heading.id;
      link.textContent = "#";
      link.setAttribute("aria-label", "Ссылка на раздел");
      heading.appendChild(link);
    });
  }

  function openAnchored(scroll) {
    if (!window.location.hash) { return; }
    var target = document.getElementById(window.location.hash.slice(1));
    if (!target) { return; }
    var details = target.closest ? target.closest("details") : null;
    if (details) {
      details.open = true;
      // Переход по якорю внутри той же страницы не перезагружает её: браузер
      // успевает отскроллить к сложенному спойлеру, и раскрытый ответ уезжает
      // вниз за экран — после раскрытия возвращаем его в поле зрения.
      if (scroll) { details.scrollIntoView({ block: "nearest" }); }
    }
  }

  // Ссылки внутри страницы (оглавление, ответы FAQ, результаты поиска) меняют
  // только hash: без этого обработчика спойлер по якорю так и остался бы свёрнут.
  window.addEventListener("hashchange", function () { openAnchored(true); });

  /* ------------------------------------------------------------------------ */

  function init() {
    markActiveNav();
    initSearch();
    initTocSpy();
    initHeadingLinks();
    openAnchored();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
