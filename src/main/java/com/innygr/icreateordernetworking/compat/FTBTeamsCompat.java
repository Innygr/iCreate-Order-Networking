package com.innygr.icreateordernetworking.compat;

import net.neoforged.fml.ModList;

/**
 * Optional FTB Teams integration gate.
 *
 * The jar filename is not a reliable way to detect a NeoForge mod; use the
 * mod id declared by FTB Teams. Keep the core mod free of hard FTB Teams class
 * references so a missing or changed optional API cannot crash class loading.
 */
public final class FTBTeamsCompat {
    public static final String MOD_ID = "ftbteams";

    private FTBTeamsCompat() {}

    /** True when FTB Teams is loaded, regardless of its installed jar filename. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * The integration can be enabled only after the supported FTB Teams API
     * has been verified for the installed version. Mod presence alone does not
     * provide team membership data or authorize a request.
     */
    public static boolean isIntegrationReady() {
        return isLoaded() && FTBTeamsApiBridge.isSupportedApiPresent();
    }

    /** Isolate optional API reflection from commonly loaded logistics classes. */
    private static final class FTBTeamsApiBridge {
        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

        private static boolean isSupportedApiPresent() {
            if (!isLoaded()) return false;
            try {
                Class.forName(API_CLASS, false, FTBTeamsCompat.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                return false;
            }
        }
    }
}
