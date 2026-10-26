# KEYBINDINGS — аудит и перераскладка комбо

Статус: **предложение, код не менялся**. Всё ниже — про то, как сейчас выдаются комбо, что в этой
схеме ломается и как её можно переделать. Приложение А — полная текущая таблица (185 техник).

Цифры сняты с дерева `1.9.0` (185 техник с комбо, `NarutoAbilities`, `NarutoKeyHandler`).

Обозначения клавиш: `1 = C`, `2 = V`, `3 = B`; предлагаемая четвёртая — `4 = N`.

---

## TL;DR

1. Комбо — это **префиксное дерево**: нажал `C V B` и остановился — кастуется то, что в этой точке
   является листом. Пока жмёшь — комбо удлиняется.
2. Дерево **упёрлось в потолок**: свободных неиспользуемых листьев глубины ≤3 осталось **три**
   (`111`, `221`, `311`). 185 техник не влезают в 39 комбинаций длиной до трёх нажатий
   (`3 + 9 + 27`).
3. Отсюда **104 техники из 185 (56 %) требуют 6 нажатий**. При `jutsuActivateDelay = 15` тиков
   (0,75 с между нажатиями) это ~4,5 секунды стояния на месте до каста.
4. Дерево **не читается по смыслу**: `132` — это `water_bullet`, а под ним 28 тайдзюцу/кендзюцу;
   `233` — `body_adaptation`, а под ним Риннеган и Восемь врат; `1223` (`sharingan`) висит под `122`
   (`chakra_feeding`). По комбо нельзя угадать технику, и дерево ничему не учит.
5. Идея с переиспользованием слотов **верная и подтверждается кодом**: глаза (`AbstractDojutsuAbility`),
   телесные режимы (`ModeBus`) и режимы-стойки уже взаимоисключающие — под них можно дать один слот.
6. Но главный выигрыш дешевле и не требует переразметки: **мгновенный каст листа** (если после
   нажатия в дереве нет продолжений — не ждать 15 тиков).
7. Стратегически нужна **четвёртая клавиша** (алфавит 4): это 64 листа глубины ≤3 вместо 27 и 256
   глубины ≤4. Тогда 84 техники из 155 доступны за ≤3 нажатия **с сохранением старых комбо как алиасов**.
8. «Чем имбовее — тем дольше» в коде **почти не выполнено**: корреляция «длина ↔ цена чакры» всего
   0,23, а `palm_block` за 0 чакры требует 6 нажатий против 4 у `kirin` за 80. Длина — плохой
   регулятор силы; за силу должен отвечать тир-бюджет + видимое удержание (I12), за запоминаемость —
   короткая одинаковая длина внутри тира.

---

## 1. Как это работает сейчас

| Место | Что делает |
| --- | --- |
| `Ability#defaultCombo()` | техника возвращает свой `long`; у 176 классов это константа `COMBO`, у остальных — литерал или `Settings#combo()` |
| `NarutoAbilities#registerKeyCombos()` | на `FMLCommonSetupEvent` собирает `Map<Long, ResourceLocation> COMBO_MAP`; дубль — только `LOGGER.error` |
| `NarutoKeyHandler#handleJustuKey(i)` | каждый клик дописывает цифру в `currentJutsuCombo` и шлёт `ServerJutsuCastingPacket(i)` (звук печати) |
| `NarutoKeyHandler#onClientTick` | если с последнего нажатия прошло `jutsuCastDelay` (15 тиков) — каст `triggerAbility(combo)`; если клавиша удерживается дольше `jutsuKeyHoldThreshold` — канал |
| `JutsuScreen` | строит список из `COMBO_MAP`, рисует комбо как буквы, группирует по `CATEGORIES` |
| Лимит | `MAX_JUTSU_VALUE = 9999999999L` — 10 цифр |
| Сервер | получает уже `ResourceLocation`, сам перепроверяет `handleCost`, кулдаун, освоение |

Три важных следствия:

* **Каст на префиксе.** `232` (sage_mode) и `2322` (byakugan) сосуществуют: нажал `C V B` и подождал —
  sage mode; нажал `C V B` и сразу `V` — byakugan. То есть «пауза» — это и есть подтверждение каста.
* **Цена ожидания платится на каждой развилке.** Около 40 листьев дерева имеют потомков, и на каждом из них
  игрок обязан выдержать паузу, чтобы «не дойти» до длинной техники.
* **Удержание последней клавиши** запускает канал, а не каст листа, — это ещё один слой, который
  приходится держать в голове.

---

## 2. Замеры

Длина комбо (число нажатий):

| Нажатий | Техник | Время до каста при 15 тиках |
| --- | --- | --- |
| 1 | 2 | 0,75 с |
| 2 | 3 | 1,5 с |
| 3 | 21 | 2,25 с |
| 4 | 26 | 3,0 с |
| 5 | 25 | 3,75 с |
| 6 | **104** | **4,5 с** |
| 7 | 4 | 5,25 с |

* Доступно за ≤3 нажатия: **26 из 185 (14 %)**.
* Самые нагруженные ветви: `132` — 28 потомков, `233` — 18, `1223` — 13, `2322` — 10, `2121` — 6.
* Свободных «чистых» листьев (ни терминала, ни префикса): глубина 2 — **0**, глубина 3 — **3**
  (`111`, `221`, `311`). Мест больше нет.
* Свободные 2-клавишные терминалы (можно поставить алиас, но он «съест» паузу у поддерева):
  `13`, `22`, `23`, `31`, `32`, `33`.
* 7-клавишные: `susanoo` (1223211), `illusory_double` (1223111), `elemental_edge` (1323121),
  `chibaku_tensei` (2331231).

---

## 3. Что именно сломано

**P1. Длина.** Больше половины книги — 6 нажатий. В бою это невозможно: 4,5 с без движения.
Дерево проектировалось как «дерево ради дерева», без бюджета на глубину.

**P2. Смысл потерян.** Родитель и ребёнок не связаны: `water_bullet` (`132`) — корень тайдзюцу и
кендзюцу; `body_adaptation` (`233`) — корень Риннегана и Восьми врат; `substitution` (`12`) — корень
всей ветви стихий. Игрок не может запомнить путь через смысл, только зубрёжкой.

**P3. Нет единой таблицы.** Комбо размазаны по 175 классам. Нет ни бюджета, ни линтера, ни места,
где видно конфликт до запуска. Дубль ловится только `LOGGER.error` в рантайме, а меню и вики
собираются из того же `COMBO_MAP` — то есть рассинхрон неизбежен.

**P4. Взаимоисключающие слоты не переиспользуются.** Пять глаз не могут быть открыты одновременно
(`closeOtherEyes`), восемь телесных режимов — тоже (`ModeBus#closeOthers`), но каждый занимает своё
комбо. Это 13 слотов, из которых реально нужно 2.

**P5. Нет алиасов.** У длинного комбо нет короткого дублёра, и нет способа дать новичку «быстрый»
путь, не ломая память ветерана.

**P6. Нет обратной связи.** Пока жмёшь — на экране ничего. Не видно ни набранного префикса, ни того,
что кастанётся, ни альтернатив. Ошибка (`jutsu.error.notfound`) приходит уже после паузы.

**P7. Пауза-подтверждение конфликтует с каналом.** Удержание последней клавиши = старт канала;
на развилке игрок не знает, ждать ему каста или держать.

**P8. Нет настройки.** Ни сервер, ни игрок не могут переразложить комбо; правка = правка 176 java-файлов.

### 3.5. Длина против силы: связь есть, но слабая

Ожидание «чем имбовее техника, тем длиннее комбо» в коде **почти не выполнено**. Замер по 132
техникам, у которых удалось разобрать цену (`masteryCost`/`useChakra`, константы раскрыты):

* корреляция «длина комбо ↔ базовая цена чакры» = **0,23**;
* средняя цена по длине: 3 нажатия — 15,3; 4 — 15,8; 5 — 27,3; 6 — **30,9**; 7 — **14,1**;
* в самом массовом ведре (6 нажатий, 79 техник) лежат одновременно `kotoamatsukami` (120 чакры),
  `tsukuyomi` (90) и `drunken_fist` / `palm_block` / `bleeding_blade` (**0 чакры**);
* `kirin` — 80 чакры и **4** нажатия, а `palm_block` — 0 чакры и **6** нажатий.

То есть длина сегодня — это **глубина дерева**, а не тир: она досталась технике по дате добавления и
месту в ветке. Совпадение с силой местами есть (у `susanoo`, `chibaku_tensei`, `elemental_edge` — по
7 нажатий), но это совпадение, а не правило.

И главное: **длина — плохой регулятор силы.** Она не наказывает за неудачный момент; она наказывает
за пинг, раскладку и занятые пальцы. Настоящие регуляторы у нас уже есть и работают: цена (0–120
чакры), кулдаун, гейт дерева, канал/зарядка (`CHANNELED`), освоение. Ультимейт должен ощущаться
тяжёлым **видимо** — зарядкой, замахом, подтверждением, — а не набором семизначного кода вслепую.

Поэтому ниже две вещи разделены: **длина комбо отвечает за запоминаемость** (одинаковая внутри тира),
**сила — за видимую ценеремонию** (зарядка/подтверждение). Это и есть ответ на вопрос «учёл ли»:
в первой редакции — нет, здесь учитываю явно, см. I12 и §5.

---

## 4. Идеи

Порядок — от «дёшево и сразу» к «большая переделка». Цена: S — часы, M — день-два, L — неделя,
XL — переписывание подсистемы.

### I1. Мгновенный каст листа (S) ⭐ самая выгодная

Если после нажатия буфер попал в узел, у которого **нет потомков** — кастовать сразу, не ожидая
`jutsuCastDelay`. Ждать нужно только на развилках (где есть продолжения), и там ожидание можно
сократить до отдельного `castLookahead` (4–5 тиков) вместо 15.

*Эффект:* все листья (а это 80 % книги) кастуются в момент последнего нажатия; 6-клавишное комбо
стоит 6 быстрых нажатий, а не 4,5 с. Ничего не переразмечается, память игроков не страдает.
*Где:* `NarutoKeyHandler#onClientTick` + статическое дерево из `COMBO_MAP`.
*Риск:* низкий; поведение меняется только в лучшую сторону, конфиг-флагом можно откатить.

### I2. Семейства: один слот на взаимоисключающие техники (M) ⭐ идея из запроса

Разрешить **несколько техник на одно комбо** и выбирать одну по состоянию игрока.

```java
record ComboBinding(long combo, ResourceLocation ability, int priority, ComboScope scope) {}
Map<Long, List<ComboBinding>> COMBO_TABLE;   // вместо Map<Long, ResourceLocation>
```

`ComboScope` — это правило, а не флаг: `ALWAYS`, `EYE`, `BODY_MODE`, `CLONE`, `STANCE`, `LAST_USED`.
Резолвер на клиенте (данные `NinjaData` синканны, так что клиент видит то же, что сервер) отбирает
кандидатов по scope, сортирует по приоритету и отдаёт первый, чей `AbilityUnlocks`-гейт пройден;
дальше уходит уже готовый `ResourceLocation` — **протокол менять не нужно**, `ServerAbilityActivatePacket`
и так носит id, а сервер перепроверяет цену и освоение.

Семейства, готовые к этому прямо сейчас:

| Ось | Техники | Правило | Выигрыш |
| --- | --- | --- | --- |
| Глаза | sharingan 1223, byakugan 2322, rinnegan 2332, jougan 3221, senrigan 3233 | только один открыт (`closeOtherEyes`) | 5 → 1 слот |
| Телесные режимы | chakra_cloak 332121, tailed_cloak 332122, divided_cloak 332212, perfect_cloak 332222, curse_seal_one 332111, curse_seal_two 332112, six_paths_mode 333311, lightning_mode 332131 | `ModeBus` (с учётом `PREREQUISITES`) | 8 → 1 слот |
| Клоны | shadow 1332, chibi 1322, multi 133231, meditation 1331, water 212112, mud 212113, clay 212121, iron 212131, decoy 2111, medic 2113, sacrifice 2123, sparring 2122 | «последний выбранный» + перебор | 12 → 1 слот |
| Стойки клинка | kamae 132222, parry 132311, palm_block 132321, bleeding_blade 132333, silent_kill 132112, elemental_edge 1323121 | *нужна новая исключительность* (сейчас стойки складываются) | 6 → 1 слот |
| Призыв/утилита | storage_chest 321, smoke 222, henge 212, campfire 313 | «последний выбранный» | 4 → 1 слот |

Итого 35 техник на 5 слотов вместо 35.

### I3. Цикл внутри слота (S, поверх I2)

Повторное нажатие того же комбо в течение ~400 мс **не кастует повторно, а перелистывает** семью:
клан-режим → хвостатый → идеальный → разделённый; печать 1 → печать 2; глаз → следующий глаз.
Клиент запоминает `slotCursor`, сервер получает конкретный id. Это превращает «режим» в барабан
вроде выбора оружия в шутерах и снимает главную боль: не надо помнить 8 комбо, чтобы переключать
режимы.

### I4. Короткие алиасы (M) ⭐

Дать технике **второе, короткое** комбо, не отбирая старое. Резолвер (из I2) уже это умеет: у одного
`long` просто два биндинга.

Ограничение: свободных «чистых» мест ≤3 клавиш всего три (`111`, `221`, `311`), поэтому без нового
алфавита (I6) алиасы неизбежно лягут на существующие развилки и «съедят» паузу у поддерева. Это
приемлемо, если алиас ставить на **малопосещаемый** узел, и плохо, если на `13` (49 потомков).

Практичный минимум без нового алфавита: `111` и `311` — под «самое нужное» (например, `311` — клон,
`111` — рывок).

### I5. Единый источник правды + линтер (M) ⭐

Вынести таблицу в `src/main/resources/data/narutomod/combos.json`:

```json
{ "ability": "narutomod:shadow_clone", "combo": "1332", "slot": "clone",
  "aliases": ["311"], "priority": 10 }
```

Загрузка на `FMLCommonSetupEvent`, а `Ability#defaultCombo()` остаётся только дефолтом/фолбэком.
Сверху — `tools/check_combos.py` в пре-коммит:

* дубли (сейчас — только лог);
* листья-префиксы, которые никто не имел в виду;
* максимальная глубина и бюджет («новая техника обязана влезть в ≤3»);
* `slot`-конфликты (два `EYE` без приоритета);
* генерация таблицы для `docs/techniques.html` и для меню — из одного файла.

Побочный плюс: `JutsuScreen` и вики перестают быть копиями.

### I6. Четвёртая клавиша / страница (M) ⭐ стратегически

Алфавит — единственный параметр, который реально увеличивает ёмкость:

| Алфавит | Ровно 3 нажатия | Ровно 4 нажатия | Пример |
| --- | --- | --- | --- |
| 3 (C/V/B) | 27 | 81 | сейчас |
| **4 (+N)** | **64** | **256** | предлагается |
| 5 (+N/M) | 125 | 625 | слишком много клавиш |
| 6 (страница по удержанию) | 216 | 1296 | сложно для рук |

Четвёртая клавиша добавляется дёшево: `registerKeyBind("naruto.keys.key4", KeyEvent.VK_N, event)` +
`JUTSU_KEYS.add(...)` (порядок списка = индекс цифры!) + `getJutsuKey(4)` + звук печати
(`ServerJutsuCastingPacket#handle` сейчас молча игнорирует всё вне 1..3 — нужен `SEAL_D` или тишина
осознанно).

Альтернатива без лишней клавиши — **страница по удержанию**: удерживая `N`, те же `C/V/B` дают цифры
`4/5/6`. Ёмкость та же, но добавляется аккорд; для боевых техник это хуже, для утилиты — нормально.

### I7. Схема v2 (L, только вместе с I6)

Если алфавит расширяется, старую разметку можно не трогать: **старые комбо остаются алиасами**, новая
схема живёт рядом (конфиг `comboScheme = legacy | v2`, выбор на игрока). Внутри схемы коллизий нет,
между схемами — приоритет той, что выбрана.

Правила v2 (пример, а не догма): `первая цифра - школа, вторая - семья, третья - техника`,
максимум 3 (+1 для хвоста и ультимейтов). 4 школы x 4 семьи x 4 = 64 слота ровно на трёх нажатиях,
плюс 16 двухклавишных и 4 одноклавишных = **84 техники в пределах трёх нажатий**, остаток — на
четырёх. Это не «всё в три», как хотелось бы, но 84 против сегодняшних 26.

| Школа | Семья | Примеры (v2) |
| --- | --- | --- |
| 1 — тело | 1 кулак, 2 замена, 3 рывок, 4 концентрация | `11` chakra_fist, `12` substitution, `13` chakra_dash, `14` chakra_charge |
| 2 — стихии | 1 огонь, 2 вода, 3 ветер, 4 земля/дерево | `211` fireball, `212` fire_breath, `221` water_bullet, `222` water_breath, `231` wind_breath, `241` earth_wall, `242` wood_breath |
| 3 — клоны/призыв | 1 клон, 2 вариант, 3 трюк, 4 утилита | `311` shadow, `312` chibi, `313` multi, `314` meditation, `321` water_clone, `331` clone_swap, `341` storage_chest |
| 4 — глаза/режимы | 1 глаза, 2 тело, 3 печати, 4 врата | `411` sharingan, `412` byakugan, `421` chakra_cloak, `422` tailed_cloak, `431` curse_seal_one, `441` eight_gates |

Ключевое: при такой раскладке `211` (fireball) никто не путает с `212` (fire_breath), а `421/422` —
это «режим», а не случайное число. Плюс почти все комбо становятся **листьями** (I1) — значит
каст по последнему нажатию.

### I8. Контекстный каст (L)

Одно комбо — разные исходы в зависимости от того, **куда смотрит игрок** и **что у него в руках**:
`посох воды` по блоку — стена, по существу — хлыст; `взрыв` по существу — ожог, по блоку — воронка.
`util/Targeting` уже разделяет `lookedAtEntity` и `lookedAtBlock`, так что данные есть. Это не
экономит слоты напрямую, но снимает необходимость во вторых комбо для «той же техники, но по-другому».

### I9. Прямые слоты (горячие техники) (M) ⭐ практично

Отдельно от дерева: 2–3 клавиши (например `Z`, `X`, `G`), на которые игрок в меню вешает **любые**
техники. Комбо остаётся «библиотекой», а в бою ты жмёшь одну клавишу. Это дешёвая альтернатива
переразметке: 90 % боя — 3 техники, и именно им не нужны 6 нажатий.

*Где:* новый блок в `JutsuScreen`, `NinjaData` (список id + `@Sync`), `NarutoKeyHandler`.
*Риск:* низкий, всем нравится.

### I10. Обратная связь и обучение (S/M)

* **HUD-хлебные крошки:** набранные цифры + «что кастанётся» + число альтернатив. Уже есть
  `Clientbound`-канал и `ModeHudOverlay`; рисовать рядом с чакрой.
* **Меню:** поиск по названию (сейчас только страницы и фильтр «только изученное»), подсветка
  «нажал `C V` → 3 варианта: …».
* **`/jutsu`** уже печатает комбо — добавить туда алиасы и длину.
* **Тренировка:** сущность-манекен, который показывает «слишком медленно» / «не то комбо».

### I11. Настройка на игрока/сервер (L)

Комбо в `SavedData` игрока (или серверный конфиг) + экран переназначения. Формально это «I5 + UI»,
но ценность отдельная: сервер-владелец может починить раскладку, не выпуская форк.

### I12. Тир-бюджет и видимое подтверждение (M) ⭐ ответ на «имба должна тыкаться дольше»

Длина перестаёт быть случайной: у техники появляется `tier`, а бюджет нажатий жёстко привязан к тиру.

| Тир | Что это | Примеры | Нажатий | Церемония |
| --- | --- | --- | --- | --- |
| 0 | утилита и стойки | water_walk, henge, storage_chest, chakra_fist | **2** | нет |
| 1 | рабочая лошадка боя | fireball, rasengan, chakra_dash, substitution, shadow_clone | **3** | нет |
| 2 | сильное, КД 10–30 с | amaterasu, kirin, tsukuyomi, eight_gates, ice_mirrors | **3–4** | короткая зарядка уже есть |
| 3 | ультимейт | susanoo, chibaku_tensei, amenominaka, izanagi, kotoamatsukami, limbo | **4** | **удержание последней клавиши 0,4–0,6 с** с видимой шкалой и отменой |

Тир выводится из данных, а не назначается руками: цена ≥ 80 или КД ≥ 20 с или уже существующий
`CHANNELED` ≥ 1 с → тир 2–3; иначе 0–1. Линтер из I5 проверяет, что комбо не длиннее бюджета своего
тира, и что тир 3 не бывает короче 3 нажатий.

Почему подтверждение, а не длина: удержание **видно** (шкала над хотбаром), **отменяемо** (отпустил —
не скастовалось), **не зависит от пинга** и одинаково для всех. Семь цифр не видны, не отменяемы и
наказывают за лаг. При этом ультимейт остаётся «тяжёлым»: 4 нажатия + 0,5 с удержания = ~1 с
подготовки, и ошибиться им в толпе по-прежнему нельзя.

Побочный эффект: тир 3 получает ещё и защиту от «случайного каста» — сейчас `kotoamatsukami` за 120
чакры можно пустить в молоко одной лишней паузой.

---

## 5. Выигрыш

Модель: человек жмёт ~0,15 с на нажатие; каст — это пауза 0,75 с (15 тиков) после последнего нажатия,
либо мгновенный каст на листе после I1. Время — от первого нажатия до срабатывания.

| Сценарий | Ср. нажатий | Время до каста | Техник ≤3 | Худший случай | Кастов/мин |
| --- | --- | --- | --- | --- | --- |
| сейчас | 5,15 | **3,86 с** | 26 | 5,25 с | 15 |
| +I1 (мгновенный лист) | 5,15 | **0,79 с** | 26 | 0,90 с | 76 |
| +I1, I2, I3 (семейства 35→5) | 4,66 | **0,55 с** | ~34 | 0,90 с | 109 |
| +I1, I2, I3, I6, I7, I12 | 3,44 | **0,37 с** | **84** | 0,45 с + 0,5 с ульта | 164 |

Итого: **~10× быстрее** до каста, полный обход книги — с **714 с до ~70 с**, доля техник, доступных за
≤3 нажатия, — с 14 % до ~55 % (84 из 155 после схлопывания семейств; остальные 71 — на 4 нажатия,
больше не нужно).

Что это значит на практике:

* сейчас каст в среднем **3,9 с** — это дольше, чем живёт окно на удар; в бою кастовать физически
  некогда, поэтому все и играют стойками и оружием;
* после I1 6-клавишная техника стоит **0,9 с** вместо 4,5 с, при этом ни одно комбо не меняется —
  это самый дешёвый шаг и его можно выпустить отдельным патчем;
* даже если считать нажатие по 0,3 с (пессимистично), выигрыш от одного I1 — **~5×**.

### 5.1. А как же «имба должна тыкаться дольше»

С I12 разница между утилитой и ультимейтом становится **осмысленной и видимой**:

| | Нажатий | Время | Защита от случайного каста |
| --- | --- | --- | --- |
| утилита (`henge`, `water_walk`) | 2 | ~0,15 с | не нужна |
| рабочая техника (`fireball`, `rasengan`) | 3 | ~0,30 с | не нужна |
| сильное (`amaterasu`, `kirin`) | 3–4 | ~0,45 с | зарядка/канал |
| ультимейт (`susanoo`, `chibaku`, `kotoamatsukami`) | 4 | ~0,45 с + **0,5 с удержания** | да, отменяемо |

То есть ульта по-прежнему «тыкается дольше» — примерно **в 6–7 раз дольше утилиты**, — но это
удержание, а не семизначный код. Сейчас же `palm_block` (0 чакры) требует 6 нажатий, а `kirin`
(80 чакры) — 4: длина не коррелирует с силой, она коррелирует с датой добавления.

Отдельно стоит разрешить длинные комбо (5 нажатий) только **внебоевым ритуалам** — печатям, порталам,
Камуи-переходу: там набор «кода» — часть фантазии, и торопиться некуда. Это тир **R**, и он не влияет
на боевой бюджет.

Отдельно важно: длинное комбо **нельзя набрать заранее** — буфер сбрасывается через 15 тиков, так что
«долгий набор» не даёт никакой тактической глубины, только потерю времени.

---

## 6. Что я бы сделал (порядок)

| Фаза | Что | Файлы | Цена |
| --- | --- | --- | --- |
| 0 | `combos.json` + линтер, `COMBO_MAP` → `COMBO_TABLE` (пока 1:1) | `NarutoAbilities`, `tools/check_combos.py`, `build.gradle` hook | M |
| 1 | I1 — мгновенный каст листа + `castLookahead` | `NarutoKeyHandler`, `NarutoConfig` | S |
| 2 | I2 — `ComboScope`, семейства глаз и режимов (13 → 2 слота) | `NarutoAbilities`, `ModeBus`, `AbstractDojutsuAbility` | M |
| 3 | I3 — цикл по повторному нажатию | `NarutoKeyHandler`, `JutsuScreen` | S |
| 4 | I9 — прямые слоты | `NinjaData`, `JutsuScreen`, `NarutoKeyHandler` | M |
| 5 | I6 — четвёртая клавиша + звук печати | `NarutoKeyHandler`, `sounds`, `lang` | S |
| 6 | I7 — схема v2 для новых игроков, legacy как алиасы | `combos.json`, `JutsuScreen`, вики | L |
| 7 | I10 — HUD-подсказки, поиск в меню | `client/`, `JutsuScreen` | M |
| 8 | I12 — `tier` и удержание-подтверждение для ультимейтов | `Ability`, `NarutoAbilities`, `NarutoKeyHandler`, `client/gui` | M |

Фазы 0–2 дают 80 % эффекта и не ломают ни одного существующего комбо. Фаза 6 — единственная, которая
меняет привычки, и её стоит делать только после того, как 1–5 поживут в релизе. I12 (фаза 8) можно
сделать раньше — она не трогает раскладку, только добавляет удержание ультимейтам.

---

## 7. Чего делать не стоит

* **Массово перенумеровать всё сейчас.** 176 классов, вики, сотни часов игроков — и без I1/I2
  выигрыш всё равно съедается паузами.
* **Ставить алиасы на `13` / `132` / `233`.** Это узлы с 28/18/49 потомками; алиас там превратит
  «паузу» в обязательную развилку для половины книги.
* **Убирать `defaultCombo()` из классов полностью.** Он остаётся дефолтом для JSON-less регистрации
  и для тех, кто форкает без ресурсов; 176 классов с константой `COMBO` — это ещё и документация
  «откуда взялось».
* **Делать комбо зависимыми от серверного состояния, которое не синкается.** Резолвер обязан считать
  по `NinjaData` (он `@Sync`), иначе клиент и сервер разойдутся на выборе из семьи.

---

## 8. Открытые вопросы

1. Четвёртая клавиша — `N`, `M` или `G`? `N` рядом с `B`, но конфликтует с… (проверить бинды).
2. Стойки клинка: складывать (как сейчас) или сделать взаимоисключающими ради одного слота?
3. Клоны: «последний выбранный» или «самый дешёвый из доступных» по умолчанию?
4. Схема v2 — opt-in для новых миров или для всех сразу?
5. Нужен ли `castLookahead` отдельным конфигом или переиспользовать `jutsuActivateDelay`?
6. Тир считать из цены/КД автоматически (и показывать в меню) или держать в `combos.json` руками?
7. `R`-ритуалы (Камуи-переход, печати) — разрешать 5 нажатий, или им тоже 4 + удержание?

---

## Приложение А. Текущая раскладка (185 техник)


| Комбо | Клавиши | Длина | Техника (`id`) | Класс | Тип |
| --- | --- | --- | --- | --- | --- |
| `1` | C | 1 | `chakra_charge` | ChakraChargeAbility | CHANNELED |
| `3` | B | 1 | `water_walk` | WaterWalkAbility | TOGGLE |
| `11` | C C | 2 | `chakra_fist` | ChakraFistAbility | TOGGLE |
| `12` | C V | 2 | `substitution` | SubstitutionJutsuAbility | INSTANT |
| `21` | V C | 2 | `chakra_dash` | ChakraDashAbility | INSTANT |
| `112` | C C V | 3 | `ore_sense` | OreSenseAbility | INSTANT |
| `113` | C C B | 3 | `chakra_adhesion` | ChakraAdhesionAbility | CHANNELED |
| `121` | C V C | 3 | `fireball` | FireballJutsuAbility | CHANNELED |
| `122` | C V V | 3 | `chakra_feeding` | ChakraFeedingAbility | TOGGLE |
| `131` | C B C | 3 | `genjutsu` | GenjutsuJutsuAbility | INSTANT |
| `132` | C B V | 3 | `water_bullet` | WaterBulletJutsuAbility | CHANNELED |
| `133` | C B B | 3 | `wide_dig` | WideDigAbility | TOGGLE |
| `211` | V C C | 3 | `clone_recall` | CloneRecallAbility | INSTANT |
| `212` | V C V | 3 | `henge` | HengeJutsuAbility | INSTANT |
| `213` | V C B | 3 | `kamui_phasing` | KamuiPhaseAbility | TOGGLE |
| `222` | V V V | 3 | `smoke_screen` | SmokeScreenAbility | INSTANT |
| `223` | V V B | 3 | `second_breath` | SecondBreathAbility | INSTANT |
| `231` | V B C | 3 | `senjutsu_charging` | SenjutsuChargingAbility | CHANNELED |
| `232` | V B V | 3 | `sage_mode` | SageModeAbility | INSTANT |
| `233` | V B B | 3 | `body_adaptation` | BodyAdaptationAbility | TOGGLE |
| `312` | B C V | 3 | `earth_wall` | EarthWallJutsuAbility | CHANNELED |
| `313` | B C B | 3 | `campfire` | CampfireJutsuAbility | INSTANT |
| `321` | B V C | 3 | `storage_chest` | StorageChestAbility | INSTANT |
| `323` | B V B | 3 | `chakra_shield` | ChakraShieldAbility | INSTANT |
| `331` | B B C | 3 | `chakra_thread` | ChakraThreadAbility | CHANNELED |
| `333` | B B B | 3 | `silence` | SilenceAbility | TOGGLE |
| `1211` | C V C C | 4 | `wood_breath` | WoodBreathAbility | CHANNELED |
| `1221` | C V V C | 4 | `ore_excavation` | OreExcavationJutsuAbility | INSTANT |
| `1223` | C V V B | 4 | `sharingan` | SharinganAbility | TOGGLE |
| `1231` | C V B C | 4 | `fire_breath` | FireBreathJutsuAbility | CHANNELED |
| `1232` | C V B V | 4 | `water_breath` | WaterBreathAbility | CHANNELED |
| `1233` | C V B B | 4 | `wind_breath` | WindBreathAbility | CHANNELED |
| `1311` | C B C C | 4 | `chakra_sensing` | ChakraSensingAbility | CHANNELED |
| `1322` | C B V V | 4 | `chibi_shadow_clone` | ShadowCloneJutsuAbility | INSTANT |
| `1331` | C B B C | 4 | `chakra_meditation` | ChakraMeditationJutsuAbility | INSTANT |
| `1332` | C B B V | 4 | `shadow_clone` | ShadowCloneJutsuAbility | INSTANT |
| `2111` | V C C C | 4 | `clone_decoy` | DecoyCloneAbility | INSTANT |
| `2112` | V C C V | 4 | `shuriken_shadow` | ShurikenShadowAbility | INSTANT |
| `2113` | V C C B | 4 | `clone_medic` | MedicCloneAbility | INSTANT |
| `2121` | V C V C | 4 | `universal_force` | UniversalForceAbility | INSTANT |
| `2122` | V C V V | 4 | `clone_sparring` | CloneSparringAbility | CHANNELED |
| `2123` | V C V B | 4 | `clone_sacrifice` | CloneSacrificeAbility | TOGGLE |
| `2322` | V B V V | 4 | `byakugan` | ByakuganAbility | TOGGLE |
| `2331` | V B B C | 4 | `chakra_regeneration` | ChakraRegenerationAbility | TOGGLE |
| `2332` | V B B V | 4 | `rinnegan` | RinneganAbility | TOGGLE |
| `2333` | V B B B | 4 | `eight_gates` | EightGatesAbility | CHANNELED |
| `3221` | B V V C | 4 | `jougan` | JouganAbility | TOGGLE |
| `3232` | B V B V | 4 | `momoshiki_absorption` | MomoshikiAbsorptionAbility | CHANNELED |
| `3233` | B V B B | 4 | `senrigan` | SenriganAbility | TOGGLE |
| `3311` | B B C C | 4 | `wind_step` | WindStepAbility | TOGGLE |
| `3312` | B B C V | 4 | `beast_carry` | BeastCarryAbility | TOGGLE |
| `3322` | B B V V | 4 | `kirin` | KirinAbility | INSTANT |
| `12111` | C V C C C | 5 | `mokuton_growth` | MokutonGrowthAbility | INSTANT |
| `12231` | C V V B C | 5 | `amaterasu` | AmaterasuAbility | INSTANT |
| `12232` | C V V B V | 5 | `kagutsuchi` | KagutsuchiAbility | INSTANT |
| `12311` | C V B C C | 5 | `suiton_watering` | WateringAbility | INSTANT |
| `13111` | C B C C C | 5 | `kai` | KaiAbility | INSTANT |
| `13112` | C B C C V | 5 | `nemuri` | SleepTouchAbility | INSTANT |
| `13113` | C B C C B | 5 | `narakumi` | NarakumiAbility | INSTANT |
| `13114` | C B C C  | 5 | `jubaku_satsu` | JubakuSatsuAbility | INSTANT |
| `13121` | C B C V C | 5 | `utakata` | UtakataAbility | INSTANT |
| `13122` | C B C V V | 5 | `kokoni_arazu` | KokoniArazuAbility | TOGGLE |
| `13123` | C B C V B | 5 | `kagaami` | KagaamiAbility | TOGGLE |
| `13131` | C B C B C | 5 | `mrak` | MrakAbility | CHANNELED |
| `13132` | C B C B V | 5 | `face_swap` | FaceSwapAbility | INSTANT |
| `13133` | C B C B B | 5 | `player_genjutsu` | PlayerGenjutsuAbility | INSTANT |
| `13134` | C B C B  | 5 | `mirage` | MirageAbility | INSTANT |
| `13313` | C B B C B | 5 | `rasengan` | RasenganJutsuAbility | CHANNELED |
| `13321` | C B B V C | 5 | `chakra_marker` | ChakraMarkerJutsuAbility | CHANNELED |
| `23223` | V B V V B | 5 | `rotation` | RotationAbility | INSTANT |
| `23321` | V B B V C | 5 | `chakra_transfer` | ChakraTransferAbility | CHANNELED |
| `23322` | V B B V V | 5 | `heaven_weight` | HeavenWeightAbility | INSTANT |
| `32211` | B V V C C | 5 | `kamui` | KamuiJutsuAbility | CHANNELED |
| `32212` | B V V C V | 5 | `chakra_sever` | ChakraSeverAbility | INSTANT |
| `32213` | B V V C B | 5 | `dimension_step` | DimensionStepAbility | INSTANT |
| `32331` | B V B B C | 5 | `senrigan_sight` | SenriganSightAbility | INSTANT |
| `32332` | B V B B V | 5 | `senrigan_flight` | SenriganFlightAbility | TOGGLE |
| `122311` | C V V B C C | 6 | `chakra_trail` | ChakraTrailAbility | INSTANT |
| `122312` | C V V B C V | 6 | `suggestion` | SuggestionAbility | INSTANT |
| `122313` | C V V B C B | 6 | `step_behind` | StepBehindAbility | INSTANT |
| `122321` | C V V B V C | 6 | `tsukuyomi` | TsukuyomiAbility | INSTANT |
| `122322` | C V V B V V | 6 | `kyoujin` | KyoujinAbility | INSTANT |
| `122323` | C V V B V B | 6 | `kurai` | KuraiAbility | INSTANT |
| `122331` | C V V B B C | 6 | `izanagi` | IzanagiAbility | INSTANT |
| `122332` | C V V B B V | 6 | `izanami` | IzanamiAbility | INSTANT |
| `122333` | C V V B B B | 6 | `kotoamatsukami` | KotoamatsukamiAbility | INSTANT |
| `132111` | C B V C C C | 6 | `pole_vault` | PoleVaultAbility | INSTANT |
| `132112` | C B V C C V | 6 | `silent_kill` | SilentKillAbility | TOGGLE |
| `132113` | C B V C C B | 6 | `nuibari` | NuibariAbility | INSTANT |
| `132121` | C B V C V C | 6 | `strong_shoulder` | StrongShoulderAbility | INSTANT |
| `132122` | C B V C V V | 6 | `dynamic_entry` | DynamicEntryAbility | INSTANT |
| `132123` | C B V C V B | 6 | `projectile_cut` | ProjectileCutAbility | INSTANT |
| `132131` | C B V C B C | 6 | `drunken_fist` | DrunkenFistAbility | TOGGLE |
| `132132` | C B V C B V | 6 | `wall_bounce` | WallBounceAbility | INSTANT |
| `132211` | C B V V C C | 6 | `mind_transfer` | MindTransferAbility | CHANNELED |
| `132212` | C B V V C V | 6 | `size_expansion` | SizeExpansionAbility | TOGGLE |
| `132213` | C B V V C B | 6 | `camellia_dance` | CamelliaDanceAbility | INSTANT |
| `132214` | C B V V C  | 6 | `bone_bullets` | BoneBulletsAbility | INSTANT |
| `132221` | C B V V V C | 6 | `chakra_whetstone` | ChakraWhetstoneAbility | INSTANT |
| `132222` | C B V V V V | 6 | `kamae` | KamaeAbility | TOGGLE |
| `132223` | C B V V V B | 6 | `kusa_giri` | KusaGiriAbility | INSTANT |
| `132231` | C B V V B C | 6 | `kesa_giri` | KesaGiriAbility | INSTANT |
| `132232` | C B V V B V | 6 | `flame_fist` | FlameFistAbility | INSTANT |
| `132233` | C B V V B B | 6 | `water_hand` | WaterHandAbility | TOGGLE |
| `132311` | C B V B C C | 6 | `blade_parry` | ParryAbility | TOGGLE |
| `132312` | C B V B C V | 6 | `vacuum_blade` | VacuumBladeAbility | INSTANT |
| `132313` | C B V B C B | 6 | `grab_throw` | GrabThrowAbility | INSTANT |
| `132321` | C B V B V C | 6 | `palm_block` | PalmBlockAbility | TOGGLE |
| `132322` | C B V B V V | 6 | `inner_strike` | InnerStrikeAbility | INSTANT |
| `132323` | C B V B V B | 6 | `hiramekarei` | HiramekareiAbility | CHANNELED |
| `132332` | C B V B B V | 6 | `shibuki` | ShibukiAbility | INSTANT |
| `132333` | C B V B B B | 6 | `bleeding_blade` | BleedingBladeAbility | TOGGLE |
| `133132` | C B B C B V | 6 | `rasenshuriken` | RasenshurikenJutsuAbility | CHANNELED |
| `133231` | C B B V B C | 6 | `multi_shadow_clone` | ShadowCloneJutsuAbility | INSTANT |
| `212111` | V C V C C C | 6 | `clone_swap` | CloneSwapAbility | INSTANT |
| `212112` | V C V C C V | 6 | `water_clone` | WaterCloneAbility | INSTANT |
| `212113` | V C V C C B | 6 | `mud_clone` | MudCloneAbility | INSTANT |
| `212121` | V C V C V C | 6 | `clay_clone` | ClayCloneAbility | INSTANT |
| `212131` | V C V C B C | 6 | `iron_guardian` | IronGuardianAbility | INSTANT |
| `212132` | V C V C B V | 6 | `blade_rain` | BladeRainAbility | INSTANT |
| `212311` | V C V B C C | 6 | `explosion_silencer` | ExplosionSilencerAbility | INSTANT |
| `212312` | V C V B C V | 6 | `katsuyu_network` | KatsuyuNetworkAbility | CHANNELED |
| `231111` | V B C C C C | 6 | `ice_mirrors` | IceMirrorsAbility | CHANNELED |
| `232211` | V B V V C C | 6 | `tenketsu_needles` | TenketsuNeedlesAbility | CHANNELED |
| `232212` | V B V V C V | 6 | `tenketsu_blind` | TenketsuBlindAbility | INSTANT |
| `232213` | V B V V C B | 6 | `chakra_grip` | ChakraGripAbility | CHANNELED |
| `232221` | V B V V V C | 6 | `node_rupture` | NodeRuptureAbility | INSTANT |
| `232222` | V B V V V V | 6 | `byakugan_farsight` | ByakuganFarsightAbility | TOGGLE |
| `232223` | V B V V V B | 6 | `byakugan_mist_sight` | ByakuganMistSightAbility | TOGGLE |
| `232231` | V B V V B C | 6 | `soshiken` | SoshikenAbility | CHANNELED |
| `232232` | V B V V B V | 6 | `palm_lance` | PalmLanceAbility | INSTANT |
| `232233` | V B V V B B | 6 | `eight_trigrams_collapse` | MountainCollapseAbility | INSTANT |
| `232311` | V B V B C C | 6 | `doctor_sight` | DoctorSightAbility | TOGGLE |
| `232312` | V B V B C V | 6 | `void_cast` | VoidCastAbility | INSTANT |
| `232313` | V B V B C B | 6 | `mechanism_sight` | MechanismSightAbility | INSTANT |
| `232321` | V B V B V C | 6 | `eight_trigrams_field` | EightTrigramsFieldAbility | TOGGLE |
| `232322` | V B V B V V | 6 | `hidden_seal` | HiddenSealAbility | INSTANT |
| `232323` | V B V B V B | 6 | `circular_block` | CircularBlockAbility | TOGGLE |
| `232331` | V B V B B C | 6 | `sixty_four_palms` | SixtyFourPalmsAbility | CHANNELED |
| `232332` | V B V B B V | 6 | `byakugan_mark` | ByakuganMarkAbility | INSTANT |
| `232333` | V B V B B B | 6 | `air_palm` | AirPalmAbility | INSTANT |
| `233231` | V B B V B C | 6 | `naraka_path` | NarakaPathAbility | INSTANT |
| `233232` | V B B V B V | 6 | `animal_path` | AnimalPathAbility | INSTANT |
| `233233` | V B B V B B | 6 | `asura_missiles` | AsuraMissilesAbility | INSTANT |
| `233311` | V B B B C C | 6 | `rinbo_hengoku` | RinboHengokuAbility | INSTANT |
| `233312` | V B B B C V | 6 | `outer_chains` | OuterPathChainsAbility | CHANNELED |
| `233313` | V B B B C B | 6 | `outer_armor` | OuterPathArmorAbility | TOGGLE |
| `233321` | V B B B V C | 6 | `black_rod` | BlackRodAbility | INSTANT |
| `233322` | V B B B V V | 6 | `item_attraction` | ItemAttractionAbility | INSTANT |
| `233323` | V B B B V B | 6 | `rinnegan_levitation` | RinneganLevitationAbility | CHANNELED |
| `233331` | V B B B B C | 6 | `gate_peacock` | GatePeacockAbility | INSTANT |
| `233332` | V B B B B V | 6 | `gate_tiger` | GateTigerAbility | INSTANT |
| `233333` | V B B B B B | 6 | `gate_elephant` | GateElephantAbility | INSTANT |
| `322211` | B V V V C C | 6 | `jougan_stasis` | JouganStasisAbility | INSTANT |
| `322212` | B V V V C V | 6 | `jougan_unweave` | JouganUnweaveAbility | INSTANT |
| `322221` | B V V V V C | 6 | `seal_portal` | SealPortalAbility | INSTANT |
| `322222` | B V V V V V | 6 | `sewing_pocket` | SewingPocketAbility | INSTANT |
| `322223` | B V V V V B | 6 | `sewing_lock` | SewingLockAbility | INSTANT |
| `322311` | B V V B C C | 6 | `chakra_shear` | ChakraShearAbility | TOGGLE |
| `322312` | B V V B C V | 6 | `structure_cut` | StructureCutAbility | INSTANT |
| `322313` | B V V B C B | 6 | `jougan_phase` | JouganPhaseAbility | TOGGLE |
| `323311` | B V B B C C | 6 | `senrigan_echo` | SenriganEchoAbility | INSTANT |
| `332111` | B B V C C C | 6 | `curse_seal_one` | CurseSealOneAbility | TOGGLE |
| `332112` | B B V C C V | 6 | `curse_seal_two` | CurseSealTwoAbility | TOGGLE |
| `332121` | B B V C V C | 6 | `chakra_cloak` | ChakraCloakAbility | TOGGLE |
| `332122` | B B V C V V | 6 | `tailed_cloak` | TailedCloakAbility | TOGGLE |
| `332131` | B B V C B C | 6 | `lightning_mode` | LightningModeAbility | TOGGLE |
| `332132` | B B V C B V | 6 | `orochimaru_body` | OrochimaruBodyAbility | INSTANT |
| `332211` | B B V V C C | 6 | `seal_vessel` | SealVesselAbility | INSTANT |
| `332212` | B B V V C V | 6 | `divided_cloak` | DividedCloakAbility | TOGGLE |
| `332221` | B B V V V C | 6 | `seal_break` | SealBreakAbility | INSTANT |
| `332222` | B B V V V V | 6 | `perfect_cloak` | PerfectCloakAbility | TOGGLE |
| `332231` | B B V V B C | 6 | `tailed_ball` | TailedBallAbility | CHANNELED |
| `333311` | B B B B C C | 6 | `six_paths_mode` | SixPathsModeAbility | TOGGLE |
| `333312` | B B B B C V | 6 | `gudodama_launch` | GudodamaLaunchAbility | INSTANT |
| `333313` | B B B B C B | 6 | `gudodama_guard` | GudodamaGuardAbility | TOGGLE |
| `333321` | B B B B V C | 6 | `gudodama_form` | GudodamaFormAbility | TOGGLE |
| `333322` | B B B B V V | 6 | `rinne_sharingan` | RinneSharinganAbility | TOGGLE |
| `333331` | B B B B B C | 6 | `limbo` | LimboAbility | INSTANT |
| `333332` | B B B B B V | 6 | `amenominaka` | AmenominakaAbility | INSTANT |
| `333333` | B B B B B B | 6 | `yin_yang_heal` | YinYangHealingAbility | CHANNELED |
| `1223111` | C V V B C C C | 7 | `illusory_double` | IllusoryDoubleAbility | INSTANT |
| `1223211` | C V V B V C C | 7 | `susanoo` | SusanooAbility | TOGGLE |
| `1323121` | C B V B C V C | 7 | `elemental_edge` | ElementalEdgeAbility | TOGGLE |
| `2331231` | V B B C V B C | 7 | `chibaku_tensei` | ChibakuTenseiAbility | CHANNELED |

Всего: 185 техник. `en_us`/`ru_ru` подписи не менялись.
