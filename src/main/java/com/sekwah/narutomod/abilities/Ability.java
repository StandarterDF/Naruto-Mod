package com.sekwah.narutomod.abilities;

import com.sekwah.narutomod.capabilities.CooldownTickEvent;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.triggers.NarutoAdvancements;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

public abstract class Ability {

    public Ability() {
    }

    public enum ActivationType {
        INSTANT,
        TOGGLE,
        CHANNELED
    }

    /**
     * For now the combo to register the ability with. May be overrideable in the future.
     * @return
     */
    public long defaultCombo() {
        return -1;
    }

    /**
     * When returning different activation types make sure you have the correct methods implemented otherwise certain things may not happen
     * see {@link Toggled} and {@link Channeled}
     * @return
     */
    public abstract ActivationType activationType();

    /**
     * Whether this technique can be switched on, off or put aside from the jutsu menu.
     *
     * <p>The menu is a reference first. A fireball has nothing to hold, so clicking it there would
     * only be a way to cast it by accident. What the menu controls are the techniques that stay on -
     * stances that drain chakra every tick and can be left running by mistake - so a player can see
     * what is currently burning and stop it without hunting for the combo. Techniques that behave
     * like a stance but report {@link ActivationType#INSTANT} (the chakra shield is armed, sage mode
     * is entered and left by recasting) override this.</p>
     */
    public boolean isMenuControllable() {
        return this.activationType() == ActivationType.TOGGLE;
    }

    /**
     * Also tell the player why they can't cast the ability.
     *
     * Handle the activation message in perform.
     *
     * In channeled abilities, this will be triggered every tick. And the charge amount will be increased by 1 each tick.
     *
     * If this fails and is above 0 for charge amount the last successful will call perform.
     *
     * If channeled and chargeAmount is -1, it will either be a minCast (if enabled) or the stop packet is
     * received at the same time. If this returns false then perform will not be called.
     *
     * @param player
     * @param ninjaData
     * @param chargeAmount
     * @return if the jutsu cost was able to be fufilled. If this is true then perform will be triggered.
     */
    public abstract boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount);

    /**
     * Do not overwrite this, use {@link Ability#handleCost(Player, INinjaData, int)}
     *
     * This is just to allow code to call the cost without the charge amount.
     * @param player
     * @param ninjaData
     */
    public boolean handleCost(Player player, INinjaData ninjaData) {
        return handleCost(player, ninjaData, 0);
    }

    /**
     * Whether a toggle may be switched on right now, checked on the server before the stance joins
     * the toggle set. A refusal here costs nothing and leaves no mark or cooldown behind, which is
     * what a mode with a one-off ritual price wants: the reason is shown at the moment the keys are
     * pressed instead of the stance flicking on and straight back off.
     *
     * <p>Most toggles pay purely per tick and need no check; the default allows every one of them.</p>
     *
     * @return false to refuse the switch-on; the implementation is expected to have told the player why.
     */
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        return true;
    }

    /**
     * If the ability should say its status in chat
     * @return
     */
    public boolean logInChat() {
        return true;
    }

    /**
     *
     * @return sound to play, if null no sound should be played
     */
    public SoundEvent castingSound() {
       return NarutoSounds.JUTSU_CAST.get();
    }

    /**
     * Sound to play when an ability fails to cast.
     * @return
     */
    public SoundEvent castingFailSound() {
        return NarutoSounds.JUTSU_FAIL.get();
    }

    public String getTranslationKey(INinjaData ninjaData) {
        var resourceKey = NarutoRegistries.ABILITIES.getResourceKey(this);
        return this.getTranslationKey(ninjaData, 0);
    }

    /**
     * If something should be added to the end of the translation string to modify what is said.
     *
     * No longer can replace based on registry name as those are not longer provided by the forge entries.
     * @param ticksActive
     * @return
     */
    public String getTranslationKey(INinjaData ninjaData, int ticksActive) {
        var resourceKey = NarutoRegistries.ABILITIES.getResourceKey(this);
        if(resourceKey.isPresent()) {
            return resourceKey.get().location().toString();
        }
        return "";
    }

    /**
     *
     * @param player the entity casting the jutsu. Will just be players for now. Though may be entity in the future.
     * @param ninjaData
     * @param ticksActive
     */
    public abstract void performServer(Player player, INinjaData ninjaData, int ticksActive);

    /**
     * Do not overwrite this, use {@link Ability#performServer(Player, INinjaData, int)}
     * @param player
     * @param ninjaData
     */
    public void performServer(Player player, INinjaData ninjaData) {
        this.performServer(player, ninjaData, 0);
    }

    /**
     * The price of this technique after mastery, for the affordability check and the payment alike,
     * so the two can never disagree about what fits in the bar. See
     * {@link Mastery#cost(INinjaData, Ability, float)}.
     */
    protected float masteryCost(INinjaData ninjaData, float baseCost) {
        float cost = Mastery.cost(ninjaData, this, baseCost);
        Nature element = this.nature();
        if (ninjaData.isSageMode()) {
            cost *= 0.8f;
        }
        if (element != null) {
            String affinity = ninjaData.getNatureAffinity();
            if (affinity != null && !affinity.isEmpty()) {
                cost *= element.getSerializedName().equals(affinity) ? 0.9f : 1.1f;
            }
        }
        return cost;
    }

    /**
     * The element this technique belongs to, or null for techniques that are not elemental (the
     * eyes, the clones, plain taijutsu). Read by {@link #masteryCost} to apply the caster's innate
     * affinity: a tenth off the element they were born to and a tenth on the rest.
     */
    public Nature nature() {
        return null;
    }

    /** How far this technique has been mastered, from zero to just under one. */
    public float masteryLevel(INinjaData ninjaData) {
        return Mastery.level(ninjaData, this);
    }

    /**
     * Records one successful cast of this technique, which is the only thing that moves mastery
     * forward. Called from the cast sites once a cast has actually gone through, never per tick.
     */
    public void recordMastery(Player player, INinjaData ninjaData) {
        Mastery.recordCast(player, ninjaData, this);
        NarutoAdvancements.onTechniqueCast(player, ninjaData, this);
        // Residual force: a technique leaves a little of itself in the next blow. The mark is what
        // the melee hook reads; it is refreshed by every cast so the reward belongs to the last one.
        if (SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "mechanic:residual_force")) {
            player.getPersistentData().putLong("narutomod:residual_force_until",
                    player.level().getGameTime() + 100L);
        }
    }

    /**
     * Records mastery that is earned by what a technique does rather than by the act of casting it,
     * measured in cast-equivalents. Used by the channelled techniques that gather a resource: they
     * call it as they work with the resource they actually produced, and leave
     * {@link #recordMastery(Player, INinjaData)} empty so a tap is not a cast. See
     * {@link Mastery#recordCredit(Player, INinjaData, Ability, float)}.
     */
    protected void recordMasteryCredit(Player player, INinjaData ninjaData, float credit) {
        Mastery.recordCredit(player, ninjaData, this, credit);
    }

    /**
     * How far a cast of this technique carries as a game event, roughly its loudness. The default is
     * the ordinary cast; quiet techniques return zero (they are not heard at all) and the big ones
     * return a radius of their own. See {@link #emitNoise(Player)}.
     */
    public int noiseRadius() {
        return 16;
    }

    /**
     * Tells the world that this technique was cast, so sculk sensors, shriekers and the warden can
     * hear it. A silence stance cancels the event before it leaves the player (see
     * {@code GameEventDispatcherMixin}), and a quiet technique never posts one in the first place.
     */
    public void emitNoise(Player player) {
        int radius = this.noiseRadius();
        if (radius <= 0 || !NarutoConfig.jutsuLoudnessEnabled) {
            return;
        }
        var event = radius >= NarutoGameEvents.BOOM_RADIUS
                ? NarutoGameEvents.JUTSU_BOOM.get()
                : NarutoGameEvents.JUTSU_CASTING.get();
        player.level().gameEvent(player, event,
                player.position().add(0, player.getEyeHeight() * 0.7, 0));
    }

    /**
     * Cast-equivalents a sustained technique earns for every tick it is actually up, so fifteen
     * seconds of it running is worth one cast. A stance, a channel or a mode is lived in rather than
     * cast in a moment, and paying mastery per bare activation would reward flicking it on and off
     * instead of using it; these techniques call
     * {@link #recordMasteryCredit(Player, INinjaData, float)} with this amount each active tick and
     * leave {@link #recordMastery(Player, INinjaData)} empty.
     */
    public static final float MASTERY_CREDIT_PER_TICK = 1.0f / (15.0f * 20.0f);

    public interface Toggled {

        /**
         *
         * For if abilities need to do anything client side (atm its only triggered with toggle to stop a spam of packets)
         *
         * @param player
         * @param ninjaData
         */
        void performToggleClient(Player player, INinjaData ninjaData);

    }

    /**
     * Channeled and charged abilities are handled the same way as there is so much overlap.
     */
    public interface Channeled {

        /**
         * If the jutsu is not channeled at all, should the jutsu activate? Stops abilities like the channeling activating for a single tick.
         *
         * @return
         */
        default boolean canActivateBelowMinCharge() {
            return true;
        }

        /**
         * If to use the charged translation strings instead of charged.
         *
         * This alters if the chat should show the ability as stopped or activated or charged and cast.
         *
         * @return
         */
        default boolean useChargedMessages() {
            return false;
        }

        /**
         * In case of other use cases where you don't want the messages. for custom states e.g. substitution.
         * @return
         */
        default boolean hideChannelMessages() {
            return false;
        }

        /**
         * Call every tick handleCost passes on server side.
         *
         * This is the main behavior that seperates a "channeled" ability from a "charged" ability as the behaviors are the same.
         *  @param player
         * @param ninjaData
         * @param ticksChanneled
         */
        default void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {}
    }

    public interface HandleEnded {
        /**
         * This will trigger on the player when they are no longer able to cast the ability or when they re-active
         * the ability to cancel it.
         *
         * For now the toggle abilities will return 0, though will return how long they lasted in the future
         * e.g. if a jutsu should hurt you more if it was left on for longer.
         *
         * This will be triggered on ActivationType.TOGGLE and ActivationType.CHARGED.
         *
         * @param player
         * @param ninjaData
         */
        void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive);
    }

    /**
     * A toggle that does something more interesting than switching off when its key is pressed while
     * it is already running. The curse seal is the first of these: the same key opens the first
     * stage, deepens it to the second and lets go.
     */
    public interface Retoggle {

        /**
         * Called instead of turning the toggle off. A false result lets the ordinary switch-off run.
         *
         * @return true when the press was consumed and the toggle must stay in the active set.
         */
        boolean onRetoggle(Player player, INinjaData ninjaData);
    }

    /**
     * Interface class to append a cooldown to a jutsu.
     */
    public interface Cooldown
    {

        /**
         * Method to  get the cooldown  value.
         * @return the cooldown specified.
         */
        int getCooldown();

        /**
         * Check if a cooldown exists for this jutus.
         * @param player - the player the jutsu is being cased from.
         * @param ninjaData - the ninjaData capability attached to the player.
         * @param translationKey - the translation key for the jutsu / unique name.
         * @return  return true if a cooldown exists or false if no cooldown exists.
         */
        default boolean checkCooldown(Player player, INinjaData ninjaData, String translationKey) {
            if  (getCooldown() > 0 && ninjaData.getCooldownEvents().containsKey(translationKey)) {
                player.displayClientMessage(Component.translatable("jutsu.fail.cooldown",
                        Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW),
                        Component.literal(String.valueOf((int) Math.ceil(ninjaData.getCooldownEvents().get(translationKey).ticks / 20f))).withStyle(ChatFormatting.YELLOW)
                ), true);
                return  true;
            }
            return  false;
        }

        /**
         * Registers that a cooldown should exist for this jutus and sets a cooldown timer in the NinjaData
         * @param player - the player the jutsu is being cast by.
         * @param ninjaData - the ninjaData capability attached to the player.
         * @param translationKey - the translation key for the jutsu / unique name.
         */
        default void registerCooldown(Player player, INinjaData ninjaData, String translationKey) {
            int cooldown = Mastery.reduceCooldown(ninjaData, translationKey, getCooldown());
            if (cooldown > 0)  {
                ninjaData.getCooldownEvents().put(translationKey, new CooldownTickEvent(cooldown));
            }
        }
    }
}
