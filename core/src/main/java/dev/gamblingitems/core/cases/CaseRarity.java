package dev.gamblingitems.core.cases;

/**
 * The six rarities of a key, from the one every mob may drop to the one a boss leaves behind.
 * Identifiers are stored in configuration and in item registries; never persist an ordinal.
 */
public enum CaseRarity {
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    EPIC("epic"),
    LEGENDARY("legendary"),
    MYTHIC("mythic");

    private final String id;

    CaseRarity(String id) {
        this.id = id;
    }

    public String id() { return id; }

    /** The item identifier of the key that opens a case of this rarity. */
    public String keyPath() { return "key_" + id; }

    /** The identifier of the case this key opens. */
    public String caseId() { return id + "_case"; }

    public static CaseRarity fromId(String id) {
        for (CaseRarity rarity : values()) {
            if (rarity.id.equals(id)) return rarity;
        }
        throw new IllegalArgumentException("Unknown case rarity: " + id);
    }
}
