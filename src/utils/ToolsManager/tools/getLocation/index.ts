import { IStreamEvent } from "@/utils/AiProviders/baseOpenAILikeProvider";
import i18n from "@/i18n";

export type ILocation = {
    country: string;
    countryCode: string;
    region: string;
    regionName: string;
    city: string;
    zip: string;
    lat: number;
    lon: number;
    timezone: string;
    asn: string;
    query: string;
}

export default {
    id: 'getLocation',
    get name() { return i18n.t('tools.get_location.name'); },
    get description() { return i18n.t('tools.get_location.description'); },
    // Location lookup is disabled by default; this build does not call the upstream geolocation API.
    defaultEnabled: false,
    category: 'default',
    definition: {
        type: 'function',
        function: {
            name: 'get_location',
            description: 'Location lookup is unavailable in this Shbeeb build; no location is sent to an external service.',
            parameters: {
                type: 'object',
                properties: {},
                required: [],
            },
        },
    },
    config: {},
    execute: async function (_args: unknown, streamEmitter?: (event: IStreamEvent, data: any) => void) {
        try {
            streamEmitter?.('report_status', i18n.t('tools.get_location.status_looking_up'));
            const location = await this._getLocation();
            if (!location) return this.description;
            return JSON.stringify({ city: location?.city, state: location?.regionName, country: location?.country });
        } catch (error) {
            console.log('getLocationTool', error);
            return 'Error getting location';
        }
    },
    _getLocation: async function (): Promise<ILocation | null> {
        // Do not transmit IP-derived location to a third-party service in the Shbeeb build.
        return null;
    }
} as const;
