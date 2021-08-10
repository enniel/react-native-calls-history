declare module 'react-native-calls-history' {
  export enum CallsHistoryItemType {
    INCOMING = 'INCOMING',
    OUTGOING = 'OUTGOING',
    MISSED = 'MISSED',
    UNKNOWN = 'UNKNOWN',
  }
  export interface CallsHistoryItem {
    duration: number;
    name: string | null;
    photoUri: string | null;
    phoneNumber: string;
    timestamp: string;
    time: string;
    type: CallsHistoryItemType;
  }
  export interface CallsHistoryResult {
    items: CallsHistoryItem[];
    pagination: {
      after?: string | null;
      before?: string | null;
    };
  }
  export default class CallsHistory {
    static load(limit: number, cursor?: string | null, search?: string | null): Promise<CallsHistoryResult>;
    static loadAll(): Promise<CallsHistoryResult>;
    static registerOnChangeListener(): void;
    static removeOnChangeListener(): void;
  }
}
