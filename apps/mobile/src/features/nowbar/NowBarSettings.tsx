import * as Notifications from "expo-notifications";
import { useEffect, useState } from "react";
import { Alert, AppState, View } from "react-native";
import { AppText as Text } from "../../components/AppText";
import { SettingsRow } from "../settings/components/SettingsRow";
import { SettingsSection } from "../settings/components/SettingsSection";
import { SettingsSwitchRow } from "../settings/components/SettingsSwitchRow";
import { nowBarNative, saveNowBarPreferences, useNowBarPreferences } from "./native";
import { checkNowBarUpdate } from "./updates";

export function NowBarSettings() {
  const preferences = useNowBarPreferences();
  const [capabilities, setCapabilities] = useState(() => nowBarNative?.capabilities());
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") setCapabilities(nowBarNative?.capabilities());
    });
    return () => subscription.remove();
  }, []);
  if (!nowBarNative) return null;
  const enable = async (enabled: boolean) => {
    setBusy(true);
    try {
      if (enabled) {
        const permission = await Notifications.requestPermissionsAsync();
        if (!permission.granted) {
          Alert.alert(
            "Notifications are disabled",
            "Allow notifications in Android settings to use Now Bar.",
            [
              { text: "Cancel", style: "cancel" },
              { text: "Open settings", onPress: () => nowBarNative?.openSettings() },
            ],
          );
          return;
        }
      }
      saveNowBarPreferences({ enabled });
      setCapabilities(nowBarNative?.capabilities());
    } catch (error) {
      Alert.alert(
        "Now Bar",
        error instanceof Error ? error.message : "Could not change notification settings.",
      );
    } finally {
      setBusy(false);
    }
  };
  return (
    <View className="gap-3">
      <View
        style={{
          backgroundColor: "#171423",
          borderRadius: 26,
          padding: 22,
          borderWidth: 1,
          borderColor: "#39304F",
          gap: 14,
        }}
      >
        <View
          style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}
        >
          <Text style={{ color: "#B9A0FF", fontSize: 12, letterSpacing: 2 }}>T3 · NOW BAR</Text>
          <Text style={{ color: "#8F879F", fontSize: 12 }}>Preview</Text>
        </View>
        <Text style={{ color: "#F4EFFF", fontSize: 23, fontWeight: "700" }}>
          Your agents, at a glance.
        </Text>
        <View style={{ backgroundColor: "#262034", borderRadius: 30, padding: 16, gap: 10 }}>
          <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
            <Text style={{ color: "#D4BFFF", fontWeight: "700" }}>ϟ Building your next idea</Text>
            <Text style={{ color: "#B9A0FF" }}>3/5</Text>
          </View>
          <View style={{ flexDirection: "row", gap: 4 }}>
            {[0, 1, 2, 3, 4].map((step) => (
              <View
                key={step}
                style={{
                  flex: 1,
                  height: 5,
                  borderRadius: 3,
                  backgroundColor: step < 3 ? "#A78BFA" : "#443A56",
                }}
              />
            ))}
          </View>
          <Text style={{ color: "#ABA0BB", fontSize: 12 }}>
            Real plan progress · One tap to jump back in
          </Text>
        </View>
        <Text style={{ color: "#A99DB8", fontSize: 12 }}>
          Violet · Working Amber · Needs you Mint · Ready to review
        </Text>
      </View>
      <SettingsSection title="Samsung Now Bar">
        <SettingsSwitchRow
          icon="bolt.circle"
          label="Live agent work"
          subtitle="Live work and unread results on your lock screen"
          value={preferences.enabled}
          disabled={busy}
          onValueChange={(value) => {
            void enable(value);
          }}
        />
        <SettingsSwitchRow
          icon="bell.badge"
          label="Completion notifications"
          subtitle="Let me know when monitored work finishes"
          value={preferences.results}
          onValueChange={(results) => saveNowBarPreferences({ results })}
        />
        <SettingsSwitchRow
          icon="lock"
          label="Hide task details"
          subtitle="Keep names and plan steps off all notification surfaces"
          value={preferences.private}
          onValueChange={(value) => saveNowBarPreferences({ private: value })}
        />
        <SettingsRow
          icon="gearshape"
          label="Live notification settings"
          value={capabilities?.promoted ? "Allowed" : "Enable"}
          onPress={() => nowBarNative?.openSettings()}
        />
      </SettingsSection>
      <Text className="px-2 text-sm text-foreground-muted">
        Enable Live notifications for T3 Code Now Bar in Samsung settings. Start monitoring while
        the app is open; it stays connected while work is active. Use Unpin to stop monitoring
        current tasks. Completed results stay until you open the thread or dismiss the result.
        Unread tracking starts with this update on this device. Opening the app again reconnects
        after Android stops it.
      </Text>
      <SettingsSection title="Fork updates">
        <SettingsSwitchRow
          icon="arrow.down.circle"
          label="Automatic update checks"
          subtitle="Check GitHub on launch and every six hours when you return"
          value={preferences.updates}
          onValueChange={(updates) => saveNowBarPreferences({ updates })}
        />
        <SettingsRow
          icon="arrow.down.circle"
          label="Check for updates"
          onPress={() => {
            void checkNowBarUpdate(true);
          }}
        />
      </SettingsSection>
    </View>
  );
}
