# Load calls history with cursor based navigation


## Installation:
Run `npm i react-native-calls-history`


### Android

#### React Native 0.60+
`auto links the module`
#### React Native <= 0.59
##### Auto
`react-native link react-native-calls-history`

#### Manual

* Edit your `android/settings.gradle` to look like this (exclude +)

```diff
+ include ':react-native-calls-history'
+ project(':react-native-calls-history').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-calls-history/android')
```

* Edit your `android/app/build.gradle` (note: **app** folder) to look like this (exclude +)

 ```diff
dependencies {
 + implementation project(':react-native-calls-history')
 }
 ```

* Edit your `MainApplication.java` from ( `android/app/src/main/java/...`) to look like this (exclude +)
```diff
+ import ru.enniel.callshistory.CallsHistoryPackage;

@Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
          new MainReactPackage(),
+         new CallsHistoryPackage()
      );
    }
```

## Usage

```typescript
import { useState } from 'react';
import { PermissionsAndroid } from 'react-native';
import CallsHistory, { CallsHistoryItem } from 'react-native-calls-history';

interface State {
  loadingFirst: boolean;
  loadingBefore: boolean;
  loadingAfter: boolean;
  items: CallsHistoryItem[];
  pagination: {
    before?: string | null;
    after?: string | null;
  };
}

const useCallsHistory = () => {
  const [state, setState] = useState<State>({
    loadingFirst: false,
    loadingBefore: false,
    loadingAfter: false,
    items: [],
    pagination: {
      before: null,
      after: null,
    },
  });

  const loadFirst = async () => {
    setState((prev) => ({
      ...prev,
      loading: true,
    }));
    const { items, pagination } = await CallsHistory.load(10)
    setState((prev) => ({
      ...prev,
      loading: false,
      items,
      pagination
    }));
  };

  const loadBefore = async () => {
    setState((prev) => ({
      ...prev,
      loadingBefore: true,
    }));
    const { items, pagination } = await CallsHistory.load(10, state.pagination.before)
    setState((prev) => ({
      ...prev,
      loadingBefore: false,
      items: [
        ...items,
        ...state.items,
      ],
      pagination: {
        before: pagination.before,
        after: prev.pagination.after,
      },
    }));
  };

  const loadAfter = async () => {
    setState((prev) => ({
      ...prev,
      loadingAfter: true,
    }));
    const { items, pagination } = await CallsHistory.load(10, state.pagination.after)
    setState((prev) => ({
      ...prev,
      loadingAfter: false,
      items: [
        ...state.items,
        ...items,
      ],
      pagination: {
        before: prev.pagination.before,
        after: pagination.after,
      },
    }));
  };

  return {
    state,
    loadFirst,
    loadBefore,
    loadAfter,
  };
};

const CallsHistoryScreen = () => {
  const { state, loadFirst, loadBefore, loadAfter } = useCallsHistory();

  useEffect(() => {
    const granted = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_CALL_LOG)
    if (granted === PermissionsAndroid.RESULTS.GRANTED) {
      loadFirst();

      CallsHistory.registerOnChangeListener();
      const emitter = new NativeEventEmitter();
      callHistoryChangeDataListener = emitter.addListener('CallsHistoryChangeData', () => {
        loadBefore();
      });
    }

    return () => {
      CallsHistory.removeOnChangeListener();
      callHistoryChangeDataListener?.remove();
    };
  }, []);

  const onLoadMore = () => loadAfter();

  ...
};
```
