# AGENTS.md — Naruto Mod (Minecraft 1.20.1 / Forge)

Порт **Sekwah's Naruto Mod** с 1.7.10 на **1.20.1 / Forge 47.1.62** (ветка `master`, `1.8.1-SNAPSHOT`).
Цель — паритет поведения с 1.7.10. `origin` — форк `StandarterDF/Naruto-Mod` (пушим туда), `upstream` —
`sekwah41/Naruto-Mod`; оригинал 1.7.10 лежит в `upstream/release-forge-1-7-10` — читать при портировании.

Это **карта проекта, а не энциклопедия**: здесь «где что лежит» и правила работы. Числа техник, точные
механики, история решений и грабли — в **[`AGENT_NOTES.md`](AGENT_NOTES.md)** (открывать по теме, не целиком).
Вики — в `docs/`, но агент её **не правит** (см. правило про `docs/` ниже).

## Сборка и запуск

```powershell
# Gradle 8.1.1 не работает на системной Java 25 — только JDK 17
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat build        # или compileJava
.\run-dev-client.bat       # дев-клиент, сам находит JDK 17
.\run-dev-server.bat       # дев-сервер
```

* Скрипты запускают Gradle с `--offline`: зависимости, Minecraft/Forge, ассеты и нативы уже в кэше
  (`~/.gradle`, `build/fg_cache`, `run/`). Ручной `.\gradlew.bat ...` без сети тоже зови с `--offline`.
  После правки зависимостей — один раз `set NARUTO_ONLINE=1`, дальше снова офлайн.
* Дев-клиент идёт с JEI 15.71.0.224 (`runtimeOnly`, CDN Modrinth): это только dev-удобство, код мода на
  его API не опирается.
* **Тесты в игре делает пользователь.** Агент доводит код до `build`/`compileJava` и останавливается;
  клиент, сервер и игровые сценарии не запускает. Автотест готовить только по отдельной просьбе:
  `runClient --args=--quickPlaySingleplayer=clonetest`, `runServer --args=--nogui`, проверка по маркеру
  `say <МАРКЕР>` в логе, артефакты — в `tmp/`/`run/` (системный temp не засорять).

## Карта репозитория

Базовый пакет — `src/main/java/com/sekwah/narutomod/`.

| Путь | Что внутри |
| --- | --- |
| `abilities/` | `Ability` (база: цена, КД, каст, освоение), `NarutoAbilities` (реестр + `COMBO_MAP`), `AbilityUnlocks` (гейт дерева — единая точка правды), `JutsuEffects` |
| `abilities/jutsus/` | По классу на технику: клоны, стихии, дыхания, додзюцу, Камуи, Расенган, земляные техники… Плюс общие предки `AbstractBreathAbility`, `AbstractDojutsuAbility`, `BreathCone` |
| `abilities/utility/` | Простые стойки/утилиты: заряд чакры, рывок, кулак, двойной прыжок, прыжок, ходьба по воде |
| `abilities/mastery/` | `Mastery` — кривая освоения, скидки, КД, штраф за смерть |
| `capabilities/` | `INinjaData`/`NinjaData` (чакра, стамина, сенин, NBT, `@Sync`), `NinjaCapabilityHandler`; вложенные данные — `skills/`, `mastery/`, `absorption/`, `disabledabilitydata/`, `toggleabilitydata/`, `lingerabilitydata/` |
| `entity/` | `ShadowCloneEntity`, `ChakraMarkerEntity`, `SmokeCloudEntity`, `SubstitutionLogEntity`, `WorkerJob`, `NarutoEntities` (регистрация и атрибуты) |
| `entity/ai/` | Голы клонов (`Clone*Goal`) и рабочих (`Worker*Goal`) |
| `entity/jutsuprojectile/` | Снаряды и временные сущности техник: Расенсюрикен, Чибаку, земляная стена, огненный шар… |
| `entity/item/`, `entity/projectile/` | Предметы-сущности и обычные снаряды (кунай, сюрикен, бумажная бомба) |
| `events/` | Forge-обработчики: клоны, взрывы, щит, врата, Камуи, друзья, броня, скиллы… |
| `network/c2s/` | Пакеты клиент → сервер: каст, канал, скиллы, друзья, сундук, отключение техник |
| `network/s2c/` | Пакеты сервер → клиент: сенсорика, руда, друзья, содержимое сундука |
| `client/` | `gui/` (JutsuScreen, SkillTreeScreen, HUD-полоски), `keybinds/`, `renderer/`, `model/`, `events/` |
| `skills/` | `Skill` (таблица узлов), `SkillTree`, `SkillPoints`, `SkillBranch`, `SkillSection`, `SkillEffect` |
| `menu/` | Походный сундук: `SupplyChestContainer`, `SupplyChestMenu`, `SupplyChestWindow` |
| `item/` | Предметы, `armor/`, `weapons/` (великие мечи) |
| `world/` | `KamuiChunkGenerator`, `EarthWallSavedData`, структуры/данжи, `NarutoWorldGen` |
| `mixin/common/`, `mixin/client/` | Миксины: аксессоры, подавление звука, бумажная бомба на нити |
| `config/NarutoConfig` | Все опции мода (Forge config, `config/narutomod-*.toml`) |
| `commands/` | `/naruto`, `/naruto debug`, `/jutsu`, `menuclick` |
| `damagetypes/` | `NarutoDamageTypes` — ключи своих типов урона |
| `trackers/` | Синк-трекеры sekclib для полей `NinjaData` |
| `friends/`, `sounds/`, `registries/`, `loot/`, `ranks/`, `clans/`, `gameevents/`, `triggers/`, `anims/` | Друзья (SavedData), звуки, реестры, лут, ранги, кланы, игровые события, триггеры, анимации |
| `util/` | `Targeting` (лучи/цели), `GuiUtils`, `ExperienceUtils`, `ColorUtil`, `StateUtils` |
| `datagen/` | Код датагена есть, но **при сборке не запускается** — ресурсы лежат руками |

Ресурсы: `src/main/resources/`

| Путь | Что внутри |
| --- | --- |
| `assets/narutomod/lang/` | `en_us.json`, `ru_ru.json` (+ другие языки); все сообщения игроку — отсюда |
| `assets/narutomod/textures/items/`, `blocks/` | Легаси-папки (множественное число) + `assets/minecraft/atlases/blocks.json` — без него текстуры фиолетовые |
| `assets/narutomod/sounds.json`, `sounds/` | Звуки (`clone_poof.ogg` и др.) |
| `data/narutomod/damage_type/` | JSON своих типов урона — обязательны, иначе сервер падает |
| `data/narutomod/dimension/`, `dimension_type/` | Камуи и типы измерений — руками |
| `data/narutomod/recipes/`, `loot_modifiers/`, `loot_tables/`, `tags/`, `worldgen/`, `advancements/` | Рецепты, лут, теги, генерация — руками |

Корень: `tools/` — python-скрипты (редактор/линтер дерева скилов, сборка вики), `docs/` — вики,
`research/` — дизайн-доки, `run/` — дев-мир, `tmp/` — рабочие артефакты, `WEBChanges.md` — очередь
правок вики.

## Где искать механику

| Механика | Файлы |
| --- | --- |
| Тени-клоны | `entity/ShadowCloneEntity`, `entity/ai/Clone*Goal`, `abilities/jutsus/ShadowCloneJutsuAbility`, `client/renderer/entity/ShadowCloneRenderer` |
| Медитация и аура | `abilities/jutsus/ChakraMeditationJutsuAbility`, `capabilities/mastery/MeditationData` |
| Рабочие смены | `entity/WorkerJob`, `entity/ai/Worker*Goal`, `ShadowCloneJutsuAbility#workerJob`, `menu/SupplyChest*` |
| Техники | `abilities/jutsus/<Name>Ability`, регистрация в `NarutoAbilities`, гейт в `AbilityUnlocks` |
| Додзюцу | `AbstractDojutsuAbility`, `SharinganAbility`/`ByakuganAbility`/`JouganAbility`/`Senrigan*`/`RinneganAbility`, `events/*Events` |
| Освоение | `abilities/mastery/Mastery`, `capabilities/mastery/MasteryData`, `trackers/MasteryDataSyncTracker` |
| Дерево скилов | `skills/Skill`, `skills/SkillTree`, `client/gui/SkillTreeScreen`, `network/c2s/ServerUnlockSkillPacket` |
| Чакра, стамина, HUD | `capabilities/NinjaData`, `client/gui/ChakraAndStaminaGUI`, `NinjaCapabilityHandler` |
| Друзья | `friends/Friends`, `friends/FriendsSavedData`, `client/gui/FriendsScreen`, `network/*Friend*` |
| Походный сундук | `abilities/jutsus/StorageChestAbility`, `menu/SupplyChest*`, `network/s2c/ClientboundSupplyChestPacket` |
| Камуи | `abilities/jutsus/KamuiJutsuAbility`, `KamuiPhaseAbility`, `events/KamuiEvents`, `world/KamuiChunkGenerator` + JSON измерения |
| Земляная стена | `EarthWallJutsuAbility`, `events/EarthWallEvents`, `world/EarthWallSavedData`, `entity/jutsuprojectile/EarthWallEntity`, `client/renderer/entity/EarthWallBlockRenderer` |
| Чибаку Тенсэй | `abilities/jutsus/ChibakuTenseiAbility`, `entity/jutsuprojectile/ChibakuTenseiEntity`, `client/renderer/entity/ChibakuTenseiRenderer` |
| Режим Мудреца | `SenjutsuChargingAbility`, `SageModeAbility`, `client/gui/SeninGUI`, `events/PlayerEvents#onEntityUpdate` |
| Сенсорика и руда | `ChakraSensingAbility`, `OreSenseAbility`, `network/s2c/Clientbound*Packet`, `client/ClientSensingData`, `client/renderer/*Renderer` |
| Отладка | `commands/NarutoDebugCommand` (`/naruto debug`), `commands/MenuClickCommand` |

## Правила работы

* Java 17. Комментарии и javadoc — **на английском**. Строки игроку — только через `lang/*.json`
  (`en_us` + `ru_ru`), хардкод в коде не оставлять.
* **Новая техника**: класс (наследник `Ability` или общего предка) → регистрация в `NarutoAbilities` →
  уникальное комбо → узел дерева и ключ `ability:<path>` в `AbilityUnlocks` → строки в оба lang-файла →
  опция в `NarutoConfig`, если технику можно выключить.
* **Новая «плюшка» клона**: гол в `entity/ai/`, регистрация в `ShadowCloneEntity#registerGoals`,
  копирование предмета в `copyEquipment`/`copyAmmo`, поза рук в `ShadowCloneRenderer`. Если плюшка
  занимает руки — её условие идёт в `ShadowCloneEntity#wantsToShoot`.
* **Сервер — источник правды**: проверка цены/условий в `handleCost`, эффект в `performServer`;
  клиент только просит пакетом (`network/c2s`), сервер перепроверяет.
* **Данные игрока**: клиенту видно — `@Sync` в `NinjaData` (+ трекер, если нужно `minTicks`);
  переживает перезаход — NBT в `serializeNBT`/`deserializeNBT`. Server-only поля не синкать.
* **Цена и КД** — через `masteryCost(...)` и `Ability.Cooldown#registerCooldown`, иначе освоение не даёт
  скидку. Учёт кастов — `recordMastery` в трёх путях пакетов; каналы с накоплением — `recordMasteryCredit`.
* **Новый тип урона** — сразу JSON в `data/narutomod/damage_type/` + ключ в `NarutoDamageTypes`. Без JSON
  сервер падает при первом попадании.
* **Новое измерение** — JSON в `data/narutomod/dimension(_type)/`. Если файл битый, `getLevel` вернёт
  `null` (без падения) — проверяй и показывай игроку сообщение.
* **Рецепты, лут, теги, worldgen** — руками в `data/`, датаген не гоняется.
* **Текстуры** — в легаси-папки `textures/items|blocks`; атлас тянет их через `assets/minecraft/atlases/blocks.json`.
* **Дерево скилов** — правится через `tools/skilltree_preview.py`: правки в браузере → `--check` →
  `--apply` → `build`. Линтер ловит дубли, межветковые связи, технику на технике и пустые ключи.
* **Доки**: `docs/` не трогать и не собирать. Если правка меняет описанное на сайте — пункт в
  `WEBChanges.md` (страницы, секции, что поправить).
* **Локальные правила**: Python — только venv проекта; тестовые артефакты — в `tmp/`/`run/`, не в
  системном temp; глобальные npm/pip/winget не ставить.

## Хрупкие места (подробности — в `AGENT_NOTES.md`)

* `player.pick(...)` видит только блоки. Цель — `util/Targeting#lookedAtEntity`, блок — `#lookedAtBlock`.
* `Player#tick` сбрасывает `noPhysics` каждый тик: флаги движения ставить в `LivingTickEvent`, на обеих
  сторонах (Камуи-фаза, сцепление).
* `GoalSelector` не сортирует по номеру: при равных приоритетах отобрать флаг нельзя, «всегда работающий»
  гол должен иметь номер меньше всех (подбор лута — 0).
* Access transformer не доезжает до compile-classpath: приватные поля открывать mixin-аксессором, не
  рефлексией по Mojang-имени.
* `LivingSpawnEvent.CheckSpawn` в 1.20.1 нет — `MobSpawnEvent.PositionCheck`.
* Свой `ChunkGenerator`: реализовать **все** абстрактные методы, включая `addDebugScreenInfo`;
  `applyBiomeDecoration` — no-op, иначе биомные фичи дорисуют «потолок». Параметр — `WorldGenLevel`.
* `Level#getHeight` на клиенте для незагруженного чанка отдаёт `minY` — подменять синканной высотой.
* `ItemStack` пишет count байтом: копии предметов клампить по 127, а сундук хранит количества отдельно.
* Ванильная регенерация от еды (до 2 HP/с) сильнее любого слива HP — гасить `LivingHealEvent`, если
  техника «сжигает» жизнь.
* `Component.keybind` зависит от раскладки — буквы комбо считать из кода клавиши (`JutsuScreen#keyLetter`).
* Forge зовёт `onExplosionDetonate` до урона и отдаёт тот же список — фильтр союзников делать там.
* `.gitattributes` (LF для `version.txt`, CRLF для `*.bat`) не откатывать: иначе ломается сборка jar.
* `Entity#lerpTo` у обычной сущности — это `setPos` без интерполяции: позиционные пакеты «шагают»,
  поэтому движение считать от возраста на обеих сторонах (Чибаку).
* `@Sync` и NBT — не одно и то же. `syncGlobally` нужен, если поле должны видеть все (Хенге), иначе его
  получает только владелец капабилити.
* Порядок регистрации голов и `MeleeAttackGoal#stop` (сбрасывает цель) — учитывать при голах, которые
  временно отбирают флаг (подбор лута).

## Подробности

`AGENT_NOTES.md` — полный справочник: механики клонов и рабочих, все техники и их числа, додзюцу,
друзья, освоение, дерево скилов, отладочные команды, устройство вики и развёрнутый список грабель.
