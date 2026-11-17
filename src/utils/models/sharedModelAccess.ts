import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

export type SharedGGUFSelection = {
  uri: string;
  name: string;
  sizeBytes: number;
};

export type OpenSharedGGUF = {
  token: string;
  path: string;
  sizeBytes: number;
};

export type SharedDownloadResult = SharedGGUFSelection & {
  sha256: string;
};

export type SharedDownloadProgress = {
  bytesWritten: number;
  totalBytes: number;
};

type NativeProgressEvent = SharedDownloadProgress & { downloadId: string };
type NativeCompleteEvent = SharedDownloadResult & { downloadId: string };
type NativeErrorEvent = { downloadId: string; message: string };

type SharedModelAccessNative = {
  pickGGUF(): Promise<SharedGGUFSelection | null>;
  pickSharedFolder(): Promise<string | null>;
  getSharedFolder(): Promise<string | null>;
  openGGUF(uri: string): Promise<OpenSharedGGUF>;
  closeGGUF(token: string): Promise<boolean>;
  releaseGGUF(uri: string): Promise<boolean>;
  startSharedDownload(downloadId: string, url: string, fileName: string, expectedSize: number): Promise<boolean>;
  cancelSharedDownload(downloadId: string): Promise<boolean>;
};

const nativeModule = NativeModules.SharedModelAccess as SharedModelAccessNative | undefined;

function requireNativeModule(): SharedModelAccessNative {
  if (Platform.OS !== 'android' || !nativeModule) {
    throw new Error('مكتبة النماذج المشتركة متاحة في نسخة Android فقط.');
  }
  return nativeModule;
}

export async function pickSharedGGUF(): Promise<SharedGGUFSelection | null> {
  return requireNativeModule().pickGGUF();
}

export async function pickSharedModelFolder(): Promise<string | null> {
  return requireNativeModule().pickSharedFolder();
}

export async function getSharedModelFolder(): Promise<string | null> {
  return requireNativeModule().getSharedFolder();
}

export async function openSharedGGUF(uri: string): Promise<OpenSharedGGUF> {
  return requireNativeModule().openGGUF(uri);
}

export async function closeSharedGGUF(token: string): Promise<void> {
  await requireNativeModule().closeGGUF(token);
}

export async function releaseSharedGGUF(uri: string): Promise<void> {
  await requireNativeModule().releaseGGUF(uri);
}

/** Download a GGUF directly to the user's SAF folder, with no app-private copy. */
export async function downloadSharedGGUF(
  url: string,
  fileName: string,
  expectedSize: number,
  onProgress: (progress: SharedDownloadProgress) => void,
  onStarted?: (downloadId: string) => void,
): Promise<SharedDownloadResult> {
  const native = requireNativeModule();
  const transferId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
  const emitter = new NativeEventEmitter(native as never);
  onStarted?.(transferId);

  return new Promise<SharedDownloadResult>((resolve, reject) => {
    let settled = false;
    const subscriptions = [
      emitter.addListener('SharedModelDownloadProgress', (event: NativeProgressEvent) => {
        if (event.downloadId === transferId) onProgress({ bytesWritten: event.bytesWritten, totalBytes: event.totalBytes });
      }),
      emitter.addListener('SharedModelDownloadComplete', (event: NativeCompleteEvent) => {
        if (event.downloadId !== transferId || settled) return;
        settled = true;
        cleanup();
        resolve({ uri: event.uri, name: event.name, sizeBytes: event.sizeBytes, sha256: event.sha256 });
      }),
      emitter.addListener('SharedModelDownloadError', (event: NativeErrorEvent) => {
        if (event.downloadId !== transferId || settled) return;
        settled = true;
        cleanup();
        reject(new Error(event.message));
      }),
    ];

    const cleanup = () => subscriptions.forEach(subscription => subscription.remove());
    native.startSharedDownload(transferId, url, fileName, expectedSize).catch(error => {
      if (settled) return;
      settled = true;
      cleanup();
      reject(error instanceof Error ? error : new Error(String(error)));
    });
  });
}

export async function cancelSharedGGUFDownload(transferId: string): Promise<void> {
  await requireNativeModule().cancelSharedDownload(transferId);
}
