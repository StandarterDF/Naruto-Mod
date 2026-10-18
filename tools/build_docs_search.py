#!/usr/bin/env python3
"""Builds ``docs/search-index.js`` and checks the wiki's links, anchors and images.

The wiki pages are plain hand-written HTML, so both jobs live here: the search index is built by
parsing the pages themselves, and the same pass verifies that everything they link to exists.

The index has two parts, both read by ``docs/app.js``:

* ``pages`` - the short names of the pages, in the order they appear in the sidebar;
* ``items`` - one row per search hit: ``[file, anchor, title, text, page index]``. Every
  ``<section id="...">`` becomes a hit (its ``<h2>`` is the title, the text below it the body),
  a ``<details class="deep" id="...">`` becomes a hit of its own (FAQ answers are searched
  separately and deep links open them), and the page head becomes a hit with an empty anchor so
  a search can also point at the top of a page.

The link check walks every ``href`` / ``src`` on every page: local pages and images must exist,
``#anchor`` must have a matching ``id`` on the target page, and duplicate ids are reported because
they silently break anchors. External links and "mailto:" are skipped.

The stylesheet link every page carries is stamped with a hash of ``style.css`` itself
(``style.css?v=<sha1>``), because GitHub Pages caches the file for ten minutes and a reload
revalidates only the page, not its subresources - an unstamped link can therefore keep the previous
stylesheet on screen after a deploy. A normal run rewrites the stamp in every page; ``--check``
reports a stale stamp as an error.

Usage:
    python tools/build_docs_search.py            # rewrite docs/search-index.js and report problems
    python tools/build_docs_search.py --check    # fail (exit 1) if the index or the stamps are stale
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
OUT = DOCS / "search-index.js"
STYLE = DOCS / "style.css"
# The stylesheet link every page carries; the version query is rewritten to the current stamp.
STYLE_LINK = re.compile(r'(href="style\.css)(?:\?v=[^"]*)?(")')

# The order pages appear in the sidebar, plus the short name the search results show for them.
PAGE_NAMES = {
    "index.html": "Обзор",
    "install.html": "Установка",
    "basics.html": "Основы",
    "techniques.html": "Техники",
    "clones.html": "Клоны и рабочие",
    "skills.html": "Дерево скилов",
    "mastery.html": "Освоение",
    "items.html": "Предметы и рецепты",
    "world.html": "Мир и Камуи",
    "config.html": "Настройки сервера",
    "faq.html": "Вопросы и решения",
}

VOID = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}
# Elements whose text never belongs to the page (nested inside <head>, but just in case).
NO_TEXT = {"script", "style", "template"}
# Elements that glue neighbouring words together: a space is inserted around them when reading text.
PAD = {
    "br",
    "p",
    "div",
    "section",
    "article",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "li",
    "ul",
    "ol",
    "tr",
    "td",
    "th",
    "table",
    "caption",
    "summary",
    "details",
    "dt",
    "dd",
    "blockquote",
    "pre",
    "nav",
    "header",
    "footer",
}

LINK = re.compile(r'(?:href|src)\s*=\s*"([^"]*)"', re.I)
ID = re.compile(r'\bid\s*=\s*"([^"]*)"', re.I)
SCHEME = re.compile(r"^[a-z][a-z0-9+.\-]*:", re.I)
SPACES = re.compile(r"\s+")


class Dom:
    """A tiny element tree; text nodes are ``Dom`` objects with ``text`` set."""

    __slots__ = ("tag", "attrs", "children", "text")

    def __init__(
        self, tag: str | None = None, attrs: dict | None = None, text: str | None = None
    ):
        self.tag = tag
        self.attrs = attrs or {}
        self.children: list[Dom] = []
        self.text = text

    def attr(self, name: str) -> str:
        return self.attrs.get(name) or ""


class Builder(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Dom("#document")
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = Dom(tag, dict(attrs))
        self.stack[-1].children.append(node)
        if tag not in VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.stack[-1].children.append(Dom(tag, dict(attrs)))

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data):
        if data.strip():
            self.stack[-1].children.append(Dom(None, None, data))


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse(path: Path) -> Dom:
    builder = Builder()
    builder.feed(read(path))
    builder.close()
    return builder.root


def walk(node: Dom):
    """Depth-first: every element, text nodes included."""
    for child in node.children:
        yield child
        yield from walk(child)


def find_all(node: Dom, tag: str) -> list[Dom]:
    return [n for n in walk(node) if n.tag == tag]


def text_of(node: Dom, skip: tuple[Dom, ...] = ()) -> str:
    """The readable text of a subtree: tags dropped, words glued with single spaces."""
    chunks: list[str] = []

    def visit(current: Dom):
        if current.text is not None:
            chunks.append(current.text)
            return
        if current.tag in NO_TEXT or current in skip:
            return
        pad = current.tag in PAD
        if pad:
            chunks.append(" ")
        for child in current.children:
            visit(child)
        if pad:
            chunks.append(" ")

    visit(node)
    return SPACES.sub(" ", "".join(chunks)).strip()


def page_files() -> tuple[list[Path], list[Path]]:
    """(pages the wiki knows about, in sidebar order) and (any other html files)."""
    known, extra = [], []
    for path in sorted(DOCS.rglob("*.html")):
        relative = path.relative_to(DOCS)
        (
            known
            if relative.name in PAGE_NAMES and relative.parent == Path(".")
            else extra
        ).append(path)
    known.sort(key=lambda p: list(PAGE_NAMES).index(p.name))
    return known, extra


def display_name(path: Path, warnings: list[str]) -> str:
    if path.name in PAGE_NAMES:
        return PAGE_NAMES[path.name]
    warnings.append("%s: not listed in PAGE_NAMES" % path.relative_to(ROOT))
    tree = parse(path)
    for node in walk(tree):
        if node.tag == "h1":
            return text_of(node)
    return path.stem


def extract_items(
    path: Path, name: str, index: int, errors: list[str], warnings: list[str]
) -> list[list]:
    tree = parse(path)
    main = next((n for n in walk(tree) if n.tag == "main"), None)
    if main is None:
        errors.append("%s: no <main> element" % path.name)
        main = tree

    items: list[list] = []
    heads = find_all(main, "header")
    items.append([path.name, "", name, text_of(heads[0]) if heads else "", index])

    for section in find_all(main, "section"):
        sid = section.attr("id")
        if not sid:
            warnings.append("%s: <section> without an id" % path.name)
            continue
        heading = next(
            (h for h in find_all(section, "h2") if h.attr("id").startswith("s-")), None
        )
        if heading is None:
            found = find_all(section, "h2") or find_all(section, "h3")
            heading = found[0] if found else None
        if heading is None:
            warnings.append("%s#%s: no section heading" % (path.name, sid))
        title = text_of(heading) if heading is not None else sid
        if not title:
            warnings.append("%s#%s: empty section heading" % (path.name, sid))
            title = sid

        # An indexed spoiler is a hit of its own (the FAQ answers); the rest stays in the section.
        own = [
            d
            for d in find_all(section, "details")
            if d.attr("id") and "deep" in d.attr("class")
        ]
        items.append([path.name, sid, title, text_of(section, tuple(own)), index])
        for detail in own:
            summaries = find_all(detail, "summary")
            dtitle = text_of(summaries[0]) if summaries else title
            if not dtitle:
                warnings.append("%s#%s: empty summary" % (path.name, detail.attr("id")))
                dtitle = title
            items.append(
                [
                    path.name,
                    detail.attr("id"),
                    dtitle,
                    text_of(detail, tuple(summaries)),
                    index,
                ]
            )

    return items


def encode(pages: list[str], items: list[list]) -> str:
    lines = [
        "/* Индекс поиска по вики. Собран скриптом tools/build_docs_search.py — правки затрёт.",
        "   window.NARUTO_INDEX = { pages: [названия страниц],",
        "                          items: [[файл, якорь, заголовок, текст, № страницы]] } */",
        "window.NARUTO_INDEX = {",
        '  "pages": %s,' % json.dumps(pages, ensure_ascii=False),
        '  "items": [',
    ]
    for number, item in enumerate(items):
        comma = "," if number + 1 < len(items) else ""
        lines.append(
            "    %s%s"
            % (json.dumps(item, ensure_ascii=False, separators=(",", ":")), comma)
        )
    lines.append("  ]")
    lines.append("};")
    return "\n".join(lines) + "\n"


def collect_ids(paths: list[Path], warnings: list[str]) -> dict[str, set[str]]:
    ids: dict[str, set[str]] = {}
    for page in paths:
        found = ID.findall(read(page))
        for duplicate in sorted({value for value in found if found.count(value) > 1}):
            warnings.append(
                '%s: duplicate id "%s"' % (page.relative_to(DOCS), duplicate)
            )
        ids[page.relative_to(DOCS).as_posix()] = set(found)
    return ids


def check_links(paths: list[Path], ids: dict[str, set[str]]) -> list[str]:
    errors: list[str] = []
    for page in paths:
        relative = page.relative_to(DOCS).as_posix()
        for value in LINK.findall(read(page)):
            value = value.strip()
            if not value:
                errors.append("%s: empty link" % relative)
                continue
            if value.startswith("#"):
                if value[1:] not in ids.get(relative, set()):
                    errors.append(
                        '%s: link "%s" -> no such anchor on the page'
                        % (relative, value)
                    )
                continue
            if value.startswith("//") or SCHEME.match(value):
                continue  # http:, https:, mailto: — чужие адреса, вики их не проверяет
            path, _, anchor = value.partition("#")
            path = path.split("?", 1)[0].strip()
            if not path:
                continue
            target = (page.parent / path).resolve()
            if not target.exists():
                errors.append('%s: link "%s" -> no such file' % (relative, value))
                continue
            if anchor and target.suffix.lower() == ".html":
                try:
                    key = target.relative_to(DOCS).as_posix()
                except ValueError:
                    continue
                if key in ids and anchor not in ids[key]:
                    errors.append(
                        '%s: link "%s" -> no such anchor in %s' % (relative, value, key)
                    )
    return errors


def style_stamp() -> str:
    """Short content hash of ``style.css``: the cache-busting version stamped into every page."""
    return hashlib.sha1(STYLE.read_bytes()).hexdigest()[:8]


def stamp_style(
    paths: list[Path], check: bool, errors: list[str], warnings: list[str]
) -> tuple[str, int]:
    """Point every page at the current ``style.css?v=<hash>``. Returns ``(stamp, pages)``.

    GitHub Pages serves ``style.css`` with ``Cache-Control: max-age=600``, and a reload revalidates
    only the page itself, so a page that keeps the bare URL can show the previous stylesheet for ten
    minutes after a deploy. The stamp is derived from the file, so it is always in step with it:
    a normal run rewrites the pages, ``--check`` reports a stale stamp as an error.
    """
    stamp = style_stamp()
    touched = 0
    for page in paths:
        # newline="" keeps the file's own EOLs (the checkout is CRLF on Windows).
        text = io.open(page, encoding="utf-8", newline="").read()
        if "style.css" not in text:
            warnings.append("%s: no style.css link" % page.relative_to(DOCS).as_posix())
            continue
        fixed = STYLE_LINK.sub(
            lambda match: "%s?v=%s%s" % (match.group(1), stamp, match.group(2)), text
        )
        if fixed == text:
            continue
        touched += 1
        if check:
            errors.append(
                "%s: style.css stamp is stale (expected ?v=%s)"
                % (page.relative_to(DOCS).as_posix(), stamp)
            )
        else:
            io.open(page, "w", encoding="utf-8", newline="").write(fixed)
    return stamp, touched


def check_sidebar(paths: list[Path], warnings: list[str]) -> None:
    expected = {page.name for page in paths if page.name in PAGE_NAMES}
    for page in paths:
        if page.name not in PAGE_NAMES:
            continue
        tree = parse(page)
        aside = next(
            (n for n in walk(tree) if n.tag == "aside" and "side" in n.attr("class")),
            None,
        )
        if aside is None:
            warnings.append("%s: no sidebar" % page.name)
            continue
        linked = {node.attr("href").split("#")[0] for node in find_all(aside, "a")}
        for missing in sorted(expected - linked):
            warnings.append("%s: sidebar has no link to %s" % (page.name, missing))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if the index is stale or the links are broken",
    )
    args = parser.parse_args()

    errors: list[str] = []
    warnings: list[str] = []
    known, extra = page_files()
    pages = [display_name(page, warnings) for page in known]
    pages += [display_name(page, warnings) for page in extra]

    items: list[list] = []
    for index, page in enumerate(known + extra):
        items.extend(extract_items(page, pages[index], index, errors, warnings))

    # The pages the search points at have to exist with exactly those anchors.
    ids = collect_ids(sorted(DOCS.rglob("*.html")), warnings)
    for file, anchor, title, body, index in items:
        if not anchor:
            continue
        if anchor not in ids.get(file, set()):
            errors.append('%s: index hit "%s" -> no such anchor' % (file, anchor))
        if not title or not body:
            warnings.append("%s#%s: empty search hit" % (file, anchor))

    errors += check_links(sorted(DOCS.rglob("*.html")), ids)
    check_sidebar(known, warnings)
    stamp, stamped = stamp_style(known + extra, args.check, errors, warnings)

    generated = encode(pages, items)
    existing = read(OUT) if OUT.exists() else ""
    stale = generated != existing

    print(
        "pages: %d   hits: %d   index: %.0f KB"
        % (len(pages), len(items), len(generated) / 1024)
    )
    for problem in warnings:
        print("  warning: " + problem)
    for problem in errors:
        print("  error: " + problem)
    print(
        "index: "
        + ("OUT OF DATE - run tools/build_docs_search.py" if stale else "up to date")
    )
    print(
        "css: style.css?v=%s   %s"
        % (
            stamp,
            "%d page(s) OUT OF DATE - run tools/build_docs_search.py" % stamped
            if args.check
            else ("%d page(s) restamped" % stamped if stamped else "in step"),
        )
    )
    print("links: " + ("%d problem(s)" % len(errors) if errors else "ok"))

    if args.check:
        return 1 if stale or errors else 0
    if stale:
        OUT.write_text(generated, encoding="utf-8", newline="\n")
        print("wrote %s" % OUT.relative_to(ROOT))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
