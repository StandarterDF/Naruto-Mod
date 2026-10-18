# План: «Освоение» техник (Mastery) + смерть + медитация + крафты

Порт Naruto Mod, 1.20.1 / Forge 47.1.62. Датаген не используется — данные пишутся руками
(типы урона, измерения, рецепты — JSON в `src/main/resources/data/narutomod/`).

## 1. Ядро прогрессии

- Счётчик **успешных кастов** на игрока и на каждую технику отдельно
  (`Map<ResourceLocation,Integer>` внутри `NinjaData`).
- Пишется в NBT (переживает смерть — `PlayerEvent.Clone` копирует NBT — и перезаход),
  синхронизируется на клиент своим `SyncTracker` (для меню).
- Степень освоения — непрерывная функция с затухающей отдачей:

  ```
  m = casts / (casts + K)          K = 40 (половина максимума за ~40 кастов)
  ```

- Растёт **после каждого** каста, без обрыва на «уровни». Все эффекты линейны от `m`:
  `множитель = 1 - MAX_ЭФФЕКТ * m`.
- Конфиг: `masteryEnabled`, `masteryHalfwayCasts` (K), `masteryCostReduction` (0.4),
  `masteryCooldownReduction` (0.4).

| Техника | Что растёт с `m` |
| --- | --- |
| Платные (любая фиксированная цена, вкл. поддержание/канал) | расход и КД `-40%` |
| Бесплатные (костёр, сундук и т.п.) | КД `-40%` |
| **Мультиклон** | клонов `4 → 10` |
| **Чакра-щит** | очки щита за ту же чакру `×(1 + 0.5·m)` |
| **Второе дыхание** | Darkness `1200 → 600` тиков (60 с → 30 с) |

Учёт кастов — один helper, вызывается в трёх точках успешного каста
(instant-пакет, включение TOGGLE, завершение CHANNELED). Канал не накручивает счётчик по тикам.
В креативе касты не считаются.

## 2. Смерть — прогрессивный откат

При `PlayerEvent.Clone#isWasDeath()`, для каждой техники:

```
t = clamp((m - 0.5) / 0.45, 0, 1)                        // 0 при m<=50%, 1 при m>=95%
p = rand( lerp(0.03, 0.10, t), lerp(0.05, 0.20, t) )     // 3-5% ... 10-20%, разброс от смерти
m' = max(0, m - p)
casts' = K * m' / (1 - m')                                // обратно в счётчик
```

- У 50% откат 3-5%, у 95% — 10-20%, каждый раз по-разному.
- Конфиг: `masteryDeathPenaltyLowMin/Max` (0.03/0.05), `masteryDeathPenaltyHighMin/Max` (0.10/0.20),
  пороги 0.5 / 0.95.

## 3. Печать чакры

- Обычно печать нужна **мультиклону и медитации**; теневой и чиби — без печати.
- При `m >= 0.9` (`masterySealFreeThreshold`) печать **не нужна нигде**, включая мультиклон.

## 4. Медитация

- Радиус линка: `8 + (RADIUS_MAX - 8) * m^3`, `RADIUS_MAX ≈ 512` блоков (~32 чанка); рост
  заострён к концу.
- Внутри радиуса — нынешний эффект: реген `0.2` чакры/тик за клона (или сенин-мана в режиме
  Мудреца), `+100` ёмкости чакры за клона, ускоренный КД.
- При `m >= 0.95` каждый медитирующий клон даёт **+50 ёмкости чакры** даже **вне радиуса**
  (без регена).
- Дальний радиус и пассив считаются по **регистрации клонов в `NinjaData`** (измерение + позиция +
  срок), а не по AABB: `getEntitiesOfClass` видит только загруженные чанки.
- Смерть кастера: медитирующие клоны развеиваются, регистрации и бонус чистятся.
- Конфиг: `meditationRadiusMax`, `meditationPassiveThreshold` (0.95), `meditationPassiveCapacity` (50).

## 5. Клоны

- Мультиклон: клонов `round(4 + 6·m)` (4 → 10).
- Боевые техники клонов (теневой, чиби, мульти — не медитация, не рабочие):
  **presence-gate** — пока у кастера жив хоть один боевой клон, каст закрыт (сообщение о КД
  показывает 300 с, со скидкой КД от освоения); как только все погибли/истекли — каст доступен
  сразу. Медитация и рабочие — на обычном КД.

## 6. Меню (`JutsuScreen`)

- В строке техники: **мини-полоска прогресса + процент** перед клавишами комбо; заполнение = `m`,
  цвет к максимуму.
- Ширина панели считается по содержимому — бейдж учитывается в `panelHalfWidth()`.

## 7. Крафты (всё крафтится, ~52 JSON)

Уже есть: `chakra_seal`, `worker_seal`, `chakra_battery`, `military_pills`, `ramen`.

**Материалы**

| Предмет | Рецепт |
| --- | --- |
| `fabric` | 3× string → 1 |
| `fabric_reinforced` | fabric + leather + iron_ingot → 1 |
| `fabric_reinforced_green` | fabric_reinforced + green_dye |
| `fabric_reinforced_black` | fabric_reinforced + black_dye |
| `armor_plate` | 3× iron_ingot → 1 |
| `armor_plate_green` | armor_plate + green_dye |

**Оружие/блоки**

| Предмет | Рецепт |
| --- | --- |
| `kunai` | iron_ingot + stick |
| `senbon` | iron_ingot → 4 |
| `shuriken` | iron_ingot → 2 |
| `explosive_kunai` | kunai + gunpowder |
| `paper_bomb` | 3× paper + gunpowder |
| `katana` | 2× iron_ingot + stick |
| `samehada` | 4× iron_ingot + 2× diamond + stick |
| `kubikiribocho` | 5× iron_ingot + 2× diamond + stick |
| `bonsai_tree` | flower_pot + oak_sapling |

**Броня**

| Предмет | Рецепт |
| --- | --- |
| `red/yellow/green/blue_anbu_mask` | 3× armor_plate + соответствующий краситель |
| `mist_anbu_mask` | 3× armor_plate + cyan_dye |
| `flak_jacket_new` | 5× armor_plate + fabric |
| `flak_jacket` | flak_jacket_new + 2× armor_plate |
| `anbu_armor` | flak_jacket_new + 2× armor_plate + fabric_reinforced_black |
| `akatsuki_cloak` | 4× fabric_reinforced_black + red_dye |

**Повязки (29 шт.)** — `fabric + armor_plate + <уникальный ингредиент>`; `*_scratched` = базовая +
`flint`; `headband_leaf_black` = `headband_leaf` + black_dye.

Уникальные ингредиенты: blue/black/red/custard → blue/black/red/yellow dye; leaf → oak_leaves;
sand → sand; rock → stone; mist → light_blue_dye; cloud → white_wool; rain → prismarine_shard;
sound → amethyst_shard; waterfall → blue_ice; grass → lime_dye; pride → magenta_dye;
youtube → redstone; lava → magma_cream.

**Прочее**

| Предмет | Рецепт |
| --- | --- |
| `lonely_march` | paper + ink_sac + gold_ingot |
| `model_test` | пропускается (тестовый предмет без таба/текстуры) |

## 8. Конфиг (сводка)

`masteryEnabled`, `masteryHalfwayCasts`, `masteryCostReduction`, `masteryCooldownReduction`,
`masterySealFreeThreshold`, `masteryDeathPenaltyLowMin/Max`, `masteryDeathPenaltyHighMin/Max`,
`meditationRadiusMax`, `meditationPassiveThreshold`, `meditationPassiveCapacity`.

## 9. Файлы

**Новые**
- `abilities/mastery/Mastery.java` — кривая, константы, резолв id, хелперы расход/КД, `recordCast`.
- `capabilities/mastery/MasteryData.java` — контейнер счётчиков.
- `trackers/MasteryDataSyncTracker.java` — синхронизация для меню.

**Правки**
- `INinjaData` / `NinjaData` — поле, доступ, NBT, `@Sync`.
- `NarutoSyncTrackers` — регистрация трекера.
- `Ability` — `masteryCost(...)`, централизованное снижение КД в `Cooldown`.
- Платные техники (~15) — цена через `masteryCost(...)`.
- `ChakraShieldAbility`, `SecondBreathAbility` — качественные бонусы.
- `ShadowCloneJutsuAbility` / `ChakraMeditationJutsuAbility` — число, печать, gate, радиус, пассив, регистрация.
- `ServerAbilityActivatePacket`, `ToggleAbilityData`, `ServerAbilityChannelPacket` — `recordCast`.
- `NinjaCapabilityHandler` — штраф смерти, развеивание медитаций.
- `JutsuScreen` — полоска + процент.
- `resources/data/narutomod/recipes/*.json` — новые рецепты.

## 10. Порядок работ

1. Ядро: данные/sync/NBT, кривая, `Ability`-хелперы.
2. Учёт кастов + скидки/КД во всех платных техниках.
3. Смерть (откат + развеивание медитаций).
4. Клоны: число, печать, presence-gate.
5. Медитация: радиус-кривая + регистрация + пассив +50.
6. Меню: полоска + процент.
7. Крафты.
8. Сборка `gradlew compileJava` (JDK 17), проверка в дев-клиенте с JEI.
