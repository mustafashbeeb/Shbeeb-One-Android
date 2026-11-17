import { useState, useEffect, useRef } from 'react';
import { Alert, Platform } from 'react-native';
import * as RNFS from '@dr.pogodin/react-native-fs';
import { resolveDestinationPathFromGGUFUrl } from '@/utils/models/defaults';
import { useNetInfo } from '@react-native-community/netinfo';
import { formatBytes } from '@/utils/formatters';
import AwaitableAlert from '@/components/AwaitableAlert';
import uiStore from '@/store/UIStore';
import PushNotifications from '@/utils/PushNotifications';
import { activateKeepAwake, deactivateKeepAwake } from '@/utils/keepAwake';
import ImportedModels from '@/utils/models/imported';
import { downloadFileAtomic } from '@/utils/fs/atomicDownload';
import { showToast } from '@/utils/Notification';
import i18n from '@/i18n';
import { cancelSharedGGUFDownload, downloadSharedGGUF, getSharedModelFolder, pickSharedModelFolder, releaseSharedGGUF } from '@/utils/models/sharedModelAccess';
import SharedModelLocations from '@/utils/models/sharedLocations';
import { sizeToBytes } from '@/utils/models/memoryFit';

const UI_PROGRESS_INTERVAL_MS = 250;
const NOTIFICATION_PROGRESS_INTERVAL_MS = 5000;

interface UseModelManagerProps {
  llmPreferences: any;
  fetchLLMPreference: () => Promise<void>;
  LLMProvider?: any;
}

export default function useModelManager({ llmPreferences, fetchLLMPreference, LLMProvider }: UseModelManagerProps) {
  const netInfo = useNetInfo();
  const [modelDownloadUrl, setModelDownloadUrl] = useState<string | null>(null);
  const [downloadProgress, setDownloadProgress] = useState(0);
  const [downloadedModels, setDownloadedModels] = useState<{ [key: string]: boolean }>({});
  const [selectedModel, setSelectedModel] = useState<string | null>(null);
  // The in-flight model download, so the card can cancel it. `jobId` is null until RNFS has started the job.
  const activeDownload = useRef<{ modelName: string; jobId: number | null; sharedDownloadId: string | null; cancelled: boolean } | null>(null);

  // Check which models are downloaded and set initial selection
  useEffect(() => {
    async function fetchModels() {
      if (LLMProvider) {
        const models = await LLMProvider.availableModels();
        updateDownloadedModels(models);
        setSelectedModel(LLMProvider.model || null);
      }
    }
    fetchModels();
  }, [LLMProvider]);

  const updateDownloadedModels = async (models: any[]) => {
    const downloaded: { [key: string]: boolean } = {};
    for (const model of models) {
      if (model.isShared) {
        downloaded[model.modelId] = true;
        continue;
      }
      const path = resolveDestinationPathFromGGUFUrl(model.downloadUrl);
      downloaded[model.modelId] = await RNFS.exists(path);
    }
    setDownloadedModels(downloaded);
  };

  /**
   * Run the pre-download confirmation checks
   * - Will deny download if there is no internet connection
   * - Will ask to continue download if the model is not a Wi-Fi connection
   * - Will final confirmation before download of model
   * @param model - The model to download
   * @returns True if the model can be downloaded, false otherwise
   */
  async function runPreDownloadConfirmations(model: any): Promise<boolean> {
    const modelSize = typeof model.size === 'number' ? formatBytes(model.size) : model.size;
    if (!netInfo.isConnected) {
      await AwaitableAlert(
        i18n.t('downloads.no_internet_title'),
        i18n.t('downloads.no_internet_model'),
        { text: i18n.t('common.dismiss'), style: 'default' },
        { text: i18n.t('common.ok'), style: 'default' },
      );
      return false;
    }

    if (netInfo.type !== 'wifi') {
      const ignoreWarning = await AwaitableAlert(
        i18n.t('downloads.data_usage_title'),
        i18n.t('downloads.data_usage_model', { size: modelSize }),
        { text: i18n.t('common.cancel'), style: 'cancel' },
        { text: i18n.t('downloads.continue_anyway'), style: 'default' },
      );
      if (!ignoreWarning) return false;
    }

    const shouldDownload = await AwaitableAlert(
      i18n.t('downloads.download_model_title'),
      i18n.t('downloads.download_model_message', { size: modelSize }),
      { text: i18n.t('common.cancel'), style: 'cancel' },
      { text: i18n.t('downloads.continue_with_download'), style: 'default' },
    );
    if (!shouldDownload) return false;
    return true;
  }

  /**
   * Download a model
   * @param model - The model to download
   * @param runPrefetchChecks - If true, the model will be downloaded and the pre-download confirmation checks will be run. Otherwise, it is assumed these checks have already been run prior to calling this function.
   * @returns True if the model was downloaded, false otherwise
   */
  /**
   * Streams one file to disk while driving the in-app progress state and the system notification.
   */
  const downloadToStorage = async ({ fromUrl, toFile, title, body }: { fromUrl: string; toFile: string; title: string; body: string }) => {
    // Create the directory if it doesn't exist
    const dirPath = toFile.substring(0, toFile.lastIndexOf('/'));
    await RNFS.mkdir(dirPath, { NSURLIsExcludedFromBackupKey: true });
    setDownloadProgress(0);

    const downloadNotificationId = await PushNotifications.send('progress', {
      title,
      body,
      android: {
        progress: {
          indeterminate: true,
        },
      },
    });

    try {
      // The in-app card animates every tick, so poll often. The system notification
      // is rate-limited by Android, so only push to it every few seconds.
      let lastNotifiedAt = 0;
      await downloadFileAtomic({
        fromUrl,
        toFile,
        progress: res => {
          const progress = Math.round((res.bytesWritten / res.contentLength) * 100);
          setDownloadProgress(progress);
          const now = Date.now();
          if (now - lastNotifiedAt < NOTIFICATION_PROGRESS_INTERVAL_MS) return;
          lastNotifiedAt = now;
          PushNotifications.send('progress', {
            id: downloadNotificationId,
            title,
            body,
            android: {
              progress: {
                current: progress,
                max: 100,
              },
            },
          });
        },
        background: true,
        discretionary: true,
        progressInterval: UI_PROGRESS_INTERVAL_MS,
      }, jobId => {
        const job = activeDownload.current;
        if (!job) return;
        job.jobId = jobId;
        if (job.cancelled) RNFS.stopDownload(jobId); // cancelled before RNFS handed us the job
      });

      // The progress callback only fires every `progressInterval` ms, so the last
      // reported value is usually short of 100. Snap to done before flipping state.
      setDownloadProgress(100);
    } finally {
      PushNotifications.cancel('progress', downloadNotificationId);
    }
  };

  const downloadToSharedStorage = async (model: any) => {
    const imported = model.isImported ? await ImportedModels.find(model.modelId) : null;
    if (model.isImported && !imported) throw new Error('تعذّر العثور على بيانات النموذج المستورد. أعد إضافته ثم حاول مجدداً.');
    const fromUrl = String(model.downloadUrl || '');
    const lastSegment = fromUrl.split(/[?#]/, 1)[0].split('/').pop() || `${model.modelId}.gguf`;
    let fallbackName = lastSegment;
    try { fallbackName = decodeURIComponent(lastSegment); } catch { /* keep the URL segment */ }
    const candidateName = imported?.filename || fallbackName;
    const modelSlug = (String(model.modelId).split('/').pop() || 'model').replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 48);
    const modelStem = candidateName.replace(/\.gguf$/i, '').replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 110) || 'weights';
    const fileName = `${modelSlug}__${modelStem}.gguf`;
    const expectedSize = sizeToBytes(model.size);
    const notificationId = `shared-${Date.now()}`;
    await PushNotifications.send('progress', {
      id: notificationId,
      title: i18n.t('downloads.notification_downloading_title'),
      body: i18n.t('downloads.notification_downloading_body', { model: model.modelId }),
      android: { progress: { indeterminate: expectedSize <= 0 } },
    });
    let lastNotifiedAt = 0;
    try {
      const result = await downloadSharedGGUF(fromUrl, fileName, expectedSize, progress => {
        const percent = progress.totalBytes > 0
          ? Math.min(100, Math.floor((progress.bytesWritten / progress.totalBytes) * 100))
          : 0;
        setDownloadProgress(percent);
        const now = Date.now();
        if (now - lastNotifiedAt < NOTIFICATION_PROGRESS_INTERVAL_MS) return;
        lastNotifiedAt = now;
        PushNotifications.send('progress', {
          id: notificationId,
          title: i18n.t('downloads.notification_downloading_title'),
          body: i18n.t('downloads.notification_downloading_body', { model: model.modelId }),
          android: { progress: { current: percent, max: 100, indeterminate: progress.totalBytes <= 0 } },
        });
      }, downloadId => {
        if (activeDownload.current) activeDownload.current.sharedDownloadId = downloadId;
      });

      if (imported) {
        await ImportedModels.add({ ...imported, source: 'shared', sharedUri: result.uri, size: result.sizeBytes });
      } else {
        await SharedModelLocations.add({
          modelId: model.modelId,
          uri: result.uri,
          filename: result.name,
          sizeBytes: result.sizeBytes,
          sha256: result.sha256,
        });
      }
      return result;
    } finally {
      PushNotifications.cancel('progress', notificationId);
    }
  };

  /**
   * Download a model. The vision projector (mmproj) of a multimodal model is NOT fetched here - the
   * user opts into that from the attachments sheet the first time they want to send an image
   * (see `utils/models/mmproj`).
   */
  const downloadModel = async (model: any, runPrefetchChecks = true) => {
    if (modelDownloadUrl) return false;

    // A SAF-backed GGUF already exists in the user's shared library. Selecting it
    // must never enter the URL download/copy path.
    if (model.isShared) return await selectModel(model);

    const isDownloaded = await checkModelDownloaded(model.downloadUrl);
    if (isDownloaded) return await selectModel(model);

    // If prefetch checks are enabled, run them before downloading to abort early
    if (runPrefetchChecks) {
      const approved = await runPreDownloadConfirmations(model);
      if (!approved) return false;
    }

    let useSharedStorage = false;
    if (Platform.OS === 'android') {
      let sharedFolderUri = await getSharedModelFolder();
      useSharedStorage = await AwaitableAlert(
        i18n.t('downloads.shared_storage_choice_title'),
        i18n.t('downloads.shared_storage_choice_message'),
        { text: i18n.t('downloads.shared_storage_app_only'), style: 'cancel' },
        {
          text: i18n.t(sharedFolderUri ? 'downloads.shared_storage_use_folder' : 'downloads.shared_storage_choose_folder'),
          style: 'default',
        },
      );
      if (useSharedStorage && !sharedFolderUri) {
        sharedFolderUri = await pickSharedModelFolder();
        if (!sharedFolderUri) return false;
      }
    }

    setModelDownloadUrl(model.downloadUrl);
    activeDownload.current = { modelName: model.name || model.modelId, jobId: null, sharedDownloadId: null, cancelled: false };
    try {
      activateKeepAwake();
      // This client keeps inference/downloads in the foreground rather than hiding long model transfers.
      showToast(i18n.t('downloads.keep_app_open'), 'long');
      uiStore.setSessionKey('@downloadInProgress', true, uiStore.globalEvents.MODEL_DOWNLOAD_STARTED);
      if (useSharedStorage) {
        await downloadToSharedStorage(model);
        setDownloadProgress(100);
      } else {
        const storageLocation = resolveDestinationPathFromGGUFUrl(model.downloadUrl);
        await downloadToStorage({
          fromUrl: model.downloadUrl,
          toFile: storageLocation,
          title: i18n.t('downloads.notification_downloading_title'),
          body: i18n.t('downloads.notification_downloading_body', { model: model.modelId }),
        });
      }
      setDownloadedModels(prev => ({ ...prev, [model.modelId]: true }));
      PushNotifications.send('primary', {
        title: i18n.t('downloads.notification_complete_title'),
        body: i18n.t('downloads.notification_complete_body', { model: model.modelId }),
      });
      return await selectModel(model);
    } catch (error) {
      if (activeDownload.current?.cancelled) {
        console.log('Download cancelled by the user');
        return false;
      }
      console.error('Download failed:', error);
      PushNotifications.send('primary', {
        title: i18n.t('downloads.download_failed_title'),
        body: i18n.t('downloads.download_failed_message'),
      });
      // Don't await the alert - a failure that lands while the phone is locked raises it while the activity
      // is paused, it never shows, and awaiting it held the `finally` cleanup (stuck progress bar).
      Alert.alert(
        i18n.t('downloads.download_failed_title'),
        error instanceof Error && error.message ? error.message : i18n.t('downloads.download_failed_message'),
        [{ text: i18n.t('common.ok'), style: 'default' }]
      );
      return false;
    } finally {
      // Always clear the active download so cards leave the progress state on
      // success as well as failure - previously this only happened in the catch.
      activeDownload.current = null;
      setModelDownloadUrl(null);
      setDownloadProgress(0);
      deactivateKeepAwake();
      uiStore.deleteSessionKey('@downloadInProgress', uiStore.globalEvents.MODEL_DOWNLOAD_COMPLETE);
    }
  };


  /**
   * Ask to confirm, then stop the in-flight model download. The partial `.part` file is removed by
   * `downloadFileAtomic` and `downloadModel` resolves false without the failure alert.
   * @returns True if the download was cancelled
   */
  const cancelDownload = async () => {
    const job = activeDownload.current;
    if (!job) return false;
    const confirmed = await AwaitableAlert(
      i18n.t('downloads.cancel_download_title'),
      i18n.t('downloads.cancel_download_message', { model: job.modelName }),
      { text: i18n.t('downloads.keep_downloading'), style: 'cancel' },
      { text: i18n.t('downloads.cancel_download'), style: 'destructive' },
    );
    // The download may have finished (or failed) while the dialog was up.
    if (!confirmed || activeDownload.current !== job) return false;
    job.cancelled = true;
    if (job.sharedDownloadId) await cancelSharedGGUFDownload(job.sharedDownloadId).catch(() => { });
    else if (job.jobId !== null) RNFS.stopDownload(job.jobId);
    return true;
  };

  const uninstallModel = async (model: any) => {
    if (model.isShared) {
      const isArabic = i18n.language.toLowerCase().startsWith('ar');
      const shouldUnlink = await AwaitableAlert(
        isArabic ? 'إزالة النموذج من القائمة؟' : 'Remove model from this list?',
        isArabic
          ? 'سيُزال مرجع Shbeeb One وإذنه فقط. لن يُحذف ملف GGUF من مجلدك المشترك، وقد تحتاج التطبيقات الأخرى إلى اختيار الملف بنفسها.'
          : 'This removes Shbeeb One’s reference and read permission only. The GGUF file stays in your shared folder; other apps must select it themselves.',
        { text: i18n.t('common.cancel'), style: 'cancel' },
        { text: isArabic ? 'إزالة المرجع' : 'Remove reference', style: 'destructive' },
      );
      if (!shouldUnlink) return false;

      try {
        if (selectedModel === model.modelId) {
          await LLMProvider?.unloadModel?.();
          setSelectedModel(null);
          await uiStore.setToStorage('llmPreference', {
            ...llmPreferences,
            config: { ...llmPreferences.config, model: null },
          });
          await fetchLLMPreference();
        }
        if (model.isImported) await ImportedModels.remove(model.modelId);
        else await SharedModelLocations.remove(model.modelId);
        if (model.sharedUri) {
          const [importedModels, sharedLocations] = await Promise.all([
            ImportedModels.list(),
            SharedModelLocations.list(),
          ]);
          const stillReferenced = importedModels.some(item => item.sharedUri === model.sharedUri)
            || sharedLocations.some(item => item.uri === model.sharedUri);
          if (!stillReferenced) await releaseSharedGGUF(model.sharedUri).catch(() => { });
        }
        const privatePath = model.downloadUrl
          ? resolveDestinationPathFromGGUFUrl(model.downloadUrl)
          : null;
        const stillInstalledPrivately = privatePath ? await RNFS.exists(privatePath) : false;
        setDownloadedModels(prev => ({ ...prev, [model.modelId]: stillInstalledPrivately }));
        return true;
      } catch (error) {
        console.error('Failed to unlink shared model:', error);
        return false;
      }
    }

    const shouldUninstall = await AwaitableAlert(
      i18n.t('downloads.uninstall_model_title'),
      i18n.t('downloads.uninstall_model_message'),
      { text: i18n.t('common.cancel'), style: 'cancel' },
      { text: i18n.t('common.uninstall'), style: 'destructive' },
    );

    if (!shouldUninstall) return false;

    try {
      const path = resolveDestinationPathFromGGUFUrl(model.downloadUrl);
      // Models added from Hugging Face only exist while installed - drop the entry too.
      if (model.isImported) await ImportedModels.remove(model.modelId);
      if (model.mmproj?.downloadUrl) {
        const mmprojPath = resolveDestinationPathFromGGUFUrl(model.mmproj.downloadUrl);
        if (await RNFS.exists(mmprojPath)) await RNFS.unlink(mmprojPath).catch(() => { });
      }
      if (await RNFS.exists(path)) {
        await RNFS.unlink(path);

        // Update downloaded models state
        setDownloadedModels(prev => ({
          ...prev,
          [model.modelId]: false,
        }));

        // If this was the selected model, clear the selection
        if (selectedModel === model.modelId) {
          setSelectedModel(null);
          await uiStore.setToStorage('llmPreference', {
            ...llmPreferences,
            config: { ...llmPreferences.config, model: null },
          });
          await fetchLLMPreference();
        }
        return true;
      }
      if (model.isImported && selectedModel === model.modelId) {
        setSelectedModel(null);
        await uiStore.setToStorage('llmPreference', {
          ...llmPreferences,
          config: { ...llmPreferences.config, model: null },
        });
        await fetchLLMPreference();
      }
      return model.isImported === true;
    } catch (error) {
      console.error('Failed to uninstall model:', error);
      return false;
    }
  };

  const selectModel = async (model: { modelId?: string }) => {
    try {
      // Set selected model state first
      setSelectedModel(model?.modelId || null);

      console.log('selectModel::llmPreferences', llmPreferences);
      console.log('selectModel::model.modelId', model?.modelId);
      // Then update preferences
      await uiStore.setToStorage('llmPreference', {
        provider: llmPreferences?.provider ?? 'native', // if the provider is not set, default to native
        config: { ...llmPreferences.config, model: model?.modelId || null },
      });

      // Only fetch preferences if we need to
      if (model?.modelId !== llmPreferences.config.model) {
        await fetchLLMPreference();
      }
      return true;
    } catch (error) {
      console.error('Failed to select model:', error);
      return false;
    }
  };

  /**
   * Check if a model is downloaded
   * @param downloadUrl - The download URL of the model to check against
   */
  const checkModelDownloaded = async (downloadUrl: string): Promise<boolean> => {
    const storageLocation = resolveDestinationPathFromGGUFUrl(downloadUrl);
    return await RNFS.exists(storageLocation);
  };

  return {
    modelDownloadUrl,
    downloadProgress,
    downloadedModels,
    selectedModel,
    downloadModel,
    cancelDownload,
    uninstallModel,
    selectModel,
    runPreDownloadConfirmations,
  };
}
