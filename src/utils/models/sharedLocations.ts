import uiStore from '@/store/UIStore';

export type SharedModelLocation = {
  modelId: string;
  uri: string;
  filename: string;
  sizeBytes: number;
  sha256?: string;
  linkedAt: string;
};

const SHARED_MODEL_LOCATIONS_KEY = 'shared_model_locations';

const SharedModelLocations = {
  async list(): Promise<SharedModelLocation[]> {
    const records = await uiStore.getFromStorage<SharedModelLocation[]>(SHARED_MODEL_LOCATIONS_KEY, []);
    return Array.isArray(records) ? records : [];
  },

  async find(modelId: string): Promise<SharedModelLocation | null> {
    const records = await SharedModelLocations.list();
    return records.find(record => record.modelId === modelId) ?? null;
  },

  async add(record: Omit<SharedModelLocation, 'linkedAt'>): Promise<SharedModelLocation[]> {
    const records = (await SharedModelLocations.list()).filter(item => item.modelId !== record.modelId);
    records.push({ ...record, linkedAt: new Date().toISOString() });
    await uiStore.setToStorage(SHARED_MODEL_LOCATIONS_KEY, records);
    return records;
  },

  async remove(modelId: string): Promise<SharedModelLocation[]> {
    const records = (await SharedModelLocations.list()).filter(item => item.modelId !== modelId);
    await uiStore.setToStorage(SHARED_MODEL_LOCATIONS_KEY, records);
    return records;
  },
};

export default SharedModelLocations;
