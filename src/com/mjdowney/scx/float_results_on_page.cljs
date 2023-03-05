(ns com.mjdowney.scx.float-results-on-page
  (:require [clojure.string :as string]
            [com.mjdowney.scx.sci :as sci]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

(defonce window-global-to-check-if-already-injected (atom false))

(def xml-escapes {\" "&quot;" \' "&apos;" \< "&lt;" \> "&gt;" \& "&amp;"})
(defn escape-xml [s] (string/escape s xml-escapes))

(def ^:const app-id "com.mjdowney.scx.float-results-on-page")
(defonce state (r/atom {:msgs []}))

(defn app []
  [:div
   [:h3 "SCI eval results:"]
   (when-let [recent (peek (:msgs @state))]
     [:<>
      [:pre (:in recent)]
      [:pre ";=> " (:out recent)]])])

(defn on-message [msg sender respondf]
  (when (= (.-type msg) (str :com.mjdowney.scx.service-worker/msg))
    (let [data (.-data msg)
          ret (sci/eval-with-sci data)]
      (js/console.log "Got:" data)
      (js/console.log ";=>" ret)
      (swap! state update :msgs conj {:in data :out ret}))))

(defn inject-stylesheet [href]
  (let [link (gdom/createElement "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-type link) "text/css")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn inject-react-app []
  (let [react-app-ele (gdom/createElement "div")]
    (set! (.-id react-app-ele) app-id)
    (.appendChild (.-body js/document) react-app-ele)
    (rdom/render [app] react-app-ele)))

(defn init []
  (when-not @window-global-to-check-if-already-injected
    (reset! window-global-to-check-if-already-injected true)
    (js/chrome.runtime.onMessage.addListener on-message)
    (inject-stylesheet (js/chrome.runtime.getURL "float_results_on_page.css"))
    (inject-react-app)
    (js/console.log "Injected SCI result viewer.")))
