import Constants from "expo-constants";
import { Alert } from "react-native";
import { nowBarNative } from "./native";
import { parseNowBarRelease, RELEASE_ROOT, type NowBarRelease } from "./update-manifest";

let lastCheck = 0;
let checking = false;
let installing = false;
let offered = 0;

async function install(release: NowBarRelease) {
  if (installing || !nowBarNative) return;
  installing = true;
  try {
    const result = await nowBarNative.installUpdate(
      release.url,
      release.sha256,
      release.versionCode,
    );
    if (result === "permission")
      Alert.alert(
        "Allow app updates",
        "Enable ‘Allow from this source’, then return to Settings → Check for updates and tap Install again.",
      );
  } catch (error) {
    Alert.alert("Update failed", error instanceof Error ? error.message : "Please try again.");
  } finally {
    installing = false;
  }
}

export async function checkNowBarUpdate(manual: boolean): Promise<void> {
  if (
    !nowBarNative ||
    checking ||
    installing ||
    (!manual && Date.now() - lastCheck < 6 * 60 * 60 * 1000)
  )
    return;
  checking = true;
  lastCheck = Date.now();
  try {
    const response = await fetch(`${RELEASE_ROOT}latest/download/update.json`, {
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok)
      throw new Error(
        response.status === 404
          ? "The first signed release is not available yet."
          : "Could not check for updates.",
      );
    const release = parseNowBarRelease(
      await response.json(),
      Constants.expoConfig?.android?.versionCode ?? 0,
    );
    if (!release) {
      if (manual) Alert.alert("You're up to date", "You have the latest T3 Code Now Bar release.");
      return;
    }
    if (!manual && offered === release.versionCode) return;
    offered = release.versionCode;
    Alert.alert(
      `T3 Code Now Bar ${release.version}`,
      "A new signed APK is ready. Download and open the Android installer?",
      [
        { text: "Later", style: "cancel" },
        {
          text: "Install",
          onPress: () => {
            void install(release);
          },
        },
      ],
    );
  } catch (error) {
    if (manual)
      Alert.alert(
        "Update check",
        error instanceof Error ? error.message : "Could not reach GitHub.",
      );
  } finally {
    checking = false;
  }
}
