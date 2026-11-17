import { ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, ChartBar } from 'phosphor-react-native';
import SafeView from '@/components/SafeView';
import useHighjackBackButtonPress from '@/hooks/useHighjackBackButtonPress';
import { IWorkspacePageKey } from '../index';
import { useTranslation } from 'react-i18next';

interface AnonymousTelemetryProps {
  goToPage: (page: IWorkspacePageKey) => void;
}

/** Telemetry is disabled in the Shbeeb integration; this screen explains the actual behavior. */
export default function AnonymousTelemetry({ goToPage }: AnonymousTelemetryProps) {
  const { t } = useTranslation();
  const insets = useSafeAreaInsets();
  const goBack = () => {
    goToPage('main');
    return true;
  };
  useHighjackBackButtonPress(goBack);

  return (
    <SafeView
      scrollable={false}
      safeAreaClassNames="pt-[21px]"
      containerClassNames="flex flex-col flex-1"
      safeAreaStyle={{ backgroundColor: '#0E0F0F' }}>
      <View
        style={{ paddingTop: insets.top, paddingBottom: 20 }}
        className="w-full flex flex-row items-center justify-center relative">
        <TouchableOpacity onPress={goBack} className="absolute left-0 flex flex-row items-center gap-2">
          <ArrowLeft size={24} color="#FFF" weight="bold" />
        </TouchableOpacity>
        <Text className="text-white text-lg font-medium">{t('settings.utility.anonymous_telemetry')}</Text>
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 8, paddingBottom: insets.bottom + 20, gap: 24 }}>
        <View className="w-full flex flex-col" style={{ gap: 12 }}>
          <Text style={{ color: '#9F9FA0' }} className="text-sm uppercase">
            {t('settings.telemetry.usage_data')}
          </Text>
          <View className="flex flex-col rounded-lg" style={{ backgroundColor: '#1B1B1E', padding: 14, gap: 12 }}>
            <View className="flex flex-row items-center" style={{ gap: 10 }}>
              <ChartBar size={20} color="#7FE7FF" />
              <Text className="text-white text-lg">{t('settings.telemetry.disabled_title')}</Text>
            </View>
            <Text style={{ color: '#B7C8D8' }} className="text-sm">
              {t('settings.telemetry.disabled_body')}
            </Text>
          </View>
          <Text style={{ color: '#B7C8D8' }} className="text-sm">
            {t('settings.telemetry.privacy_note')}
          </Text>
        </View>
      </ScrollView>
    </SafeView>
  );
}
