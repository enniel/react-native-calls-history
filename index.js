import { NativeModules } from 'react-native';

class CallsHistory {
  static async load(limit, cursor) {
    if (!cursor) {
      return NativeModules.CallsHistory.load(limit);
    }

    return NativeModules.CallsHistory.loadWithCursor(limit, { cursor })
  }

  static async loadAll() {
    return NativeModules.CallsHistory.loadAll()
  }

  static registerOnChangeListener() {
    NativeModules.CallsHistory.registerOnChangeListener();
  }

  static removeOnChangeListener() {
    NativeModules.CallsHistory.removeOnChangeListener();
  }
}

export default CallsHistory;
