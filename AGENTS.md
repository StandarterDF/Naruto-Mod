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

Дев-клиент запускается вместе с **JEI** (Just Enough Items 15.71.0.224) — рецепты можно смотреть прямо в
игре. Зависимость объявлена как `runtimeOnly fg.deobf("maven.modrinth:jei:15.71.0.224")`, репозиторий —
`https://api.modrinth.com/maven`. (Тот же jar лежит и на `maven.blamejared.com`, но с этой машины он
качается по ~300 байт/с, поэтому берём CDN Modrinth — не переключать обратно.) `runtimeOnly` выбран
осознанно: JEI остаётся **только dev-удобством**, он не попадает ни в compile-classpath, ни в публикуемые
jar-ы, и код мода на его API не опирается. Дев-серверу он тоже не мешает: мод сам проверяет сторону
(`DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)`), на выделенном сервере грузится только его common-часть.

**Тесты в игре делает пользователь, а не агент.** Агент доводит код до успешной компиляции
(`build` / `compileJava`) и на этом останавливается; дев-клиент/дев-сервер и SelfTest-сценарии агент
не запускает. В Minecraft автотесты работают ужасно: долгий запуск, ложные результаты, а игровой
клиент отбирает управление и время. Проверка поведения в игре — всегда за пользователем.

Если пользователь **отдельно** попросит подготовить автотест, приёмы такие (иначе не запускать):

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
| ИИ | `entity/ai/CloneFollowOwnerGoal`, `CloneDefendOwnerGoal`, `CloneOwnerHurtByTargetGoal`, `CloneOwnerHurtTargetGoal`, `CloneRangedAttackGoal`, `CloneUseShieldGoal`, `ClonePickupLootGoal` |
| Рендер | `client/renderer/entity/ShadowCloneRenderer.java` (`MeditatingCloneModel` + `ModelLayers.PLAYER(_SLIM)`) |
| Регистрация/атрибуты | `entity/NarutoEntities.java` |
| Звук | `sounds/NarutoSounds.java` + `assets/narutomod/sounds.json` (`clone_poof.ogg`) |

### Техники

| Техника | Комбо | Чакра | Клонов | Жизнь | Урон | Скорость | КД |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Теневой клон | `C B B V` | 40 | 1 → 4 (по освоению) | 25 с | 2 | 0.3 | 3 с |
| Чиби-клон | `C B V V` | 20 | 1 → 5 (по освоению) | 15 с | 1 | 0.35 | 2 с |
| Мульти-клон | `C B B V B C` | 80 | 4 → 10 (по освоению) | 25 с | 2 | 0.3 | 10 с |
| Медитация чакры | `C B B C` | 60 | 2 | **180 с** | 0 | 0 | 60 с |

**Расход печати.** Печать (`narutomod:chakra_seal`) тратят только **мульти-клон и медитация**
(`Settings#requiresSeal`; обычный и чиби-клон бесплатны), и тратится **одна** печать за каст —
независимо от числа клонов. Проверка в `ShadowCloneJutsuAbility#handleCost`, списание в
`#performServer`; на полном освоении техники печать не нужна вовсе (`Mastery.sealFree`). Креатив
ничего не тратит и может кастовать без печати. Нет печати — `jutsu.fail.noseal`. **Рабочий призыв
(шифт) печати не требует вообще** — см. раздел про рабочих ниже.

### Поведение (паритет с 1.7.10)

* **1 HP у боевого клона**: любой непокрытый удар «попает» его. Урон/скорость/время жизни задаются
  техникой через `applyVariantAttributes` (у одного типа сущности один `AttributeSupplier`, поэтому
  значения ставятся после создания). Исключение — **рабочий**: у него `WORKER_HEALTH = 4` (ставится в
  `setWorker(true)`, там же клон долечивается, если был целым), чтобы он мог пережить удар и ответить.
  В `readAdditionalSaveData` флаг рабочего применяется **до** `super.readAdditionalSaveData`: ваниль
  клампит сохранённое здоровье по максимуму, и без этого раненый рабочий возвращался бы с 1 HP.
* **Падение клонов**: `ShadowCloneEntity#causeFallDamage` гасит урон, пока `fallDistance <=`
  `SAFE_FALL_DISTANCE = 10` блоков (клон — чакра-конструкт с «подготовкой» кастера, а не мешок с
  мясом, и от лёгкого падения не должен разваливаться). Выше 10 блоков гашение НЕ работает —
  `super.causeFallDamage` наносит обычный урон, и клон с 1 HP (или рабочий с 4) попает на земле.
  Слайм/сено и прочие `Block#fallOn` со своим множителем проходят как обычно.
* Смерть = `dispel()`/`pop()`: облако дыма + `clone_poof`, без лута и без трупа (`dropChance = 0`).
* `pop()` **всегда** отдаёт мешок рабочего **сразу** — и когда истёк срок (`dispel()`), и когда убили
  (`die` → `pop()`). Отложенной выдачи по таймеру больше нет: она выглядела так, будто собранное
  пропало. Убитый клон дополнительно взрывается, если был сделан из бумажной бомбы.
* Клон **не** деспавнится по расстоянию (`removeWhenFarAway = false`), персистентность
  переприменяется в `readAdditionalSaveData` — ванильный `Mob#readAdditionalSaveData` её сбрасывает.
* Цели **без самопоиска врагов**, но защита владельца упреждающая: `HurtByTarget` (клона ударили) →
  `CloneDefendOwnerGoal` (**моб уже взял владельца в цель**, но ещё не ударил) → `CloneOwnerHurtByTargetGoal`
  (владельца ранили) → `CloneOwnerHurtTargetGoal` (владелец ударил). Сам клон врагов **не** ищет
  (`NearestAttackableTargetGoal` убран осознанно): иначе он уходит бить пиглинов, с которыми кастер
  мирно торгует. `CloneDefendOwnerGoal` считается по `Mob#getTarget() == владелец`, поэтому ловит именно
  того, кто сам напал на кастера (зомби, скелет, крипер), и не реагирует на корову, которую кастер задел.
  Раз в 10–20 тиков (свой кулдаун) клон сканирует `Mob.class` в кубе 16 блоков вокруг владельца и берёт
  ближайшего; дальше бой ведут обычные `MeleeAttackGoal`/`CloneRangedAttackGoal`, а сам гол сразу
  отпускает цель (`canContinueToUse → false`) и потому не держит флаг. Медитирующий клон из гола
  выключен, рабочий — тоже: цель уходит через `setTarget`, где рабочему проходит только самозащита.
  В target-селекторе клонов теперь 1 `HurtByTarget` → 2 `CloneDefendOwnerGoal` →
  3 `CloneOwnerHurtByTargetGoal` → 4 `CloneOwnerHurtTargetGoal`.
* У **рабочих** нет целей по чужому выбору: `ShadowCloneEntity#setTarget` при `isWorker()` пропускает
  всё, кроме **самозащиты** — принять можно только того, кто ударил самого рабочего
  (`target == getLastHurtByMob()`), и `null` (сброс цели) проходит всегда. Так рабочий не отвлекается
  на драку по цели владельца (иначе клон с мечом в руке убегал убивать соседских коров, которых задел
  кастер) и не срывается на чужую тревогу (`HurtByTargetGoal#alertOthers` зовёт `setTarget` у соседних
  клонов), но не стоит беспомощно под зомби. Когда цель есть, `WorkerBlockGoal#canUse/canContinueToUse`
  уступают (`isDefending()`), и бой ведёт обычный `MeleeAttackGoal`; цель снимается ванильным
  `HurtByTargetGoal`, после чего смена продолжается. Если рядом лежит лут, рабочий сперва сходит за ним
  (гол лута выше боя) и только потом отвечает обидчику.
* Голы: **подбор лута 0** → бой (дальний бой 1, ближний бой 2) → щит 3 → следование за владельцем 4 →
  двери/прогулка/взгляд. Бой раньше следования — как в 1.7.10: клон с целью идёт в бой, а не липнет
  к кастеру. Лут выше всего (см. ниже): клон сходит за предметом, даже если дерётся или работает.
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
  `INinjaData#getCooldownEvents()`, то есть КД техник идут вдвое быстрее. **В Режиме Мудреца** тот же
  приток идёт в **сенин-ману** (`addSeninMana`), а не в чакру: режим живёт на природной энергии, и
  медитация должна питать именно её. Второй тик снимается копией
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
    Channeling, Loyalty) сохраняются, но вернувшийся по Loyalty трезубец клон не поймает — подхватить
    снаряд на лету умеют только игроки (упавший на землю предмет клон подобрать уже может, см.
    `ClonePickupLootGoal`). Riptide игнорируется (рывок есть только у игрока), бросок выполняется всегда.
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
    Тот же фильтр реализован и для серии взрывов расенсюрикена (там `getDirectSourceEntity()` — сам
    диск, а щадит он сторону владельца диска).
  * Бомба у кастера **не тратится** — это только «метка» стиля клона.
* **Подбор лута**: `entity/ai/ClonePickupLootGoal.java` — подбирает **любой** валяющийся на земле предмет
  (любой `ItemEntity`, без исключений по типу) в радиусе `SEARCH_RANGE` = 8 блоков и отдаёт его тем же
  `collect` → походный сундук, а если владельца нет рядом — в свой мешок. Гол **один на все случаи** и
  стоит на **приоритете 0** — выше боя, следования, щита и рабочих смен. `applies()` отсекает только
  клиентскую сторону и медитирующего клона, то есть подбирают **все** клоны, включая атакующих: уходя за
  лутом, клон отдаёт боевому голу (`MeleeAttackGoal`) флаг `MOVE`, а его `stop()` ванилью **сбрасывает
  цель** — поэтому цель читается в `canUse()` (`savedTarget`) до старта гола и возвращается в `stop()`,
  если клон не нашёл себе новую. Так он возвращается в драку, а не забывает её.
  Поиск идёт раз в `SEARCH_INTERVAL = 10` тиков, брать можно с `PICKUP_RANGE_SQR` = 4 (два блока — как
  `REACH_SQR` у рабочего). Предмет моложе `SNATCH_TICKS = 10` тиков не трогается (чтобы не хватать
  брошенное в воздухе); ванильный `ItemEntity#hasPickUpDelay()` **не** уважается — иначе брошенный
  кастером предмет клон ждал бы две секунды и выглядело бы, будто он его не видит. Недостижимый предмет
  бросается через `GIVE_UP_TICKS = 120` / `STUCK_TICKS = 40` и забывается на `FORGET_TICKS = 400`,
  иначе клон вечно стоял бы над ним. Дропы с убийства забираются мгновенно, ещё в
  `ShadowCloneEvents#onLivingDrops` (для **любого** клона, включая боевого), так что гол добирает только
  то, что осталось лежать.
  **Про приоритеты в ваниле**: `GoalSelector` перебирает голы в порядке регистрации (множество —
  `LinkedHashSet`, сортировки по номеру нет), а `WrappedGoal#canBeReplacedBy` отдаёт флаг только тому,
  у кого номер **строго меньше**: `this.isInterruptable() && other.getPriority() < this.priority`,
  где `isInterruptable()` по умолчанию `true` у любого гола. Отсюда два следствия: при равных
  приоритетах выигрывает зарегистрированный раньше, но **отобрать** флаг у уже работающего гола с равным
  приоритетом нельзя, и гол, который должен работать всегда, обязан иметь номер меньше всех — иначе его
  `canUse()` просто не вызовут. Проверено по байткоду `joined-...-srg.jar`: `Goal.m_6767_()` — это
  `isInterruptable()` (`return true;`), а `MeleeAttackGoal#stop()` зовёт `mob.setTarget(null)`, если
  цель не креативный/наблюдающий игрок.
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
(`1`, `2`, `3`, `12`, `121`, `1332`, `1322`):

| Техника / предмет | Комбо | Тип | Расход | Файл |
| --- | --- | --- | --- | --- |
| Кулак чакры | `C C` | TOGGLE | 0.02 чакры/тик + 1 за блок + 3 за удар | `abilities/utility/ChakraFistAbility.java` + `events/ChakraFistEvents.java` |
| Чакра-щит | `B V B` | INSTANT | вся чакра (мин. 20) | `abilities/jutsus/ChakraShieldAbility.java` + `events/ChakraShieldEvents.java` |
| Второе дыхание | `V V B` | INSTANT | вся чакра (мин. 60), КД 10 мин | `abilities/jutsus/SecondBreathAbility.java` + `events/SecondBreathEvents.java` |
| Регенерация чакрой | `V B B C` | TOGGLE | 6 чакры + 1 еда за 1 HP (2 HP/с) | `abilities/jutsus/ChakraRegenerationAbility.java` |
| Рудокоп (Земля) | `C V V C` | INSTANT | 10 чакры + 4 за каждый сломанный блок (нужна кирка в руке) | `abilities/jutsus/OreExcavationJutsuAbility.java` |
| Широкий прокоп (Земля) | `C B B` | TOGGLE | 2 чакры за каждый доп. блок (нужен инструмент в руке) | `abilities/jutsus/WideDigAbility.java` + `events/WideDigEvents.java` |
| Чутьё руды (Земля) | `C C V` | INSTANT | 30 чакры, КД 30 с | `abilities/jutsus/OreSenseAbility.java` + `network/s2c/ClientboundOreSensePacket.java` + `client/ClientOreSenseData.java` + `client/renderer/OreSenseRenderer.java` |
| Костер (Огонь) | `B C B` | INSTANT | 20 чакры (нужно смотреть на бревно) | `abilities/jutsus/CampfireJutsuAbility.java` |
| Огненное Дыхание (Огонь) | `C V B C` | CHANNELED | 0.5 чакры/тик + 10 за каст | `abilities/jutsus/FireBreathJutsuAbility.java` |
| Водяное Дыхание (Вода) | `C V B V` | CHANNELED | 0.5 чакры/тик + 10 за каст | `abilities/jutsus/WaterBreathAbility.java` |
| Ветряное Дыхание (Ветер) | `C V B B` | CHANNELED | 0.5 чакры/тик + 10 за каст | `abilities/jutsus/WindBreathAbility.java` |
| Дыхание Роста (Дерево) | `C V C C` | CHANNELED | 0.5 чакры/тик + 10 за каст | `abilities/jutsus/WoodBreathAbility.java` + `abilities/jutsus/AbstractBreathAbility.java` |
| Расенган | `C B B C B` | CHANNELED | 0.6 чакры/тик | `abilities/jutsus/RasenganJutsuAbility.java` |
| Расенсюрикен | `C B B C B V` | CHANNELED | 1 чакры/тик + 25 за бросок | `abilities/jutsus/RasenshurikenJutsuAbility.java` + `entity/jutsuprojectile/RasenshurikenJutsuEntity.java` |
| Восемь врат | `V B B B` | CHANNELED | 25 чакры + 15 стамины за врата (тап — открыть следующее); удержание последней клавиши — закрыть (врата 1–7, бесплатно) | `abilities/jutsus/EightGatesAbility.java` |
| Камуи | `B V V C C` | CHANNELED | 40 чакры шаг / 1.5 за тик + 30 за затяг | `abilities/jutsus/KamuiJutsuAbility.java` + `events/KamuiEvents.java` |
| Фазовое тело Камуи | `V C B` | TOGGLE | 0.4 чакры/тик | `abilities/jutsus/KamuiPhaseAbility.java` + `events/KamuiEvents.java` |
| Сбор природной энергии | `V B C` | CHANNELED | бесплатно, но только стоя на месте | `abilities/jutsus/SenjutsuChargingAbility.java` |
| Режим Мудреца | `V B V` | INSTANT | 50 сенин-маны на вход, 0.1/тик | `abilities/jutsus/SageModeAbility.java` |
| Гендзюцу | `C B C` | INSTANT | 35 чакры, КД 10 с | `abilities/jutsus/GenjutsuJutsuAbility.java` + `events/GenjutsuEvents.java` |
| Чувство чакры | `C B C C` | CHANNELED | удержание — 0.3 чакры/тик; тап — ночное зрение за 1 чакру/с | `abilities/jutsus/ChakraSensingAbility.java` + `network/s2c/ClientboundSensingPacket.java` + `client/ClientSensingData.java` + `client/gui/SensingGUI.java` |
| Возврат клонов | `V C C` | INSTANT | 30 чакры, КД 5 с | `abilities/jutsus/CloneRecallAbility.java` |
| Хенге | `V C V` | INSTANT | 30 чакры, КД 5 с | `abilities/jutsus/HengeJutsuAbility.java` + `client/renderer/HengeRenderEvents.java` |
| Чакра-нить | `B B C` | CHANNELED | 0.25 чакры/тик | `abilities/jutsus/ChakraThreadAbility.java` |
| Сцепление чакрой | `C C B` | CHANNELED | 0.15 чакры/тик | `abilities/jutsus/ChakraAdhesionAbility.java` + `client/ClientAdhesionHandler.java` |
| Адаптация тела | `V B B` | TOGGLE | 0.2 чакры/тик, в лаве 0.5 | `abilities/jutsus/BodyAdaptationAbility.java` |
| Дымовая завеса | `V V V` | INSTANT | 25 чакры, КД 20 с | `abilities/jutsus/SmokeScreenAbility.java` + `entity/SmokeCloudEntity.java` + `events/SmokeScreenEvents.java` |
| Питание чакрой | `C V V` | TOGGLE | 10 чакры за 1 ед. сытости (раз в 2 с) | `abilities/jutsus/ChakraFeedingAbility.java` |
| Подавление звука | `B B B` | TOGGLE | 0.2 чакры/тик | `abilities/jutsus/SilenceAbility.java` + `mixin/common/GameEventDispatcherMixin.java` + `events/SilenceEvents.java` |
| Походный сундук | `B V C` | INSTANT | бесплатно | `abilities/jutsus/StorageChestAbility.java` + `menu/SupplyChest*.java` |
| Чакра-печать | — | предмет | 1 за призыв клонов | `NarutoItems.CHAKRA_SEAL` |
| Печать рабочего | присесть + комбо клонов | режим клонов | бесплатной печати нет; 75 чакры за призыв | `entity/WorkerJob.java` + `entity/ai/WorkerBlockGoal.java` |
| Чакра-батарея | — | предмет | хранит 500, привязка к первому владельцу; сама питает поддерживающие техники | `item/ChakraBatteryItem.java` |
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
  **`event.getNewSpeed()` — это уже посчитанная ванилью скорость вместе с множителем инструмента**
  (`ForgeEventFactory.getBreakSpeed` вызывается в конце `Player#getDigSpeed`, после
  `inventory.getDestroySpeed(state)`, Efficiency и Haste), так что `* 2.5` масштабирует «скорость с
  киркой», а не добавляет фиксированные «в два раза». `originalSpeed` для этого не годится — он бы
  выбросил чужие бонусы.
* **Чакра-щит** — «вторая полоска здоровья», показанная как броня. Вся чакра превращается в пул
  очков (`INinjaData#getShieldPoints`), пул висит на атрибуте `Attributes.ARMOR` как
  `addTransientModifier` с фиксированным UUID (`ChakraShieldAbility.applyShield/removeShield`), а урон
  снимается **до** здоровья в `ChakraShieldEvents#onLivingHurt` (щит тратится 1:1, атрибут
  переприменяется, чтобы полоска брони шла вниз). Стакать нельзя: при активном щите каст падает с
  `jutsu.fail.shield`. На респавне — `PlayerEvent.Clone`: смерть стирает щит, смена измерения
  переприменяет модификатор (новый `Player` не наследует модификаторы).
  **Сколько даёт бар — решает освоение**: `ARMOUR_PER_CHAKRA_BASE = 0.15` на старте линейно растёт до
  `ARMOUR_PER_CHAKRA_MASTER = 1.0` к полному освоению (`rate = base + (master - base) * masteryLevel`).
  То есть новичок за всю чакру получает тонкую плёнку (100 чакры → 15 очков), а мастер — полноценную
  вторую полоску (~100). Платится при этом всегда вся чакра, так что на раннем этапе техника дорогая и
  скромная.
* **Второе дыхание** — тратит **всю** чакру и ставит флаг `INinjaData#isSecondBreathArmed`. Флаг
  висит, пока не прилетит **смертельный** удар: `SecondBreathEvents#onLivingHurt` (приоритет
  `LOWEST`, чтобы не отбирать спасение у других эффектов) отменяет событие, поднимает HP до
  `SECOND_LIFE_HEALTH = 10`, снимает флаг и даёт `DARKNESS` на минуту (мягкая «слепота»: ванильный
  `BLINDNESS` слепит полностью, а просили «не сильную»). КД 10 минут — через `Ability.Cooldown`.
* **Регенерация чакрой** — TOGGLE, лечит по 1 HP раз в `HEAL_INTERVAL = 10` тиков (два HP в секунду —
  быстрее любого зелья; изначальные 40 тиков оказались бесполезно медленными). С едой —
  6 чакры + 1 food point (`FoodData#setFoodLevel`), без еды — 15 чакры и без расхода еды. Еды
  хватает только на 2 HP за «окорочек», поэтому основной расход всё равно чакра.
* **Рудокоп** — находит руды в кубе радиусом 5 и **обходит каждую жилу целиком**: BFS по соседним
  блокам **той же руды** (`Block`, не тег — чтобы две разные жилы, случайно коснувшиеся, не слились),
  поэтому жила вытаскивается и за пределами куба. **Начинает с той жилы, на которую смотрит кастер**:
  `collectVeins(level, player, tool)` сперва сеет BFS из `Targeting.lookedAtBlock(player, RADIUS)` и
  лишь потом прочёсывает куб — иначе при нескольких рудах в радиусе «в работу» уходила произвольная
  жила, иногда вообще за спиной, а чакра всё равно тратилась. Обход одной жилы вынесен в `followVein`.
  Кап — `MAX_BLOCKS = 128`. **Цена — за работу**:
  `handleCost` берёт `MIN_COST = 10`, а `performServer` списывает `PER_BLOCK = 4` за каждый блок **по
  ходу рубки** и останавливается, когда чакры не хватает (недобитая жила остаётся в земле). **Требует
  кирку в руке** (`handleCost` отказывает и не берёт чакру, если в главной руке не `PickaxeItem`), и
  каждая руда проверяется `ItemStack#isCorrectToolForDrops(state)` — то есть ломается только то, что
  эта кирка реально взяла бы (тир учитывается: каменная не возьмёт алмазную руду). Прочность тратит
  `ItemStack#mineBlock(level, state, pos, player)`, по 1 за блок; кирка проверяется **заново перед
  каждым блоком**, поэтому сломавшаяся в середине каста кирка обрывает остальное. Позиции собираются
  **до** разрушения (иначе перебор `BlockPos.immutable()` в живом уровне), ломается через
  `level.destroyBlock(pos, true, player)`.
* **Широкий прокоп** (`WideDigAbility`, TOGGLE `C B B` = `133`) — стойка, которая расширяет **каждый
  удар** в плоскость 3×3 по грани, в которую целились. Логика живёт в `WideDigEvents`
  (`BlockEvent.BreakEvent`, приоритет `LOWEST`, чтобы видеть отмену чужих защит): грань берётся у
  `Targeting.lookedAtBlock`, а если луч уже не совпадает со сломанным блоком — по наибольшему
  смещению глаз кастера от центра блока. Плоскость — восемь соседей по двум осям, **перпендикулярным**
  нормали грани. Ломается любой блок, который кастер мог бы взять этим инструментом
  (`WideDigEvents#canBreak`: блок «есть и разрушаем», и либо инструмент для него верный
  (`isCorrectToolForDrops`, тир учитывается), либо блок не требует верного инструмента вовсе — земля,
  гравий, листва). Раньше проверялся только `isCorrectToolForDrops`, и под киркой в прокоп попадал
  лишь камень; теперь рост копания шире — это ровно «копать шире», а не «камень вокруг камня». Вода и
  блоки с `getDestroySpeed < 0` пропускаются; прочность — `ItemStack#mineBlock` за каждый блок. Цена —
  `CHAKRA_PER_EXTRA_BLOCK = 2` **за каждый доп. блок** (через `masteryCost`), списывается по ходу и
  обрывает прокоп, когда чакры не хватает. Сама стойка бесплатна по тикам (`handleCost` только
  проверяет `wideDigEnabled`), а **освоение капает за каждый реально вынутый блок**
  (`WideDigAbility#creditBrokenBlock` → `MASTERY_CREDIT_PER_BLOCK = 1/32`, то есть один «каст» за 32
  блока), а не по времени удержания: стойку, которую держат в пустом туннеле, не прокачивают. `performServer`
  и `recordMastery` — пустышки. `breakingExtras` — guard от
  рекурсии: `destroyBlock` соседей не должен запускать новый прокоп. Кап `MAX_EXTRA_BLOCKS = 26`
  оставлен с запасом (текущая плоскость даёт 8). Конфиг — `wideDigEnabled`.
* **Чутьё руды** (`OreSenseAbility`, INSTANT `C C V` = `112`) — импульс на 5 секунд: сервер один раз
  прочёсывает загруженные секции в радиусе `24 → 32` (по освоению) и шлёт ближайшие
  `MAX_MARKERS = 64` блока клиенту пакетом `ClientboundOreSensePacket`. Секции сначала спрашиваются
  через `LevelChunkSection#maybeHas`, поэтому пустые/безрудные не перебираются. Ищется тег
  `forge:ores`; с `SPECIALS_AT = 0.5` добавляются древние обломки, сундуки/бочки/эндер-сундуки/шалкеры
  и спавнеры. Древние обломки проверяются **до** тега, чтобы на низком освоении не показываться, даже
  если пак их включает в `forge:ores`. Клиент (`ClientOreSenseData` + `OreSenseRenderer`) рисует
  проволочные кубы на `AFTER_LEVEL`: используется view-матрица, снятая `SensingEspRenderer` на
  `AFTER_PARTICLES`, depth-буфер чистится, поэтому руда видна сквозь породу; последняя секунда —
  плавное затухание. Конфиг — `oreSenseEnabled` (на публичных серверах это по сути краткий x-ray).
* **Костер** (`CampfireJutsuAbility`, INSTANT `B C B`) — поджигает **бревно под прицелом** и ставит на
  его место `Blocks.CAMPFIRE` с `LIT = true` и `FACING` по направлению кастера (как будто поставленный
  рукой), плюс `FLINTANDSTEEL_USE` и частицы `FLAME`/`LARGE_SMOKE`. Блок ищется лучом
  `Targeting.lookedAtBlock(player, REACH = 8)` (по коллайдерам, сквозь стены не видит), а целью служит
  тег `#minecraft:logs_that_burn`, а не `#minecraft:logs`: варпед-стебель в костёр не превратится.
  Горит **одно бревно за каст** — ствол «догорает» кастами, если вести прицелом. Проверка бревна
  стоит в `handleCost` **до** списания чакры, поэтому промах (листва, стена, воздух) отказывает
  сообщением `jutsu.fail.campfire.notlog` и не стоит ничего; `performServer` ищет блок **заново**
  (между ценой и эффектом кастер мог отвернуться). `setBlockAndUpdate` сам создаёт block entity костра,
  поэтому костёр настоящий: светит, готовит еду и обогревает. Комбо `313` свободно и не является ничьим
  префиксом.
* **Огненное Дыхание** (`FireBreathJutsuAbility`, CHANNELED `C V B C` = `1231`) — струя жгучей чакры
  конусом перед кастером. `handleCost` жжёт `CHAKRA_PER_TICK = 0.5` чакры/тик, а тап (< 2 тиков) —
  пшик за `TAP_COST = 10`; урон считается не каждый тик, а раз в `DAMAGE_INTERVAL = 20` тиков, по
  существам внутри конуса (`CONE_COS = 0.55`, то есть ~±56°). Дальность и урон растут от освоения:
  `reach 4→7` и `damage 3→6` линейно по `m`. **Ничего не поджигает** — ни блоки, ни сущности: это
  отдельный тип урона `narutomod:fire_breath` (свой JSON в `data/narutomod/damage_type/`), не
  помеченный как огонь, поэтому ванильные поджог/`onFire` не срабатывают. Зато дыхание **греет**:
  * **Блоки** — мокрую губку (`Blocks.WET_SPONGE`) в конусе обжигает в сухую (`Blocks.SPONGE`), как
    печка, и **подгоняет печку**: если в прицеле до `reach` стоит `AbstractFurnaceBlockEntity`, её
    `cookingProgress` подталкивается на `FURNACE_COOK_BOOST = 2` (с упором в `cookingTotalTime - 1`,
    чтобы не перескочить готовку) и `litTime` поднимается не ниже `FURNACE_LIT_BOOST = 40` — печка
    греет и варит без топлива. Поля печки приватные, их открывает **mixin-аксессор**
    `mixin/common/AbstractFurnaceAccessor` (см. грабли про AT).
  * **Освоение** — по времени горения, а не по кастам: `MASTERY_CREDIT_PER_TICK` за каждый
    продержанный тик через `recordMasteryCredit`. Своя задержка регенерации чакры
    (`CHAKRA_REGEN_DELAY = 20`), как у кулака чакры.
* **Семейство дыханий** (`AbstractBreathAbility` + `WaterBreathAbility`, `WindBreathAbility`,
  `WoodBreathAbility`) — три дыхания, добавленные после огненного, вынесены на **общий предок**:
  конус, цена (`CHAKRA_PER_TICK = 0.5`, `TAP_COST = 10`, `CHAKRA_REGEN_DELAY = 20`), дальность
  `reach 4→7` по `m`, `CONE_COS = 0.55`, частицы и время между «касаниями» живут в базовом классе,
  а наследник задаёт только элемент: звук, частицы и что конус делает с существами и блоками.
  Огненное дыхание осталось отдельным классом (у него свой тип урона и миксин-аксессоры печки), но
  читается по тем же числам. Все три мастерятся **по времени** (`MASTERY_CREDIT_PER_TICK` каждый тик,
  `recordMastery` не переопределяется — как у огня). Общий обход — `coneEntities` (живые, кроме
  кастера и союзников) и `forEachConeBlock` (координатный обход куба вокруг конуса, позиции
  `immutable()`), поэтому ветер/вода/дерево не дублируют геометрию.
  * **Водяное Дыхание** (`C V B V` = `1232`) — ничего не жжёт: существам гасит огонь
    (`clearFire`), чуть отталкивает и **мочит сухую губку** (`SPONGE` → `WET_SPONGE`), тушит
    `FIRE`/`SOUL_FIRE` (`removeBlock`) и гасит зажжённый `CampfireBlock` (`LIT = false`). Зеркало
    огня: тот сушит мокрую губку, этот мочит сухую.
  * **Ветряное Дыхание** (`C V B B` = `1233`) — **без вреда**: сдувает живых (`Entity#push` по
    направлению взгляда + `PUSH_LIFT`, `hurtMarked`), сильнее и дальше с освоением, и убирает
    мусор в конусе — `COBWEB`, `VINE`, `TRIPWIRE` уходят через `destroyBlock(pos, true)` (паутина
    роняет нить). Частота касаний выше (`PUSH_INTERVAL = 10`), иначе толчок был бы вялым.
  * **Дыхание Роста** (`C V C C` = `1211`) — **костная мука**: каждый блок конуса, который
    `instanceof BonemealableBlock` и проходит `isValidBonemealTarget`/`isBonemealSuccess`, растёт
    через `performBonemeal`; существ не трогает. Кап `MAX_GROWTHS = 4` за импульс, иначе одно
    дыхание могло бы разом вырастить лес на склоне.
* **Расенган** — CHANNELED: `handleCost` жжёт 0.6 чакры/тик, `handleChannelling` рисует шар у руки,
  `performServer` получает число продержанных тиков и бьёт по площади спереди. Тап (< 20 тиков) —
  пшик, 60 тиков — максимум (урон 4→16, вылет 0.8→1.8). Дальше тиков больше — урон не растёт.
* **Расенсюрикен** (`RasenshurikenJutsuAbility`, CHANNELED `C B B C B V`) — тот же расенган, но
  **брошенный**: удержание стоит 1 чакры/тик, а на отпускании платится ещё `RELEASE_COST = 25`
  (специально **ниже** `UPKEEP_RESERVE` батареи — иначе кастер с полной батареей мог бы выкачать диск
  и не суметь его бросить) и в мир летит `RasenshurikenJutsuEntity`. Тап (< 20 тиков) — пшик, заряд
  от 20 до 60 тиков линейно поднимает `power` (0.7→1.0) и скорость (0.55→0.95).
  **Модели у него нет** — рисуется предметом `narutomod:shuriken`, увеличенным в **5 раз** и
  крутящимся (`RasenshurikenJutsuRenderer`, спин считается в `getSpinTicks/getPrevSpinTicks` на
  **обеих** сторонах), а «ветер» — это частицы `CLOUD`/`CRIT`/`SWEEP_ATTACK`; по той же логике
  расенган сделан частицами, а не моделью.
  * **Попадание по живому = захват, а не взрыв.** Диск не лопается о цель, а **подхватывает её**:
    в синхронизированное поле `DATA_CARRIED_ID` (id цели, `EntityDataSerializers.INT`) пишется номер
    сущности, и **обе стороны** сами ведут диск за целью (`level().getEntity(id)`, позиция цели
    выставляется каждый тик) — по сети гонять позицию диска не нужно, поэтому `updateInterval`
    остался 10. Только сервер считает `carryTicks` и поднимает цель: `Entity#teleportTo` (а не
    `push`) на `CARRY_RISE = 0.4` блока за тик, `fallDistance = 0` — телепорт, потому что игрок на
    `setDeltaMovement` просто уйдёт из захвата своим клиентом. Креатив, наблюдатели, кастер и его
    `isAlliedTo` не хватают (`canCarry` → сразу `burst`).
  * **После `CARRY_TICKS = 24` тиков подъёма** (≈10 блоков) начинается шторм: до
    `CARRY_TICKS + DETONATION_TICKS = 54` тиков каждые `BARRAGE_INTERVAL = 5` тиков в
    `BARRAGE_SPREAD = 2` блоках вокруг цели срабатывает 1–2 взрыва `BARRAGE_RADIUS = 1.6`
    (`Level#explode`, `ExplosionInteraction.NONE` — блоки не ломаются), а в конце — финальный
    `FINAL_RADIUS = 3` и `burst` с уроном `narutomod:wind_release`. Свою сторону от серии взрывов
    защищает `ExplosionEvents` (см. клона из бумажной бомбы). Если цель умерла/выгрузилась раньше —
    шторм срабатывает там, где диск.
  * Прямой удар — 14 урона (`narutomod:wind_release`, `data/narutomod/damage_type/wind_release.json`,
    как у остальных: **без JSON датапак-тип роняет сервер**), финальный `burst` — до 9 по площади с
    падением по дистанции в радиусе 4.5 и отбросом; свои (`isAlliedTo`) и кастер не задеваются.
    Пролетев 80 тиков без цели, диск лопается сам.
* **Восемь врат** — состояние `INinjaData#getGateCount` (server-only, до 8). Одна техника на оба
  действия: комбо `V B B B`, тип CHANNELED. **Тап** (и вообще `performServer` с
  `ticksActive < RELEASE_HOLD_TICKS = 10`) открывает следующее окно за `25` чакры + `15` стамины;
  **удержание последней клавиши** (`B` — третьего знака комбо; клиент вообще начинает канал только
  после `NarutoConfig.jutsuKeybindHoldThreshold = 15` тиков удержания) закрывает все открытые врата.
  Закрытие делает `handleChannelling`: при `ticksChanneled >= RELEASE_HOLD_TICKS` он зовёт `seal` и сам
  снимает канал (`setCurrentlyChanneledAbility(player, null)`), поэтому эффекты падают в момент
  срабатывания удержания, а не когда клавишу отпустят. Тап всегда значит «открыть»: проверка и оплата
  открытия живут в `performServer`, а `handleCost` лишь отсеивает тап без чакры/стамины и каст при
  `gateCount >= MAX_GATES` (в канале он бесплатен по тикам, иначе цена списывалась бы каждый тик).
  Отдельной `GateReleaseAbility` (`V B B V`) больше нет — класс удалён, а `jutsu.fail.gates_none` /
  `gates_open` использует `seal`. **На восьмых вратах запечатать нельзя**
  (`jutsu.fail.gates_open`) — единственный выход из них это смерть, и в
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
  6 — 67 с, 7 — 49 с. `EightGatesAbility.close` вызывает либо `seal` (по удержанию), либо смерть
  (`PlayerEvents#onPlayerClone`); `gateCount` в NBT не пишется, поэтому выход из игры врата тоже
  сбрасывает.
* **Камуи** — техника-два-в-одном, различимая по способу ввода: **тап** (`chargeAmount == -1`) это шаг
  в карманное измерение `narutomod:kamui` и обратно (40 чакры, точка возврата —
  `INinjaData#getKamuiReturnLoc/getKamuiReturnDimension`, пишется в NBT, поэтому выход из игры внутри
  пустоты не запирает), а **удержание** (≥ `MIN_CHANNEL = 20` тиков, 1.5 чакры/тик) затягивает цель.
  Цель — то, во что смотрит `Targeting.lookedAtEntity(player, PULL_RANGE)` (лучи не проходят через стены),
  `LivingEntity`, кроме кастера и креативных/наблюдателей; за сам затяг ещё 30 чакры.
  **Если цель уже в кармане — это бросок наружу** (`eject`): то же удержание, но на сущность внутри
  `narutomod:kamui` не затягивает её (она уже там), а выбрасывает — затянутую **ровно туда, откуда
  взяли** (`BANISH_RETURN`), а родившуюся в пустоте (спавн-яйцо) — через **дверь кастера**
  (`casterHome`: сначала `BANISH_RETURN` самого кастера, потом `getKamuiReturnLoc/Dimension`), с
  откатом на мировую точку спавна оверворлда, если у кастера и её нет. Стоит те же 30 чакры. Без этого
  животное, заспавненное в Камуи, оттуда было не достать: `pullTarget` на такую цель молча выходил.
  **Измерение — датапак + свой генератор**: `data/narutomod/dimension_type/kamui.json` +
  `data/narutomod/dimension/kamui.json` (биом `the_void`, небо Края, `min_y=0, height=256`), а
  рельеф делает `world/KamuiChunkGenerator` (кодек `narutomod:kamui_pillars` в
  `world/NarutoWorldGen`, `Registries.CHUNK_GENERATOR`): сетка квадратных **столбов**
  `PLATFORM_SIZE = 5` с шагом `PLATFORM_PITCH = 7` (2 блока пустоты между ними) высотой
  `PILLAR_HEIGHT_LOW = 7` / `PILLAR_HEIGHT_HIGH = 8` в шахматном порядке по индексу столба
  (`pillarHeight`): соседи отличаются на 1 блок, поле проходимо и выглядит как поле столбов.
  `y = 0` — бедрок (чтобы поле нельзя было выкопать из-под кастера), выше — камень. Никаких
  пещер, руды и мобов: `fillFromNoise` кладёт блоки по координатам, остальные абстрактные методы
  `ChunkGenerator` — пустышки. **`applyBiomeDecoration` переопределён в no-op**: иначе ваниль
  декорирует `the_void` его фичами, и `minecraft:void_start_platform` вешается над точкой появления
  каменной плитой-потолком. Точка прилёта кастера — `CASTER_ARRIVAL` (0.5, `surfaceY(0,0)`, 0.5),
  цели — `TARGET_ARRIVAL` (3.5, `surfaceY(3,0)`, 0.5), обе на первом столбе; высоты берутся из
  генератора (`KamuiChunkGenerator#surfaceY`), а не пишутся числом. Спавн запрещён событием.
  Затянутое живёт по таймеру и возвращается
  **ровно туда, откуда его взяли**: координата и измерение пишутся в `entity.getPersistentData()`
  (`BANISH_TICKS` / `BANISH_RETURN` — работает и на мобах, и на игроках, и переживает сейв), обратный
  отсчёт — `KamuiEvents#onLivingTick`, который смотрит только сущности **внутри** кармана. Кастер под
  таймер не попадает. Срок хранения — конфиг `kamuiEnabled` / `kamuiTransferSeconds`.
* **Фазовое тело Камуи** (`abilities/jutsus/KamuiPhaseAbility.java`, TOGGLE `V C B`) — **отдельная
  техника**, из `KamuiJutsuAbility` фазовое тело убрано. `isActive(ninjaData)` = наличие
  `narutomod:kamui_phasing` в `toggleAbilityData` (то есть поле `@Sync`, состояние едет на клиент).
  `KamuiPhaseAbility#apply(player)` зовётся из `KamuiEvents#onLivingTick` (`LivingTickEvent`, **обе
  стороны**) и выставляет `noPhysics` + `setNoGravity`. **Почему именно `LivingTickEvent`, а не
  `PlayerTickEvent`:** `Player#tick()` первой строкой делает `this.noPhysics = this.isSpectator()`,
  то есть ваниль сбрасывает флаг **до** движения каждый тик; `LivingTickEvent` — внутри `tick()` после
  сброса и до `aiStep`, поэтому флаг доживает до шага. `PlayerTickEvent.END` для этого не годится
  вообще. Управление вверх/вниз — через `mayfly`/`flying` (server-only, `onUpdateAbilities()`), а
  исходное состояние полёта сохраняется в `player.getPersistentData()` (`FLIGHT_TAG`) и возвращается в
  `release` (вызывается из `handleAbilityEnded`, из `PlayerEvents#onPlayerClone` при смерти и при
  любом тике, когда стойка уже не активна). Урон гасится в `KamuiEvents#onLivingHurt` (приоритет
  `HIGHEST`, чтобы не тратить «второе дыхание» на удар, который и так не пройдёт): исключения —
  `DamageTypeTags.BYPASSES_INVULNERABILITY` (`/kill`, пустота) и `DamageTypes.STARVE`. `release` зовёт
  `unstick` (свободное место в радиусе 2/вверх 4), чтобы выключение внутри стены не закапывало кастера.
* **Чакра-нить** (`ChakraThreadAbility`, CHANNELED `B B C`, 0.25 чакры/тик) — прицельный граппл.
  Зацепка берётся **один раз** на первом тике канала (`handleCost` с `chargeAmount == 0`) и живёт в
  `Map<UUID, Pull>` самого синглтона способности: сначала `Targeting.lookedAtEntity` (живая цель),
  иначе `Targeting.lookedAtBlock` (грань блока); луч упирается в первую стену, так что сквозь неё
  нить не цепляется. Дальше прицел можно уводить — нить держится за то, во что попала. Тап
  отключён (`canActivateBelowMinCharge → false`, сообщение `jutsu.channel.needed`).
  * **За блок** — кастер тянется к `BlockHitResult#getLocation()`: каждый тик
    `setDeltaMovement(направление × скорость)` + `hurtMarked = true`, скорость
    `min(1.0, 0.35 + расстояние × 0.03)`, так что 32 блока проходятся примерно за 1.6 с, а вблизи
    нет рывка. Конец — прибытие (`ARRIVE_RANGE = 1.8`), `player.horizontalCollision` (упёрся в
    стену) или потеря цели.
  * **За сущность** — `LivingEntity` (кроме кастера, `isAlliedTo`, креатива/наблюдателей) тянется к
    груди кастера со скоростью `min(0.85, 0.3 + расстояние × 0.025)` плюс небольшой подъём
    `ENTITY_LIFT = 0.06`; конец — `ENTITY_ARRIVE_RANGE = 2.0`, смерть/выгрузка цели или
    `LEASH_RANGE = 48`. Никого не телепортирует: только `setDeltaMovement`, поэтому «притянул +
    расенган» и вытаскивание моба из толпы работают без разрыва дистанции.
  * **Падение гасится** — `fallDistance = 0` каждый тик и при любом завершении, иначе выдернутый
    вверх кастер ломался бы о землю. `finish` (прибытие/стена/потеря цели) дополнительно сбрасывает
    скорость до 30 %, играет `LEASH_KNOT_BREAK`, один раз засчитывает освоение и снимает канал через
    `setCurrentlyChanneledAbility(null)`; при обычном отпускании/нехватке чакры это делает
    `performServer` (освоение в этом случае начисляет штатный STOP-путь). Частицы — `ELECTRIC_SPARK`
    по линии от `JutsuEffects.handPosition` до зацепки.
  * **Попутный фикс:** в `ServerAbilityChannelPacket` STOP теперь сравнивает
    `resourceKey.location().equals(ninjaData.getCurrentlyChanneledAbility())`, а не наоборот: канал
    может закончиться на сервере раньше, чем клиент отпустит клавиши (нить оборвалась, чакра
    кончилась), и вызов `equals` на `null` ронял обработчик.
* **Сцепление чакрой** (`ChakraAdhesionAbility`, CHANNELED `C C B` = `113`, 0.15 чакры/тик,
  конфиг `adhesionEnabled`) — медленное лазание по любой поверхности, **не копия нити**: нить
  цепляется за точку и тянет к ней (по воздуху, нужен зацеп), сцепление просто держит за стену,
  поэтому работает и в тесной пещере, где нить бесполезна. Тап отключён
  (`canActivateBelowMinCharge → false`), удержание обязательно. Каждый тик канала при
  `player.horizontalCollision` пишется только **вертикальная** составляющая:
  `setDeltaMovement(dx, climbSpeed, dz)` (горизонтальная скорость не трогается, поэтому вдоль стены
  можно ехать), `hurtMarked = true` (иначе клиент откатит) и `fallDistance = 0` — пока держишься,
  падение не копится, отпустил — считается как обычно. Скорость от освоения:
  `BASE_CLIMB_SPEED = 0.12` → `+ MASTERY_CLIMB_SPEED = 0.08` (мастер 0.20 бл/тик).
  **Движение пишется на обеих сторонах**: сервер — в `handleChannelling`, клиент — в
  `client/ClientAdhesionHandler` на `LivingTickEvent` (для **локального** игрока), потому что скорость
  игрока принадлежит клиенту и серверную поправку он затирает следующим же пакетом движения.
  `LivingTickEvent` выбран потому, что он идёт внутри `Player#tick` после сброса транзитных флагов,
  но до шага движения (как у фазового тела Камуи).
* **Адаптация тела** (`BodyAdaptationAbility`, TOGGLE `V B B` = `233`, конфиг `adaptationEnabled`) —
  стойка выживания под среду, **без урона/брони/скорости**: в воде `WATER_BREATHING` +
  `setAirSupply(getMaxAirSupply())`, в лаве/огне `FIRE_RESISTANCE` + `setRemainingFireTicks(0)`,
  в рыхлом снегу/при обморожении `setTicksFrozen(0)`. Цена зависит от среды:
  `BASE_COST = 0.2` / `LAVA_COST = 0.5` чакры/тик (не копия водяной ходьбы — та про поверхность,
  это про то, где вообще можно находиться). Эффекты выдаются короткой длительностью
  `EFFECT_DURATION = 60` и обновляются каждый тик — при выключении стойки сами истекают за пару
  секунд, снимать руками ничего не надо. Освоение — по времени (`MASTERY_CREDIT_PER_TICK` из
  `performServer`), поэтому `recordMastery` переопределён в пустоту.
* **Дымовая завеса** (`SmokeScreenAbility`, INSTANT `V V V` = `222`, 25 чакры, КД 20 с, конфиг
  `smokeEnabled`) — облако-сущность `SmokeCloudEntity` на 8 с, которое скрывает **всех** внутри,
  свой и чужой. Мобы внутри теряют цель (`setTarget(null)` раз в 5 тиков) и **не могут взять новую**:
  это не гонка гола с облаком, а отказ на источнике — `events/SmokeScreenEvents` отменяет
  `LivingChangeTargetEvent`, пока моб в дыму (сброс цели в `null` пропускается всегда, иначе моба
  нельзя было бы отпустить). Игрокам, кроме кастера, вешается `DARKNESS` (флаг
  `smokeBlindPlayers`, чтобы на PvP-сервере можно было оставить дым только для мобов). Облако —
  чистая сущность без модели: `SmokeCloudRenderer` пустой (нужен лишь чтобы у типа был
  зарегистрированный рендерер), клиент рисует `LARGE_SMOKE` частицами в своём `tick`. Это **не
  подмена** (та меняет местами с бревном) и **не гендзюцу** (то одна цель и перенаправление).
* **Питание чакрой** (`ChakraFeedingAbility`, TOGGLE `C V V` = `122`) — медленно переводит чакру в
  сытость: раз в `FEED_INTERVAL = 40` тиков при `getFoodLevel() < 20` списывает `CHAKRA_PER_POINT = 10`
  (через `masteryCost`) и добавляет 1 ед. сытости + щепотку насыщения. Не копия военных пилюль (те
  мгновенные и с баффами) и не заменяет еду: только «дожить до базы». Сама стойка бесплатна по тикам
  (`handleCost → true`), цена — только за фактические единицы. Освоение — по времени.
* **Подавление звука** (`SilenceAbility`, TOGGLE `B B B` = `333`, 0.2 чакры/тик, конфиг
  `silenceEnabled`) — глушит шум кастера: пока стойка держится, его игровые события не доходят до
  мира, поэтому скалк-сенсоры и шрикеры молчат, а Хранитель не слышит. Единственная точка входа всех
  вибраций — `GameEventDispatcher#post(GameEvent, Vec3, GameEvent$Context)`, и
  `mixin/common/GameEventDispatcherMixin` отменяет `post` на `HEAD`, если `context.sourceEntity()` —
  игрок со стойкой. Один хук ловит и шаги, и прыжки, и удары, и установку блоков, не трогая события
  без источника (мир шумит как обычно). Refmap проверить после сборки: `post` должен уехать в
  `m_245905_` (см. `build/tmp/compileJava/mixins.narutomod.refmap.json`). **Это не дымовая завеса** (та
  скрывает визуально и на время) и **не подавление агрессии** (тут никто не теряет цель — кастера
  просто не слышат).
  * **Глухие уши мобов** — `events/SilenceEvents`: `LivingChangeTargetEvent` отменяется, если новая
    цель — «подавленный» игрок дальше `DETECTION_RANGE = 8` блоков (вплотную ещё замечают), плюс раз
    в 20 тиков сбрасываются уже взятые цели в радиусе `CULL_RANGE = 48`, чтобы погоня, начатая до
    включения стойки, тоже закончилась.
  * **`isSilenced(Player)` берёт капабилити** (`NinjaCapabilityHandler.NINJA_DATA`) и смотрит
    `toggleAbilityData` — как у `ChakraFistAbility.isActive`. Стойка — обычный TOGGLE, состояние
    `@Sync`, но проверка идёт на сервере (события серверные).
* **Мягкая посадка** (без комбо, автоматически) — в `PlayerEvents#livingFall`: считается урон, который
  приземление всё равно отняло бы (ванильная формула `ceil(distance - 3 - jumpBoost)`), цена —
  `SOFT_LANDING_STAMINA_PER_DAMAGE = 2` стамины за единицу урона. Хватает — падение гасится полностью
  (`setDistance(0)`, облако частиц) с задержкой регенерации 3 с; не хватает — **приземление обычное и
  стамина не тратится вообще**. Прежний «частичный пересчёт» (`distance *= 0.3`, `-= 5`, `*= 0.6`)
  удалён: он и выглядел случайным. Цена теперь пропорциональна опасности: 4 блока — 2 стамины,
  40 блоков — 74.
* **Рабочий клон** — это **не предмет и не отдельная техника**, а **режим** обычных клонов: если при
  касте комбо клонов зажать **шифт** (`ShadowCloneJutsuAbility#summoningWorkers` = `player.isShiftKeyDown()`),
  клоны выходят рабочими. Никаких предметов-катализаторов: печати крафтить не надо, чакра-печать за каст
  **не** тратится. Рабочий призыв стоит `WORKER_COST = 75` чакры и даёт **столько клонов, сколько дала бы
  сама техника** (теневой — 1→4 по освоению, чиби — 1→5, мульти — 4→10), срок жизни —
  `ShadowCloneEntity.WORKER_LIFETIME` = 1.5 минуты.
  КД у рабочих — тоже **presence-gate**, как у боевых (`hasLivingClones(player, true)`): пока живы
  рабочие этого кастера, повторный призыв заблокирован, а таймер не ставится (`registerCooldown` только
  для медитации). Медитация (`Settings#meditating`) режим ignore — она всегда медитация. Работа —
  **enum `entity/WorkerJob`** (`FARM`/`MINE`/`CHOP`/`HUNT`), выбирается по предмету в **основной** руке
  (`ShadowCloneJutsuAbility#workerJob`): кирка → `MINE`, топор → `CHOP`, меч (`SwordItem`; туда же
  `ComboSwordItem`) → `HUNT`, мотыга → `FARM`. **Инструмент обязателен**: если в основной руке нет ни
  одного из четырёх, `workerJob` возвращает `null`, и `handleCost` отказывает каст целиком
  (`jutsu.fail.workertool`) — без чакры и без клонов-«фермеров по умолчанию». Плюс **каждая смена
  открывается своим узлом дерева** (`WorkerJob#unlockKey` → `worker:farm/mine/chop/hunt`, ветка
  `LABOUR`): если инструмент подходящий, но узел смены не изучен, `handleCost` тоже отказывает, уже с
  `jutsu.fail.workerlocked`. То есть по умолчанию рабочих нет вообще, а «шахтёр», «лесоруб» и т. д.
  покупаются отдельно.
  Флаг `worker` (`DATA_WORKER`) и `workerJob` (строка в NBT) — server-side.
  Инструмент остаётся клону в **основной** руке (`ShadowCloneEntity#setWorkerTool`), а во второй руке —
  витрина факелов (см. ниже).
* Четыре смены рабочих — общий предок `entity/ai/WorkerBlockGoal` (приоритет 1): ищет ближайший блок
  своего класса **вокруг самого рабочего** (центр — `worker.blockPosition()`, не пост) в радиусе
  `searchRadius()` / ±3. `searchRadius()`: по умолчанию 8, `WorkerMineGoal` — 12, `WorkerHuntGoal` — 16,
  `WorkerChopGoal` — **24** (лесоруб специально широкий: смысл в том, чтобы быстро вырубить лес). Рабочий
  идёт к цели, берёт её (кулдаун между подходами — `SWING_COOLDOWN = 15`, скан пустой цели — 20), лут
  кладёт в мешок (`collect`, NBT `WorkerBag`).
* **Берёт по одному блоку, а не всё сразу.** `plan()` возвращает список блоков на текущую цель (обычно
  один), и гол снимает их **по одному за `TAKE_TICKS = 6` тиков** (`take`): список уходит в очередь
  `queue`, и дерево падает бревно за бревном, а не исчезает целиком за тик — это чисто «для красоты».
  Поэтому в план лесоруба входит **весь ствол сразу** (`connectedLogs` сортируется по `getY`, снизу
  вверх): верхние брёвна не обязаны быть достижимы сами по себе, их всё равно сносит по очереди.
* **Брошенные и недостижимые цели.** Если рабочий стоит (`getNavigation().isDone()`) и всё ещё дальше
  `REACH_SQR = 4` (2 блока) дольше `STUCK_TICKS = 40` тиков (~2 с, счётчик `stuckTicks`), он **бросает**
  блок — раньше висел до 300 тиков и «зависал» у дерева. Абсолютный кап — `UNREACHABLE_TICKS = 300`.
  Брошенная позиция уходит в `abandoned` на `ABANDON_TICKS = 300` и пропускается при выборе; если
  `plan()` вернул пусто (кирка не берёт блок), цель тоже уходит в `abandoned` — иначе рабочий бил бы в
  неё бесконечно.
* **Расходятся по разным деревьям.** Пока рабочий занят блоком, он его **бронирует**: статическая карта
  `CLAIMS` (`GlobalPos` → `Claim(holder, expiry)`, `CLAIM_TICKS = 1200`), а `WorkerChopGoal#claimGroup`
  бронирует **весь ствол**. `findNearest` пропускает блоки, забронированные другим рабочим, поэтому
  второй лесоруб идёт к своему дереву, а не встаёт к тому же.
* **Обход.** Когда вокруг пусто, цель не находится, и гол сам ведёт рабочего бродить: выбирает
  проходимую точку в радиусе `ROAM_RANGE = 8` (`findRoam`, до `ROAM_TICKS = 200` тиков на переход), а
  не полагается на ванильную прогулку (`WaterAvoidingRandomStrollGoal`), чей случайный шаг замолкает,
  если моб долго стоит (`getNoActionTime()`) — из-за этого рабочие и замирали. `workerHome`
  (`player.blockPosition()` места призыва) теперь чисто справочная запись о точке призыва (в NBT).
  **Навигация идёт не в сам блок, а в проходимого соседа** (`standOptions`/`isStandable`):
  у сплошного блока (бревно, камень) нет узла пути, и `moveTo` в него молча ничего не делает — рабочий
  стоит на месте. Кандидаты — 4 горизонтальных соседа цели на уровнях 0/+1/−1, отсортированные по
  дистанции до рабочего, с твёрдой опорой снизу; непустых (грядки) ведут прямо к блоку, как раньше.
  **Мест несколько, и они перебираются.** У ближнего бока ствола путь часто не строится (его загораживает
  сам ствол широкого дерева или склон), поэтому, если навигация закончилась, а до блока всё ещё далеко,
  гол берёт **следующее** место (`nextStand`) и только перебрав все — бросает цель. Последним в списке
  идёт **сам блок**: наведение прямо на него заставляет пейфайндер закончить путь в ближайшей достижимой
  точке, и рабочий упирается в ствол вплотную. Блоки, к которым подойти некуда (`hasStand`), вообще не
  выбираются целью (иначе рабочий упрётся в недостижимую жилу/бревно).
  Два прохода выбора цели: сначала `matches`, и только если ничего достижимого нет — `secondary`
  (по умолчанию пусто; так лесоруб расчищает листву, см. ниже). Подклассы задают `matches`/`plan`/`take`:
  * `WorkerHarvestGoal` (`FARM`) — спелые `#minecraft:crops`, `plan` = один блок; `take` срезает через
    `Block.getDrops(...)` + `level.destroyBlock(pos, false)` и **сразу засевает заново**
    (`CropBlock#getStateForAge(0)`).
  * `WorkerMineGoal` (`MINE`) — **только руды** `forge:ores` (обычный камень тега
    `#minecraft:base_stone_overworld` убран осознанно: рабочий «копал всё под себя», то есть просто рыл
    яму под ногами, а должен искать руду); `plan` пуст, если `ItemStack#isCorrectToolForDrops` не
    проходит (тир кирки учитывается); `take` изнашивает инструмент (`ItemStack#hurtAndBreak(1, worker, …)`
    — `mineBlock` требует **Player**, а клон им не является; кирка проверяется **заново перед каждым
    блоком**, сломалась — смена прекращается). **Копает и через камень**: у шахтёра `needsStand`
    переопределён в `false` (сплошной камень рядом — не повод бросать цель), а когда к руде не подойти,
    `WorkerBlockGoal` зовёт `clearPath` — рабочий пробивает **первый** камень перед собой на уровнях 0/1
    (саму руду `clearPath` не трогает) и `afterClear` заводит его в пробитую нишу, откуда цель становится
    достижимой. Так руда, закопанная в толще, больше не остаётся недостижимой.
  * `WorkerChopGoal` (`CHOP`) — `#minecraft:logs`; `plan` = `connectedLogs` (BFS, кап `MAX_LOGS = 128`),
    снизу вверх; `secondary` = `#minecraft:leaves`: если достижимых брёвен нет (ствол заслонён листвой),
    лесоруб сперва срубает мешающую листву (`plan` = один лист) и тем открывает себе подход.
  * `WorkerHuntGoal` (`HUNT`) — **единственный, кто ещё и охотится**, поэтому он расширяет базовый гол, а
    не только задаёт блоки. Блоки: спелая `SweetBerryBushBlock` (AGE ≥ 2) и `Blocks.MELON`; `take` у куста
    собирает дропы (`Block.getDrops`) и **не выкапывает** его — ставит `AGE = 1`, как будто ягод нарвали
    руками, а арбуз просто ломает. Пшеница и прочие `#minecraft:crops` **не трогаются** — это `FARM`.
    Охота: `findPrey` берёт ближайшего `Animal` в радиусе `PREY_RANGE = 16`, кроме `TamableAnimal#isTame`
    и списка `NOT_PREY` (`wolf`, `polar_bear`, `goat`, `llama`, `trader_llama` — те кусаются, а рабочий
    не боец);
    `tick` прерывает работу по блокам, если рядом объявился зверь, идёт к нему
    (`REPATH_TICKS = 10`), бьёт `doHurtTarget` раз в `ATTACK_COOLDOWN = 20` тиков; `canContinueToUse` —
    пока зверь жив и в `GIVE_UP_SQR = 32²`; `stop` сбрасывает жертву. Свою цель он берёт **сам**, а не
    через `setTarget` (там у рабочих проходит только самозащита), поэтому ванильная система целей не
    трогается. Дропы убитого зверя перехватывает `ShadowCloneEvents#onLivingDrops` и кладёт в мешок
    (см. ниже).
* **Факелы (все смены).** У рабочего в левой руке — витрина факелов, а их запас (число копий) снимается
  с инвентаря кастера в момент призыва (`ShadowCloneJutsuAbility#copyWorkerTorches`, как стрелы для
  лука). `entity/ai/WorkerTorchGoal` — общий гол **на все** смены: когда рабочему темно
  (`level.getBrightness(LightLayer.BLOCK, …)` ниже порога) и под ногами есть место, он ставит факел,
  тратя одну копию из запаса, и продолжает смену. Так шахта и вырубка не остаются в темноте, где плодятся
  мобы; инструмент при этом остаётся в основной руке.
* `CloneFollowOwnerGoal` отказывается работать у рабочих, как и у медитирующих. **Добыча идёт в походный
  сундук** — контейнер, который живёт в данных владельца (см. ниже), поэтому он доступен всегда:
  `ShadowCloneEntity#collect` (звать из всех рабочих голов и из `ShadowCloneEvents#onLivingDrops` для
  охотника) кладёт предмет в `INinjaData#getSupplyChest()` **сразу** при подборе, а в `bag` попадает
  только то, что не влезло или когда владельца нет в сети.
  Мешок отдаётся владельцу **сразу**, без задержки: и по таймеру (`dispel` → `pop`), и когда убит
  (`die` → `pop`). Раньше по таймеру лут приходил через 30 с (`WORKER_LOOT_DELAY`,
  `INinjaData#scheduleDelayedTickEvent`) — выдача через отложенное событие и выглядела как «мешок
  пропал», поэтому от неё отказались. Убить рабочего уже не так просто, как раньше: у него 4 HP, он
  огрызается на того, кто его ударил, а собранное всё равно доходит. Если владелец не в сети — лут
  выпадает на месте клона. Мешок у каждого клона **свой** (поле `bag`, не `static`).
* **Походный сундук** (`StorageChestAbility`, INSTANT `B V C`) — **не блок и не привязан к клонам**:
  это виртуальное хранилище, которое целиком живёт в NBT игрока и переживает выход из игры. Сундук
  **один** на игрока, его нельзя потерять или «забыть» в незагруженном чанке. Техника только
  **открывает** его: `performServer` зовёт `NetworkHooks#openScreen` с `SupplyChestMenu`, `handleCost`
  бесплатен, `logInChat` выключен (это взгляд внутрь, а не каст).
  Меню регистрируется в `menu/NarutoMenus.java` (`DeferredRegister<MenuType<?>>`, `supply_chest`) как
  `IContainerFactory`: сервер пишет текущий размер сундука в extra-data окна (`buffer.writeVarInt`), а
  клиент строит по нему пустой контейнер ровно того же размера — иначе клиент и сервер разошлись бы в
  числе слотов. Экран — `client/gui/SupplyChestScreen.java` (подключается через
  `client/NarutoClientScreens#register` из `clientSetup`, т.к. в этой сборке Forge нет
  `RegisterMenuScreensEvent`).
  Рабочие пишут в контейнер **напрямую** — `ShadowCloneEntity#depositInSupplyChest` через
  `SupplyChestContainer#insert`, без координат, измерений и проверок загрузки чанка. Полный контейнер
  или офлайн-владелец → возврат в `bag`.

  **Сундук только на выдачу.** Положить в него что-либо руками нельзя: `SupplyChestMenu#clicked`
  обрабатывает клики по слотам сундука сам и знает только «взять» (`PICKUP` с пустой рукой, `QUICK_MOVE`,
  `THROW`), а всё, что могло бы положить (`PICKUP` с предметом в руке, перетаскивание, обмен с хотбаром,
  креативный клон, shift-клик из инвентаря) — просто игнорируется. `quickMoveStack` для слотов игрока
  перекладывает предметы **внутри** инвентаря (`moveWithinInventory`), а не в сундук. Смысл: сундук — это
  ящик доставки рабочих, кастер в него только смотрит и забирает.

  **Размер динамический.** `SupplyChestContainer` начинается с `MIN_SIZE = 27` слотов (три ряда —
  ровно столько, сколько показывает окно) и растёт на ряд (9 слотов) в `insert`, пока не влезет всё, до
  потолка `MAX_SIZE = 243`; размер пишется в NBT рядом с содержимым (`Size`), старые сейвы подтягиваются
  по максимальному слоту. Меню показывает фиксированные `MAX_VISIBLE_ROWS = 3` ряда: сундук заводится
  уже трёхрядным, поэтому **форма окна не меняется по мере роста** (слоты `Slot` имеют финальные
  `x`/`y`/`index`, двигать их нельзя) — всё, что выше третьего ряда, достаётся прокруткой.
  `imageHeight`, позиция инвентаря игрока и высота фона считаются от числа рядов
  (`SupplyChestMenu#imageHeight/playerInvY`), а `renderBg` режет `generic_54.png` на две части, как это
  делает ванильный `ContainerScreen`: верх — область сундука (`rows * 18 + 17`), низ — фиксированный
  блок инвентаря с source `y = 126`.

  **Стаки по 256.** Ванильный `ItemStack` не может нести больше 127: `ItemStack.save` пишет размер
  байтом, а `FriendlyByteBuf#writeItem` шлёт теги только у damageable-предметов. Поэтому контейнер свой:
  `menu/SupplyChestContainer.java` хранит на слот **шаблон** предмета с count = 1 плюс отдельный
  `int`-массив `amounts[]` (кап `MAX_AMOUNT = 256`). Ванильная синхронизация меню везёт безопасные
  шаблоны, а реальные количества едут своим пакетом `network/s2c/ClientboundSupplyChestPacket.java`
  (весь массив `int`, длина — по размеру сундука), который шлётся из `SupplyChestMenu#broadcastChanges`,
  когда снимок `snapshotAmounts()` разошёлся с последним отправленным (ловит и трату игроком, и доклад
  рабочего). **Длина массива — это и есть текущий размер сундука**: клиент по ней узнаёт, что сундук
  подрос, и тянет свой контейнер за сервером. Клиент кладёт количества в `SupplyChestMenu#clientAmounts`
  (`applyAmounts`, заодно `SupplyChestContainer#ensureSize`), а бейдж рисует
  `SupplyChestScreen` из `getDisplayAmount` — **в переопределённом `render` после `super.render`**, иначе
  иконка предмета (z=100) закрывает цифры; тултип ваниль зовёт ещё позже, поэтому он остаётся сверху.
  Из руки кастер берёт обычный ванильный стак (`CARRY_LIMIT = 64`), shift-клик высыпает в инвентарь
  столько, сколько влезет.
  В NBT контейнер пишется целиком под `SUPPLY_CHEST_TAG` (`NinjaData`), старый формат-список из
  `SimpleContainer` подхватывается `loadLegacy` — предметы не теряются.

  **Прокрутка и рост на лету.** Слот `Slot` имеет финальные `x`/`y`/`index`, поэтому сдвигать слоты
  нельзя: и позиция, и число слотов фиксируются при постройке меню. Меню держит `visibleRows` рядов
  (`SupplyChestMenu.SupplyChestSlot`) поверх `menu/SupplyChestWindow.java` — обёртки над контейнером,
  которая прибавляет к локальному индексу `offset` (кратный девяти). Прокрутка меняет только `offset`,
  а слоты остаются на месте; клиент сообщает новый ряд пакетом
  `network/c2s/ServerSupplyChestScrollPacket.java`, сервер выставляет тот же `offset` (иначе клик по
  локальному слоту `i` попал бы в чужой слот) и пересинхронирует содержимое.
  **Ключ к тому, что доступны все слоты — окно фиксированной высоты плюс стриминг размера.** Сундук
  заводится уже на `MAX_VISIBLE_ROWS` рядов (`MIN_SIZE = 27`), поэтому форма окна не зависит от того,
  сколько в сундуке лежит сейчас, и не меняется, пока он растёт: меняется только длина массива в
  `ClientboundSupplyChestPacket`. `SupplyChestMenu#getTotalRows` берёт
  `max(container.getContainerSize(), syncedTotalSize)`, а клиент в `applyAmounts` растягивает свой
  контейнер до присланного размера (`SupplyChestContainer#ensureSize`), иначе прокрутка вниз читала бы
  за концом сетки. Старое переоткрытие окна при росте (`visibleRows(...)`/`reopen`) оставлено страховкой,
  но при `MIN_SIZE = 27` уже не срабатывает.
  Экран рисует ванильную `textures/gui/container/generic_54.png`, полосу прокрутки и бейдж количества, а
  `renderBackground(GuiGraphics)` переопределён в **no-op** — именно он затемнял мир серым при открытии
  любого контейнера.

* **XP и дропы клона — владельцу/в мешок.** `events/ShadowCloneEvents#onExperienceDrop`
  (`LivingExperienceDropEvent`): если `event.getEntity().getKillCredit()` — `ShadowCloneEntity`, а её
  владелец в сети, опыт обнуляется (`setDroppedExperience(0)`) и выдаётся владельцу
  (`Player#giveExperiencePoints`). Так опыт за убийство клоном не остаётся орбами за полкарты. Если
  владелец не в сети — орбы падают как обычно (не теряются). А `ShadowCloneEvents#onLivingDrops`
  (`LivingDropsEvent`) переносит дропы убитого **любым** клоном (`getKillCredit() instanceof
  ShadowCloneEntity`) в тот же `collect` → походный сундук и **очищает** `event.getDrops()` (иначе мясо
  сгнило бы в поле, пока клон дойдёт до владельца). Раньше это делал только охотник
  (`isWorker() && getWorkerJob() == HUNT`), теперь — любой клон, включая боевого. Всё, что клон не
  подобрал с убийства, лежит на земле: его доберёт `entity/ai/ClonePickupLootGoal.java`.
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
  сбрасывает. Тратит `SageModeAbility` (INSTANT, `V B V`): вход требует 50, дальше `DRAIN_PER_TICK = 0.02`,
  режим **сам гаснет на нуле** (не по таймеру): полный пул в 100 держится ~4 минуты (вход ничего не
  списывает, важно только «не меньше 50 на входе», поэтому с минимальным пулом режим живёт ~2 минуты).
  **Прогрессия — по времени использования**, а не по кастам: `SageModeAbility` копит `recordMasteryCredit`
  из `maintain` за каждый тик режима, а расход на старте множится на `BEGINNER_DRAIN_MULTIPLIER = 12.0f`,
  так что новичок держит режим 10–20 с и лишь постепенно дотягивается до полного. **Плюшки
  открываются постепенно** по `m`: `STRENGTH_AT = 0.15`, `RESISTANCE_AT = 0.35`, `JUMP_AT = 0.5`,
  `SPEED_UP_AT = 0.6`, `STRENGTH_UP_AT = 0.6`, `REGENERATION_AT = 0.7` — до порога эффект просто не
  выдаётся. **Повторный каст закрывает режим** (то же `V B V`), а `logInChat` выключен: вход/выход не
  спамят чат.
  Эффекты выдаются на `EFFECT_DURATION = 200` тиков и обновляются каждый тик —
  значит, вместе с режимом они и заканчиваются; плюс `+0.5` чакры за тик и частицы `HAPPY_VILLAGER`.
  Обслуживание — `SageModeAbility#maintain`, зовётся из `PlayerEvents#onEntityUpdate` рядом с
  `EightGatesAbility#maintain`. Флаг `sageMode` (`@Sync`) **не** пишется в NBT: смерть и выход из игры
  его закрывают. Полоска — `client/gui/SeninGUI`: рисуется **той же текстурой и геометрией, что и
  полоска чакры** (`ChakraAndStaminaGUI.barTypes[NarutoConfig.chakraBarDesign]`), прямо над ней, с
  зелёной заливкой (семейство частиц сенина) и подписью через `GuiUtils.centeredTextOutlined`, и
  появляется только когда есть что показывать (накоплена мана или идёт режим) — иначе пустая плашка
  была бы мусором. Добавлена в `overlays` в `NarutoInGameGUI`.
* **Гендзюцу** (`GenjutsuJutsuAbility`, `C B C`, INSTANT, 35 чакры, КД 10 с) — цель берётся
  `Targeting.lookedAtEntity(player, RANGE=24)` → `Mob` (см. грабли про `player.pick`). Состояние висит **на самой сущности**
  в `getPersistentData()` (`narutomod:genjutsu_until` = `gameTime + DURATION`, `narutomod:genjutsu_caster`),
  поэтому переживает выгрузку чанка и сейв. `events/GenjutsuEvents` держит поведение: каждый тик
  сбрасывает недопустимую цель, раз в секунду назначает ближайшего другого моба (`findVictim`, радиус 16)
  и сыплет `WITCH`. Ключевое — `LivingChangeTargetEvent`: пока иллюзия держится, смена цели на игрока
  **отменяется** (а `newTarget == null` пропускается, иначе жертву нельзя было бы отпустить). Из целей
  исключены `Player`, `ShadowCloneEntity`, другие жертвы и всё, с чем моб и так `isAlliedTo`.
  **Приручение**: если цель — `TamableAnimal` (волк/кошка/попугай), которого держит не другой игрок, то
  гендзюцу заодно **делает его питомцем кастера** и надевает поводок (`tame`, `setLeashedTo`), а сам
  эффект иллюзии остаётся как был. То есть собакой можно «приручить» через гендзюцу: после каста она
  своя, следует за кастером и её можно сажать, как любого ванильного питомца. Занятых чужих питомцев
  не трогает (`tryTame`).
* **Чувство чакры** (`ChakraSensingAbility`, `C B C C`, CHANNELED) — одна техника, два чтения по способу
  ввода. **Удержание** — собственно сенсорика (`0.3` чакры/тик, но с **доп. скидкой освоения** до `×0.2`:
  `SENSE_COST_MASTERY_DISCOUNT = 0.8` поверх общего `masteryCost`, так что новичок жжёт много, а мастер
  почти ничего); **тап** — **ночное зрение**, кастуется как тумблер (повторный тап гасит) и жрёт
  `sensingNightVisionChakraPerSecond` (1/с; со скидкой освоения `0.9` на высоком `m` почти бесплатно)
  в `maintain` (вызов из `PlayerEvents#onEntityUpdate`), гаснет само на нуле чакры. Эффект выдаётся на
  `NIGHT_VISION_EFFECT_TICKS = 20*20` и снимается в `stopNightVision`: короткий/мигающий эффект заставлял
  HUD мерцать, поэтому и запас длительности, и явное снятие при выключении.
  **Постепенное проявление (пороги `m`).** Каждая категория сначала идёт как **облако мелких частиц**,
  видимое сквозь стены и сначала тусклое/редкое, а по мере роста освоения частиц становится всё больше,
  пока категория не «разрешится» в **полное ESP — настоящую модель существа сквозь стены**. Частицы
  сидят **на самой модели**, а не на AABB: `SensingEspRenderer#shapePoints` один раз прогоняет
  `EntityRenderer#render` существа в `ShapeCapture` (свой `VertexConsumer`, которому важны только
  позиции), собирает вершины после всех трансформаций позы/анимации, скругляет их до 0.01 и убирает
  дубли. Поэтому оболочка обводит голову, лапы, меч в руке — моба видно «кто это», а не просто пятно
  рядом. Рисуется не больше `MAX_SHELLS = 24` оболочек (ближайшие) и не больше
  `MAX_MOTES_PER_ENTITY = 120` точек на существо (`stride`), доля точек растёт с `intensity`.
  Если рендерер существа отказался работать вне обычного прохода (или моб невидим), остаётся старый
  fallback по граням AABB. Пороги:
  животные (`ANIMALS_OUTLINE_AT = 0` → `ANIMALS_ESP_AT = 0.15`), враждебные (`HOSTILE_OUTLINE_AT = 0.30`
  → `HOSTILE_ESP_AT = 0.45`), игроки (`PLAYERS_OUTLINE_AT = 0.60` → `PLAYERS_ESP_AT = 0.80`), структуры —
  `STRUCTURES_AT = 0.90`. Шкала `intensity(...)` линейна между порогами и начинается с `MIN_VISIBLE = 0.08`,
  чтобы даже самый первый каст уже что-то показывал. Категория считается на клиенте по типу:
  `Player` → игрок, `Enemy` → враждебный, всё остальное живое → животное. Цвет частиц: зелёный/красный/
  голубой соответственно.
  **Рендер — целиком клиентский, двумя классами.** `client/ClientSensingData` каждый клиент-тик собирает
  `Map<LivingEntity, Float intensity>` в радиусе из синка `NinjaData#sensingRadius` (`@Sync`, в NBT не
  пишется) и `Mastery`, а при отпускании техники **не гасит картинку сразу**, а плавно растворяет её за
  `FADE_OUT_TICKS = 20` тиков (в это время всё, включая полное ESP, рисуется частицами и тает по альфе).
  Рисует `client/renderer/SensingEspRenderer` на **`RenderLevelStageEvent.Stage.AFTER_LEVEL`** (единственная
  точка, где можно чистить depth-буфер, не ломая прозрачность/погоду). Тонкости: pose stack события на
  `AFTER_LEVEL` — это **проекция**, а не вид; **вид снимается на `AFTER_PARTICLES`** (`VIEW` из
  `event.getPoseStack().last().pose()`) и переиспользуется, потому что `Camera#rotation()` — это
  camera→world, то есть обратная матрица, и точки уезжали. На `AFTER_LEVEL` modelview временно ставится
  в identity (`RenderSystem.getModelViewStack()` → `pushPose/setIdentity/applyModelViewMatrix`, потом
  `popPose`). Частицы — билборд-квады `POSITION_COLOR` с выключенным depth-test; тело —
  `EntityRenderDispatcher#render` после `RenderSystem.clear(GL_DEPTH_BUFFER_BIT)` (иначе стена перед
  существом его прячет), с `LightTexture.turnOnLightLayer()`/`turnOffLightLayer()` вокруг. Не больше
  `MAX_MODELS = 24` тел за кадр, ближайшие первыми. `VIEW`/`hasView` отдаются наружу — той же матрицей
  пользуется `OreSenseRenderer` для кубов руды. `client/gui/SensingGUI` рисует маркеры структур как
  HUD-иконки (та же проекция, что маркер подмены) и тоже множит альфу на `getFade()`. Радиус —
  `BASE_RADIUS = 16` → `NarutoConfig.sensingRadiusMax` (по умолчанию **96**, шесть чанков) линейно от `m`.
  Структуры ищет **сервер** в загруженных чанках (`ChunkAccess#getAllStarts`, радиус в чанках) и шлёт
  `ClientboundSensingPacket` раз в 20 тиков (пустой пакет — при отпускании). Прежний хак с ванильным
  глоу-флагом (`EntityAccessor` + `setSharedFlag(6, ...)`) **удалён** — его заменил собственный рендер.
* **Возврат клонов** (`CloneRecallAbility`, `V C C`, INSTANT, 30 чакры, КД 5 с) — собирает всех **своих**
  клонов (`isOwnedBy`, кроме медитирующих) в радиусе `CALL_RANGE=256` обратно к кастеру: ищет свободное
  место кольцами вокруг него (`findAll`-цикл `findArrival` с `noCollision`), телепортирует, гасит навигацию
  и цель. Клон в **незагруженном** чанке не находится (сущности там нет) — зов дотягивается лишь до
  загруженного мира. Комбо сменилось с `C B V` (`132`) на `V C C` (`211`): `132` уже занято водяной
  пулей, из-за чего зов вообще не регистрировался в `COMBO_MAP` и был некрафтуем.
* **Чакра-батарея** (`item/ChakraBatteryItem`, `stacksTo(1)`) — 500 чакры в NBT стека
  (`Chakra`), привязка к первому владельцу (`Owner` UUID + `OwnerName` для подсказки). ПКМ — забрать до
  50, ПКМ+Shift — долить до 50; чужому владельцу отказывает `jutsu.fail.battery.owner`. Привязка живёт
  в NBT самого предмета, поэтому переживает смерть и сундуки; `isFoil` включается при заряде > 0.
  **Батарея сама питает поддерживающие техники** — `ChakraBatteryItem.fuelUpkeep(player, ninjaData)`
  зовётся в **двух** местах обслуживания: в цикле тогглов (`NinjaCapabilityHandler#onPlayerUpdate`,
  один раз на тик перед `handleCost`) и в канальном апкипе (`NinjaData#updateDataServer`, рядом с
  `handleCost`). Работает как **резервный бак**: если своя чакра упала ниже `UPKEEP_RESERVE = 30`, из
  батарей в инвентаре в пул переливается до `UPKEEP_TOP_UP = 50` (клампится по свободному месту), и
  техника платит из пула как обычно. Поэтому батареи тратятся примерно со скоростью техники, а не
  залпом, а мгновенные касты из батареи **не** финансируются — только поддержание. Батарея без
  владельца при этом привязывается к тому, кто её «съел», как при первом ПКМ; чужие пропускаются.
* **Хенге** (`HengeJutsuAbility`, `V C V`, INSTANT, 30 чакры, КД 5 с) — личина на 1 минуту. Цель —
  любое **живое существо**, в которое смотрит кастер (`Targeting.lookedAtEntity`): другой игрок
  (копируется скин) или моб (копируется модель). Клоны (`ShadowCloneEntity`) в цели не берутся — они
  сами чей-то облик. Сервер пишет `hengeTarget` (UUID игрока, строка) **или** `hengeEntityType`
  (реестровый id моба, напр. `minecraft:cow`), плюс `hengeName` — все `@Sync(minTicks = 1)` (String
  **поддерживается** синхронизацией sekclib, `StringSyncTracker` зарегистрирован в
  `BaseSyncTrackers`), и `hengeTicks` (server-only, **не** в NBT — выход из игры личину снимает).
  `isHenged()` истинно, если заполнен любой из двух (target/entityType). Повторный каст снимает
  личину без цели. Клиент (`client/renderer/HengeRenderEvents`, FORGE-шина) в `RenderPlayerEvent.Pre`
  **отменяет ванильный рендер** и рисует вместо него либо копию игрока, либо моба:
  * **игрок** — тот же ванильный `PlayerRenderer` через свою копию (`HengePlayerRenderer extends
    PlayerRenderer`) с переопределённым `getTextureLocation`; так сохраняются броня, предметы, элитры
    и слои. Копий две (wide/slim): скин и модель берутся у **цели** (`PlayerInfo#getSkinLocation/
    getModelName` из `getConnection()`, иначе сущность в уровне, иначе `DefaultPlayerSkin`), поэтому
    худая цель рисует широкого кастера худым. Ник подменяется в `RenderNameTagEvent#setContent`.
  * **моб** — держится stand-in сущность нужного типа (`EntityType.byString(...).create(level)`,
    кэш `fakeMob`/`fakeMobType`/`fakeMobLevel`), ей выставляется поза кастера в `syncPose` (позиция,
    `yRot/xRot/yHeadRot/yBodyRot`, `tickCount`, `walkAnimation.update`) и рисуется её **собственным**
    ванильным рендерером (`getEntityRenderDispatcher().getRenderer(mob)`) в точке игрока. Stand-in
    создаётся на клиенте и никогда не тикает — поэтому позу и махи руками переносим руками. Имя —
    `setCustomName(hengeName)` + `setCustomNameVisible`, дальше ваниль рисует нам-тег.
  Рекурсия в собственный хук гасится флагом `rendering`. Контекст рендера для `bakeLayer` игрока
  приходит из `NarutoRenderEvents#addLayers` (MOD-шина) в `HengeRenderEvents#setContext`.
* **Броня с бонусами** (`item/armor/NarutoArmorItem$Bonus`, `events/NarutoArmorEvents`) — броня мода
  даёт не только защиту: `Bonus(chakraCapacity, maxHealth, chakraRegenPerSecond)` вешается на предмет
  через `setBonus(...)` (у головных повязок — регенерация, у масок АНБУ — запас чакры, у бронежилетов
  и плаща Акацуки — здоровье/чакра), показывается в подсказке предмета (`appendHoverText`) и
  суммируется каждый тик (server-only `LivingTickEvent`) по `player.getInventory().armor`. Чакра идёт
  в **отдельное** поле `INinjaData#getArmorChakraBonus` (складывается с `chakraCapacityBonus` от
  медитации, оба прибавляются в `getMaxChakra()`), здоровье — через `AttributeModifier` на
  `Attributes.MAX_HEALTH` с фиксированным UUID. Модификатор **перестраивается только когда сумма
  изменилась**, а факт применения кэшируется в `armorHealthBonus` (server-only, не в NBT; после смерти
  новый `Player` чист, поэтому кэш ≠ сумма и бонус навешивается заново). Регенерация — плоский
  `addChakra` раз в 20 тиков (не трогает natural-regeneration cooldown, чтобы бонус нельзя было
  «выключить» тратой).
* **Меню J** (`client/gui/JutsuScreen`) — вместо старой команды `/jutsu list` список техник рисуется в
  GUI: строки берутся из `NarutoAbilities.COMBO_MAP` (combo → ability id, заполняется в
  `FMLCommonSetupEvent` на **клиенте тоже**, поэтому карта доступна без сервера), внутри группы
  сортируются по комбо.
  Клавиши рисуются **не** через `Component.keybind` (он зависит от раскладки ОС — на кириллице давал
  с/и/м), а из кода клавиши (`JutsuScreen#keyLetter` → латинские A–Z). Пагинация по 10 строк, кнопки
  `<`/`>` плюс прежние Become a ninja / Done. **Список показывается только когда игрок уже ниндзя**;
  иначе — подсказка `naruto.gui.jutsu.locked` и укороченное меню (кнопки подтягиваются вверх).
  **Группировка по типу**, а не по длине комбо: `JutsuScreen$Category` (BASICS/CLONES/DEFENCE/ELEMENTAL/
  SPACE/BODY/ILLUSION/OTHER) + карта `CATEGORIES` (id → категория; неизвестный id попадает в OTHER, а не
  пропадает), заголовки групп золотым. Пагинация идёт по строкам (заголовок тоже строка), но страница
  **никогда не кончается на голом заголовке** (`buildPages` требует места под заголовок+технику), а
  группа, перешедшая на следующую страницу, **печатает заголовок заново** — иначе на новой странице
  шёл бы список имён без пояснения. Ширина панели считается по содержимому (`panelHalfWidth`).
  **Маркер «работает сейчас»** — зелёный `[+]` (`ACTIVE_MARKER`, `ACTIVE_COLOUR`) в **зарезервированной
  фиксированной колонке** слева от названия техники: берётся **только из синхронизированного состояния**
  (`toggleAbilityData` и т.п.), поэтому в меню видно, что `Chakra Fist`/`Chakra Regeneration`/`Kamui
  Phase`/`Water Walk` (и т.п.) прямо сейчас включены. Ночное зрение — **исключение**: оно не помечается,
  потому что это тап-режим, а не «горящая» техника.

## Освоение техник (mastery)

Непрерывная прогрессия: каждая техника запоминает, **сколько раз её кастнули**, и от этого числа
растёт её освоение. Уровни не дискретны — каждый каст даёт немного.

* Кривая `m = casts^e / (casts^e + K^e)`, `K = NarutoConfig.masteryHalfwayCasts` (40 → половина за
  ~40 кастов), `e = NarutoConfig.masteryExponent` (по умолчанию 2 — начало освоения почти не
  двигается: пара кастов даёт доли процента, а не 7%; при `e = 1` кривая снова линейна). `m` всегда
  < 1. Все эффекты линейны от `m`.
* Хранится как **счётчик кастов**, а не уровень (`capabilities/mastery/MasteryData`,
  `Map<ResourceLocation,Integer>`), чтобы перетюнить кривую, не трогая сейвы. В NBT (`NinjaData`,
  `MASTERY_TAG`), синкается своим `trackers/MasteryDataSyncTracker` (для полоски в меню).
* Ядро — `abilities/mastery/Mastery.java`: `level(...)`, `cost(...)`, `reduceCooldown(...)`,
  `sealFree(...)`, `meditationRadius(...)`, `applyDeathPenalty(...)`.
* **Учёт кастов** — один helper `Ability#recordMastery`, зовётся ровно в трёх точках успешного каста:
  instant-пакет (`ServerAbilityActivatePacket`), **включение** TOGGLE (там же; выключение не считается),
  и завершение CHANNELED (`ServerAbilityChannelPacket`: `STOP` при `ticks > 0` и `MIN_ACTIVATE`).
  По тикам счётчик **не** крутится. В креативе касты не считаются (`Mastery.recordCast`).
* **Освоение «по работе», а не по кастам**: техники-каналы, которые что-то накапливают, мастерятся
  тем, сколько они реально накопили, а не тем, как часто нажали клавишу (иначе `Chakra Charge` качался
  тапом `C`). Механизм — `Mastery.recordCredit(player, ninjaData, ability, credit)`: `credit` в
  «каст-эквивалентах», дробная часть копится в `MasteryData#credit` (`Map<ResourceLocation,Float>`,
  server-only, **не** в NBT и **не** синкается), а целые единицы падают в обычный счётчик кастов, так
  что полоска в меню и все эффекты работают как раньше. Техника зовёт `Ability#recordMasteryCredit(...)`
  из `handleChannelling`, меряя **фактическое** приращение (читать до/после `addChakra`, а не прибавлять
  номинал — в полную полосу чакра не влезает и работать не должна), и **переопределяет
  `recordMastery` в пустоту** — иначе завершение канала добавило бы ещё один каст.
  Единица кредита = **один полный столб**: `Chakra Charge` — `restored / getMaxChakra()`,
  `SenjutsuCharging` — `gathered / getMaxSeninMana()`.
* **Поддержание мастерят по времени, а не по кастам** (`MASTERY_CREDIT_PER_TICK = 1/(15*20)`, то есть один
  «каст» за пятнадцать секунд работы) — для TOGGLE и для тех каналов, чей смысл в длительности: `Chakra
  Fist`, `Chakra Regeneration`, `Kamui Phase`, `Water Walk`, Режим Мудреца (из `maintain`), Огненное
  Дыхание. Иначе техника, которую один раз включил, прокачивалась одним нажатием, а не работой. Эти
  техники зовут `recordMasteryCredit` каждый тик, а завершение тумблера/канала — по-прежнему
  **не** добавляет каст.
* **Скидки**: `Ability#masteryCost(ninjaData, base)` — цена через него и в проверке, и в списании;
  снижение КД — централизованно в `Ability.Cooldown#registerCooldown` (по строковому id техники).
  Оба вклада — `masteryCostReduction` / `masteryCooldownReduction` (по 0.4). Исключения — «весь столб»
  (`ChakraShield`, `SecondBreath`): у них бонус качественный (щит: скорость конверсии чакры в
  очки `0.15 → 1.0` по `m`; Darkness `1200→600`).
  `registerCooldown` теперь принимает `player` (нужен presence-gate клонов).
* **Мультиклон**: клонов `lerp(minClones, masteryMaxClones, m)` (+1 разброс) — `4 → 10`
  (`Settings#masteryMaxClones`; у остальных техник = `maxClones`, то есть без роста).
* **Печать**: `Settings#requiresSeal` — только мультиклон и медитация. При `m >= masterySealFree` (0.9)
  печать **не нужна нигде** (`Mastery.sealFree`), проверка в `handleCost`, списание в `performServer`.
* **Боевые клоны — presence-gate, а не таймер**: пока жив хоть один боевой клон кастера,
  `ShadowCloneJutsuAbility#checkCooldown` закрывает каст (сообщение о КД показывает 300 с, со скидкой
  КД от освоения), `registerCooldown` при этом **не** ставит таймер — как только все клоны мертвы, каст
  снова доступен сразу. Рабочие и медитация — на обычном КД.
* **Смерть** (`NinjaCapabilityHandler#playerClone`, `isWasDeath`): откат прогресса на уровне, а не на
  счётчике (`Mastery.applyDeathPenalty`), т.к. у верха кривой доля счётчика почти не двигает `m`.
  `t = clamp((m-0.5)/0.45,0,1)`; `p = rand(lerp(low..), lerp(high..))`; `m' = max(0, m-p)`; назад в
  счётчик. На `m≈0.5` — 3–5%, на `m≈0.95` — 10–20%, разброс на каждую смерть свой.

### Медитация (радиус и пассив)

* **Радиус линка** растёт по `m`: `8 + (meditationRadiusMax-8)·m³` (до ~512 блоков, ~32 чанка; рост
  заострён к концу).
* Внутри радиуса — прежний эффект: `0.2` чакры/тик (или сенин-мана в Режиме Мудреца) за клона, `+100`
  ёмкости чакры за клона, ускоренный КД. При `m >= meditationPassiveThreshold` (0.95) каждый клон вне
  радиуса даёт **+50** ёмкости (`meditationPassiveCapacity`) без регена.
* **Радиус нельзя считать через `getEntitiesOfClass`**: он видит только загруженные чанки, а радиус —
  много чанков. Поэтому клон сам **регистрируется** у кастера: `capabilities/mastery/MeditationData`
  (`Map<UUID, Link(dimension, position, expiresAt)>`), `ShadowCloneEntity#refreshMeditationLink` каждый
  тик, `dropMeditationLink` при `pop()`. `ChakraMeditationJutsuAbility#tickAura` берёт `Mastery`-радиус,
  `prune` по `gameTime` (выгруженный чанк не кормит вечно) и делит клонов на «в радиусе» / «вне».
  `MeditationData` — server-only, **не** синкается и **не** пишется в NBT (умерший кастер медитаций не
  сохраняет).
* **Смерть кастера**: `NinjaCapabilityHandler` развеивает достижимых медитирующих клонов (по
  `getLinks()`), остальное чистится тем, что у нового `NinjaData` пустой `MeditationData`.
* **Крафты**: помимо пяти прежних — генератор рецептов руками (датагена нет) дал 52 JSON в
  `data/narutomod/recipes/` (материалы, оружие, броня, 27 повязок, `lonely_march`, `bonsai_tree`).
  Шаблон и таблица — в `docs/mastery-plan.md`.

## Дерево скилов (`skills`)

Глобальное дерево, которое покупается **очками ванильного опыта** (не уровнями). Оно и пассивки даёт, и
**открывает техники и механики**: у только что ставшего ниндзя доступны лишь три базовые техники, всё
остальное — включая двойной прыжок, рабочие смены, клоны и стихии — покупается по веткам. Так дерево
само рассказывает о механиках мода.

* **Данные** — `skills/Skill.java`: не enum, а обычный класс со **таблицей узлов** в статическом блоке,
  потому что ветки, растущие во все стороны, в enum читались бы плохо. **118 узлов**: центральный корень
  `root` и девять ветвей, расходящихся от него во все стороны. Два вида узлов:
  * `node(path, branch, x, y, cost, effect, amount, требует...)` — пассивка (`SkillEffect` + величина);
  * `unlock(path, branch, x, y, cost, unlockKey, требует...)` — узел-открытие; ключ вида
    `ability:fireball` или `worker:mine` лежит на самом узле (`Skill#unlock()`), а эффект —
    маркер `SkillEffect.UNLOCK`.
  Узел — это ветка, позиция на доске в «шагах сетки» (x/y могут быть отрицательными), цена в **очках
  опыта**, эффект и величина, пути предков и (для открытий) ключ. Плюс два ключа локализации
  (`naruto.skill.<path>.name/.desc`) — больше нигде узел прописывать не надо. `SkillTree` даёт индекс
  `path→Skill`, индекс `unlockKey→Skill` (`hasUnlock`), разрешает предков (неизвестный путь — падение на
  старте, а не молча неоткрываемый узел), проверяет `prerequisitesMet` и суммирует
  `bonus(unlocked, effect)`. `Skill.all()` — единственный вход к списку узлов.
* **Хранение/синк** — `capabilities/skills/SkillData` (множество id изученных узлов) живёт в
  `NinjaData` как `@Sync(minTicks = 1)` (трекер `SkillDataSyncTracker`), пишется в NBT
  (`SKILLS_TAG`, CompoundTag id→true) и переживает смерть. Хранятся только id: числа узла берутся из
  таблицы, поэтому перетюнивание не трогает сейвы.
* **Цена в опыте** — `util/ExperienceUtils#currentPoints(player)` переводит уровень + прогресс в
  **число очков опыта** (точные формулы ванилы для суммы опыта до уровня плюс `progress * getXpNeededForNextLevel()`).
  Именно это число списывается: `player.giveExperiencePoints(-cost)` (ванильный метод переживает
  отрицательные значения и сам понижает уровни), а не `giveExperienceLevels`.
* **Покупка** — клиент шлёт `ServerUnlockSkillPacket(path)`, сервер (`ServerUnlockSkillPacket.Handler`)
  единственный проверяет ниндзя-режим, отсутствие узла, предков и `currentPoints(player) >= cost`,
  затем `giveExperiencePoints(-cost)` + `SkillData#unlock`. Экран только просит.
* **Гейт техник** (`abilities/AbilityUnlocks`) — **единственная точка правды**: базовые техники
  (`chakra_charge`, `substitution`, `leap`) доступны всегда, у остальных `isUnlocked` спрашивает
  `SkillTree.hasUnlock(..., "ability:<path>")`. Проверка стоит **на сервере** в обоих пакетах каста
  (`ServerAbilityActivatePacket`/`ServerAbilityChannelPacket`) и в `DoubleJumpAbility#handleCost`, так что
  хакнутый клиент дерево не обойдёт; клиент лишь гасит двойной прыжок в `LocalPlayerMixin` и красит
  закрытые строки в меню J. **Рабочие смены** гейтятся так же: `ShadowCloneJutsuAbility#handleCost`
  отказывает, если у смены (`WorkerJob#unlockKey`, `worker:farm/mine/chop/hunt`) не изучен узел
  (`jutsu.fail.workerlocked`). Инструмент в руке обязателен по-прежнему — без него `jutsu.fail.workertool`.
  То есть по умолчанию **ни одна рабочая смена не доступна**, и даже двойной прыжок надо купить.
* **Эффекты** (10 + маркер `UNLOCK`) — максимум чакры и стамины прибавляются в
  `NinjaData.getMaxChakra/getMaxStamina`, регенерация чакры/стамины (величина «в секунду» делится на 20) —
  в `updateDataServer`, скидки цены/КД складываются с освоением в `Mastery.cost/reduceCooldown` (общий кап
  `MAX_REDUCTION = 0.9`), а **здоровье, броня, скорость передвижения и урон атаки** — атрибуты, их пишет
  `events/SkillTreeEvents` (server-only `LivingTickEvent`, модификаторы с фиксированными UUID
  перестраиваются только при смене суммы).
* **Стамина в дереве** — 14 узлов (максимум и регенерация) разбросаны по BODY/AGILITY/LABOUR/ELEMENTAL,
  глубокие тиры — `body_ironlungs` (+40, от `body_lungs`), `body_steady` (+0.4/с), и после Восьми врат
  `body_reservoir` (+60) → `body_heart` (+0.5/с), потому что врата — главный потребитель стамины
  (15 за окно). **Здесь была мина**: `addStamina` и тик-кламп в `updateDataServer` срезали стамину по
  `maxStamina` (поле из конфига) вместо `getMaxStamina()` (с бонусами дерева), поэтому все «+N к
  максимуму» работали только в подписи полоски: набрать больше конфижных 100 было нельзя. Плюс
  конструктор `NinjaData(boolean)` заполнял пулы **перекрёстно** (`stamina = maxChakra`,
  `chakra = maxStamina`). Оба места исправлены; если добавляешь эффект, который может перерасти
  конфижный максимум, — клампь по геттеру с бонусами, а не по полю.
* **Базовая чакра 25** — `NarutoConfig.maxChakra` по умолчанию **25**, а корень `root` даёт **+50**, то
  есть первый же купленный узел доводит чакру до 75. (Уже существующий `config/narutomod-*.toml`
  сохраняет старое число — чтобы увидеть 25, конфиг надо удалить или поправить.)
* **Экран** — `client/gui/SkillTreeScreen`: доска рисуется в своих координатах, затем целиком
  сдвигается/масштабируется (`panX/panY/zoom`, зум колесом к курсору, `MIN_ZOOM = 0.35` — дерево
  большое). Узлы красятся по состоянию (заблокирован / готов / готов, но не хватает опыта / изучен).
  Цена на узле сокращается (`1.2k`), полная — в тултипе. **Опыт игрока пишется слева снизу**, а
  **справа снизу — кнопка «К центру»** (`center()`, сброс панорамы и зума). Тап по узлу → покупка,
  перетаскивание → панорама, `onClose` возвращает в `JutsuScreen`.
  * **Рендер нарочно лёгкий** (дерево большое и раньше подтормаживало): позиции, имена и цены всех узлов
    считаются **один раз** в статическом блоке (`NODES`/`NODE_NAME`/`NODE_COST`/`PREREQUISITES`), а не
    каждый кадр; узлы и связи за пределами видимой области **отсекаются** до того, как что-то стоят;
    связи — единственное, чему нужна рисованная линия — кладутся в **один общий буфер** и рисуются одним
    вызовом `guiGraphics.flush()` вместо `fill`-вызова на каждый пиксель; при отдалении линия становится
    **толще** (`cell = ceil(1/zoom)`), поэтому длинная связь стоит меньше квадов. Имена узлов рисуются
    только при `zoom >= LABEL_ZOOM` (0.65) — на мелком масштабе они дороже всего, а имя всё равно есть в
    тултипе.
* **Меню J** — после `Become a ninja` кнопки «стать/передумать» **скрываются**, вместо них появляется
  `naruto.gui.jutsu.skilltree` → `SkillTreeScreen`. Состояние читается из `ninjaModeEnabled` в
  `JutsuScreen.applyState`. Закрытые техники в списке **не прячутся**: строка тускнеет, к имени
  добавляется `naruto.gui.jutsu.locked_suffix`, а процент/полоска освоения заменяются прочерком — так
  игрок видит, что ещё есть чему учиться, и идёт в дерево.
* **Ветки** (9): `ROOT` (центр, фундамент), `CHAKRA` (север, запас/реген), `CLONES` (северо-восток,
  клоны + сундук), `CONTROL` (восток, скидка цены/КД), `ELEMENTAL` (юго-восток, стихии и копание),
  `BODY` (юг, здоровье/броня/стамина/защитные стойки/сендзюцу), `LABOUR` (`labour_hands` → `labour_worker` →
  четыре рабочие смены), `AGILITY` (запад, скорость/урон/двойной прыжок/сцепление), `SPACE`
  (юго-запад, метка/нить/Камуи) и `MIND` (северо-запад, чувство чакры/гендзюцу/хенге/дым/тишина).
  **Раскладка правится без запуска игры**: `tools/skilltree_preview.py` (только stdlib) — сразу и
  линтер, и **редактор доски**. Он парсит `Skill.java`/`SkillBranch.java`/оба `lang`-файла и рисует ту же
  доску, что в игре (`GRID_X`/`GRID_Y`/`NODE` как в `SkillTreeScreen`), с пан/зумом, подсветкой маршрута
  по наведению и изоляцией ветки. На доске узлы можно **таскать** (снап по клеткам, занятые клетки
  отклоняются), карточка узла правит цену/эффект/предков/локаль, `+ узел` добавляет узел рядом, `Ctrl+Z`
  отменяет, `Ctrl+S` сохраняет. Сохранение шлёт всю таблицу POST-ом на `/save` — для этого скрипт
  запускают в режиме `--serve` (проще всего `tools\skilltree_editor.bat`, он же открывает браузер); без
  сервера страница просто скачивает JSON. Файл правок — `tmp/skilltree-edits.json`.
  Линтер (внизу страницы и в `--check`) ловит: дубли клеток и путей, неизвестные предки, линии длиннее
  двух шагов (корню можно три), недостижимые узлы, «техника висит на технике» (у техники обязан быть
  предок-пассивка), дубли ключей-открытий и пустые ключи локализации; пересечение «боксов» веток — просто
  заметка. **Порядок работы**: правки двигаются в браузере → «сохранить» → агент гоняет
  `python tools\skilltree_preview.py --check` → `--apply` (правит `Skill.java` и оба `lang`-файла
  построчно, комментарии и CRLF сохраняя, бэкапы в `tmp/backup/<штамп>/`, невалидный payload
  отклоняется) → смотрит HTML → `build`. Прогон `--apply` на неизменённом файле обязан писать
  `statements updated: 0` — это дешёвый тест, что строка узла собирается ровно так, как лежит в
  исходнике.
  **Связи короткие по построению**: любой узел и его предок стоят не дальше двух шагов сетки (у корневых
  «спиц» — трёх), иначе на доске протягивалась бы линия через пол-экрана. Внутри ветки: цепочка идёт
  прямой, два предка одного узла зеркальны относительно него (симметричная «V»), а ветки не залезают в
  «боксы» друг друга. **Техника никогда не висит на технике**: между двумя техниками всегда стоит
  пассивка-«подводка» (`clone_drill`, `elem_ember`, `elem_delving`, `elem_spiral`, `labour_grit` и т. п.),
  поэтому цепочка читается как «техника → тренировка → техника», а покупка не сводится к «следующему
  квадратику рядом». Одна подводка может открывать несколько техник сразу (`clone_drill` — три варианта
  клона, `labour_grit` — три смены, `elem_ember` — четыре техники), поэтому 33 прежние связи
  «техника → техника» закрылись 22 узлами.
  Из-за этого `LABOUR` растёт не от корня, а от клона: рабочие — те же клоны, поэтому
  ветка выходит из `clone_storage` через **мостик-пассивку `labour_hands`** (Tireless Hands, +20 стамины,
  200 опыта) и только затем открывает `labour_worker`. Это тот самый «минорный скилл» между клонами и
  рабочими; при добавлении узла держи те же правила (старый вариант с `LABOUR` на юге и линией к
  северо-восточному `clone_shadow` выглядел как «линия через всё»).


## Вики (`docs/`)

Статика для GitHub Pages (файл `.nojekyll` рядом): чистый HTML без сборки, страница — самодостаточный
файл, общий только `style.css` и `app.js`. Страницы: `index` · `install` · `basics` · `techniques` ·
`clones` · `skills` · `mastery` · `items` · `world` · `config` · `faq`; навигация и «шапка» продублированы
в каждой (генератора страниц нет). Правила разметки, на которые опирается `app.js` и поиск:

* секция — `<section id="slug">`, её заголовок — `<h2 id="s-slug">` (якорь для оглавления и ссылок);
* спойлер — `<details class="deep" id="...">`; `app.js#openAnchored` раскрывает его при переходе по
  якорю (и на загрузке, и по `hashchange`), поэтому такими спойлерами сделаны ответы FAQ;
* комбо рисуются `.combo`/`<kbd>`, таблицы — `.tbl`, врезки — `.callout--note|tip|warn`;
* ссылка на общий CSS — `style.css?v=<дата>` (сейчас `style.css?v=2026-10-19`). GitHub Pages отдаёт
  `style.css` с `Cache-Control: max-age=600`, поэтому после правки CSS у читателя ещё десять минут
  живёт старая версия (именно так «пережил» деплой баг с шапкой таблиц). Дата бампается на всех
  страницах вместе с правкой `style.css`; `build_docs_search.py` query в ссылке срезает, проверка
  ссылок от метки не страдает.

Инструменты (оба — stdlib-питон, без зависимостей; гонять `python tools\...`, как и `skilltree_preview`):

* `tools/build_docs_search.py` — собирает `docs/search-index.js` (лениво грузится из `app.js`:
  по записи на секцию и на пронумерованный спойлер) **и** проверяет все `href`/`src` всех страниц:
  существование файла, наличие якоря на целевой странице, дубли `id`, ссылки сайдбара на все страницы.
  Правки страниц заканчиваются прогоном этого скрипта, иначе `--check` (и поиск) покажет устаревший
  индекс; сам файл руками не правится — шапка об этом говорит.
* `tools/build_docs_skills.py` — перегенерирует таблицы дерева скилов между
  `<!-- SKILLS:START -->` / `<!-- SKILLS:END -->` в `docs/skills.html` из `skills/Skill.java` и
  `lang/*.json`; то, что вне маркеров, не трогает. `--check` — тест на устаревание.

Числа на страницах — из кода текущей версии, а не из 1.7.10; при сомнении правится страница, а не
память. Публикуемая вики живёт только в `docs/` — `docs/forgeupdatefile/narutomod.json` руками не трогать.


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
* **`player.pick(...)` видит только блоки.** `Entity#pick` строит `ClipContext` по блокам и возвращает
  `BlockHitResult` или `MISS` — `EntityHitResult` из него не приходит **никогда**, поэтому «найди, на
  кого смотрит игрок» через `player.pick` не работает (гендзюцу, хенге и затяг Камуи так и не находили
  цель). Клиент строит свой луч для прицела в рендере, но он недоступен на сервере. Правильный путь —
  `util/Targeting.lookedAtEntity(player, range)`: сначала `level().clip(new ClipContext(..., Block.COLLIDER, ...))`
  до первой стены, затем `ProjectileUtil.getEntityHitResult(shooter, eye, end, box, isPickable, distSq)`
  на этом отрезке. Так луч не проходит сквозь стены и упирается в чужую цель.
  Для «найди блок, на который смотрит игрок» в `Targeting` есть `lookedAtBlock(player, range)`: он делает
  только `level().clip(new ClipContext(..., Block.COLLIDER, ...))` и возвращает `BlockHitResult` (или
  `null`), и этим лучом пользуется техника костра. Смотреть сквозь стены он не даёт — это и нужно.

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
* **`noPhysics` у игрока ваниль сбрасывает каждый тик.** `Player#tick()` начинается с
  `this.noPhysics = this.isSpectator()`, а движение (`aiStep`) идёт позже в том же `tick()`. Поэтому
  «пройти сквозь стену» нельзя включить из `TickEvent.PlayerTickEvent` (ни START, ни END — START
  раньше сброса, END позже движения): флаг не доживает до шага. Рабочий хук — `LivingEvent.LivingTickEvent`
  (`LivingEntity#tick` — уже внутри `Player#tick`, после сброса, до `aiStep`). Так же нужны **обе
  стороны**: сервер авторитетен по позиции лишь формально, клиент считает коллизии сам и выталкивает
  игрока из стены. Флаг состояния живёт в `toggleAbilityData` (`@Sync`), так что клиент знает о стойке.
* **`Component.keybind` следует раскладке клавиатуры.** Внутри он берёт имя клавиши через
  `GLFW.glfwGetKeyName`, поэтому на русской раскладке `C V B` приходят кириллицей (с/м/и, что визуально
  похоже на C/M/H) — в меню техник это выглядело как «C H M». Код клавиши от раскладки не зависит,
  поэтому буква вычисляется из `InputConstants.Key#getValue()` (A–Z = 65–90, 0–9 = 48–57), а fallback
  на `getDisplayName()` остаётся только для не-букв. Так делает `JutsuScreen#keyLetter`.
* **`ChunkGenerator#applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)`** — первый
  параметр `WorldGenLevel` (`net.minecraft.world.level.WorldGenLevel`), **не** `WorldGenRegion`;
  перепутаешь — получишь «does not override or implement a method from a supertype». Переопределение
  в no-op нужно, если генератор сам кладёт рельеф: иначе биомные фичи (`the_void` →
  `minecraft:void_start_platform`) дорисовываются поверх и в измерении появляется «каменный потолок».
* **Навигация мобов к сплошному блоку — путь в никуда.** `PathNavigation#moveTo` на координаты
  блока, в котором стоит `BLOCKED`-нода (бревно, камень, любой блок с коллизией), молча возвращает
  «пути нет», и моб просто стоит. Грядки/факелы/трава (без коллизии) работают, а вот бревно — уже нет:
  именно на этом рабочий-лесоруб «зависал» с топором. Идти надо в **проходимого соседа** цели (см.
  `WorkerBlockGoal#standPosition`), а недостижимые цели отсеивать заранее (`hasStand`). Тот же капкан
  ждёт любой другой «подойди и сломай блок» гол (`WorkerMineGoal`, `WorkerChopGoal`). Отдельный случай —
  **шахтёр**: ему `needsStand` переопределён в `false`, а `WorkerBlockGoal#clearPath`/`afterClear` дают
  пробить первый камень перед рудой и войти в нишу (см. рабочие смены).
* **Access transformer не доезжает до compile-classpath — брать mixin-аксессор.** `accessTransformer =
  file('src/main/resources/META-INF/accesstransformer.cfg')` в `build.gradle` есть, но новые строки в нём
  на компиляцию не влияют: `forge-1.20.1-47.1.62_mapped_official_1.20.1.jar` собирается из
  `injected.jar` отдельной задачей, и приватные поля там так и остаются приватными (`javap` показывал
  package-private `cookingProgress` в `AbstractFurnaceBlockEntity` даже с AT-строкой). Проверено на
  попытке открыть поля печки. Рабочий путь — **mixin-аксессор**: `@Mixin(Target.class)` +
  `interface` с `@Accessor("field")`/сеттерами (см. `mixin/common/AbstractFurnaceAccessor`), он ещё и
  ремапится через refmap (`cookingProgress → f_58318_:I`), то есть корректен после обфускации. Не
  полагаться на reflection по Mojang-имени: в продакшене поле зовётся SRG (`f_58318_`), и такой код
  молча не найдёт поле.
