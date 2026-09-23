package dev.jalikdev.lowCore.antifreecam;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public enum AntiModClient {
    FREECAM("freecam", "Freecam", Material.SPYGLASS, List.of(
            new Signature("key.freecam.toggle", SignatureType.KEYBIND),
            new Signature("freecam.config.gui.title", SignatureType.TRANSLATABLE)
    )),
    METEOR("meteor", "Meteor Client", Material.FIRE_CHARGE, List.of(
            new Signature("key.meteor-client.open-gui", SignatureType.TRANSLATABLE_RAW_SIGNAL)
    )),
    WURST("wurst", "Wurst Client", Material.COOKED_PORKCHOP, List.of(
            new Signature("description.wurst.hack.clickgui", SignatureType.TRANSLATABLE)
    )),
    LIQUIDBOUNCE("liquidbounce", "LiquidBounce", Material.WATER_BUCKET, List.of(
            new Signature("liquidbounce.module.clickGUI.description", SignatureType.TRANSLATABLE)
    )),
    THUNDERHACK("thunderhack", "ThunderHack", Material.LIGHTNING_ROD, List.of(
            new Signature("descriptions.client.clickgui", SignatureType.TRANSLATABLE)
    ));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<Signature> signatures;

    AntiModClient(String id, String displayName, Material icon, List<Signature> signatures) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.signatures = signatures;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public List<Signature> signatures() {
        return signatures;
    }

    public static List<ProbeSignature> allSignatures() {
        return Arrays.stream(values())
                .flatMap(client -> client.signatures.stream()
                        .map(signature -> new ProbeSignature(client, signature.key(), signature.type())))
                .toList();
    }

    public enum SignatureType {
        KEYBIND,
        TRANSLATABLE,
        TRANSLATABLE_RAW_SIGNAL
    }

    public record Signature(String key, SignatureType type) {
    }

    public record ProbeSignature(AntiModClient client, String key, SignatureType type) {
    }
}
