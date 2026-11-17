import DeviceInfo from 'react-native-device-info';
import { Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';

/** Shows the installed Shbeeb version locally; update checks are not sent to the upstream CDN. */
export default function ApkVersion() {
  const { t } = useTranslation();
  return (
    <View className="flex flex-row items-end justify-between">
      <Text style={{ color: '#9F9FA0' }} className="text-sm uppercase">
        {t('settings.version.about')}
      </Text>
      <Text style={{ color: '#888' }} className="text-sm">
        v{DeviceInfo.getVersion()}
      </Text>
    </View>
  );
}
