package org.orecruncher.dsurround.config.biome;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;

import java.awt.*;
import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public final class BiomeInfo implements Comparable<BiomeInfo>, IBiomeSoundProvider {

    private static final IModLog LOGGER = Client.LOGGER.createChild(BiomeInfo.class);
    public static final int DEFAULT_ADDITIONAL_SOUND_CHANCE = 1000 / 4;
    public static final String DEFAULT_SOUND_CHANCE = String.valueOf(1D / DEFAULT_ADDITIONAL_SOUND_CHANCE);

    private final int version;
    private final Identifier biomeId;
    private final String biomeName;

    private Color fogColor;
    private String additionalSoundChance = DEFAULT_SOUND_CHANCE;
    private String moodSoundChance = DEFAULT_SOUND_CHANCE;

    private final ObjectArray<AcousticEntry> loopSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> moodSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> additionalSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> musicSounds = new ObjectArray<>();

    private final BiomeTraits traits;

    private ObjectArray<String> comments;

    private final boolean isRiver;
    private final boolean isOcean;
    private final boolean isDeepOcean;

    public BiomeInfo(final int version, final Identifier id, final String name, BiomeTraits traits) {
        this.version = version;
        this.biomeId = id;
        this.biomeName = name;

        this.traits = traits;
        this.isRiver = this.traits.contains("RIVER");
        this.isOcean = this.traits.contains("OCEAN");
        this.isDeepOcean = this.isOcean && this.traits.contains("DEEP");
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isRiver() {
        return this.isRiver;
    }

    public boolean isOcean() {
        return this.isOcean;
    }

    public boolean isDeepOcean() {
        return this.isDeepOcean;
    }

    public Identifier getBiomeId() {
        return this.biomeId;
    }

    void addComment(final String comment) {
        if (!StringUtils.isEmpty(comment)) {
            if (this.comments == null)
                this.comments = new ObjectArray<>();
            this.comments.add(comment);
        }
    }

    public String getBiomeName() {
        return this.biomeName;
    }

    public Color getFogColor() {
        return this.fogColor;
    }

    void setFogColor(final Color color) {
        this.fogColor = color;
    }

    public boolean getHasFog() {
        return this.fogColor != null;
    }

    void setAdditionalSoundChance(final String chance) {
        this.additionalSoundChance = chance;
    }

    void setMoodSoundChance(final String chance) {
        this.moodSoundChance = chance;
    }

    public BiomeTraits getTraits() {
        return this.traits;
    }

    @Override
    public Collection<SoundEvent> findBiomeSoundMatches() {
        ObjectArray<SoundEvent> results = new ObjectArray<>();
        for (final AcousticEntry sound : this.loopSounds) {
            if (sound.matches())
                results.add(sound.getAcoustic());
        }
        return results;
    }

    @Override
    public @Nullable SoundEvent getExtraSound(final SoundEventType type, final Random random) {

        ObjectArray<AcousticEntry> sourceList = null;

        switch (type) {
            case ADDITION -> {
                var chance = ConditionEvaluator.INSTANCE.eval(this.additionalSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.additionalSounds : null;
                }
            }
            case MOOD -> {
                var chance = ConditionEvaluator.INSTANCE.eval(this.moodSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.moodSounds : null;
                }
            }
            case MUSIC -> sourceList = this.musicSounds;
        }

        if (sourceList == null || sourceList.size() == 0)
            return null;

        var candidates = sourceList.stream().filter(AcousticEntry::matches).collect(Collectors.toList());
        return new WeightTable<>(candidates).next();
    }

    void clearSounds() {
        this.loopSounds.clear();
        this.additionalSounds.clear();
        this.musicSounds.clear();
        this.moodSounds.clear();
        this.moodSoundChance = DEFAULT_SOUND_CHANCE;
        this.additionalSoundChance = DEFAULT_SOUND_CHANCE;
    }

    public void update(final BiomeConfigRule entry) {

        addComment(entry.comment);

        if (entry.fogColor != null) {
            setFogColor(ColorPalette.fromHTMLColorCode(entry.fogColor));
        }

        if (entry.clearSounds) {
            addComment("> Sound Clear");
            clearSounds();
        }

        if (entry.additionalSoundChance != null)
            setAdditionalSoundChance(entry.additionalSoundChance);

        if (entry.moodSoundChance != null)
            setMoodSoundChance(entry.moodSoundChance);

        for (final AcousticConfig sr : entry.acoustics) {
            final Identifier res = SoundLibrary.resolveIdentifier(Client.ModId, sr.soundEventId);
            final SoundEvent acoustic = SoundLibrary.getSound(res);

            switch (sr.type) {
                case LOOP -> {
                    final AcousticEntry acousticEntry = new AcousticEntry(acoustic, sr.conditions);
                    this.loopSounds.add(acousticEntry);
                }
                case MUSIC, MOOD, ADDITION -> {
                    final int weight = sr.weight;
                    final AcousticEntry acousticEntry = new AcousticEntry(acoustic, sr.conditions, weight);

                    if (sr.type == SoundEventType.ADDITION)
                        this.additionalSounds.add(acousticEntry);
                    else if (sr.type == SoundEventType.MOOD)
                        this.moodSounds.add(acousticEntry);
                    else
                        this.musicSounds.add(acousticEntry);
                }
                default -> LOGGER.warn("Unknown SoundEventType %s", sr.type);
            }
        }
    }

    public void trim() {
        this.loopSounds.trim();
        this.additionalSounds.trim();
        this.moodSounds.trim();
        this.musicSounds.trim();
        if (this.comments != null)
            this.comments.trim();
    }

    private Biome getBiome() {
        return BiomeLibrary.getBiome(this.biomeId);
    }

    @Override
    public String toString() {
        final String indent = "    ";

        String tags;

        if (this.biomeId.getNamespace().equalsIgnoreCase(Client.ModId)) {
            // It's fake and has no tags
            tags = "FAKE BIOME";
        } else {
            tags = GameUtils.getWorld().getTagManager()
                    .getOrCreateTagGroup(Registry.BIOME_KEY)
                    .getTagsFor(getBiome()).stream()
                    .map(Identifier::toString)
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("Biome [").append(getBiomeName()).append('/').append(this.biomeId).append("]");
        builder.append("\nTags: ").append(tags);
        builder.append("\n").append(getTraits().toString());

        if (this.fogColor != null) {
            builder.append("\nfogColor: ").append(ColorPalette.toHTMLColorCode(this.fogColor));
        }

        if (this.loopSounds.size() > 0) {
            builder.append("\nLOOP sounds [\n");
            builder.append(this.loopSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (this.musicSounds.size() > 0) {
            builder.append("\nMUSIC sounds [\n");
            builder.append(this.musicSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (this.additionalSounds.size() > 0) {
            builder.append("\nADDITIONAL chance: ").append(this.additionalSoundChance);
            builder.append("\nADDITIONAL sounds [\n");
            builder.append(this.additionalSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (this.moodSounds.size() > 0) {
            builder.append("\nMOOD chance: ").append(this.additionalSoundChance);
            builder.append("\nMOOD sounds [\n");
            builder.append(this.moodSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (this.comments != null && this.comments.size() > 0) {
            builder.append("\ncomments:\n");
            builder.append(this.comments.stream().map(c -> indent + c).collect(Collectors.joining("\n")));
            builder.append('\n');
        }

        builder.append("\n");

        return builder.toString();
    }

    @Override
    public int compareTo(final BiomeInfo o) {
        return getBiomeId().compareTo(o.getBiomeId());
    }
}