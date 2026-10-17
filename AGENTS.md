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
| Медитация чакры | `C B B C` | 60 | 2 | 30 с | 0 | 0 | 90 с |

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

`abilities/jutsus/ChakraMarkerJutsuAbility.java` — CHANNELED, комбо `C B` (сосед техники подмены `C V`):

* **Тап** (`MIN_ACTIVATE`, `ticksActive == -1`, `canActivateBelowMinCharge()` по умолчанию true) — обмен:
  кастер встаёт туда, где бревно, бревно — туда, где стоял кастер (15 чакры). То есть возврат «домой»
  и возврат «обратно» стоят по техники.
* **Удержание** — поставить новое бревно под ноги (25 чакры), старое стирается.

Логика:

* Бревно — сущность `entity/ChakraMarkerEntity.java` (`Mob`, `MobCategory.MISC`): не двигается
  (`setNoGravity`, `isPushable → false`, голов нет вовсе), неубиваемое, без деспавна
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

## Грабли, на которые уже наступали

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
