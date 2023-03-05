(ns com.mjdowney.scx.float-results-on-page)

(defonce window-global-to-check-if-already-injected (atom false))

(defn init []
  (when-not @window-global-to-check-if-already-injected
    (reset! window-global-to-check-if-already-injected true)

    (js/chrome.runtime.onMessage.addListener
      (fn [msg sender respondf]
        (js/console.log #js {:msg msg :sender sender})))

    (js/alert "Injected")))
