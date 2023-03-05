(ns com.mjdowney.scx.float-results-on-page
  (:require [clojure.string :as string]
            [com.mjdowney.scx.sci :as sci]))

(defonce window-global-to-check-if-already-injected (atom false))

(def xml-escapes {\" "&quot;" \' "&apos;" \< "&lt;" \> "&gt;" \& "&amp;"})
(defn escape-xml [s] (string/escape s xml-escapes))

(defn init []
  (when-not @window-global-to-check-if-already-injected
    (reset! window-global-to-check-if-already-injected true)

    (js/chrome.runtime.onMessage.addListener
      (fn [msg sender respondf]
        (when (= (.-type msg) (str :com.mjdowney.scx.service-worker/msg))
          (let [data (.-data msg)
                ret (sci/eval-with-sci data)]
            (js/console.log "Got:" data)
            (js/console.log ";=>" ret)
            (let [ele (js/document.createElement "div")]
              (set! (.-innerHTML ele)
                (str "<pre>" (escape-xml (.stringify js/JSON msg)) "\n;=> "
                  (escape-xml ret)
                  "</pre>"))
              (.appendChild (.-body js/document) ele))))))

    (js/console.log "Injected SCI result viewer.")))
