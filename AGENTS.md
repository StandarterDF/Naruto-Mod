# AGENTS.md — Naruto Mod (порт на Minecraft 1.20.1 / Forge)

## Что это

Порт мода **Sekwah's Naruto Mod** на **1.20.1 / Forge 47.1.62** (ветка `master`, версия `1.8.1-SNAPSHOT`).
Корень репозитория — корень проекта. Ремуты: `origin` = форк `StandarterDF/Naruto-Mod` (туда пушим),
`upstream` = оригинал `sekwah41/Naruto-Mod` (обновления: `git fetch upstream && git merge upstream/master`).
Оригинал 1.7.10 лежит в ветке `upstream/release-forge-1-7-10`, её стоит читать при портировании любой
механики: цель — паритет поведения с 1.7.10.

Стиль кода: Java 17, javadoc/комментарии **на английском**, сообщения игроку — через `lang/*.json`
(`en_us.json`, `ru_ru.json`), никаких хардкодных строк.

## Сборка и запуск

```powershell
# ВАЖНО: Gradle 8.1.1 не работает на системной Java 25 — только JDK 17
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat build      # или compileJava
.\run-dev-client.bat      # дев-клиент (сам находит JDK 17)
.\run-dev-server.bat      # дев-сервер (сам находит JDK 17)
```

Автотесты без ручного геймплея:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat runClient --args=--quickPlaySingleplayer=clonetest   # клиент сразу в тест-мир
.\gradlew.bat runServer --args=--nogui                             # нужен run/eula.txt
```

Тест-датапаки (в `.gitignore`, создаются заново по мере надобности):

* `run/saves/clonetest/datapacks/clonetest/` — клиентский мир, `minecraft:load` → `clonetest:client_load`.
* `run/world/datapacks/clonetest/` — серверный мир, `minecraft:load` → `clonetest:load`.

Приёмы, проверенные на практике:

* Функции датапака выполняются в точке спавна мира (`~`), поэтому сущностей всегда суммонить
  относительно `~`, а не в `0 0` (в незагруженном чанке они просто не появятся).
* Результат теста проверять по `say <МАРКЕР>` в логе и грепать маркер, а не глазами.
* После теста убивать свой клиент/сервер по PID, у которого в командной строке есть
  `Sekwah_Naruto_Mod`. Чужие java-процессы (например, другой мод в соседнем проекте) не трогать.
* Все тестовые артефакты — только внутри проекта (`tmp/`, `run/`), системный temp не засорять.

## Тени-клоны (главная фича порта)

| Что | Где |
| --- | --- |
| Сущность клона | `entity/ShadowCloneEntity.java` |
| Четыре техники | `abilities/jutsus/ShadowCloneJutsuAbility.java` (один класс + `Settings`; медитация — `ChakraMeditationJutsuAbility`) |
| ИИ | `entity/ai/CloneFollowOwnerGoal`, `CloneOwnerHurtByTargetGoal`, `CloneOwnerHurtTargetGoal`, `CloneRangedAttackGoal`, `CloneUseShieldGoal` |
| Рендер | `client/renderer/entity/ShadowCloneRenderer.java` (`MeditatingCloneModel` + `ModelLayers.PLAYER(_SLIM)`) |
| Регистрация/атрибуты | `entity/NarutoEntities.java` |
| Звук | `sounds/NarutoSounds.java` + `assets/narutomod/sounds.json` (`clone_poof.ogg`) |

### Техники

| Техника | Комбо | Чакра | Клонов | Жизнь | Урон | Скорость | КД |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Теневой клон | `C B B V` | 40 | 2–3 | 25 с | 2 | 0.3 | 3 с |
| Чиби-клон | `C B V V` | 20 | 3–5 | 15 с | 1 | 0.35 | 2 с |
| Мульти-клон | `C B B V B C` | 80 | 8–11 | 25 с | 2 | 0.3 | 10 с |
| Медитация чакры | `C B B C` | 60 | 2 | 60 с | 0 | 0 | 60 с |

**Расход печати.** Любой призыв клонов (все четыре техники) требует `narutomod:chakra_seal` в
инвентаре и тратит **одну** печать за каст — независимо от числа клонов. Проверка в
`ShadowCloneJutsuAbility#handleCost`, списание в `#performServer` (креатив ничего не тратит и может
кастовать без печати). Нет печати — `jutsu.fail.noseal`.

### Поведение (паритет с 1.7.10)

* **1 HP**: любой непокрытый удар «попает» клона. Урон/скорость/время жизни задаются техникой через
  `applyVariantAttributes` (у одного типа сущности один `AttributeSupplier`, поэтому значения ставятся
  после создания).
* Смерть = `dispel()`: облако дыма + `clone_poof`, без лута и без трупа (`dropChance = 0`).
* Клон **не** деспавнится по расстоянию (`removeWhenFarAway = false`), персистентность
  переприменяется в `readAdditionalSaveData` — ванильный `Mob#readAdditionalSaveData` её сбрасывает.
* Цели **только реактивные** (как в 1.7.10): `HurtByTarget` (клона ударили) → `CloneOwnerHurtByTargetGoal`
  (владельца ранили) → `CloneOwnerHurtTargetGoal` (владелец ударил). Клон **не** ищет врагов сам
  (`NearestAttackableTargetGoal` убран осознанно): иначе он уходит бить пиглинов, с которыми кастер
  мирно торгует. Если хочется, чтобы клон сам набрасывался на мобов — это надо возвращать отдельно.
* Голы: бой (дальний бой 1, ближний бой 2) → щит 3 → следование за владельцем 4 → двери/прогулка/взгляд.
  Бой раньше следования — как в 1.7.10: клон с целью идёт в бой, а не липнет к кастеру.
* `isAlliedTo` намеренно не вызывает `super` (иначе любой моб того же типа стал бы союзником) и влияет
  только на выбор цели: кастер может спокойно попать своего клона.
* Имя над головой скрыто (`shouldShowName → false`), иначе клон выдаёт себя биркой.
* Чиби-клон — `scale 0.5` и уменьшенный хитбокс (`CHIBI_DIMENSIONS`).
* **Медитирующий клон** (флаг `Settings#meditating()`, синг `DATA_MEDITATING`): руки пустые, снаряжение и
  эффекты **не** копируются, урон и скорость = 0 (атрибуты `applyVariantAttributes`), а
  `CloneFollowOwnerGoal` / `CloneRangedAttackGoal` / `CloneUseShieldGoal` от него сами отказываются —
  стоит где поставили. Садится через `MeditatingCloneModel` (ноги крестом) + сдвиг вниз в
  `ShadowCloneRenderer#setupRotations`. Баф кастеру выдаёт не клон, а
  `ChakraMeditationJutsuAbility#tickAura` (вызывается из `PlayerEvents#onEntityUpdate`): пока рядом
  (8 блоков) есть медитирующий клон владельца — `+0.2` чакры за тик на каждого и лишний тик в
  `INinjaData#getCooldownEvents()`, то есть КД техник идут вдвое быстрее. Второй тик снимается копией
  ключей карты, иначе `ConcurrentModificationException`.
* **Доп. чакра медитации**: каждый медитирующий клон в радиусе поднимает **максимум** чакры кастера
  на `CAPACITY_PER_CLONE = 100` через `INinjaData#setChakraCapacityBonus` (у `getMaxChakra()` бонус
  прибавляется к настраиваемому максимуму, сам бонус — `@Sync` и в NBT не пишется). Клон исчез /
  отошёл — бонус сбрасывается в том же тике (`tickAura` выставляет его всегда, даже когда клонов нет).

### «Плюшки»: клон наследует стиль боя кастера

Копирование происходит **в момент призыва** (`ShadowCloneEntity(Level, Player, lifetime, chibi)`):
скин владельца, вся экипировка (обе руки + 4 слота брони) и стрелы.

* **Щит** (левая рука владельца → левая рука клона). Мобы в ванили не блокируют, поэтому
  `CloneUseShieldGoal` просто держит щит поднятым (`startUsingItem(OFF_HAND)`), пока у клона есть цель
  и он не стреляет. Дальше работает ваниль: `LivingEntity#hurt` → `isDamageSourceBlocked` → урон 0 и
  износ щита. Для клона с 1 HP это единственный способ пережить удар, поэтому щит поднимается **заранее**,
  а не в ответ на попадание. Цель: поднять щит, когда есть цель; опустить, когда стреляем.
* **Дальний бой** (правая рука владельца → правая рука клона). `CloneRangedAttackGoal` — общий гол для
  лука, арбалета и трезубца (ванильные `RangedBowAttackGoal`/`RangedCrossbowAttackGoal` принимают только
  `Monster`, а клон — `PathfinderMob`): подход, стрейф, интервал 20 тиков, выстрел на дистанции. Отличается
  только зарядка: лук — 20 тиков, арбалет — `CrossbowItem#getChargeDuration` (25 тиков, Quick Charge
  ускоряет), трезубец — 10 тиков. Ближе `MELEE_REACH` (3 блока) клон убирает оружие и работает мечом.
  * **Лук**: выстрел идёт через `ShadowCloneEntity#performRangedAttack` → `ProjectileUtil.getMobArrow`
    (тип стрелы и её эффекты, в т.ч. зельевые/спектральные, сохраняются) + `BowItem#customArrow`
    (зачарования лука: Power, Flame, Punch).
  * **Арбалет**: заряжается сам — `LivingEntity#updateUsingItem` → `CrossbowItem#onUseTick` ставит тег
    `Charged` (проверено, что это работает у мобов: так же заряжаются пиллагеры). Выстрел делает ванильный
    `CrossbowItem#performShooting`, который спрашивает стрелка о снаряде, поэтому
    `ShadowCloneEntity#getProjectile` отдаёт скопированные стрелы (Multishot, Piercing, Quick Charge
    работают как у игрока). Звук `CROSSBOW_SHOOT` клон играет сам: ванильный `releaseUsing` играет его
    только игрокам.
  * **Трезубец**: бросается как у дроуна (`ThrownTrident` + `shoot`). Трезубец — оружие, а не боеприпас,
    поэтому **не расходуется** и бросать его можно сколько угодно. Зачарования снаряда (Impaling,
    Channeling, Loyalty) сохраняются, но вернувшийся по Loyalty трезубец клон не поймает — мобы не
    подбирают предметы. Riptide игнорируется (рывок есть только у игрока), бросок выполняется всегда.
* **Стрелы**: тип — из первой стопки стрел в инвентаре владельца, количество — сумма **всех** стрел
  (`ArrowItem`). Это **копия**, инвентарь кастера не тратится. Каждый выстрел из лука или арбалета
  уменьшает запас; когда запас кончился — клон «попает» (`dispel()`), поэтому стрелять вечно он не может.
  Запас живёт в NBT (`Ammo`) и в поле `ShadowCloneEntity#ammo`. Ракеты для арбалета (фейерверки) пока
  не копируются — только стрелы.
* **Эффекты кастера** копируются в момент призыва (`copyEffects`): все активные `MobEffectInstance`
  с тем же усилителем и остатком длительности — и баффы, и дебаффы (клона отравленного кастера
  тоже отравит, это честно).
* **Клон из бумажной бомбы** («Explosive Paper» / `narutomod:paper_bomb`). Если кастер в момент
  призыва держит бумажную бомбу в любой руке — клон помечается `explosive` (поле + NBT `Explosive`)
  и **взрывается, когда попает от удара** (`die()` → `explode()`). Голод по чакре и кончившиеся
  стрелы взрыва не дают: они вызывают `dispel()` напрямую, минуя `die()`.
  * Радиус — `NarutoConfig.paperbombExplosionRadius` (тот же, что у самой бомбы); блоки **никогда**
    не ломаются (`Level.ExplosionInteraction.NONE` жёстко), хотя у обычной бумажной бомбы это
    настраивается через `paperbombBlockDamage`.
  * Урон и звук — ванильные, от `Level#explode`. Своим (кастеру и его же клонам) урон и отброс
    не достаются: `events/ExplosionEvents` на `ExplosionEvent.Detonate` выкидывает всех `isAlliedTo`
    из `getAffectedEntities()`. Это работает, потому что Forge вызывает `onExplosionDetonate` **до**
    цикла урона и передаёт тот же самый список (проверено по `patches/.../Explosion.java.patch`).
  * Бомба у кастера **не тратится** — это только «метка» стиля клона.
* **Координация оружие/щит**: единая точка правды — `ShadowCloneEntity#wantsToShoot()` (есть дальнобойное
  оружие, для лука/арбалета есть стрелы, цель дальше `MELEE_REACH`). Дальнобойное оружие берёт верх на
  дистанции, щит — в ближнем бою; оба гола спрашивают этот метод, поэтому они не дерутся за руки клона.
  Если добавляешь третью «плюшку» с использованием рук — добавляй её условие туда же.

Новая «плюшка» обычно = новый `Goal` в `entity/ai/` + регистрация в `registerGoals()` +
(если нужно) копирование предмета в `copyEquipment`/`copyAmmo` + поза рук в `ShadowCloneRenderer`.
Голы регистрируются всегда, а применяются по условию — так клону не нужно пересобирать цели, если предмет
появился или пропал.

### Рендер

`ShadowCloneRenderer` переключает `wideModel`/`slimModel` по скину кастера, берёт скин у загруженного
`AbstractClientPlayer` (иначе `DefaultPlayerSkin` по UUID) и выставляет позы рук как `PlayerRenderer`:
`ArmPose.BLOCK` для щита, `ArmPose.BOW_AND_ARROW` для натянутого лука, `ArmPose.THROW_SPEAR` для
замаха трезубцем, `ArmPose.CROSSBOW_CHARGE`/`CROSSBOW_HOLD` для арбалета, иначе `ITEM`/`EMPTY`.

## Техники вне клонов

### Солдатские пилюли (`narutomod:military_pills`)

`item/MilitaryPillItem.java` — еда (`FoodProperties`), а на `finishUsingItem` (только на сервере):
`+35` чакры и стамины (`INinjaData#addChakra/addStamina`, оба клампят), Speed I + Haste I на 30 с и
Regeneration I на 8 с, плюс `getCooldowns().addCooldown(this, 60*20)` — минута, дольше действия баффов.
Текстура `textures/items/military_pills.png` лежала в моде неиспользованной ещё с 1.7.10 (там предмет
звался `item.military_pills.name=Military Pills`), так что модель — одна ссылка `narutomod:items/military_pills`.
Рецепт — рукописный `data/narutomod/recipes/military_pills.json` (в проекте датагена нет вообще, `data/`
до этого не было): sugar + wheat + glowstone dust → 3 штуки. В креатив-таб «материалы» добавлено.

### Бревно-метка (`narutomod:chakra_marker`)

`abilities/jutsus/ChakraMarkerJutsuAbility.java` — CHANNELED, комбо `C B B V` → **`C B B V C`** (5
печатей; удлинено по просьбе — двух знаков было слишком мало):

* **Тап** (`MIN_ACTIVATE`, `ticksActive == -1`, `canActivateBelowMinCharge()` по умолчанию true) — обмен:
  кастер встаёт туда, где бревно, бревно — туда, где стоял кастер (15 чакры). То есть возврат «домой»
  и возврат «обратно» стоят по техники.
* **Удержание** — поставить новое бревно под ноги (25 чакры), старое стирается.

Логика:

* Бревно — сущность `entity/ChakraMarkerEntity.java` (`Mob`, `MobCategory.MISC`): не двигается
  (`setNoGravity`, `isPushable → false`, голов нет вовсе), **неубиваемое по-настоящему**
  (`hurt → false`, `isInvulnerableTo → true`, пустые `kill`/`die`: `setInvulnerable` одного не хватает,
  его пробивает креатив и `genericKill` от `/kill`), без деспавна
  (`setPersistenceRequired` + переприменение в `readAdditionalSaveData`), светится портальными
  частицами раз в 4 тика, знает своего владельца (`Owner` в NBT). Рендер — тем же
  `SubstitutionLogModel` + `SubstitutionLogRenderer.SUBSTITUTION_LOG`, отдельный `ChakraMarkerRenderer`.
* Координата и измерение бревна живут в `INinjaData#getMarkerLoc/getMarkerDimension/setMarkerLoc`
  (`NinjaData`: поля + `serializeNBT`/`deserializeNBT`, **без** `@Sync`). В отличие от метки подмены
  (только в памяти) бревно переживает выход из игры — это и есть смысл.
* Стирание старого бревна — поиск `getEntitiesOfClass(ChakraMarkerEntity.class, AABB(координата).inflate(2),
  e -> e.isOwnedBy(player))` в его измерении. Если чанк старого бревна не загружен, оно не найдётся и
  останется стоять — известное и осознанное ограничение.
* Обмен между измерениями запрещён (`jutsu.fail.wrongdimension`), в одном — на любой дистанции; в
  сообщениях `jutsu.fail.nomarker` / `wrongdimension` (в RU-файле нашлись свои строки для них, но у
  остальных ванильных `jutsu.fail.*` русских переводов как не было, так и нет).
* `getTranslationKey(ninjaData, ticksActive)` добавляет `.mark` при удержании, ровно как у подмены:
  «You cast Chakra Marker» / «... - Log Planted».

## Техники и предметы второго набора

Всё ниже — один батч, добавленный после клонов. Комбо свободны и не конфликтуют с ванильными
(`1`, `2`, `3`, `12`, `121`, `132`, `1332`, `1322`, `133231`):

| Техника / предмет | Комбо | Тип | Расход | Файл |
| --- | --- | --- | --- | --- |
| Кулак чакры | `C C` | TOGGLE | 0.02 чакры/тик + 1 за блок + 3 за удар | `abilities/utility/ChakraFistAbility.java` + `events/ChakraFistEvents.java` |
| Чакра-щит | `B V B` | INSTANT | вся чакра (мин. 20) | `abilities/jutsus/ChakraShieldAbility.java` + `events/ChakraShieldEvents.java` |
| Второе дыхание | `V V B` | INSTANT | вся чакра (мин. 60), КД 10 мин | `abilities/jutsus/SecondBreathAbility.java` + `events/SecondBreathEvents.java` |
| Регенерация чакрой | `V B B C` | TOGGLE | 6 чакры + 1 еда за 1 HP | `abilities/jutsus/ChakraRegenerationAbility.java` |
| Рудокоп (Земля) | `C V V C` | INSTANT | 25 чакры (нужна кирка в руке) | `abilities/jutsus/OreExcavationJutsuAbility.java` |
| Расенган | `C B B C B` | CHANNELED | 0.6 чакры/тик | `abilities/jutsus/RasenganJutsuAbility.java` |
| Восемь врат | `V B B B` | INSTANT | 25 чакры + 15 стамины за врата | `abilities/jutsus/EightGatesAbility.java` |
| Запечатать врата | `V B B V` | INSTANT | бесплатно (только врата 1–7) | `abilities/jutsus/GateReleaseAbility.java` |
| Камуи | `B V V C C` | CHANNELED | 40 чакры шаг / 1.5 за тик + 30 за затяг | `abilities/jutsus/KamuiJutsuAbility.java` + `events/KamuiEvents.java` |
| Сбор природной энергии | `V B C` | CHANNELED | бесплатно, но только стоя на месте | `abilities/jutsus/SenjutsuChargingAbility.java` |
| Режим Мудреца | `V B V` | INSTANT | 50 сенин-маны на вход, 0.25/тик | `abilities/jutsus/SageModeAbility.java` |
| Гендзюцу | `C B C` | INSTANT | 35 чакры, КД 10 с | `abilities/jutsus/GenjutsuJutsuAbility.java` + `events/GenjutsuEvents.java` |
| Чакра-печать | — | предмет | 1 за призыв клонов | `NarutoItems.CHAKRA_SEAL` |
| Печать рабочего | — | предмет | 1 за рабочего | `item/WorkerSealItem.java` + `entity/ai/WorkerHarvestGoal.java` |
| Чакра-батарея | — | предмет | хранит 500, привязка к первому владельцу | `item/ChakraBatteryItem.java` |
| Рамен | — | предмет (еда) | — | `NarutoItems.RAMEN` |
| Самехада / Кубикирибочо | — | оружие | — | `item/ComboSwordItem.java` + `events/ComboSwordEvents.java` |

Ключевые решения:

* **Кулак чакры** — сила живёт в Forge-событиях, а не в способности: `PlayerEvent.BreakSpeed` множит
  скорость копания на `2.5` (правится **на обоих сторонах**, иначе клиент рисует медленное копание),
  `BlockEvent.BreakEvent` берёт по 1 чакре **за сломанный блок** (не за тик), `LivingHurtEvent` —
  3 чакры за ближний удар и `+3` урона. `handleCost` дополнительно держит регенерацию чакры
  «придавленной» (`REGEN_DELAY = 10`, обновляется каждый тик) — стойка стоит не только 0.02/тик.
  Способность реализует `Ability.Toggled`, состояние спрашивается через
  `ChakraFistAbility.isActive(ninjaData)` (`getToggleAbilityData().getAbilitiesHashSet().contains(ID)`).
* **Чакра-щит** — «вторая полоска здоровья», показанная как броня. Вся чакра превращается в пул
  очков (`INinjaData#getShieldPoints`), пул висит на атрибуте `Attributes.ARMOR` как
  `addTransientModifier` с фиксированным UUID (`ChakraShieldAbility.applyShield/removeShield`), а урон
  снимается **до** здоровья в `ChakraShieldEvents#onLivingHurt` (щит тратится 1:1, атрибут
  переприменяется, чтобы полоска брони шла вниз). Стакать нельзя: при активном щите каст падает с
  `jutsu.fail.shield`. На респавне — `PlayerEvent.Clone`: смерть стирает щит, смена измерения
  переприменяет модификатор (новый `Player` не наследует модификаторы).
* **Второе дыхание** — тратит **всю** чакру и ставит флаг `INinjaData#isSecondBreathArmed`. Флаг
  висит, пока не прилетит **смертельный** удар: `SecondBreathEvents#onLivingHurt` (приоритет
  `LOWEST`, чтобы не отбирать спасение у других эффектов) отменяет событие, поднимает HP до
  `SECOND_LIFE_HEALTH = 10`, снимает флаг и даёт `DARKNESS` на минуту (мягкая «слепота»: ванильный
  `BLINDNESS` слепит полностью, а просили «не сильную»). КД 10 минут — через `Ability.Cooldown`.
* **Регенерация чакрой** — TOGGLE, лечит по 1 HP раз в `HEAL_INTERVAL = 40` тиков. С едой —
  6 чакры + 1 food point (`FoodData#setFoodLevel`), без еды — 15 чакры и без расхода еды. Еды
  хватает только на 2 HP за «окорочек», поэтому основной расход всё равно чакра.
* **Рудокоп** — флудит куб радиусом 5 и ломает всё, что под тегом `forge:ores` (модовые руды тоже),
  но не больше `MAX_BLOCKS = 64` блоков за каст. **Требует кирку в руке** (`handleCost` отказывает и
  не берёт чакру, если в главной руке не `PickaxeItem`), и каждая руда проверяется
  `ItemStack#isCorrectToolForDrops(state)` — то есть ломается только то, что эта кирка реально взяла
  бы (тир учитывается: каменная не возьмёт алмазную руду). Прочность тратит
  `ItemStack#mineBlock(level, state, pos, player)`, по 1 за блок; кирка проверяется **заново перед
  каждым блоком**, поэтому сломавшаяся в середине каста кирка обрывает остальное. Позиции собираются
  **до** разрушения (иначе перебор `BlockPos.immutable()` в живом уровне), ломается через
  `level.destroyBlock(pos, true, player)`.
* **Расенган** — CHANNELED: `handleCost` жжёт 0.6 чакры/тик, `handleChannelling` рисует шар у руки,
  `performServer` получает число продержанных тиков и бьёт по площади спереди. Тап (< 20 тиков) —
  пшик, 60 тиков — максимум (урон 4→16, вылет 0.8→1.8). Дальше тиков больше — урон не растёт.
* **Восемь врат** — состояние `INinjaData#getGateCount` (server-only, до 8). Каст `V B B B` открывает
  следующее окно, **закрыть врата 1–7 можно только отдельной техникой** `V B B V`
  (`GateReleaseAbility`, бесплатно, `jutsu.fail.gates_none` когда нечего закрывать), а **на восьмых
  вратах запечатать уже нельзя** (`jutsu.fail.gates_open`) — единственный выход из них это смерть, и в
  этом смысл: восьмые врата рассчитаны на то, чтобы их потратить. Эффекты (`SPEED`, `DAMAGE_BOOST`,
  `DAMAGE_RESISTANCE`, `JUMP`, длительность 40 тиков) обновляются каждый тик из
  `EightGatesAbility.maintain`, вызываемого в `PlayerEvents#onEntityUpdate`. Плата — слив здоровья
  **напрямую**, раз в секунду, **квадратично** по числу врат (`DRAIN_COEFFICIENT = 0.00833 × врата²`).
  Слив идёт мимо пайплайна урона, прямо в `player.setHealth(...)` (и `player.die(...)` на смертельном
  пороге), потому что полоска здоровья рисуется половинками сердца: дробный урон визуально «не
  существует», а «нормальный» урон могли бы съесть броня, `DAMAGE_RESISTANCE` самих врат, чакра-щит
  или креатив. Остаток копится в `INinjaData#getGateBacklashCarry`, списываются только **целые**
  единицы HP. Отклик ванильного удара (вспышка через `broadcastEntityEvent((byte) 2)`, хрип,
  `DAMAGE_INDICATOR`) проигрывается вручную, а `NarutoDamageTypes.GATE_BACKLASH` (JSON в
  `data/narutomod/damage_type/` + теги `bypasses_*`) нужен только для сообщения о смерти;
  `ChakraShieldEvents` его отдельно не поглощает. Кривая привязана к двум числам: 2-е врата стоят
  0.033 HP/с (полная полоса — 10 минут), 8-е — 0.533 HP/с (~37 с жизни); между ними: 4 — 2.5 мин,
  6 — 67 с, 7 — 49 с. `EightGatesAbility.close` вызывает либо техника закрытия, либо смерть
  (`PlayerEvents#onPlayerClone`); `gateCount` в NBT не пишется, поэтому выход из игры врата тоже
  сбрасывает.
* **Камуи** — техника-два-в-одном, различимая по способу ввода: **тап** (`chargeAmount == -1`) это шаг
  в карманное измерение `narutomod:kamui` и обратно (40 чакры, точка возврата —
  `INinjaData#getKamuiReturnLoc/getKamuiReturnDimension`, пишется в NBT, поэтому выход из игры внутри
  пустоты не запирает), а **удержание** (≥ `MIN_CHANNEL = 20` тиков, 1.5 чакры/тик) затягивает цель.
  Цель — то, во что смотрит `player.pick(PULL_RANGE, 0, false)` (лучи не проходят через стены),
  `LivingEntity`, кроме кастера и креативных/наблюдателей; за сам затяг ещё 30 чакры.
  **Измерение — датапак + свой генератор**: `data/narutomod/dimension_type/kamui.json` +
  `data/narutomod/dimension/kamui.json` (биом `the_void`, небо Края, `min_y=0, height=256`), а
  рельеф делает `world/KamuiChunkGenerator` (кодек `narutomod:kamui_pillars` в
  `world/NarutoWorldGen`, `Registries.CHUNK_GENERATOR`): сетка квадратных платформ
  `PLATFORM_SIZE = 5` с шагом `PLATFORM_PITCH = 7` (то есть 2 блока пустоты между ними), все одной
  высоты, верхняя грань на y = 1. Никаких пещер, руды и мобов: `fillFromNoise` кладёт блоки по
  координатам, остальные абстрактные методы `ChunkGenerator` — пустышки. Точка прилёта кастера —
  `CASTER_ARRIVAL` (0.5, 1, 0.5), цели — `TARGET_ARRIVAL` (3.5, 1, 0.5), обе на первой платформе.
  Спавн запрещён событием. Затянутое живёт по таймеру и возвращается
  **ровно туда, откуда его взяли**: координата и измерение пишутся в `entity.getPersistentData()`
  (`BANISH_TICKS` / `BANISH_RETURN` — работает и на мобах, и на игроках, и переживает сейв), обратный
  отсчёт — `KamuiEvents#onLivingTick`, который смотрит только сущности **внутри** кармана. Кастер под
  таймер не попадает. Срок хранения — конфиг `kamuiEnabled` / `kamuiTransferSeconds`.
* **Фазовое тело Камуи** — пока канал удержан, `INinjaData#isKamuiPhasing()` (поле `@Sync`, поэтому
  состояние едет на клиент) включается в `handleChannelling`, а применяется каждый тик в
  `KamuiJutsuAbility#applyPhasing`, который зовётся из `NinjaCapabilityHandler#onPlayerUpdate` **на
  обеих сторонах**: `player.noPhysics = true` + `setNoGravity(true)`. Клиент обязан выключать
  коллизии тоже, иначе он выталкивает игрока из стены, которую сервер разрешает пройти, и шаг
  дёргается; гравитация выключается, чтобы кастер не провалился сквозь пол, пока идёт сквозь стену.
  Урон гасится в `KamuiEvents#onLivingHurt` (приоритет `HIGHEST`, чтобы не тратить «второе дыхание» на
  удар, который и так не пройдёт): исключения — `DamageTypeTags.BYPASSES_INVULNERABILITY` (`/kill`,
  пустота) и `DamageTypes.STARVE`. `isHolding` не верит одному флагу — техника обязана быть той же,
  что каналится, иначе смерть/разрыв связи оставили бы игрока бессмертным. `performServer` всегда
  снимает флаг и зовёт `unstick`, который ищет свободное место в радиусе 2/вверх 4, если игрок отпустил
  комбо внутри стены.
* **Мягкая посадка** (без комбо, автоматически) — в `PlayerEvents#livingFall`: считается урон, который
  приземление всё равно отняло бы (ванильная формула `ceil(distance - 3 - jumpBoost)`), цена —
  `SOFT_LANDING_STAMINA_PER_DAMAGE = 2` стамины за единицу урона. Хватает — падение гасится полностью
  (`setDistance(0)`, облако частиц) с задержкой регенерации 3 с; не хватает — **приземление обычное и
  стамина не тратится вообще**. Прежний «частичный пересчёт» (`distance *= 0.3`, `-= 5`, `*= 0.6`)
  удалён: он и выглядел случайным. Цена теперь пропорциональна опасности: 4 блока — 2 стамины,
  40 блоков — 74.
* **Печать рабочего** (`item/WorkerSealItem`, ПКМ по блоку) — тот же `ShadowCloneEntity`, но с флагом
  `worker` (`DATA_WORKER`, сохраняется в NBT), пустыми руками, сроком жизни 10 минут и координатой
  «поста» (`workerHome`). Гол `WorkerHarvestGoal` (приоритет 1, работает только у рабочих) ищет
  спелые `#minecraft:crops` в радиусе 8 блоков от поста, срезает их через
  `Block.getDrops(...)` + `level.destroyBlock(pos, false)` и **сразу засевает заново**
  (`CropBlock#getStateForAge(0)`), а лут кладёт в свой мешок (`collect`), сериализуемый в NBT
  (`WorkerBag`). `CloneFollowOwnerGoal` отказывается работать у рабочих, как и у медитирующих.
  При исчезновении (`dispel` → `releaseLoot`) мешок отдаётся **через 30 секунд**
  (`WORKER_LOOT_DELAY`) через `INinjaData#scheduleDelayedTickEvent`: это то, что делает рабочего
  работником, а не телепортом фермы. Если владелец не в сети — лут выпадает на месте клона.
* **Великие мечи** (`ComboSwordItem`) — цепочка ударов живёт **в NBT стека**
  (`narutomod:combo`, `narutomod:combo_time`): каждый удар, попавший в окно (4–5 с), добавляет звено,
  урон следующего удара растёт (`damagePerStep`; Самехада — 0.5, Кубикирибочо — 0.75, максимум 20
  звеньев). Урон добавляется в `ComboSwordEvents#onLivingHurt` (приоритет `HIGH`) — момент удара
  единственный, когда цепочку можно прочитать и продлить до расчёта урона. Самехада дополнительно
  «высасывает» 1.5 чакры за удар. Оба меча — 2D-спрайты (`item/handheld`), 3D-модели 1.7.10 пока не
  портированы.
* **Сбор природной энергии и режим Мудреца.** Пул `seninMana` (float, `@Sync`, **пишется** в NBT) живёт
  в `INinjaData`/`NinjaData`; максимум — конфиг `maxSeninMana` (по умолчанию 100), выключение всей ветки
  — `sageModeEnabled`. Копит `SenjutsuChargingAbility` (CHANNELED, `V B C`): **0.5/тик и только если
  `isStill`** (на земле и `getDeltaMovement().horizontalDistanceSqr() < 0.0016`), движение прогресс не
  сбрасывает. Тратит `SageModeAbility` (INSTANT, `V B V`): вход требует 50, дальше `DRAIN_PER_TICK = 0.25`,
  режим **сам гаснет на нуле** (не по таймеру). Эффекты выдаются на 40 тиков и обновляются каждый тик —
  значит, вместе с режимом они и заканчиваются; плюс `+0.5` чакры за тик и частицы `HAPPY_VILLAGER`.
  Обслуживание — `SageModeAbility#maintain`, зовётся из `PlayerEvents#onEntityUpdate` рядом с
  `EightGatesAbility#maintain`. Флаг `sageMode` (`@Sync`) **не** пишется в NBT: смерть и выход из игры
  его закрывают. Полоска — `client/gui/SeninGUI` (рисуется руками, только когда есть что показывать,
  над полоской чакры), добавлена в `overlays` в `NarutoInGameGUI`.
* **Гендзюцу** (`GenjutsuJutsuAbility`, `C B C`, INSTANT, 35 чакры, КД 10 с) — цель берётся
  `player.pick(RANGE=24, 0, false)` → `EntityHitResult` → `Mob`. Состояние висит **на самой сущности**
  в `getPersistentData()` (`narutomod:genjutsu_until` = `gameTime + DURATION`, `narutomod:genjutsu_caster`),
  поэтому переживает выгрузку чанка и сейв. `events/GenjutsuEvents` держит поведение: каждый тик
  сбрасывает недопустимую цель, раз в секунду назначает ближайшего другого моба (`findVictim`, радиус 16)
  и сыплет `WITCH`. Ключевое — `LivingChangeTargetEvent`: пока иллюзия держится, смена цели на игрока
  **отменяется** (а `newTarget == null` пропускается, иначе жертву нельзя было бы отпустить). Из целей
  исключены `Player`, `ShadowCloneEntity`, другие жертвы и всё, с чем моб и так `isAlliedTo`.
* **Чакра-батарея** (`item/ChakraBatteryItem`, `stacksTo(1)`) — 500 чакры в NBT стека
  (`Chakra`), привязка к первому владельцу (`Owner` UUID + `OwnerName` для подсказки). ПКМ — забрать до
  50, ПКМ+Shift — долить до 50; чужому владельцу отказывает `jutsu.fail.battery.owner`. Привязка живёт
  в NBT самого предмета, поэтому переживает смерть и сундуки; `isFoil` включается при заряде > 0.

## Грабли, на которые уже наступали

* **Типы урона мода задаются вручную.** Датаген в проект не запускается при сборке
  (`src/generated/resources` пуст), поэтому JSON-ы типов урона (`fireball`, `water_bullet`,
  `gate_backlash`) лежат руками в `src/main/resources/data/narutomod/damage_type/`. Если файла нет,
  `NarutoDamageTypes#getDamageSource` падает с `Missing key in ResourceKey[... narutomod:<name>]`
  в первый же момент, когда снаряд попадает по цели, и **роняет сервер**. Поэтому в
  `NarutoDamageTypes` есть только те ключи, на которые есть JSON (плюс теги `bypasses_*` для
  `gate_backlash`). Добавляешь новый тип урона — сразу пиши и JSON.
* **Ванильная регенерация от еды.** При сытости ≥ 18 и насыщении игрок лечит до 2 HP/с, что больше
  любого слива здоровья в моде. Если технике нужно «сжигать» жизнь (восемь врат), одного урона
  мало — надо гасить и лечение: см. `PlayerEvents#livingHeal` (`LivingHealEvent#setAmount(0)` при
  `getGateCount() > 0`).
* **Измерения — тоже датапак, и тоже руками.** Как и типы урона, кастомное измерение задаётся файлами
  в `src/main/resources/data/narutomod/dimension(_type)/`, датаген для них не запускается. Формат
  сверяй по ванильным данным из `client.jar` (там же, в кэше Gradle, лежат
  `data/minecraft/dimension_type/*.json` и `worldgen/flat_level_generator_preset/*.json`). Если
  `dimension_type` битый или отсутствует, сервер **не падает**: измерение просто не регистрируется, а
  `Server#getLevel(...)` возвращает `null` — поэтому в коде всегда проверяй результат и показывай
  игроку `jutsu.fail.kamui.nodimension`, а не телепортируй «в никуда».
* **Свой `ChunkGenerator` требует реализации больше, чем кажется.** В 1.20.1 кроме `codec()` абстрактны
  `fillFromNoise`, `applyCarvers`, `buildSurface`, `spawnOriginalMobs`, `getGenDepth`, `getSeaLevel`,
  `getMinY`, `getBaseHeight`, `getBaseColumn` **и `addDebugScreenInfo(List<String>, RandomState,
  BlockPos)`** — последний легко забыть: он не имеет диапазона строк в `client_mappings.txt`, но
  именно он даёт ошибку «is not abstract and does not override abstract method». Генератор
  регистрируется как кодек (`DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID)`), а его
  `codec()` — `RecordCodecBuilder` с полем `biome_source` (`BiomeSource.CODEC`), потому что генератор
  обязан получить источник биомов из JSON (`{"type": "minecraft:fixed", "biome": "minecraft:the_void"}`).
  Официальные имена методов и сигнатуры удобно доставать грепом по `client_mappings.txt` из кэша
  ForgeGradle (`mcp_repo/.../joined/downloadClientMappings/client_mappings.txt`), а не угадывать.
* **`LivingSpawnEvent.CheckSpawn` в 1.20.1 больше нет.** Проверка позиции моба в цепочке спавна — это
  `net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck` (`@HasResult`;
  `setResult(Event.Result.DENY)` запрещает спавн). Старый `CheckSpawn` из туториалов не компилируется:
  класса `LivingSpawnEvent` в Forge 47 нет (проверено по `forge-...-sources.jar`).
* **`ITeleporter.placeEntity`.** Переброска не-игрока между измерениями — это
  `entity.changeDimension(level, teleporter)`, где `placeEntity(...) -> repositionEntity.apply(false)`
  выполняет **ванильную** логику переноса (патч `Entity.java.patch`: «Forge: Start vanilla logic»), то
  есть сущность уже добавлена в целевой уровень и чанк загружен. Позицию можно выставить после этого
  через `moveTo`. Игрока проще телепортировать `ServerPlayer#teleportTo(ServerLevel, ...)`: у
  `Entity` такой перегрузки нет (в SRG видно только
  `m_264318_(ServerLevel, double, double, double, Set<RelativeMovement>, float, float)`).

* **Текстуры предметов/блоков.** В моде они лежат в легаси-папках `textures/items/` и `textures/blocks/`
  (множественное число, как в 1.7.10), а в 1.19.4+/1.20.1 атлас собирается из директорий
  `textures/item/` и `textures/block/` (единственное число) плюс описаний атласа. Поэтому в моде есть
  `src/main/resources/assets/minecraft/atlases/blocks.json` с `directory`-источниками на `items/` и
  `blocks/`. Без него в логе 79 предупреждений `Missing textures in model ...` и все предметы
  фиолетово-чёрные. **Описание атласа обязано лежать в namespace самого атласа** (`assets/minecraft/atlases/blocks.json`);
  файл в своём namespace (`assets/narutomod/atlases/blocks.json`) не читается вообще (проверено:
  `SpriteResourceLoader.create` ищет `atlases/<path>.json` в namespace атласа, а `getResourceStack`
  склеивает описания из всех паков — поэтому ванильные источники при этом не теряются).
* **Переводы строк.** На машине `core.autocrlf=true`, из-за этого ломался `version.txt` (в версии
  попадал `\r`, и Gradle не собирал jar). Лечится `.gitattributes` (LF для `version.txt`, CRLF для
  `*.bat`) и `replaceAll('[\\r\\n]', '')` в `build.gradle`. Не откатывать.
* **Порядок аргументов `Goal`.** У `Goal` без флагов (`CloneUseShieldGoal`) приоритет не важен для
  параллельного запуска, но голы, которые борются за руки (`startUsingItem`), нужно разводить условиями,
  иначе один голос навсегда блокирует второй.
* **`@Override` на `performRangedAttack`** требует `implements RangedAttackMob`: в 1.20.1 `Mob` его не
  реализует (в отличие от `AbstractSkeleton`).
