import { NativeModules } from 'react-native';

class CallsHistory {
  static async load(limit, cursor, search) {
    const filter = {}
    if (cursor) {
      filter.cursor = cursor;
    }
    if (search && search.length) {
      filter.search = search;
    }

    return NativeModules.CallsHistory.loadWithCursor(limit, filter)
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
